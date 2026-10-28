package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-8 复盘回写审批实体（{@code app.retrospective_feedback}，V57）。
 * <p>
 * <b>审批流</b>：提交后为 {@code PENDING}，此时<b>不写入</b>模板效果统计 / 知识库效果统计；
 * 只有 {@code APPROVED} 才真正写回。审批人必须是 ADMIN / OWNER（冻结决策 D3）。
 * </p>
 * <p>
 * 污染风险控制：单条复盘结果只能作为<b>效果统计样本</b>（指标 + 标签），
 * 不得直接成为知识库文档正文。
 * </p>
 */
@Data
@TableName(value = "retrospective_feedback", autoResultMap = true)
public class RetrospectiveFeedback {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 来源复盘报告 ID */
    private UUID reportId;

    /** 所属工作区 */
    private String workspaceId;

    /** 回写目标类型：TEMPLATE / KB_DOCUMENT */
    private String targetType;

    /** 回写目标 ID */
    private String targetId;

    /** 拟写入的内容（模板正文片段 / 知识库标签说明） */
    private String proposedContent;

    /** 拟写入的效果统计样本 JSONB（指标 + 标签，不含知识库正文） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String proposedStats;

    /** 状态：PENDING / APPROVED / REJECTED / WITHDRAWN */
    private String status;

    /** 审批人 */
    private Long reviewedBy;

    /** 审批时间 */
    private OffsetDateTime reviewedAt;

    /** 审批意见（驳回时必填） */
    private String reviewComment;

    /** 提交人 */
    private Long createdBy;

    /** 实际写回效果统计的时间（审批通过后写入） */
    private OffsetDateTime appliedAt;

    /** 幂等键（工作区内唯一） */
    private String idempotencyKey;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────

    public static final String TARGET_TEMPLATE = "TEMPLATE";
    public static final String TARGET_KB_DOCUMENT = "KB_DOCUMENT";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
}
