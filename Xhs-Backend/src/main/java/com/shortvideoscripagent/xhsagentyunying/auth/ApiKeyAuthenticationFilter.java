package com.shortvideoscripagent.xhsagentyunying.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局 API Key 过滤器：支持在所有 API 端点使用 X-API-Key 认证。
 * <p>
 * 此 Filter 优先级低于 Spring Security UsernamePasswordAuthenticationFilter，
 * 但可以在 Spring Security 认证之前尝试从 X-API-Key 提取身份。
 * </p>
 *
 * <h3>支持的 Header</h3>
 * <ul>
 *   <li>{@code X-API-Key: xhs_sk_xxx} — workspace API Key</li>
 *   <li>{@code X-Workspace-Id: ws_xxx} — 可选，明确指定 workspace</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";
    public static final String ATTR_WORKSPACE_ID = "auth.workspaceId";
    public static final String ATTR_USER_ID = "auth.userId"; // API Key 无用户态，仅 workspaceId

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String workspaceId = apiKeyService.validateAndGetWorkspaceId(apiKey);
            if (workspaceId == null) {
                writeUnauthorized(response, "invalid_api_key");
                return;
            }

            // 从 Header 覆盖 workspace（如果提供）
            String workspaceIdHeader = request.getHeader(HEADER_WORKSPACE_ID);
            if (workspaceIdHeader != null && !workspaceIdHeader.isBlank()) {
                if (!workspaceId.equals(workspaceIdHeader)) {
                    writeForbidden(response, "workspace_mismatch");
                    return;
                }
            }

            // 放入 request attribute 供后续 Controller / Interceptor 使用
            request.setAttribute(ATTR_WORKSPACE_ID, workspaceId);
            // API Key 场景无用户态（仅 workspace 隔离）
            request.setAttribute(ATTR_USER_ID, null);

            log.debug("API Key authenticated: workspace={}", workspaceId);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("API Key authentication error: {}", e.getMessage());
            writeUnauthorized(response, "api_key_auth_error");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(40101, message));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(40301, message));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 跳过健康检查等路径
        String path = request.getRequestURI();
        return path.startsWith("/api/actuator")
                || path.startsWith("/api/swagger")
                || path.startsWith("/api/v3/api-docs")
                || path.startsWith("/api/doc.html");
    }
}
