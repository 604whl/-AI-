package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentCallbackService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * S4-2 支付渠道回调接口。
 * <p>
 * POST /v1/payments/callback/{provider}
 * </p>
 * <p>
 * <b>鉴权模型</b>：本接口<b>不走用户态鉴权</b>——调用方是支付渠道，
 * 身份由<b>渠道签名</b>确定（{@code verifyCallback}）。
 * </p>
 * <p>
 * 为此，{@code billing/payment/config/PaymentCallbackAuthFilter} 会在
 * {@code AuthInterceptor} / {@code WorkspaceInterceptor} 之前拦截本路径
 * （渠道回调没有用户态，也没有 {@code X-Workspace-Id}），
 * 直接完成验签 + 入账，避免被用户态拦截器挡成 401。
 * </p>
 * <p>
 * <b>安全约束</b>：
 * <ul>
 *   <li>验签失败 → 一律拒绝并记审计，<b>绝不凭订单号直接改状态</b>；</li>
 *   <li>同一渠道事件 ID 只处理一次（重放保护）；</li>
 *   <li>回调金额必须等于订单金额，否则拒付并告警。</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentCallbackService paymentCallbackService;

    /**
     * 接收渠道回调。
     *
     * @param provider 渠道名（mock / alipay / wechat）
     * @param rawBody  渠道原始报文
     * @param headers  请求头（渠道签名相关）
     */
    @PostMapping(value = "/v1/payments/callback/{provider}",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE,
                    MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<OrderDtos.CallbackResult> callback(
            @PathVariable String provider,
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader Map<String, String> headers) {
        String body = rawBody == null ? "" : new String(rawBody, StandardCharsets.UTF_8);
        Map<String, String> safeHeaders = new HashMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null) {
                    safeHeaders.put(k, v == null ? "" : v);
                }
            });
        }
        OrderDtos.CallbackResult result = paymentCallbackService.handleCallback(provider, body, safeHeaders);
        return ApiResponse.ok(result);
    }
}
