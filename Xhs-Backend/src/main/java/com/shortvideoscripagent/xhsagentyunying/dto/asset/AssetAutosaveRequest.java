package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssetAutosaveRequest {

    @Size(max = 255, message = "title_max_255")
    private String title;

    @Size(max = 50000, message = "body_max_50000")
    private String body;
}
