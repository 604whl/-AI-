package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Hot topic list item DTO.
 */
@Data
@Builder
public class HotTopicListItem {

    private Long id;
    private String platform;
    private String category;
    private String title;
    private Integer heatScore;
    private OffsetDateTime fetchedAt;
    private Boolean promotedToPool;
}
