package com.shortvideoscripagent.xhsagentyunying.dto.review;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交审核请求（P0-6）。
 */
@Data
public class ReviewSubmitRequest {

    /** 可选：指定审核人 userId（ADMIN/OWNER 可指定，普通 Editor 忽略） */
    private Long assignToReviewerId;
}
