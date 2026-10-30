package com.shortvideoscripagent.xhsagentyunying.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbMapTypeHandler;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.PgListTypeHandler;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.UuidTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MyBatis-Plus 扩展配置。
 * <p>
 * 注册 {@link UuidTypeHandler} 为 UUID 的全局默认类型处理器，
 * 并注册三个 jsonb 类型处理器（Map / String / List）为
 * {@code (javaType, JdbcType.OTHER)} 的全局映射。
 * <p>
 * <b>为什么必须全局注册</b>：实体一旦标注 {@code @TableName(autoResultMap = true)}，
 * MyBatis-Plus 构建 ResultMap 时会严格校验每个属性的 typeHandler，
 * 未显式声明的 {@link java.util.UUID} 字段会抛
 * {@code IllegalStateException: No typehandler found for property xxx}，
 * 导致整个 Spring 容器启动失败。PostgreSQL JDBC 驱动的原生 UUID 支持只覆盖驱动自动映射路径，
 * 不覆盖 MyBatis-Plus 的 ResultMap 构建路径。
 * <p>
 * <b>为什么 jsonb 处理器也必须全局注册（P0 级缺陷修复）</b>：
 * <ul>
 *   <li>{@code autoResultMap = true} <b>只对 MyBatis-Plus 内置方法</b>
 *       （{@code selectById} / {@code selectList} / {@code selectPage} …）生效；</li>
 *   <li>自定义 {@code @Select} 走的是 MyBatis 原生的「自动映射」路径，
 *       它按「属性类型 + 列的 JDBC 类型」去全局注册表里查处理器；</li>
 *   <li>PostgreSQL 的 jsonb 列在 JDBC 元数据里是 {@link JdbcType#OTHER}，
 *       而项目里的 jsonb 处理器只写在实体的 {@code @TableField(typeHandler = ...)} 上，
 *       <b>没有</b>进全局注册表 → 自动映射找不到处理器 → jsonb 列<b>静默变成 null</b>。</li>
 * </ul>
 * 实际表现（修复前）：
 * <ul>
 *   <li>{@code GET /v1/plans} 返回的 {@code includedUnits} / {@code featureFlags} 全是 {@code {}}，
 *       而库里明明有数据；</li>
 *   <li>{@code GET /v1/workspaces/{id}/subscription} 返回的 {@code planCode} / {@code price} 全是 null，
 *       导致「变更套餐」NPE 500。</li>
 * </ul>
 * 注册为 {@code (javaType, JdbcType.OTHER)} 后，
 * 自动映射路径与 ResultMap 路径行为一致，且不影响其它列类型
 * （VARCHAR / INT / TIMESTAMPTZ 的 JDBC 类型都不是 OTHER）。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 将 UUID 与 jsonb 类型处理器注册进 MyBatis 全局注册表。
     */
    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return (MybatisConfiguration configuration) -> {
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            // 注意：必须用 (javaTypeClass, typeHandlerInstance) 重载。
            // register(Class, Class) 的语义是 (javaType, jdbcType)，传 Class 会让 MyBatis
            // 误把 UUID 当成 TypeHandler 去实例化，抛 "Unable to find a usable constructor for class java.util.UUID"。
            registry.register(java.util.UUID.class, UuidTypeHandler.UUID_TYPE_HANDLER);

            // ── jsonb 处理器（见类注释：自定义 @Select 的自动映射路径必须能查到它们） ──
            registry.register(Map.class, JdbcType.OTHER, new JsonbMapTypeHandler());
            registry.register(String.class, JdbcType.OTHER, new JsonbStringTypeHandler());
            // List 有两种列形态（jsonb 数组 / PG TEXT[]），用 PgListTypeHandler 在运行时按
            // 结果集元数据分派，避免注册成 jsonb 后把 TEXT[] 列（如 app.topic_pool.tags）读挂。
            registry.register(List.class, JdbcType.OTHER, new PgListTypeHandler());
        };
    }

    /**
     * 注册分页插件。
     * <p>
     * <b>为什么必须注册</b>：使用 {@code IPage} 作为 Mapper 返回值时，
     * MyBatis-Plus 依赖分页拦截器自动生成 count 查询，从而填充 {@code page.getTotal()}。
     * 未注册时 {@code total} 恒为 0，前端分页器会认为「没有数据」而无法翻页。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 单页上限，避免恶意 size 参数拖垮数据库
        pagination.setMaxLimit(200L);
        // 溢出总页数后返回空列表而不是回到第一页
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
