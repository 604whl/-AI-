package com.shortvideoscripagent.xhsagentyunying.dto.review;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 指派审核人请求（P0-6）。
 */
@Data
public class ReviewAssignRequest {

    /** 目标审核人 userId */
    @NotNull(message = "reviewer_id_required")
    private Long reviewerId;
}
