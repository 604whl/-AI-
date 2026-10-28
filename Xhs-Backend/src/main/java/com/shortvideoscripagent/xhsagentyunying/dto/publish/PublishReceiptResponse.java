package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 发布回执响应。
 * <p>
 * 验收契约：回填接口返回 {@code {id, verified}}；列表接口返回
 * {@code [{id, externalContentId, sourceUrl, publishedAt, publishedBy}]}。
 */
@Data
@Builder
public class PublishReceiptResponse {

    /** 回执 ID */
    private String id;

    /** 关联发布任务 ID */
    private String publishTaskId;

    /** 内容资产 ID */
    private String assetId;

    /** 平台侧内容 ID */
    private String externalContentId;

    /** 平台侧内容链接 */
    private String sourceUrl;

    /** 实际发布时间 */
    private OffsetDateTime publishedAt;

    /**
     * 发布时间来源：{@code platform}（平台返回）/ {@code local}（本地兜底，必须单独标注）。
     */
    private String publishedAtSource;

    /** 回填人 ID */
    private Long publishedBy;

    /** 是否已核验 */
    private Boolean verified;

    private OffsetDateTime createdAt;
}
