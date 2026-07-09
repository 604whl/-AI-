package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        private List<String> fallbackProviders = new ArrayList<>();
        private Dashscope dashscope = new Dashscope();
        private OpenAi openai = new OpenAi();
        private CompatibleProvider deepseek = new CompatibleProvider();
        private CompatibleProvider zhipu = new CompatibleProvider();
    }

    @Data
    public static class Dashscope {
        private boolean enabled = true;
        private String visionModel = "qwen-vl-plus";
    }

    @Data
    public static class OpenAi {
        private boolean enabled;
    }

    @Data
    public static class CompatibleProvider {
        private boolean enabled;
        private String apiKey = "";
        private String baseUrl = "";
        private String model = "";
    }

    @Data
    public static class Rag {
        private boolean enabled;
        private boolean analysisEnabled;
        private boolean titleEnabled;
        private int topK = 5;
    }
}
