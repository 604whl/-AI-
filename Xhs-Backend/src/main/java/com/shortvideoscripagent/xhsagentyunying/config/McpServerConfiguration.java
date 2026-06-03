package com.shortvideoscripagent.xhsagentyunying.config;

import com.shortvideoscripagent.xhsagentyunying.ai.mcp.McpAuthFilter;
import com.shortvideoscripagent.xhsagentyunying.auth.AuthService;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * MCP Server 相关 Spring Bean 注册。
 * <p>
 * 仅在 {@code app.agent.mcp.enabled=true} 时激活，与
 * {@code spring.ai.mcp.server.enabled} 保持一致（见 application.yml）。
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.agent.mcp", name = "enabled", havingValue = "true")
public class McpServerConfiguration {

    /**
     * 注册 MCP 鉴权过滤器，优先级高于 {@link com.shortvideoscripagent.xhsagentyunying.auth.AuthInterceptor}。
     * URL 模式对应 Spring AI Streamable HTTP 默认端点 {@code /mcp}。
     */
    @Bean
    public FilterRegistrationBean<McpAuthFilter> mcpAuthFilterRegistration(
            AuthService authService,
            UserMapper userMapper,
            AppAgentProperties appAgentProperties,
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<McpAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpAuthFilter(authService, userMapper, appAgentProperties, objectMapper));
        registration.addUrlPatterns("/mcp", "/mcp/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setName("mcpAuthFilter");
        return registration;
    }
}
