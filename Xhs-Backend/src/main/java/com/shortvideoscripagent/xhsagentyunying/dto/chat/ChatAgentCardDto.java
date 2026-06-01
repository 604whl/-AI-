package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ChatAgentCardDto {

    private String type;
    private String taskId;
    private Map<String, Object> payload;
}
