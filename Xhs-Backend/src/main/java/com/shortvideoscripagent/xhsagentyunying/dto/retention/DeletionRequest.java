package com.shortvideoscripagent.xhsagentyunying.dto.retention;

/**
 * 账号注销请求 DTO（P0-9）。
 */
public record DeletionRequest(
        String confirm  // 必须输入 "DELETE" 确认
) {}
