package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class AppStorageProperties {

    private String type = "local";
    private Minio minio = new Minio();
    private Local local = new Local();

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "xhs-agent";
    }

    @Data
    public static class Local {
        private String baseDir = "./data/uploads";
        private String publicBaseUrl = "/api/v1/files/cover";
    }
}
