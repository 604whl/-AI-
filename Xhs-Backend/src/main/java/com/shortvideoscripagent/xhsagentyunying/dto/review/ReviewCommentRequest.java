package com.shortvideoscripagent.xhsagentyunying.dto.review;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加批注请求（P0-6）。
 */
@Data
public class ReviewCommentRequest {

    /** 批注正文 */
    @NotBlank(message = "comment_required")
    private String comment;

    /** 被批注的正文片段（可选） */
    private String highlightedText;
}
