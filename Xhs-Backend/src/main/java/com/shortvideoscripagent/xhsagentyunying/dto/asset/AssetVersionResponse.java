package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 内容资产版本响应（P0-3）。
 */
@Data
@Builder
public class AssetVersionResponse {

    private UUID id;
    private UUID assetId;
    private Integer versionNo;
    private String title;
    private String body;
    private Long editedBy;
    private String editReason;
    private OffsetDateTime createdAt;
}
