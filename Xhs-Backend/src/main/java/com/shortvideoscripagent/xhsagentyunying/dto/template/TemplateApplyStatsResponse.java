package com.shortvideoscripagent.xhsagentyunying.dto.template;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模板引用统计响应（P0-4）。
 * <p>
 * 返回按日聚合的引用次数。
 */
@Data
@Builder
public class TemplateApplyStatsResponse {

    /** 模板 ID */
    private String templateId;

    /** 总引用次数 */
    private Long totalCount;

    /** 每日统计列表（最近 30 天） */
    private List<DailyStat> dailyStats;

    @Data
    @Builder
    public static class DailyStat {
        /** 日期，格式：YYYY-MM-DD */
        private String day;

        /** 当日引用次数 */
        private Long count;
    }
}
