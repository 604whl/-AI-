package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Trend API response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicTrendResponse {

    private String title;
    private List<HotTopicTrendPoint> points;
    /** "rising" | "falling" | "flat" | "new" */
    private String trend;
    /** Absolute delta: latest heatScore - oldest heatScore in window */
    private Integer delta;
    /** Most recent heatScore in the window */
    private Integer latest;
}
