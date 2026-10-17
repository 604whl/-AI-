package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 数据导出任务记录（P0-9）。
 * <p>
 * 支持个人数据导出和工作区全量导出，文件存储在 MinIO，24h 后过期。
 */
@Data
@TableName("export_request")
public class ExportRequest {

    public static final String TYPE_PERSONAL_DATA = "personal_data";
    public static final String TYPE_WORKSPACE_DATA = "workspace_data";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_EXPIRED = "expired";

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** Workspace ID（String 类型） */
    private String workspaceId;

    private Long userId;

    /** 导出类型：personal_data / workspace_data */
    private String exportType;

    /** 状态：pending / processing / ready / failed / expired */
    private String status;

    /** MinIO 对象 key，ready 后写入 */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 下载链接过期时间 */
    private OffsetDateTime expiresAt;

    /** 失败时的错误信息 */
    private String errorMessage;

    private OffsetDateTime createdAt;

    /** 导出完成时间 */
    private OffsetDateTime finishedAt;

    // ── 状态判断 ──────────────────────────────────────────────────────────

    public boolean isReady() {
        return STATUS_READY.equals(status);
    }

    public boolean isProcessing() {
        return STATUS_PROCESSING.equals(status) || STATUS_PENDING.equals(status);
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
