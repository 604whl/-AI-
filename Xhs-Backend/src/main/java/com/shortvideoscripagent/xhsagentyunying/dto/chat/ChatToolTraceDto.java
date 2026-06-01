package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatToolTraceDto {

    private String tool;
    private boolean success;
    private int latencyMs;
    private String error;
}
