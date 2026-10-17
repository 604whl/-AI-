package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * P0-1: 笔记分析任务载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskPayload {
    private String taskId;
    private String scenario;
    private String persona;
    private String title;
    private String body;
    private String coverImageUrl;
    private String publishedMetrics;
    private String competitorContext;
    private String promptVersion;
    private String modelProvider;
    private String modelName;
}
