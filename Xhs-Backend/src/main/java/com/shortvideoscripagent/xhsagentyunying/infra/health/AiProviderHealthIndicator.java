package com.shortvideoscripagent.xhsagentyunying.infra.health;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderHealthIndicator implements HealthIndicator {

    private final AiRuntimePolicy aiRuntimePolicy;
    private final AppAiProperties appProperties;

    @Override
    public Health health() {
        boolean mock = appProperties.getAi().isMockEnabled();
        boolean hasRealKey = aiRuntimePolicy.hasRealApiKey();

        if (mock || hasRealKey) {
            return Health.up()
                    .withDetail("mock", mock)
                    .withDetail("hasRealApiKey", hasRealKey)
                    .withDetail("provider", appProperties.getAi().getDefaultProvider())
                    .build();
        }

        return Health.down()
                .withDetail("error", "no_real_api_key_configured")
                .withDetail("mock", false)
                .withDetail("hasRealApiKey", false)
                .build();
    }
}