package com.shortvideoscripagent.xhsagentyunying.infra.upload;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload.virus-scan")
public class VirusScanProperties {
    private boolean enabled = false;
    private String host = "clamav";
    private int port = 3310;
    private int timeoutMs = 5000;
}
