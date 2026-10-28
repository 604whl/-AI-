package com.shortvideoscripagent.xhsagentyunying.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * UUID 类型处理器。
 * <p>
 * <b>为什么需要</b>：当实体使用 {@code @TableName(autoResultMap = true)} 时，MyBatis-Plus 会为
 * ResultMap 做严格校验，未显式指定 typeHandler 的属性会直接抛
 * {@code IllegalStateException: No typehandler found for property xxx}。
 * PostgreSQL JDBC 驱动虽原生支持 {@code java.util.UUID}，但那只在驱动自动映射路径生效，
 * 不覆盖 MyBatis-Plus 的 ResultMap 构建路径。
 * <p>
 * 该 handler 通过 {@link #UUID_TYPE_HANDLER} 注册为全局默认（见 MybatisPlusConfig），
 * 同时也可在字段上用 {@code @TableField(typeHandler = UuidTypeHandler.class)} 显式引用。
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    /** 全局注册用的类型处理器实例 */
    public static final UuidTypeHandler UUID_TYPE_HANDLER = new UuidTypeHandler();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, java.sql.Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    /**
     * 兼容 PostgreSQL 驱动返回 {@link UUID}、{@link String} 或 {@code null} 三种情况。
     */
    private static UUID toUuid(Object raw) throws SQLException {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String s) {
            return UUID.fromString(s);
        }
        throw new SQLException("Cannot convert value of type " + raw.getClass().getName() + " to UUID");
    }
}