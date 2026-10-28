package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * S3-8 模板效果统计响应（{@code GET /templates/{id}/effect-stats}）。
 * <p>
 * 统计基于 {@code app.template_apply_log}（含 S3-8 新增的 {@code effect_stats} 列），
 * <b>不新建模板统计表</b>。
 * </p>
 */
@Data
@Builder
public class TemplateEffectStatsResponse {

    /** 模板 ID */
    private java.util.UUID templateId;

    /** 模板标题 */
    private String templateTitle;

    /** 模板引用次数 */
    private Long applyCount;

    /** 采用率（已发布资产数 / 引用次数，%） */
    private BigDecimal adoptionRate;

    /** 平均曝光量 */
    private BigDecimal avgImpressions;

    /** 平均收藏率（收藏 / 曝光） */
    private BigDecimal avgCollectRate;

    /** 平均私信咨询数 */
    private BigDecimal avgDmInquiries;

    /** 参与统计的样本量（带 effect_stats 的引用数） */
    private Long sampleSize;
}
