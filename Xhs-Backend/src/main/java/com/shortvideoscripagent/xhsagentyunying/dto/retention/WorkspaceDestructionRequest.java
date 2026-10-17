package com.shortvideoscripagent.xhsagentyunying.dto.retention;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 工作区销毁请求 DTO（P0-9）。
 */
public record WorkspaceDestructionRequest(
        @NotBlank(message = "confirm phrase is required")
        @Size(max = 64, message = "confirm phrase too long")
        String confirm  // 必须输入 "DELETE" 确认
) {}
