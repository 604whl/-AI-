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

import java.util.List;
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

    /**
     * 精确路径白名单（不区分 HTTP 方法）。
     */
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
            "/v1/system/industries",
            "/v1/platform/admin"
    );

    /**
     * 前缀白名单 + 允许的 HTTP 方法列表。
     * 用途：解决同一前缀下不同方法需要不同待遇的场景（如 /v1/files/cover/ 仅 GET 豁免）。
     */
    private static final List<PathMethodRule> EXEMPT_RULES = List.of(
            new PathMethodRule("/swagger-ui",       Set.of("GET", "HEAD")),
            new PathMethodRule("/v3/api-docs",      Set.of("GET", "HEAD")),
            new PathMethodRule("/doc.html",         Set.of("GET", "HEAD")),
            new PathMethodRule("/webjars/",         Set.of("GET", "HEAD")),
            new PathMethodRule("/mcp",              Set.of("GET", "HEAD")),
            new PathMethodRule("/v1/platform/",     Set.of("GET", "POST", "PUT", "DELETE", "PATCH")),
            // 封面下载 GET /v1/files/cover/{objectKey} 豁免，POST /v1/files/cover（上传）需要 workspace 注入
            new PathMethodRule("/v1/files/cover/", Set.of("GET", "HEAD"))
    );

    /**
     * 按路径前缀 + HTTP 方法匹配的白名单规则。
     *
     * @param path    路径前缀
     * @param methods 允许豁免的 HTTP 方法集合（不区分大小写）
     */
    public record PathMethodRule(String path, Set<String> methods) {
        public boolean matches(String uri, String method) {
            if (method == null) {
                return false;
            }
            return uri.startsWith(path) && methods.contains(method.toUpperCase());
        }
    }

    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = resolvePath(request);
        String method = request.getMethod();
        if (isExempt(path, method)) {
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

    private boolean isExempt(String path, String method) {
        if (WORKSPACE_EXEMPT_PATHS.contains(path)) {
            return true;
        }
        return EXEMPT_RULES.stream().anyMatch(r -> r.matches(path, method));
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