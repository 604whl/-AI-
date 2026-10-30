package com.shortvideoscripagent.xhsagentyunying.billing.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentErrors;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.controller.v1.PaymentCallbackController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * S4-2 支付回调鉴权旁路过滤器。
 * <p>
 * <b>为什么需要</b>：渠道回调（{@code POST /api/v1/payments/callback/{provider}}）的调用方是
 * <b>支付渠道</b>，既没有用户态（无 Bearer Token / 无 X-API-Key），
 * 也没有 {@code X-Workspace-Id}，会被 {@code AuthInterceptor} / {@code WorkspaceInterceptor} 直接挡成 401。
 * </p>
 * <p>
 * <b>本过滤器如何解决</b>：在两个拦截器之前（Filter 早于 HandlerInterceptor）拦截该路径，
 * 直接调用 {@link PaymentCallbackController} 完成「验签 + 入账 + 履约入队」，
 * 并把结果写回响应；<b>不进入拦截器链</b>。
 * </p>
 * <p>
 * <b>安全边界</b>：身份完全由<b>渠道签名</b>确定（{@code verifyCallback}）：
 * 签名 / 时间戳窗口 / 事件 ID 去重 / 金额校验全部在
 * {@code PaymentCallbackService} 里执行，验签失败一律拒绝并写审计。
 * </p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class PaymentCallbackAuthFilter extends OncePerRequestFilter {

    /** 回调路径（相对 context-path） */
    private static final String CALLBACK_PATH_PREFIX = "/v1/payments/callback/";

    private final PaymentCallbackController paymentCallbackController;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 注意：shouldNotFilter 返回 true 表示「跳过本过滤器」。
        // 只有非回调路径才应跳过；回调路径（provider 非空）必须执行本过滤器。
        // 早期实现把这里的判断写反了，导致回调路径反而被跳过，
        // 请求落到 AuthInterceptor 被挡成 401，渠道永远调不通。
        String provider = extractProvider(request);
        return provider.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provider = extractProvider(request);
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            writeError(response, HttpStatus.BAD_REQUEST, PaymentErrors.CALLBACK_MALFORMED,
                    "callback_body_unreadable");
            return;
        }

        Map<String, String> headers = new HashMap<>();
        for (String name : java.util.Collections.list(request.getHeaderNames())) {
            if (name == null) {
                continue;
            }
            headers.put(name, request.getHeader(name));
        }

        try {
            ApiResponse<?> result = paymentCallbackController.callback(
                    provider, body, headers);
            writeJson(response, HttpStatus.OK, result);
        } catch (BusinessException ex) {
            log.warn("Payment callback rejected: provider={} code={} message={}",
                    provider, ex.getCode(), ex.getMessage());
            writeError(response, mapStatus(ex.getCode()), ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Payment callback failed unexpectedly: provider={} err={}",
                    provider, ex.getMessage(), ex);
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, 50001, "internal_error");
        }
    }

    /**
     * 从路径里取渠道名；非回调路径返回空串。
     */
    private String extractProvider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri;
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            path = uri.substring(contextPath.length());
        }
        if (!path.startsWith(CALLBACK_PATH_PREFIX)) {
            return "";
        }
        String provider = path.substring(CALLBACK_PATH_PREFIX.length());
        if (provider.contains("/")) {
            provider = provider.substring(0, provider.indexOf('/'));
        }
        return provider.trim();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, int code, String message)
            throws IOException {
        writeJson(response, status, ApiResponse.fail(code, message));
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
        response.getWriter().flush();
    }

    /**
     * 错误码 → HTTP 状态（与 {@code GlobalExceptionHandler} 分段保持一致）。
     */
    private HttpStatus mapStatus(int code) {
        if (code >= 40300 && code < 40400) {
            return HttpStatus.FORBIDDEN;
        }
        if (code >= 40400 && code < 40500) {
            return HttpStatus.NOT_FOUND;
        }
        if (code >= 40900 && code < 41000) {
            return HttpStatus.CONFLICT;
        }
        if (code >= 42900 && code < 43000) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code >= 50200 && code < 50300) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (code >= 50300 && code < 50400) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code >= 50000 && code < 60000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
