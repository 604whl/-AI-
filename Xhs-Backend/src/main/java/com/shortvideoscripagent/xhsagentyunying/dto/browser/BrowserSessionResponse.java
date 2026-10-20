package com.shortvideoscripagent.xhsagentyunying.dto.browser;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BrowserSessionResponse {

    private UUID id;

    private String platform;

    private String accountLabel;

    private String status;

    private UUID assetId;

    private OffsetDateTime cookieExpiresAt;

    private OffsetDateTime lastUsedAt;

    private OffsetDateTime createdAt;

    private String policyNotice;
}