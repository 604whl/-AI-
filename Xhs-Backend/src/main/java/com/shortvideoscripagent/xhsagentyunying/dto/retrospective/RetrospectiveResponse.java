package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3-7 复盘报告详情响应。
 * <p>
 * 预测侧（{@code predictions}）与实际侧（{@code actuals}）<b>分字段</b>返回，
 * 各自标注来源与时间；{@code suggestions} 每条必须带 {@code evidenceRefs}。
 * </p>
 */
@Data
@Builder
public class RetrospectiveResponse {

    /** 报告 ID */
    private UUID id;

    /** 报表维度 */
    private String scopeType;

    /** 维度键 */
    private String scopeKey;

    /** 关联内容资产 */
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

    /** 预测侧聚合（维度 → 预测值 + 来源） */
    private Map<String, Object> predictions;

    /** 实际侧聚合（维度 → 实际值 + 来源） */
    private Map<String, Object> actuals;

    /** 命中维度 */
    private List<DimensionEvidence> hitDimensions;

    /** 失败维度 */
    private List<DimensionEvidence> missedDimensions;

    /** 建议（每条带 evidenceRefs） */
    private List<RetrospectiveSuggestion> suggestions;

    /** 数据不足时缺失的维度说明 */
    private List<String> missingDimensions;

    /** 状态：PENDING / COMPLETED / DEGRADED / INSUFFICIENT_DATA */
    private String status;

    /** 是否降级报告 */
    private Boolean degraded;

    /** 降级原因 */
    private String degradedReason;

    /** 生成所用模型 */
    private String modelName;

    /** 生成所用提示词版本 */
    private String promptVersion;

    /** 生成所用知识库版本 */
    private String kbVersion;

    /** 报告生成时间 */
    private OffsetDateTime generatedAt;

    /**
     * 维度证据（命中 / 失败维度共用）。
     */
    @Data
    @Builder
    public static class DimensionEvidence {
        /** 维度名 */
        private String dimension;
        /** 证据描述（指标 + 数值 + 来源） */
        private String evidence;
        /** 可能原因（失败维度） */
        private List<String> possibleReasons;
    }

    /**
     * 复盘建议。
     * <p>
     * {@code evidenceRefs} 必须非空且指向真实存在的 snapshot / comparison；
     * 无证据的建议在落库前被丢弃。
     * </p>
     */
    @Data
    @Builder
    public static class RetrospectiveSuggestion {
        /** 建议类型：TOPIC / TITLE / BODY / POST_TIME */
        private String type;
        /** 可执行动作描述 */
        private String action;
        /** 预期影响 */
        private String expectedImpact;
        /** 证据引用（snapshot / comparison ID） */
        private List<String> evidenceRefs;
        /** 置信度 */
        private String confidence;
        /** 合规扫描命中项（规则码 + 片段） */
        private List<String> complianceFlags;
    }
}
