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
import java.util.Map;

/**
 * JSONB ↔ {@code Map<String, Object>} 类型处理器。
 * <p>
 * PostgreSQL 的 jsonb 列无法直接绑定 {@code java.util.Map}，
 * 这里统一序列化为 JSON 字符串并以 {@code PGobject(jsonb)} 写入；
 * 读取时按 JSON 文本反序列化为 Map。
 * </p>
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setObject(i, toPgJsonb(MAPPER.writeValueAsString(parameter)), Types.OTHER);
        } catch (Exception ex) {
            throw new SQLException("Failed to bind jsonb map parameter", ex);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new SQLException("Failed to read jsonb map column", ex);
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
