package com.shortvideoscripagent.xhsagentyunying.dto.chat;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    private String persona = "agency";

    private String linkedTaskId;

    @Size(max = 128)
    private String title;
}
