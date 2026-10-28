package com.shortvideoscripagent.xhsagentyunying.config.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL {@code jsonb} 数组列（{@code List<String>}）的类型处理器。
 * <p>
 * 适用场景：数据库列是 {@code jsonb}（存的是 JSON 数组文本），但实体字段是
 * {@code List<String>}。此时既不能用默认的 {@code List} 处理器（MyBatis 没有），
 * 也不能用 {@link TextArrayTypeHandler}（那是给真正的 PostgreSQL {@code TEXT[]} 列用的）。
 * <p>
 * 写入时把 List 序列化成 JSON 数组文本再按 jsonb 绑定；读取时把 jsonb 文本
 * 反序列化成 List。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> REF = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
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
        return parse(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static List<String> parse(String raw) throws SQLException {
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
