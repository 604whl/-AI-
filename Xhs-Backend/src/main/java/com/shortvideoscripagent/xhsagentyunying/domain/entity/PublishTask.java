package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-4 发布任务实体（对应 {@code app.publish_task}，V56）。
 * <p>
 * 三条降级路径<b>共用同一张表</b>，用 {@link #level} 区分：
 * <ul>
 *   <li>{@code OFFICIAL_API} / {@code AUTHORIZED_CONNECTOR} —— 系统自动发布并回写回执</li>
 *   <li>{@code BROWSER_ASSIST} —— 系统只填充不点发布，用户确认后自行发布 → 回填回执</li>
 *   <li>{@code MANUAL_IMPORT} —— 生成待发布清单，运营自行发布 → 回填回执</li>
 * </ul>
 * <p>
 * <b>幂等</b>：{@code idempotencyKey} 唯一（数据库唯一约束），重试必须复用原键。
 * <b>严禁伪造外部内容 ID</b>：需要人工确认时任务置 {@code manual_required}，不写任何外部 ID。
 */
@Data
@TableName("publish_task")
public class PublishTask {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 关联排期（可空：直接创建的发布任务没有排期） */
    private UUID scheduleId;

    /** 关联内容资产 */
    private UUID assetId;

    /** 平台代码 */
    private String platformCode;

    /** 平台账号 ID */
    private String accountId;

    /** 接入所用的连接器实现（app.connector_provider.code） */
    private String connectorProviderCode;

    /** 连接等级：OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT */
    private String level;

    /** 幂等键（数据库 UNIQUE） */
    private String idempotencyKey;

    /** pending / running / succeeded / failed / manual_required / cancelled */
    private String status;

    /** 尝试次数 */
    private Integer attempt;

    /** 失败错误码 */
    private Integer errorCode;

    /** 失败原因（可读文本，不得为 unknown） */
    private String errorMessage;

    /** 失败是否可重试 */
    private Boolean retryable;

    /** 计划发布时间（UTC） */
    private OffsetDateTime scheduledAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private OffsetDateTime createdAt;

    // ── 状态常量 ────────────────────────────────────────────────────────

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    /** 需要人工在平台侧确认 / 自行发布（系统不代发） */
    public static final String STATUS_MANUAL_REQUIRED = "manual_required";
    public static final String STATUS_CANCELLED = "cancelled";

    /** 可被取消的状态 */
    public boolean isCancellable() {
        return STATUS_PENDING.equals(status)
                || STATUS_MANUAL_REQUIRED.equals(status)
                || STATUS_FAILED.equals(status);
    }
}
