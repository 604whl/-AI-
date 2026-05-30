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
}
