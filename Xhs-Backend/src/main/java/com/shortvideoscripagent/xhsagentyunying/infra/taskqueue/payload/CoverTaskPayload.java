package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.payload;

import lombok.Data;

/**
 * P0-1: 封面分析任务载荷。
 */
@Data
public class CoverTaskPayload {
    private String taskId;
    private String coverImageUrl;
    private String title;
    private String body;
}
