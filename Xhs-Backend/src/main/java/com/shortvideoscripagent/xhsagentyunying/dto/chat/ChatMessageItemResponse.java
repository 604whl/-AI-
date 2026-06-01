package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatMessageItemResponse {

    private Long id;
    private String role;
    private String content;
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
}
