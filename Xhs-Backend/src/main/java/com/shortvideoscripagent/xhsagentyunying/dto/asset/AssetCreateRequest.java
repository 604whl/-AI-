package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建内容资产请求（P0-3）。
 */
@Data
public class AssetCreateRequest {

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title max 255 chars")
    private String title;

    private String body = "";

    /** 人设：agency / mentor / senior */
    private String persona;

    /** 场景：draft / published / competitor */
    private String scenario;

    private String industry;

    /** 关联的分析任务 ID（可选） */
    private String sourceTaskId;

    /** 目标平台 */
    private String platform;

    /** 平台账号 ID */
    private String accountId;
}
