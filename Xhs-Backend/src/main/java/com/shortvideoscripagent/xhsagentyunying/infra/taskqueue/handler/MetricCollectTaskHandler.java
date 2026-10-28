package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorRegistry;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricFetchRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricSample;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricCollectTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricCollectTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import com.shortvideoscripagent.xhsagentyunying.service.MetricSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * S3-5 指标采集任务处理器。
 * <p>
 * 处理类型：{@code METRIC_COLLECT}
 * </p>
 * <p>
 * <b>采集不计费</b>：本处理器<b>不</b>调用 {@code AtomicQuotaGuard}、<b>不</b>写 {@code usage_ledger} DEBIT、
 * <b>不</b>触发任何 {@code BillableUnit} 计量。
 * </p>
 * <p>
 * 失败时抛异常，由 {@code TaskQueueConsumer} 捕获重试，超过 maxRetry 后进 DLQ（既有机制自动处理）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class MetricCollectTaskHandler implements TaskHandler {

    private final MetricCollectTaskMapper collectTaskMapper;
    private final MetricSnapshotService metricSnapshotService;
    private final ConnectorRegistry connectorRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String taskType() {
        return "METRIC_COLLECT";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        log.info("MetricCollectTaskHandler: taskId={}", taskId);

        String workspaceId = str(payload, "workspaceId");
        if (workspaceId == null) {
            throw new IllegalStateException("workspaceId required in payload");
        }
        String platformCode = str(payload, "platformCode");
        String accountId = str(payload, "accountId");
        String providerCode = str(payload, "providerCode");

        UUID collectTaskId = parseUuid(taskId);
        MetricCollectTask task = collectTaskId == null
                ? null : collectTaskMapper.selectByIdAndWorkspace(collectTaskId, workspaceId);
        if (task == null) {
            log.warn("Metric collect task row not found: taskId={} ws={}", taskId, workspaceId);
            return;
        }

        OffsetDateTime windowStart = parseDateTime(str(payload, "windowStart"), task.getWindowStart());
        OffsetDateTime windowEnd = parseDateTime(str(payload, "windowEnd"), task.getWindowEnd());

        // 1) 状态 → running
        transactionTemplate.executeWithoutResult(tx -> {
            MetricCollectTask update = new MetricCollectTask();
            update.setId(task.getId());
            update.setStatus(MetricCollectTask.STATUS_RUNNING);
            update.setAttempt((task.getAttempt() == null ? 0 : task.getAttempt()) + 1);
            update.setUpdatedAt(OffsetDateTime.now());
            collectTaskMapper.updateById(update);
        });

        try {
            // 2) 解析连接器（未授权的连接器不会注册到 registry，这里天然拒绝）
            PlatformConnector connector = resolveConnector(platformCode, providerCode);

            MetricFetchRequest request = new MetricFetchRequest(
                    workspaceId, platformCode, accountId, null, windowStart, windowEnd);
            List<MetricSample> samples = connector.fetchMetrics(request);

            // 3) 落库（幂等：同窗口重复采集不产生重复快照）
            int inserted = metricSnapshotService.persistCollectedSamples(workspaceId, task, samples, providerCode);

            // 4) 状态 → succeeded + 同步状态刷新
            OffsetDateTime now = OffsetDateTime.now();
            transactionTemplate.executeWithoutResult(tx -> {
                MetricCollectTask update = new MetricCollectTask();
                update.setId(task.getId());
                update.setStatus(MetricCollectTask.STATUS_SUCCEEDED);
                update.setLastSuccessAt(now);
                update.setUpdatedAt(now);
                collectTaskMapper.updateById(update);
            });
            metricSnapshotService.markCollectSuccess(workspaceId, platformCode, accountId);

            log.info("Metric collect succeeded: taskId={} samples={} inserted={}",
                    taskId, samples == null ? 0 : samples.size(), inserted);

        } catch (Exception ex) {
            log.error("Metric collect failed: taskId={}", taskId, ex);
            int code = ex instanceof BusinessException be ? be.getCode() : 50000;
            transactionTemplate.executeWithoutResult(tx -> {
                MetricCollectTask update = new MetricCollectTask();
                update.setId(task.getId());
                update.setStatus(MetricCollectTask.STATUS_FAILED);
                update.setErrorCode(code);
                update.setErrorMessage(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                update.setUpdatedAt(OffsetDateTime.now());
                collectTaskMapper.updateById(update);
            });
            // 连续失败计数 +1，达到阈值标 DEGRADED
            metricSnapshotService.markCollectFailure(workspaceId, platformCode, accountId, ex.getMessage());
            throw ex;
        }
    }

    private PlatformConnector resolveConnector(String platformCode, String providerCode) {
        if (providerCode != null) {
            Optional<PlatformConnector> byProvider = connectorRegistry.find(providerCode);
            if (byProvider.isPresent()) {
                return byProvider.get();
            }
        }
        List<PlatformConnector> byPlatform = connectorRegistry.listByPlatform(platformCode);
        if (!byPlatform.isEmpty()) {
            return byPlatform.get(0);
        }
        throw new BusinessException(40302, "CONNECTOR_NOT_AUTHORIZED");
    }

    private String str(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? null : text;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private OffsetDateTime parseDateTime(String raw, OffsetDateTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
