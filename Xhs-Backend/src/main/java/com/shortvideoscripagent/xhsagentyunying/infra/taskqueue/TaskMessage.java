package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

/**
 * P0-1: 任务队列消息体。
 * 投递到 Redis Stream 的每条消息包含以下字段。
 */
public record TaskMessage(
        /** 数据库 analysis_task.id */
        String taskId,
        /** 任务类型：ANALYSIS / TITLE_GENERATION / BODY_GENERATION / COVER_ANALYSIS */
        String taskType,
        /** JSON 序列化的任务载荷 */
        String payload,
        /** 当前尝试次数（从 1 开始） */
        int attempt,
        /** 任务创建时间或计划执行时间 */
        long scheduledAt
) {
}
