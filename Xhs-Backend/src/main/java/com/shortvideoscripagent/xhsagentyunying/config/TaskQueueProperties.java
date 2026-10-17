package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * P0-1: Redis Streams 任务队列配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.task-queue")
public class TaskQueueProperties {

    /** Redis Stream key */
    private String streamKey = "xhs:tasks:stream";

    /** 死信队列 Stream key */
    private String dlqKey = "xhs:tasks:dlq";

    /** Consumer Group 名称 */
    private String consumerGroup = "xhs-workers";

    /** 最大重试次数 */
    private int maxRetry = 3;

    /** 指数退避秒数序列，如 [1,2,4] */
    private List<Integer> retryBackoff = List.of(1, 2, 4);

    /** 当前 worker 唯一标识 */
    private String workerId = System.getProperty("HOSTNAME", "worker-1");

    /** XREADGROUP 阻塞超时 */
    private Duration pollTimeout = Duration.ofSeconds(5);

    /** 是否启用 Redis Streams 任务队列，默认 false（走旧的 @Async 兜底） */
    private boolean enabled = false;

    /** 任务恢复：心跳超时秒数 */
    private int heartbeatTimeoutSeconds = 30;

    /** 幂等键保留天数 */
    private int idempotencyKeyTtlDays = 90;

    /**
     * 获取第 N 次重试的延迟秒数（N 从 1 开始）。
     */
    public long retryDelaySeconds(int attempt) {
        if (retryBackoff == null || retryBackoff.isEmpty()) {
            return (long) Math.pow(2, attempt - 1);
        }
        int index = Math.min(attempt - 1, retryBackoff.size() - 1);
        return retryBackoff.get(index);
    }
}
