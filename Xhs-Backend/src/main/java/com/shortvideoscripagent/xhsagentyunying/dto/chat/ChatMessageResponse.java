package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatMessageResponse {

    private Long messageId;
    private String role;
    private String content;
    private List<ChatAgentCardDto> cards;
    private List<ChatToolTraceDto> toolTraces;
}
