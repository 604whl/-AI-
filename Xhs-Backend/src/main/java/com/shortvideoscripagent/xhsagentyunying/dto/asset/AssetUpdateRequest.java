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

    /**
     * 乐观锁期望版本号。
     * 若非 null，Service 层会校验 DB.version == expectedVersion，
     * 不匹配时抛出 OptimisticLockingFailureException → HTTP 409。
     * 为 null 时跳过校验，兼容阶段1 既有调用方。
     */
    private Integer expectedVersion;
}
