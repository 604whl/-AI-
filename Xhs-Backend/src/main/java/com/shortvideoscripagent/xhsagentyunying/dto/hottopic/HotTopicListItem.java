package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * S3-9 热点列表项 DTO。
 * <p>
 * <b>硬性约束</b>：{@code sourceType} / {@code isRealtime} 必须对外暴露，
 * 前端据此显式标注"非实时"——禁止把静态种子伪装成实时热点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicListItem {

    private Long id;

    private String platform;

    private String category;

    private String title;

    private Integer heatScore;

    /** 采集时间（接口必须对外暴露） */
    private OffsetDateTime fetchedAt;

    private Boolean promotedToPool;

    // ── S3-9 新增：来源与可信度 ──────────────────────────────────────────

    /** 来源名称（web_search / static_seed / 连接器 providerCode / 手动导入标记） */
    private String sourceName;

    /** 来源类型：SEARCH_TOOL / OFFICIAL_API / AUTHORIZED_CONNECTOR / MANUAL_IMPORT / SEED */
    private String sourceType;

    /** 可信度分级：HIGH / MEDIUM / LOW / UNVERIFIED */
    private String confidence;

    /** 是否实时采集；静态种子恒为 false，前端必须显式标注"非实时" */
    private Boolean isRealtime;

    /** 原始来源链接 */
    private String sourceUrl;

    /** 该条数据是否处于降级状态 */
    private Boolean degraded;

    /** 降级原因（禁止静默展示陈旧数据） */
    private String degradedReason;

    /** 去重状态：active / superseded */
    private String status;

    /**
     * 可做方向数组；每条必须引用该热点的真实标题 + 来源 + 采集时间。
     * <p>
     * 结构：{@code [{"angle":"...","evidence":{"title":"...","sourceName":"...","fetchedAt":"..."}}]}
     * </p>
     */
    private List<HotTopicSuggestedAngle> suggestedAngles;

    /** 趋势：rising / falling / flat / new（单条记录不得输出"上升"） */
    private String trend;

    /**
     * 可做方向。
     *
     * @param angle   方向描述
     * @param evidence 证据（真实标题 + 来源 + 采集时间），禁止泛化
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotTopicSuggestedAngle {
        private String angle;
        private Evidence evidence;

        /**
         * 证据引用。
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Evidence {
            /** 该热点的真实标题 */
            private String title;
            /** 来源名称 */
            private String sourceName;
            /** 采集时间 */
            private OffsetDateTime fetchedAt;
        }
    }
}
