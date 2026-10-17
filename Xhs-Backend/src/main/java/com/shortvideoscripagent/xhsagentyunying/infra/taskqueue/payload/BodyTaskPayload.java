package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.payload;

import lombok.Data;

/**
 * P0-1: 正文生成任务载荷。
 */
@Data
public class BodyTaskPayload {
    private String analysisId;
    private String goal;
    private String tone;
    private Integer maxLength;
    private java.util.List<String> keywords;
}
