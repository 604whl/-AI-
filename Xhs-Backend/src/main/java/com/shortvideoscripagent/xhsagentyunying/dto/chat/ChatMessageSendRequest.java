package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ChatMessageSendRequest {

    @NotBlank
    @Size(max = 8000)
    private String content;

    private Map<String, Object> attachments;
}
