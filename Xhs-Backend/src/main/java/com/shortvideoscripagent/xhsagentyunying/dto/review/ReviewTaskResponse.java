package com.shortvideoscripagent.xhsagentyunying.dto.review;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审核任务详情响应（P0-6）。
 */
@Data
@Builder
public class ReviewTaskResponse {

    private UUID id;
    private String workspaceId;
    private UUID assetId;
    private Integer versionNo;
    private Long submittedBy;
    private String submitterNickname;
    private Long reviewerUserId;
    private String reviewerNickname;
    private String status;
    private Boolean isHighRisk;
    private Integer riskScore;
    private List<String> riskTags;
    private String comment;
    private OffsetDateTime decidedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 关联资产快照 */
    private AssetSnapshot asset;

    /** 批注列表 */
    private List<ReviewCommentItem> comments;

    /**
     * 资产快照（审核时展示正文等信息）。
     */
    @Data
    @Builder
    public static class AssetSnapshot {
        private UUID id;
        private String title;
        private String body;
        private String persona;
        private String platform;
        private String accountId;
        private Integer currentVersion;
    }

    /**
     * 批注列表项。
     */
    @Data
    @Builder
    public static class ReviewCommentItem {
        private UUID id;
        private String comment;
        private String highlightedText;
        private String commentType;
        private Long createdBy;
        private String creatorNickname;
        private OffsetDateTime createdAt;
    }
}
