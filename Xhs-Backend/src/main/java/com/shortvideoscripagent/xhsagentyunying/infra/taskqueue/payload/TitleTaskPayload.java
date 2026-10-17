package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.payload;

import lombok.Data;

/**
 * P0-1: 标题生成任务载荷。
 */
@Data
public class TitleTaskPayload {
    private String analysisId;
    private String title;
    private String body;
    private String persona;
    private String goal;
    private Integer count;
}
