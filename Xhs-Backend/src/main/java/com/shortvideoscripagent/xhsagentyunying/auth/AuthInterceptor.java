package com.shortvideoscripagent.xhsagentyunying.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_DELETION_SCHEDULED = 42301;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/v1/auth/login",
            "/v1/auth/register",
            "/v1/auth/refresh",
            "/v1/auth/logout",
            "/v1/health"
    );

    /**
     * 公开只读路径<b>前缀</b>。
     * <p>
     * S4-1 套餐目录：阶段四接口契约规定 {@code GET /api/v1/plans} 与
     * {@code GET /api/v1/plans/{code}} 的权限是「全部」。
     * 未登录用户也要能在定价页看到套餐与价格，否则无法引导注册。
     * </p>
     * <p>
     * 用前缀而非枚举具体 code，避免新增套餐（code 是数据驱动的）时
     * 忘记同步白名单，导致套餐页对新套餐 401。
     * </p>
     */
    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/v1/plans"
    );

    private final AuthService authService;
    private final ApiKeyService apiKeyService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 优先把 IP / UA 写入上下文（用于审计），即使后面 401 也保留
        RequestContext.setRequestIp(resolveClientIp(request));
        RequestContext.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = resolvePath(request);
        if (isPublicPath(path) || isSwaggerPath(path) || isMcpPath(path)) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            Long userId = authService.resolveUserIdFromAccessToken(token);
            if (userId != null) {
                // P0-9: 检查账号是否在软删期间
                if (isScheduledForDeletion(userId)) {
                    writeError(response, HttpStatus.LOCKED.value(), CODE_DELETION_SCHEDULED,
                            "account_scheduled_for_deletion");
                    return false;
                }
                RequestContext.setUserId(userId);
                return true;
            }
        }

        // X-API-Key: workspace API Key（API Key 无用户态，跳过用户鉴权）
        String apiKeyHeader = request.getHeader(ApiKeyAuthenticationFilter.HEADER_API_KEY);
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return true;
        }

        writeUnauthorized(response);
        return false;
    }

    /**
     * P0-9: 检查账号是否在软删期间。
     * 软删期间禁止访问 API（登录除外）。
     */
    private boolean isScheduledForDeletion(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && "scheduled".equals(user.getDeletionStatus());
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

    /**
     * 解析客户端真实 IP：优先取 {@code X-Forwarded-For} 首段，回退 {@code X-Real-IP}，最后取 {@code remoteAddr}。
     * 注意：前置必须配置可信代理，否则可能被伪造。生产环境应仅信任来自反向代理的 XFF。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            // 精确匹配或前缀 + "/"（避免 "/v1/plansfoo" 之类被误判为公开）
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSwaggerPath(String path) {
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/doc.html")
                || path.startsWith("/webjars/");
    }

    private boolean isMcpPath(String path) {
        return path.startsWith("/mcp");
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, CODE_UNAUTHORIZED, "unauthorized");
    }

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws Exception {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
