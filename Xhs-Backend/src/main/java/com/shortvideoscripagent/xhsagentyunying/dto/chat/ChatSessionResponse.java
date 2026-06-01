package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ChatSessionResponse {

    private String sessionId;
    private String title;
    private String persona;
    private String linkedTaskId;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
