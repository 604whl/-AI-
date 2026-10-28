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
 * S3-5 指标快照实体。
 * <p>
 * 对应 {@code app.metric_snapshot}（V52）。
 * <ul>
 *   <li>{@code metrics} 固定 10 个核心键，缺失补 0（见 {@code connector.MetricKeys#CORE_KEYS}）</li>
 *   <li>平台差异字段只进 {@code metricExtra}</li>
 *   <li>人工修订产生新快照（{@code revisedFromId} 指向原快照），不原地覆盖</li>
 *   <li>{@code collectionKey} 为采集幂等键：平台 + 账号 + 外部内容 ID + 采集窗口</li>
 * </ul>
 */
@Data
@TableName(value = "metric_snapshot", autoResultMap = true)
public class MetricSnapshot {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 平台代码（大写，与 app.platform.code 一致） */
    private String platformCode;

    /** 平台账号 ID */
    private String accountId;

    /** 关联内容资产（可空；资产被删除时置空） */
    private UUID assetId;

    /** 平台侧内容 ID */
    private String externalContentId;

    /** 内容类型（NOTE / VIDEO / ARTICLE ...） */
    private String contentType;

    /** 内容发布时间 */
    private OffsetDateTime publishedAt;

    /** 统一口径指标 JSONB（固定 10 键） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metrics;

    /** 平台扩展指标 JSONB（核心键之外的字段） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metricExtra;

    /** 数据来源：OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT / LEGACY_JSONB */
    private String metricSource;

    /** 可信度：HIGH / MEDIUM / LOW / UNVERIFIED */
    private String confidence;

    /** 采集时间（平台实际统计时间） */
    private OffsetDateTime collectedAt;

    /** 是否人工录入 / 人工修订 */
    private Boolean isManual;

    /** 修订链：指向被修订的原快照 */
    private UUID revisedFromId;

    /** 修订人 */
    private Long revisedBy;

    /** 采集幂等键（唯一） */
    private String collectionKey;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────

    public static final String SOURCE_OFFICIAL_API = "OFFICIAL_API";
    public static final String SOURCE_AUTHORIZED_CONNECTOR = "AUTHORIZED_CONNECTOR";
    public static final String SOURCE_BROWSER_ASSIST = "BROWSER_ASSIST";
    public static final String SOURCE_MANUAL_IMPORT = "MANUAL_IMPORT";
    public static final String SOURCE_CSV_IMPORT = "CSV_IMPORT";
    /** 仅用于旧 analysis_task.published_metrics 的兼容读取（不落库） */
    public static final String SOURCE_LEGACY_JSONB = "LEGACY_JSONB";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    public static final String CONFIDENCE_UNVERIFIED = "UNVERIFIED";
}
