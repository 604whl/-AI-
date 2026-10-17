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

    private void consumeLoop() {
        String streamKey = properties.getStreamKey();
        String group = properties.getConsumerGroup();
        String consumer = properties.getWorkerId();

        // 确保 Consumer Group 存在
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
        } catch (Exception ex) {
            // BUSYGROUP 表示已存在，忽略
            if (!ex.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group {} may already exist: {}", group, ex.getMessage());
            }
        }

        // 写入初始心跳
        heartbeat.heartbeat();

        while (running.get()) {
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
