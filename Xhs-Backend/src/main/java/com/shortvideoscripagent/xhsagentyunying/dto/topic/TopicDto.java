package com.shortvideoscripagent.xhsagentyunying.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicDto {

    private Long id;
    private Long workspaceId;
    private String title;
    private String source;
    private Integer heatScore;
    private String status;
    private List<String> tags;
    private String targetPersona;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private LocalDate scheduledDate;
}