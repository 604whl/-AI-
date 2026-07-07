package com.shortvideoscripagent.xhsagentyunying.config;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

    private static final String DEV_JWT_SECRET = "xhs-agent-dev-jwt-secret-change-in-production-32b";

    private final Environment environment;
    private final AppAuthProperties authProperties;
    private final AppAiProperties appProperties;
    private final AiRuntimePolicy aiRuntimePolicy;

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdProfile()) {
            return;
        }

        List<String> errors = new ArrayList<>();
        if (isBlank(authProperties.getJwtSecret()) || DEV_JWT_SECRET.equals(authProperties.getJwtSecret())) {
            errors.add("JWT_SECRET must be set to a non-default value");
        }
        if (appProperties.getAi().isMockEnabled()) {
            errors.add("AI_MOCK_ENABLED must be false in prod");
        }
        if (!aiRuntimePolicy.hasRealApiKey()) {
            errors.add("DASHSCOPE_API_KEY must be set in prod");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join("; ", errors));
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
