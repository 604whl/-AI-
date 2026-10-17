package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("analysis_task")
public class AnalysisTask {

    @TableId
    private String id;

    private Long userId;

    private String workspaceId;

    private String scenario;

    private String persona;

    private String title;

    private String body;

    private String coverImageUrl;

    private String status;

    private String failureReason;

    private Integer failureCode;

    private String promptVersion;

    private String modelProvider;

    private String modelName;

    private Integer processingMs;

    private String publishedMetrics;

    private String competitorContext;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    // P0-1: task queue fields
    private String idempotencyKey;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private Integer retryCount;

    private OffsetDateTime deadLetterAt;

    private String workerId;

    /** P0-4: 关联的模板 ID（分析时可选择模板作为参考上下文） */
    private String templateId;
}
