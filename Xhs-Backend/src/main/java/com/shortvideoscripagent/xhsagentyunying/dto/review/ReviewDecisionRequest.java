package com.shortvideoscripagent.xhsagentyunying.dto.review;

import lombok.Data;

/**
 * 审核决定请求（approve / reject）（P0-6）。
 */
@Data
public class ReviewDecisionRequest {

    /** 审核意见（reject 时必须填写） */
    private String comment;
}
