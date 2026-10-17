package com.shortvideoscripagent.xhsagentyunying.dto.retention;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 导出任务响应 DTO（P0-9）。
 */
@Data
@Builder
public class ExportRequestResponse {

    private String id;

    /** 导出类型：personal_data / workspace_data */
    private String exportType;

    /** 状态：pending / processing / ready / failed / expired */
    private String status;

    /** 下载地址（ready 时返回） */
    private String downloadUrl;

    /** 文件大小（字节，ready 时返回） */
    private Long fileSize;

    /** 过期时间 */
    private OffsetDateTime expiresAt;

    /** 错误信息（failed 时返回） */
    private String errorMessage;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 完成时间 */
    private OffsetDateTime finishedAt;
}
