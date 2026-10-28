package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 发布任务响应。
 * <p>
 * 创建接口返回 {@code {id, status}}；详情接口额外返回错误分类、尝试次数与回执。
 */
@Data
@Builder
public class PublishTaskResponse {

    /** 任务 ID */
    private String id;

    /** pending / running / succeeded / failed / manual_required / cancelled */
    private String status;

    /** 关联的排期 ID */
    private String scheduleId;

    /** 内容资产 ID */
    private String assetId;

    private String platformCode;

    private String accountId;

    /** 接入所用的连接器实现 */
    private String connectorProviderCode;

    /** 连接等级：OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT */
    private String level;

    /** 幂等键 */
    private String idempotencyKey;

    /** 尝试次数 */
    private Integer attempt;

    /** 失败错误码 */
    private Integer errorCode;

    /** 失败原因（可读文本） */
    private String errorMessage;

    /** 失败是否可重试 */
    private Boolean retryable;

    private OffsetDateTime scheduledAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private OffsetDateTime createdAt;

    /** 关联回执（详情接口返回；创建接口为 null） */
    private PublishReceiptResponse receipt;

    /**
     * 降级建议：连接器不可用时前端展示的替代路径
     * （BROWSER_ASSIST / MANUAL_IMPORT / EXPORT_ONLY）。
     */
    private String degradedTo;

    /** 需人工确认提示（BROWSER_ASSIST / MANUAL_IMPORT 路径必须显著提示"用户自负发布风险"） */
    private String manualNotice;
}
