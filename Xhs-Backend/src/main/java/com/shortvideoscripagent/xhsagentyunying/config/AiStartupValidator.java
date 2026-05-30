package com.shortvideoscripagent.xhsagentyunying.config;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiStartupValidator implements ApplicationRunner {

    private final AiRuntimePolicy aiRuntimePolicy;

    @Override
    public void run(ApplicationArguments args) {
        if (aiRuntimePolicy.useMockResponses()) {
            log.warn("AI mock mode is ON (AI_MOCK_ENABLED=true). Analysis, titles and optimize-draft use sample data.");
            return;
        }
        if (aiRuntimePolicy.hasRealApiKey()) {
            log.info(
                    "Real AI enabled: DashScope model={}, mock disabled",
                    aiRuntimePolicy.dashscopeModel()
            );
            return;
        }
        log.error(
                "Real AI requested (AI_MOCK_ENABLED=false) but DASHSCOPE_API_KEY is missing or placeholder. "
                        + "Set DASHSCOPE_API_KEY in environment or application-local.yml, "
                        + "or set AI_MOCK_ENABLED=true for offline development."
        );
    }
}
