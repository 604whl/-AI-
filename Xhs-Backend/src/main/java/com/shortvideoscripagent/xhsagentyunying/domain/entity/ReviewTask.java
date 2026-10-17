package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审核任务实体（P0-6）。
 * <p>
 * 状态枚举：pending / in_review / approved / rejected / cancelled
 */
@Data
@TableName("review_task")
public class ReviewTask {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** Workspace ID，多租户隔离 */
    private UUID workspaceId;

    /** 关联的资产 ID */
    private UUID assetId;

    /** 提交审核时的资产版本号 */
    private Integer versionNo;

    /** 提交人 userId */
    private Long submittedBy;

    /** 指派的审核人 userId（可选，ADMIN/OWNER 可预先指派） */
    private Long reviewerUserId;

    /**
     * 审核状态：
     * pending   = 待领取/待审核
     * in_review = 审核中
     * approved  = 已通过
     * rejected  = 已驳回（退回 draft）
     * cancelled = 已撤回
     */
    private String status;

    /** 是否高风险：合规扫描 risk_score > 70 时为 true */
    private Boolean isHighRisk;

    /** 合规风险分 0-100 */
    private Integer riskScore;

    /** 风险标签列表 JSON */
    private List<String> riskTags;

    /** 审核结论/驳回原因 */
    private String comment;

    /** 审核决定时间 */
    private OffsetDateTime decidedAt;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;

    // ── 状态常量 ─────────────────────────────────────────────────────────

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_REVIEW = "in_review";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final int RISK_THRESHOLD_HIGH = 70;

    // ── 状态判断 ─────────────────────────────────────────────────────────

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isInReview() {
        return STATUS_IN_REVIEW.equals(status);
    }

    public boolean isDecided() {
        return STATUS_APPROVED.equals(status) || STATUS_REJECTED.equals(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    public boolean isHighRisk() {
        return Boolean.TRUE.equals(isHighRisk);
    }
}
