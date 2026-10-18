package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import lombok.Data;

/**
 * 更新内容资产请求（P0-3）。
 */
@Data
public class AssetUpdateRequest {

    @jakarta.validation.constraints.Size(max = 255, message = "title max 255 chars")
    private String title;

    private String body;

    private String persona;

    private String scenario;

    private String industry;

    /** 状态变更（draft / in_review / approved / scheduled / published / archived） */
    private String status;

    private String platform;

    private String accountId;

    /** 编辑原因（自动记录版本历史） */
    private String editReason;
}
