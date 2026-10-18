package com.shortvideoscripagent.xhsagentyunying.infra.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload.virus-scan.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVirusScanner implements VirusScanner {

    @Override
    public String scan(byte[] data, String name) {
        return null;
    }
}
