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
 * S3-7 复盘报告实体（{@code app.retrospective_report}，V57）。
 * <p>
 * 预测侧聚合存 {@code predictions}，实际侧聚合存 {@code actuals}，<b>分字段</b>，不混。
 * </p>
 * <p>
 * {@code suggestions} 每条必须带 {@code evidenceRefs[]}（指向真实存在的
 * {@code metric_snapshot.id} / {@code metric_comparison.id}），无证据的建议在落库前被丢弃。
 * </p>
 */
@Data
@TableName(value = "retrospective_report", autoResultMap = true)
public class RetrospectiveReport {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区 */
    private String workspaceId;

    /** 报表维度：ASSET / ACCOUNT / PLATFORM / CATEGORY / CAMPAIGN / PERIOD */
    private String scopeType;

    /** 维度键（如资产 ID / 账号 ID / 平台码） */
    private String scopeKey;

    /** 关联内容资产（可空） */
    private UUID assetId;

    /** 平台账号 ID */
    private String accountId;

    /** 平台代码 */
    private String platformCode;

    /** 品类编码 */
    private String categoryCode;

    /** 活动 ID */
    private String campaignId;

    /** 统计周期开始 */
    private OffsetDateTime periodStart;

    /** 统计周期结束 */
    private OffsetDateTime periodEnd;

    /** 报告摘要 */
    private String summary;

    /** 预测侧聚合 JSONB（维度 → 预测值 + 来源） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String predictions;

    /** 实际侧聚合 JSONB（维度 → 实际值 + 来源） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String actuals;

    /** 命中维度 JSONB 数组 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String hitDimensions;

    /** 失败维度 JSONB 数组 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String missedDimensions;

    /** 建议 JSONB 数组（每条必须带 evidenceRefs） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String suggestions;

    /** 生成所用模型 */
    private String modelName;

    /** 生成所用提示词版本 */
    private String promptVersion;

    /** 生成所用知识库版本 */
    private String kbVersion;

    /** 报告生成时间 */
    private OffsetDateTime generatedAt;

    /** 状态：PENDING / COMPLETED / DEGRADED / INSUFFICIENT_DATA */
    private String status;

    /** 是否降级报告（AI 调用 / 解析失败 / 数据不足） */
    private Boolean degraded;

    /** 降级原因 */
    private String degradedReason;

    /** 数据不足时缺失的维度说明 JSONB 数组 */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String missingDimensions;

    /** 幂等键（工作区内唯一） */
    private String idempotencyKey;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────

    public static final String SCOPE_ASSET = "ASSET";
    public static final String SCOPE_ACCOUNT = "ACCOUNT";
    public static final String SCOPE_PLATFORM = "PLATFORM";
    public static final String SCOPE_CATEGORY = "CATEGORY";
    public static final String SCOPE_CAMPAIGN = "CAMPAIGN";
    public static final String SCOPE_PERIOD = "PERIOD";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
}
