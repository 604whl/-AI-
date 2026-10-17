package com.shortvideoscripagent.xhsagentyunying.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Workspace 路径级鉴权拦截器：在 AuthInterceptor 之后执行。
 * <p>
 * - 若请求路径不在白名单（如 /v1/auth/*、/v1/health、Swagger 等），强制要求：
 *   - Header {@code X-Workspace-Id} 非空
 *   - 当前用户必须是该 workspace 的 active member
 *   - 把 workspaceId 写入 {@link WorkspaceContext} 供业务方法使用
 * <p>
 * 失败直接返回 401（未携带 workspaceId）或 403（无访问权限）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceInterceptor implements HandlerInterceptor {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;

    private static final Set<String> WORKSPACE_EXEMPT_PATHS = Set.of(
            "/v1/auth/login",
            "/v1/auth/register",
            "/v1/auth/refresh",
            "/v1/auth/logout",
            "/v1/auth/profile",
            "/v1/auth/usage",
            "/v1/auth/change-password",
            "/v1/health",
            "/actuator/health",
            "/v1/system/dict",
            "/v1/system/industries"
    );

    private static final Set<String> WORKSPACE_EXEMPT_PREFIXES = Set.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/doc.html",
            "/webjars/",
            "/mcp"
    );

    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = resolvePath(request);
        if (isExempt(path)) {
            return true;
        }

        Long userId = RequestContext.getUserId();
        if (userId == null) {
            // AuthInterceptor 没跑通，但被路由到了这里。直接拒。
            writeUnauthorized(response, "unauthorized");
            return false;
        }

        String workspaceId = request.getHeader("X-Workspace-Id");
        if (workspaceId == null || workspaceId.isBlank()) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    ApiResponse.fail(40003, "workspace_id_required：请在 Header X-Workspace-Id 提供当前工作区 ID"));
            return false;
        }

        try {
            workspaceService.resolveAndAuthorize(userId, workspaceId);
            WorkspaceContext.setUserId(userId);
            return true;
        } catch (com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException ex) {
            log.warn("workspace access denied for user={} workspace={}: {}",
                    userId, workspaceId, ex.getMessage());
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    ApiResponse.fail(ex.getCode(), ex.getMessage()));
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        WorkspaceContext.clear();
    }

    private boolean isExempt(String path) {
        if (WORKSPACE_EXEMPT_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : WORKSPACE_EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String resolvePath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiResponse.fail(CODE_UNAUTHORIZED, message));
    }

    private void writeJson(HttpServletResponse response, int status, Object body) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), body);
        } catch (Exception e) {
            log.error("Failed to write JSON response: {}", e.getMessage());
        }
    }
}