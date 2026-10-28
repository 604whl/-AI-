package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 回填发布回执请求。
 * <p>
 * {@code externalContentId} 与 {@code sourceUrl} <b>至少提供一项</b>，两者皆空时
 * 返回 {@code 400 RECEIPT_INCOMPLETE}。
 */
@Data
public class PublishReceiptCreateRequest {

    /** 发布任务 ID */
    @NotBlank(message = "publish_task_id_required")
    @Size(max = 64, message = "publish_task_id_too_long")
    private String publishTaskId;

    /** 平台侧内容 ID（与 sourceUrl 至少有一项非空） */
    @Size(max = 128, message = "external_content_id_too_long")
    private String externalContentId;

    /** 平台侧内容链接（与 externalContentId 至少有一项非空） */
    @Size(max = 2048, message = "source_url_too_long")
    private String sourceUrl;

    /** 实际发布时间（可空：为空则用当前时间兜底并标注 publishedAtSource=local） */
    private OffsetDateTime publishedAt;

    /** 是否已核验 */
    private Boolean verified;
}
