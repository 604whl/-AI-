package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 导出记录（P0-5）。
 * <p>
 * 记录每一次导出请求的状态，支持 MD / DOCX / ZIP / CSV 四种格式。
 * 状态枚举：pending → processing → ready / failed / expired
 */
@Data
@TableName("export_log")
public class ExportLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** Workspace ID，多租户隔离（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 发起导出的用户 ID */
    private Long userId;

    /** 关联的资产 ID（可选；CSV 批量导出时为 null） */
    private UUID assetId;

    /**
     * 导出格式：md / docx / zip / csv
     */
    private String format;

    /**
     * 导出状态：pending / processing / ready / failed / expired
     */
    private String status;

    /** MinIO 对象 key，ready 后填充 */
    private String objectKey;

    /** 文件大小（byte），ready 后填充 */
    private Long fileSize;

    /** 签名 URL 过期时间 */
    private OffsetDateTime expiresAt;

    /** 失败原因 */
    private String errorMessage;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 完成时间 */
    private OffsetDateTime finishedAt;

    // ── 格式常量 ───────────────────────────────────────────────

    public static final String FORMAT_MD   = "md";
    public static final String FORMAT_DOCX = "docx";
    public static final String FORMAT_ZIP  = "zip";
    public static final String FORMAT_CSV  = "csv";

    // ── 状态常量 ──────────────────────────────────────────────

    public static final String STATUS_PENDING    = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_READY      = "ready";
    public static final String STATUS_FAILED     = "failed";
    public static final String STATUS_EXPIRED    = "expired";

    public static final String CODE_VALID_FORMATS = "md,docx,zip,csv";

    /** 导出文件 MinIO bucket */
    public static final String BUCKET = "xhs-exports";

    /** 签名 URL 有效期：24 小时 */
    public static final int SIGNED_URL_EXPIRY_SECONDS = 24 * 3600;
}
