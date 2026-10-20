package com.shortvideoscripagent.xhsagentyunying.template;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateLeaderboardEntry {
    private String templateId;
    private String title;
    private Long applyCount30d;
    private Double adoptionRate;
}