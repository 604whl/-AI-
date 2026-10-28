package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * S3-8 复盘回写审批请求（审批通过 / 驳回共用）。
 * <p>
 * 驳回时 {@code comment} 必填（错误码 400 COMMENT_REQUIRED）。
 * </p>
 */
@Data
public class RetrospectiveFeedbackReviewRequest {

    /** 审批意见（驳回时必填） */
    @Size(max = 2000)
    private String comment;
}
