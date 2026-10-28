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

    /** Workspace ID（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

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

    // ── 阶段 3 S3-9：热点来源透传 ──────────────────────────────────────────

    /** 来源名称（透传自 hot_topic.source_name） */
    private String sourceName;

    /** 可信度分级（透传自 hot_topic.confidence）：HIGH / MEDIUM / LOW / UNVERIFIED */
    private String confidence;

    /** 来源热点采集时间（透传自 hot_topic.fetched_at） */
    private OffsetDateTime sourceFetchedAt;
}