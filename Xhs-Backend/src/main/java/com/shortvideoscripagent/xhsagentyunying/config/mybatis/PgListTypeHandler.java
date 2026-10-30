package com.shortvideoscripagent.xhsagentyunying.config.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code List<String>} 的「自适应」类型处理器：同时兼容 PostgreSQL 的
 * {@code TEXT[]}（原生数组列）与 {@code jsonb}（JSON 数组文本列）。
 * <p>
 * <b>背景</b>：MyBatis 的「自动映射」路径（自定义 {@code @Select} 走的就是这条路）
 * 只知道「属性类型 + 列的 JDBC 类型」，拿不到实体字段上写的
 * {@code @TableField(typeHandler = ...)}。于是同一个 Java 类型
 * {@code List<String>} 可能对应两种截然不同的列：
 * <ul>
 *   <li>{@code app.topic_pool.tags} / {@code app.kb_document.tags} → {@code TEXT[]}
 *       （JDBC 类型 {@link Types#ARRAY}）；</li>
 *   <li>{@code app.api_key.scopes} / {@code app.review_task.risk_tags} → {@code jsonb}
 *       （JDBC 类型 {@link Types#OTHER}）。</li>
 * </ul>
 * 光看 Java 类型无法区分，必须按<b>结果集元数据里的真实列类型</b>分派。
 * <p>
 * <b>注册方式</b>：作为全局 {@code (List, JdbcType.OTHER)} 的<b>唯一</b>处理器注册
 * （见 {@code MybatisPlusConfig}）。当列的 JDBC 类型不是 {@code OTHER} 时，
 * MyBatis 会回退到 {@code pickSoleHandler}——由于本类型映射里<b>只有一项</b>，
 * 回退恰好落到本处理器，再由运行时分派决定真实语义。
 * <p>
 * <b>为什么不能只注册 jsonb 版本</b>：只注册 {@code (List, OTHER)} 时，
 * {@code TEXT[]} 列（{@code ARRAY}）会因为「精确匹配失败 → 回退到唯一处理器」，
 * 被错误地当作 jsonb 解析，抛
 * {@code Failed to parse jsonb array}（PG 数组字面量 {@code {a,b}} 不是合法 JSON）。
 * <p>
 * <b>写入</b>：显式指定 {@code jdbcType = ARRAY} 时按 PG 数组绑定，否则按 jsonb 绑定。
 * 实体字段上显式声明的 {@code @TableField(typeHandler = TextArrayTypeHandler.class)}
 * / {@code @TableField(typeHandler = JsonbListTypeHandler.class)} 优先级更高，不受本类影响。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PgListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> REF = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        if (jdbcType == JdbcType.ARRAY) {
            String[] values = parameter == null ? new String[0] : parameter.toArray(new String[0]);
            Array array = ps.getConnection().createArrayOf("text", values);
            ps.setArray(i, array);
            return;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(parameter == null ? new ArrayList<>() : parameter);
        } catch (Exception ex) {
            throw new SQLException("Failed to serialise List<String> to jsonb", ex);
        }
        ps.setObject(i, toPgJsonb(json), Types.OTHER);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return read(rs, columnName, resolveColumnType(rs, columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return read(rs, columnIndex, resolveColumnType(rs, columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        // CallableStatement 无 getMetaData，按 jsonb 语义处理（项目中无 CallableStatement 路径）。
        return parseJson(cs.getString(columnIndex));
    }

    /**
     * 读取结果集列：按真实列类型分派到「PG 数组」或「JSON 数组」解析。
     */
    private static List<String> read(ResultSet rs, int columnIndex, int columnType) throws SQLException {
        if (columnType == Types.ARRAY) {
            return toList(rs.getArray(columnIndex));
        }
        return parseJson(rs.getString(columnIndex));
    }

    private static List<String> read(ResultSet rs, String columnName, int columnType) throws SQLException {
        if (columnType == Types.ARRAY) {
            return toList(rs.getArray(columnName));
        }
        return parseJson(rs.getString(columnName));
    }

    /**
     * 取列的真实 JDBC 类型；元数据不可用时按 jsonb（{@code OTHER}）处理。
     */
    private static int resolveColumnType(ResultSet rs, String columnName) {
        try {
            return resolveColumnType(rs, findColumnIndex(rs, columnName));
        } catch (SQLException ex) {
            return Types.OTHER;
        }
    }

    private static int findColumnIndex(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (columnName.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int resolveColumnType(ResultSet rs, int columnIndex) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            if (columnIndex < 1 || columnIndex > meta.getColumnCount()) {
                return Types.OTHER;
            }
            return meta.getColumnType(columnIndex);
        } catch (SQLException ex) {
            return Types.OTHER;
        }
    }

    private static List<String> toList(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object raw = array.getArray();
        List<String> values = new ArrayList<>();
        if (raw instanceof Object[] rawArray) {
            for (Object element : rawArray) {
                values.add(element == null ? null : String.valueOf(element));
            }
        }
        return values;
    }

    private static List<String> parseJson(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> values = MAPPER.readValue(raw, REF);
            return values == null ? new ArrayList<>() : values;
        } catch (Exception ex) {
            throw new SQLException("Failed to parse jsonb array", ex);
        }
    }

    private static Object toPgJsonb(String value) throws SQLException {
        try {
            Class<?> pgObjectClass = Class.forName("org.postgresql.util.PGobject");
            Object json = pgObjectClass.getDeclaredConstructor().newInstance();
            pgObjectClass.getMethod("setType", String.class).invoke(json, "jsonb");
            pgObjectClass.getMethod("setValue", String.class).invoke(json, value);
            return json;
        } catch (ReflectiveOperationException ex) {
            throw new SQLException("Failed to bind jsonb parameter", ex);
        }
    }
}
