package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 内容资产主表（P0-3）。
 * <p>
 * 状态枚举：draft / in_review / approved / scheduled / published / archived
 */
@Data
@TableName("content_asset")
public class ContentAsset {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** Workspace ID，多租户隔离 */
    private UUID workspaceId;

    /** 资产所有者 userId */
    private Long ownerUserId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String body;

    /** 人设：agency / mentor / senior */
    private String persona;

    /** 场景：draft / published / competitor */
    private String scenario;

    /** 行业 */
    private String industry;

    /**
     * 资产状态。
     * draft → in_review → approved → scheduled → published
     * 或 archived（归档）
     */
    private String status;

    /** 关联的分析任务 ID（可选） */
    private UUID sourceTaskId;

    /** P0-4: 关联的模板 ID（创建草稿时可选择模板） */
    private UUID templateId;

    /** 目标平台：xiaohongshu / douyin / weibo */
    private String platform;

    /** 平台账号 ID */
    private String accountId;

    /**
     * 当前版本号：乐观锁 + 业务版本号合并字段。
     * <p>
     * MyBatis-Plus @Version 在 updateById 时会：
     * 1) UPDATE ... SET current_version = current_version + 1 WHERE id = #{id} AND current_version = #{oldVersion}
     * 2) 影响行数为 0 时抛 OptimisticLockingFailureException
     * 业务侧原本手动维护 currentVersion += 1 的逻辑被该机制替代。
     */
    @Version
    private Integer currentVersion;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;

    // ── 状态常量 ────────────────────────────────────────────

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_IN_REVIEW = "in_review";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    /** 允许的写操作状态：只有 draft 可编辑 */
    public boolean isEditable() {
        return STATUS_DRAFT.equals(status);
    }

    /** 允许提交审核的状态 */
    public boolean canSubmitReview() {
        return STATUS_DRAFT.equals(status) || STATUS_ARCHIVED.equals(status);
    }
}
