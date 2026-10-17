package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P0-1: 任务队列消费者运行循环。
 * <p>
 * 使用 Redis Stream XREADGROUP 消费消息，将任务路由到 {@link TaskRegistry} 对应的 {@link TaskHandler}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TaskQueueConsumerRunner {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TaskQueueProperties properties;

    @Autowired
    private TaskRegistry taskRegistry;

    @Autowired
    private TaskHeartbeat heartbeat;

    @Autowired
    private TaskQueueProducer producer;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private TaskCancellationService cancellationService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService executor;

    /** 确保只初始化一次，防止 Redis Stream 不存在时反复重试 */
    private volatile boolean groupInitialized = false;

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "task-queue-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::consumeLoop);
        log.info("TaskQueueConsumer started, workerId={}", properties.getWorkerId());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
        heartbeat.unregister();
        log.info("TaskQueueConsumer stopped");
    }

    /**
     * [P2 FIX] 初始化 Consumer Group。
     * - 先 createGroup：Stream 存在时正常创建 Group
     * - 再 XADD dummy 消息：确保 Stream 存在（MKSTREAM 的等价实现）
     * 调用方通过检查 `groupInitialized` 标志判断是否成功。
     */
    private void initConsumerGroup(String streamKey, String group) {
        try {
            // 1. 尝试创建 Consumer Group（Stream 存在时正常，Stream 不存在时抛 NOGROUP）
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
            log.info("Consumer group {} created for stream {}", group, streamKey);
            groupInitialized = true;
            return;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("BUSYGROUP")) {
                // Group 已存在，非错误，初始化成功
                log.debug("Consumer group {} already exists, skipping", group);
                groupInitialized = true;
                return;
            } else if (msg.contains("NOGROUP")) {
                // Stream 不存在 → 用 XADD 创建 Stream，同时 XADD 一条 __init__ 消息充当占位
                // 后续 XREADGROUP 会读到这条 __init__ 消息，在 processRecord 中跳过它
                log.info("Stream {} does not exist, creating with XADD", streamKey);
                try {
                    redisTemplate.opsForStream().add(
                            streamKey,
                            Map.of("taskId", "__init__", "taskType", "__init__",
                                    "payload", "{}", "attempt", "0", "scheduledAt", "0")
                    );
                    // 再次创建 Group（此时 Stream 已存在）
                    redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
                    log.info("Stream {} and consumer group {} created", streamKey, group);
                    groupInitialized = true;
                    return;
                } catch (Exception initEx) {
                    String initMsg = initEx.getMessage() == null ? "" : initEx.getMessage();
                    if (initMsg.contains("BUSYGROUP")) {
                        log.debug("Consumer group {} already exists after init", group);
                        groupInitialized = true;
                        return;
                    } else {
                        log.warn("Failed to init stream/group for {}: {}", streamKey, initMsg);
                    }
                }
            } else {
                log.warn("Unexpected error initializing stream/group for {}: {}", streamKey, msg);
            }
        }
        // 初始化失败，groupInitialized 保持 false，下次循环重试
    }

    private void consumeLoop() {
        String streamKey = properties.getStreamKey();
        String group = properties.getConsumerGroup();
        String consumer = properties.getWorkerId();

        while (running.get()) {
            // [P2 FIX] 初始化/重试 Consumer Group，直到成功。
            // - Stream 不存在 → createGroup 抛 NOGROUP → XADD 创建 Stream → 重试 createGroup
            // - BUSYGROUP → Group 已存在，非错误，初始化成功
            // - 其他错误 → warn + sleep(2s) + 下次循环重试（groupInitialized 仍为 false）
            // 初始化成功后不再重试，避免 XREADGROUP 白白报错。
            if (!groupInitialized) {
                initConsumerGroup(streamKey, group);
                if (!groupInitialized) {
                    // 初始化未成功（可能 NOGROUP + XADD 失败，或 Redis 暂时不可用），稍后重试
                    sleep(2000);
                    continue;
                }
            }

            try {
                // 定期心跳
                heartbeat.heartbeat();

                // XREADGROUP 阻塞读取
                var records = redisTemplate.opsForStream().read(
                        Consumer.from(consumer, consumer),
                        StreamReadOptions.empty()
                                .count(1)
                                .block(Duration.ofMillis(properties.getPollTimeout().toMillis())),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    try {
                        processRecord(record);
                        // 处理成功后 ACK
                        redisTemplate.opsForStream().acknowledge(streamKey, group, record.getId());
                    } catch (Exception ex) {
                        handleFailure(record, ex);
                    }
                }
            } catch (Exception ex) {
                if (running.get()) {
                    log.error("Consumer loop error, will retry", ex);
                    sleep(1000);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processRecord(MapRecord<String, Object, Object> record) throws Exception {
        Map<Object, Object> fields = record.getValue();

        String taskId = String.valueOf(fields.get("taskId"));
        String taskType = String.valueOf(fields.get("taskType"));
        String payloadJson = String.valueOf(fields.get("payload"));
        int attempt = Integer.parseInt(String.valueOf(fields.get("attempt")));
        long scheduledAt = Long.parseLong(String.valueOf(fields.get("scheduledAt")));

        log.debug("Processing task: taskId={} taskType={} attempt={}", taskId, taskType, attempt);

        // 检查用户取消信号（仅在执行 handler 之前；运行中取消由 handler 内部检查）
        if (cancellationService.isCancelled(taskId)) {
            log.info("Skipping cancelled task: taskId={} taskType={}", taskId, taskType);
            cancellationService.clearSignal(taskId);
            return;
        }

        // 调度时间还没到，等待
        long waitMs = scheduledAt - System.currentTimeMillis();
        if (waitMs > 0) {
            log.debug("Task {} scheduled for later, waiting {}ms", taskId, waitMs);
            sleep(Math.min(waitMs, 5000));
        }

        // 二次检查（避免等待期间被取消）
        if (cancellationService.isCancelled(taskId)) {
            log.info("Skipping cancelled task after wait: taskId={}", taskId);
            cancellationService.clearSignal(taskId);
            return;
        }

        // 查找 handler
        TaskHandler handler = taskRegistry.getHandler(taskType);

        // 反序列化 payload
        Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(payloadJson, Map.class);

        // 处理（handler 内部会更新状态）
        handler.handle(taskId, payload);

        // 处理成功后清理取消信号（避免 key 长期残留）
        cancellationService.clearSignal(taskId);

        log.info("Task completed: taskId={} taskType={}", taskId, taskType);
    }

    private void handleFailure(MapRecord<String, Object, Object> record, Exception ex) {
        Map<Object, Object> fields = record.getValue();
        String taskId = String.valueOf(fields.get("taskId"));
        String taskType = String.valueOf(fields.get("taskType"));
        String payload = String.valueOf(fields.get("payload"));
        int attempt = Integer.parseInt(String.valueOf(fields.get("attempt")));
        int maxRetry = properties.getMaxRetry();

        log.error("Task failed: taskId={} taskType={} attempt={}/{}",
                taskId, taskType, attempt, maxRetry, ex);

        // ACK 当前消息（无论成功失败都 ACK，防止消息阻塞）
        try {
            redisTemplate.opsForStream().acknowledge(
                    properties.getStreamKey(),
                    properties.getConsumerGroup(),
                    record.getId()
            );
        } catch (Exception ackEx) {
            log.warn("ACK failed for taskId={}: {}", taskId, ackEx.getMessage());
        }

        if (attempt >= maxRetry) {
            // 超次数，入 DLQ
            int errorCode = 50000;
            if (ex instanceof com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException be) {
                errorCode = be.getCode();
            }
            deadLetterQueue.enqueue(
                    taskId,
                    taskType,
                    payload,
                    ex.getMessage(),
                    errorCode,
                    attempt
            );
        } else {
            // 调度重试
            producer.scheduleRetry(taskId, taskType, payload, attempt + 1);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
