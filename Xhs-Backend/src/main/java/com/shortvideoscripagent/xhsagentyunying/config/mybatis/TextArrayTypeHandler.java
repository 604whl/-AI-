package com.shortvideoscripagent.xhsagentyunying.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL {@code TEXT[]} ↔ {@code List<String>} 的类型处理器。
 * <p>
 * 项目中 {@code kb.kb_document.persona / tags}、{@code app.topic_pool.tags} 均为 {@code TEXT[]}，
 * MyBatis 默认没有 {@code List<String>} 的处理器，需要显式声明本处理器。
 * </p>
 * <p>
 * 刻意不加 {@code @MappedTypes}：避免被 MyBatis 注册为全局 {@code List} 处理器，
 * 污染其它 {@code IN (...)} 参数。只在实体字段上通过
 * {@code @TableField(typeHandler = TextArrayTypeHandler.class)} 显式引用。
 * </p>
 */
@MappedJdbcTypes(JdbcType.ARRAY)
public class TextArrayTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        String[] values = parameter == null ? new String[0] : parameter.toArray(new String[0]);
        Array array = ps.getConnection().createArrayOf("text", values);
        ps.setArray(i, array);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
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
}
