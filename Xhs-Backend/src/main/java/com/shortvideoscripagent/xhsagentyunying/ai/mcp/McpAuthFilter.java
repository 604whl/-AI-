package com.shortvideoscripagent.xhsagentyunying.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.auth.AuthService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * MCP 端点鉴权过滤器。
 * <p>
 * Spring AI MCP Server 默认挂载在 {@code /mcp}（完整路径 {@code /api/mcp}，含 context-path）。
 * 该路径不走 {@link com.shortvideoscripagent.xhsagentyunying.auth.AuthInterceptor}，
 * 而由此 Filter 在 MCP 协议处理前完成身份校验并绑定 {@link McpRuntimeContext}。
 * </p>
 *
 * <h3>支持的鉴权方式（按优先级）</h3>
 * <ol>
 *   <li><b>Bearer JWT</b> — 与 REST API 相同，推荐生产环境使用</li>
 *   <li><b>X-Mcp-Api-Key + X-Mcp-User-Id</b> — 本地/集成测试用，需配置 {@code app.agent.mcp.api-key}</li>
 * </ol>
 *
 * <h3>可选请求头</h3>
 * <ul>
 *   <li>{@code X-Mcp-Session-Id} — 指定虚拟会话 ID；缺省时自动生成</li>
 *   <li>{@code X-Mcp-Persona} — 覆盖默认人设（agency/mentor/senior）</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class McpAuthFilter extends OncePerRequestFilter {

    private static final int CODE_UNAUTHORIZED = 40101;

    /** MCP Streamable HTTP 默认端点（相对 servlet path，不含 context-path） */
    public static final String MCP_ENDPOINT_PREFIX = "/mcp";

    public static final String HEADER_MCP_API_KEY = "X-Mcp-Api-Key";
    public static final String HEADER_MCP_USER_ID = "X-Mcp-User-Id";
    public static final String HEADER_MCP_SESSION_ID = "X-Mcp-Session-Id";
    public static final String HEADER_MCP_PERSONA = "X-Mcp-Persona";

    private final AuthService authService;
    private final UserMapper userMapper;
    private final AppAgentProperties appAgentProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!appAgentProperties.getMcp().isEnabled()) {
            return true;
        }
        String path = servletPath(request);
        return !path.startsWith(MCP_ENDPOINT_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            AuthResult auth = resolveAuth(request);
            if (auth == null) {
                writeUnauthorized(response);
                return;
            }

            User user = userMapper.selectById(auth.userId());
            if (user == null) {
                writeUnauthorized(response);
                return;
            }

            String sessionId = resolveSessionId(request, auth.userId());
            String persona = resolvePersona(request, user);

            RequestContext.setUserId(auth.userId());
            McpRuntimeContext.bind(auth.userId(), sessionId, persona);

            filterChain.doFilter(request, response);
        } finally {
            McpRuntimeContext.clear();
            RequestContext.clear();
        }
    }

    /**
     * 解析调用者身份：优先 JWT，其次 API Key + User-Id。
     */
    private AuthResult resolveAuth(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            Long userId = authService.resolveUserIdFromAccessToken(token);
            if (userId != null) {
                return new AuthResult(userId, "jwt");
            }
        }

        String configuredKey = appAgentProperties.getMcp().getApiKey();
        if (configuredKey != null && !configuredKey.isBlank()) {
            String apiKey = request.getHeader(HEADER_MCP_API_KEY);
            if (configuredKey.equals(apiKey)) {
                Long userId = parseUserIdHeader(request.getHeader(HEADER_MCP_USER_ID));
                if (userId != null) {
                    return new AuthResult(userId, "api_key");
                }
            }
        }

        return null;
    }

    private String resolveSessionId(HttpServletRequest request, Long userId) {
        String header = request.getHeader(HEADER_MCP_SESSION_ID);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String prefix = appAgentProperties.getMcp().getSessionIdPrefix();
        return prefix + userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String resolvePersona(HttpServletRequest request, User user) {
        String header = request.getHeader(HEADER_MCP_PERSONA);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        if (user.getDefaultPersona() != null && !user.getDefaultPersona().isBlank()) {
            return user.getDefaultPersona();
        }
        return "agency";
    }

    private Long parseUserIdHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(CODE_UNAUTHORIZED, "unauthorized"));
    }

    private static String servletPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    private record AuthResult(Long userId, String mode) {
    }
}
