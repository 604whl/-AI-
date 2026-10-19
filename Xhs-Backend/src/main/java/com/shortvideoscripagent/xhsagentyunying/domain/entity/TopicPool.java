package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@TableName("topic_pool")
public class TopicPool {

    @TableId(type = IdType.AUTO)
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