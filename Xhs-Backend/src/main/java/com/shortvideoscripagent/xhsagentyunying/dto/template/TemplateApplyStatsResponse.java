package com.shortvideoscripagent.xhsagentyunying.dto.template;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TemplateApplyStatsResponse {
    private String templateId;
    private Long totalCount;
    private Long adoptionCount;
    private Double adoptionRate;
    private List<DailyStat> dailyStats;

    @Data @Builder
    public static class DailyStat {
        private String day;
        private Long count;
    }
}