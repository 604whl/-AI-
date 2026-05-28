package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    private String jwtSecret;
    private long accessTokenExpireSeconds = 7200;
    private long refreshTokenExpireSeconds = 604800;
}
