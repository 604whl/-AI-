package com.shortvideoscripagent.xhsagentyunying.dto.browser;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotBlank
    @Pattern(regexp = "XIAOHONGSHU|DOUYIN|WEIBO|BILIBILI", message = "invalid_platform")
    private String platform;

    @Size(max = 128)
    private String accountLabel;
}