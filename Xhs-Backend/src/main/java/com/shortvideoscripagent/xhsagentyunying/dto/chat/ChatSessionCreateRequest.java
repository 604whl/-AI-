package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    @Pattern(regexp = "agency|mentor|senior", message = "invalid_persona")
    private String persona = "agency";

    @Size(max = 32)
    private String linkedTaskId;

    @Size(max = 128)
    private String title;
}
