package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Single data point in a hot topic trend series.
 */
@Data
@Builder
public class HotTopicTrendPoint {

    private String date;       // yyyy-MM-dd
    private Integer heatScore;
    private OffsetDateTime fetchedAt;
}
