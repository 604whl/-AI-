package com.shortvideoscripagent.xhsagentyunying.dto.export;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 导出响应（P0-5）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportResponse {

    /** 导出记录 ID */
    private UUID id;

    /** 导出格式：md / docx / zip / csv */
    private String format;

    /**
     * 导出状态：pending / processing / ready / failed / expired
     */
    private String status;

    /** 错误信息（failed 时有值） */
    private String errorMessage;

    /** 签名下载 URL（ready 时有值） */
    private String downloadUrl;

    /** 文件大小（byte，ready 时有值） */
    private Long fileSize;

    /** 过期时间（ready 时有值） */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime expiresAt;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime createdAt;

    /** 完成时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime finishedAt;
}
