package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppAiProperties {

    private Ai ai = new Ai();
    private Rag rag = new Rag();

    @Data
    public static class Ai {
        private String defaultProvider = "dashscope";
        private boolean mockEnabled = true;
        private int analysisTimeoutSeconds = 45;
        private int analysisMaxRetries = 1;
        private Dashscope dashscope = new Dashscope();
        private OpenAi openai = new OpenAi();
    }

    @Data
    public static class Dashscope {
        private String visionModel = "qwen-vl-plus";
    }

    @Data
    public static class OpenAi {
        private boolean enabled;
    }

    @Data
    public static class Rag {
        private boolean enabled;
        private boolean analysisEnabled;
        private boolean titleEnabled;
        private int topK = 5;
    }
}
