package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * S3-10 竞品账号维度互动率对比。
 * <p>
 * <b>统一口径</b>：点赞率 = likes / impressions、收藏率 = collects / impressions、
 * 评论率 = comments / impressions；分母为 0 时比率记 0 并标注 {@code rateAvailable=false}
 * （不写 Infinity / 不伪造数值）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAccountComparison {

    /** 对比口径说明（前端直接展示，避免跨平台口径误读） */
    private String metricBasis;

    /** 各账号的对比数据 */
    private List<AccountRow> rows;

    /**
     * 单账号对比行。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountRow {
        private String accountId;
        private String platformCode;
        private String accountName;
        private String category;
        private Long followerCount;
        private Long noteCount;
        private Long impressions;
        private Long likes;
        private Long collects;
        private Long comments;
        /** 点赞 / 曝光 */
        private Double likeRate;
        /** 收藏 / 曝光 */
        private Double collectRate;
        /** 评论 / 曝光 */
        private Double commentRate;
        /** 分母是否为 0（true 表示比率不可算） */
        private Boolean rateAvailable;
        /** 最后更新时间 */
        private java.time.OffsetDateTime updatedAt;
    }
}
