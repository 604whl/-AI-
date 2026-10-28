package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-3 排期实体（对应 {@code app.publish_schedule}，V56）。
 * <p>
 * 排期必须建立在<b>已审核通过</b>（{@code content_asset.status='approved'}）的资产上。
 * <p>
 * 时间口径：{@code scheduledAt} 统一以 UTC 落库，{@code timezone} 保存 IANA 时区串
 * （如 {@code Asia/Shanghai}），用于跨时区展示与排序。
 */
@Data
@TableName("publish_schedule")
public class PublishSchedule {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 关联内容资产 */
    private UUID assetId;

    /** 平台代码（大写枚举名，与 app.platform.code 一致） */
    private String platformCode;

    /** 平台账号 ID（app.platform_account.id 或其 external_account_id） */
    private String accountId;

    /** 排期时刻（UTC） */
    private OffsetDateTime scheduledAt;

    /** IANA 时区串（如 Asia/Shanghai） */
    private String timezone;

    /** scheduled / executing / succeeded / failed / cancelled / expired */
    private String status;

    /** 关联的发布任务 ID */
    private UUID publishTaskId;

    /** 失败错误码 */
    private Integer errorCode;

    /** 失败原因（可读文本） */
    private String errorMessage;

    /** 创建人 */
    private Long createdBy;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    // ── 状态常量 ────────────────────────────────────────────────────────

    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_EXECUTING = "executing";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED = "expired";

    /** 非终态：这些状态参与冲突检测 */
    public boolean isActive() {
        return STATUS_SCHEDULED.equals(status) || STATUS_EXECUTING.equals(status);
    }
}
