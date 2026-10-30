package com.shortvideoscripagent.xhsagentyunying.billing.payment;

import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * S4-2 渠道调用重试 / 退避工具。
 * <p>
 * 复用 {@link TaskQueueProperties#retryDelaySeconds(int)} 的指数退避序列：
 * 第 1 次失败等待 {@code retryBackoff[0]} 秒，第 2 次 {@code retryBackoff[1]} 秒……
 * </p>
 * <p>
 * <b>不重试</b>的场景：渠道未配置（503）、参数非法（4xx）——重试也不会成功。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCallRetry {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final TaskQueueProperties taskQueueProperties;

    /**
     * 执行一次渠道调用，失败按指数退避重试。
     *
     * @param providerName 渠道名（仅用于日志）
     * @param op           操作名（仅用于日志）
     * @param callable     实际调用
     */
    public <T> T call(String providerName, String op, Supplier<T> callable) {
        return call(providerName, op, callable, DEFAULT_MAX_ATTEMPTS);
    }

    public <T> T call(String providerName, String op, Supplier<T> callable, int maxAttempts) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                return callable.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt >= maxAttempts) {
                    break;
                }
                long delaySeconds = taskQueueProperties.retryDelaySeconds(attempt);
                log.warn("Payment provider call failed, retrying: provider={} op={} attempt={}/{} delay={}s reason={}",
                        providerName, op, attempt, maxAttempts, delaySeconds, ex.getMessage());
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last == null
                ? PaymentErrors.providerError(providerName, "unknown")
                : last;
    }

    /**
     * 只有 502 / 503（渠道故障、超时）才重试；4xx（参数 / 验签）不重试。
     */
    private boolean isRetryable(RuntimeException ex) {
        if (!(ex instanceof com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException be)) {
            return false;
        }
        return be.getCode() == PaymentErrors.PROVIDER_ERROR
                || be.getCode() == PaymentErrors.PROVIDER_TIMEOUT
                || be.getCode() == PaymentErrors.PROVIDER_UNAVAILABLE;
    }
}
