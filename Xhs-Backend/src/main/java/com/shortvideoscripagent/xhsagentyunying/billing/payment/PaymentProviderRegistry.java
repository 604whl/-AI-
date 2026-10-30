package com.shortvideoscripagent.xhsagentyunying.billing.payment;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S4-2 支付渠道注册表。
 * <p>
 * 按 {@code provider} 路由到具体 {@link PaymentProvider} 实现。
 * 渠道实现全部无条件注册（<b>配置缺失不影响启动</b>），未配置的渠道在调用时抛 503。
 * </p>
 */
@Slf4j
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();

    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        for (PaymentProvider provider : providerList) {
            PaymentProvider previous = providers.put(provider.provider(), provider);
            if (previous != null) {
                log.warn("Duplicate PaymentProvider registered for provider={}",
                        provider.provider());
            }
        }
        log.info("Payment providers registered: {}", providers.keySet());
    }

    /**
     * 取渠道实现。
     *
     * @throws BusinessException 40011 未知渠道
     */
    public PaymentProvider require(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException(PaymentErrors.PROVIDER_UNKNOWN, "provider_required");
        }
        String key = provider.trim().toLowerCase();
        PaymentProvider found = providers.get(key);
        if (found == null) {
            throw new BusinessException(PaymentErrors.PROVIDER_UNKNOWN,
                    "unknown_payment_provider:" + key);
        }
        return found;
    }

    public boolean supports(String provider) {
        return provider != null && providers.containsKey(provider.trim().toLowerCase());
    }
}
