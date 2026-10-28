package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S3-8 复盘回写记录响应。
 */
@Data
@Builder
public class RetrospectiveFeedbackResponse {

    /** 回写记录 ID */
    private UUID id;

    /** 来源复盘报告 ID */
    private UUID reportId;

    /** 回写目标类型：TEMPLATE / KB_DOCUMENT */
    private String targetType;

    /** 回写目标 ID */
    private String targetId;

    /** 拟写入的内容 */
    private String proposedContent;

    /** 拟写入的效果统计样本 */
    private Map<String, Object> proposedStats;

    /** 状态：PENDING / APPROVED / REJECTED / WITHDRAWN */
    private String status;

    /** 审批人 */
    private Long reviewedBy;

    /** 审批时间 */
    private OffsetDateTime reviewedAt;

    /** 审批意见 */
    private String reviewComment;

    /** 实际写回时间 */
    private OffsetDateTime appliedAt;

    /** 提交时间 */
    private OffsetDateTime createdAt;
}
