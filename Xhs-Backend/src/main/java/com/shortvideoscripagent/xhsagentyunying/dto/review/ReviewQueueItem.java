package com.shortvideoscripagent.xhsagentyunying.dto.review;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审核队列列表项（P0-6）。
 */
@Data
@Builder
public class ReviewQueueItem {

    private UUID id;
    private UUID assetId;
    private Integer versionNo;
    private Long submittedBy;
    private String submitterNickname;
    private String status;
    private Boolean isHighRisk;
    private Integer riskScore;
    private List<String> riskTags;
    private OffsetDateTime createdAt;
    private OffsetDateTime decidedAt;
}
