package com.shortvideoscripagent.xhsagentyunying.dto.review;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审核批注响应（P0-6）。
 */
@Data
@Builder
public class ReviewCommentResponse {

    private UUID id;
    private UUID reviewTaskId;
    private String comment;
    private String highlightedText;
    private String commentType;
    private Long createdBy;
    private String creatorNickname;
    private OffsetDateTime createdAt;
}
