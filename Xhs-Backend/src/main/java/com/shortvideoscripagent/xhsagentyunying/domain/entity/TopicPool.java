package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.TextArrayTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@TableName(value = "topic_pool", autoResultMap = true)
public class TopicPool {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Workspace ID（VARCHAR(32)，与 app.workspace.id 一致；V49 由 BIGINT 归一） */
    private String workspaceId;

    private String title;

    private String source;

    private Integer heatScore;

    private String status;
@TableField(typeHandler = TextArrayTypeHandler.class)

    private List<String> tags;

    private String targetPersona;

    private Long createdBy;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private LocalDate scheduledDate;

    // ── 阶段 3 S3-9：热点来源透传 ──────────────────────────────────────────

    /** 来源名称（透传自 hot_topic.source_name），用于选题来源可信度追溯 */
    private String sourceName;

    /** 可信度分级（透传自 hot_topic.confidence）：HIGH / MEDIUM / LOW / UNVERIFIED */
    private String confidence;

    /** 来源热点采集时间（透传自 hot_topic.fetched_at），用于判断选题时效性 */
    private OffsetDateTime sourceFetchedAt;

    /**
     * Normalized title for deduplication lookup.
     * Not persisted to DB directly; populated by V40 migration or set during insert.
     */
    @TableField(exist = false)
    private String normalizedTitle;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_USED = "used";
    public static final String STATUS_ARCHIVED = "archived";
}