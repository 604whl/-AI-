package com.shortvideoscripagent.xhsagentyunying.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.UuidTypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 扩展配置。
 * <p>
 * 注册 {@link UuidTypeHandler} 为 UUID 的全局默认类型处理器。
 * <p>
 * <b>为什么必须全局注册</b>：实体一旦标注 {@code @TableName(autoResultMap = true)}，
 * MyBatis-Plus 构建 ResultMap 时会严格校验每个属性的 typeHandler，
 * 未显式声明的 {@link java.util.UUID} 字段会抛
 * {@code IllegalStateException: No typehandler found for property xxx}，
 * 导致整个 Spring 容器启动失败。PostgreSQL JDBC 驱动的原生 UUID 支持只覆盖驱动自动映射路径，
 * 不覆盖 MyBatis-Plus 的 ResultMap 构建路径。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 将 UUID 类型处理器注册进 MyBatis 全局注册表。
     */
    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return (MybatisConfiguration configuration) -> {
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            // 注意：必须用 (javaTypeClass, typeHandlerInstance) 重载。
            // register(Class, Class) 的语义是 (javaType, jdbcType)，传 Class 会让 MyBatis
            // 误把 UUID 当成 TypeHandler 去实例化，抛 "Unable to find a usable constructor for class java.util.UUID"。
            registry.register(java.util.UUID.class, UuidTypeHandler.UUID_TYPE_HANDLER);
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