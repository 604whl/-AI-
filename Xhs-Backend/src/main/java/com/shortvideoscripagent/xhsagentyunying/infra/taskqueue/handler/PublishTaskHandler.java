package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorErrorCode;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorRegistry;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.connector.PublishRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.PublishResult;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishReceiptMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishReceipt;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import com.shortvideoscripagent.xhsagentyunying.service.PublishScheduleService;
import com.shortvideoscripagent.xhsagentyunying.service.PublishTaskService;
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
 * S3-4 发布任务处理器。
 * <p>
 * 处理类型：{@code PUBLISH_TASK}
 * </p>
 * <p>
 * <b>三条发布路径</b>（同一套 {@code publish_task} 表，用 {@code level} 区分）：
 * <ul>
 *   <li>{@code OFFICIAL_API} / {@code AUTHORIZED_CONNECTOR}：系统自动发布，
 *       拿到 {@code externalContentId} 后自动写回执</li>
 *   <li>{@code BROWSER_ASSIST}：系统只填充不点击发布 → 任务置 {@code manual_required}，
 *       <b>绝不写伪造的 external_content_id</b>，由用户回填</li>
 *   <li>{@code MANUAL_IMPORT}：生成待发布清单，由运营自行发布后回填</li>
 * </ul>
 * <p>
 * <b>发布不计费</b>：本处理器<b>不</b>调用 {@code AtomicQuotaGuard}、<b>不</b>写
 * {@code usage_ledger} DEBIT、<b>不</b>触发任何 {@code BillableUnit} 计量。
 * </p>
 * <p>
 * 失败时抛异常，由 {@code TaskQueueConsumer} 捕获重试；超过 maxRetry 后进 DLQ（既有机制自动处理）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class PublishTaskHandler implements TaskHandler {

    private final PublishTaskMapper publishTaskMapper;
    private final PublishReceiptMapper receiptMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final PublishTaskService publishTaskService;
    private final PublishScheduleService publishScheduleService;
    private final ConnectorRegistry connectorRegistry;
    private final ConnectorHealthService connectorHealthService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String taskType() {
        return PublishTaskService.TASK_TYPE;
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        log.info("PublishTaskHandler: taskId={}", taskId);

        String workspaceId = str(payload, "workspaceId");
        if (workspaceId == null) {
            throw new IllegalStateException("workspaceId required in payload");
        }
        String platformCode = str(payload, "platformCode");
        String accountId = str(payload, "accountId");
        String providerCode = str(payload, "providerCode");

        UUID publishTaskId = parseUuid(taskId);
        PublishTask task = publishTaskId == null
                ? null : publishTaskMapper.selectByIdAndWorkspace(publishTaskId, workspaceId);
        if (task == null) {
            log.warn("Publish task row not found: taskId={} ws={}", taskId, workspaceId);
            return;
        }

        // 已取消 / 已终态的任务直接跳过（保证"重复发布率 = 0"）
        if (PublishTask.STATUS_CANCELLED.equals(task.getStatus())
                || PublishTask.STATUS_SUCCEEDED.equals(task.getStatus())) {
            log.info("Skip publish task in terminal state: taskId={} status={}", taskId, task.getStatus());
            return;
        }

        // 1) 状态 → running
        int attempt = (task.getAttempt() == null ? 0 : task.getAttempt()) + 1;
        transactionTemplate.executeWithoutResult(tx -> publishTaskService.markRunning(
                workspaceId, task.getId(), attempt));

        try {
            // 2) 解析连接器（未授权的连接器不会注册到 registry，这里天然拒绝）
            PlatformConnector connector = resolveConnector(platformCode, providerCode);

            ContentAsset asset = task.getAssetId() == null
                    ? null : contentAssetMapper.selectById(task.getAssetId());
            String title = asset == null ? null : asset.getTitle();
            String body = asset == null ? null : asset.getBody();

            PublishRequest request = new PublishRequest(
                    workspaceId,
                    task.getAssetId(),
                    platformCode,
                    accountId,
                    title,
                    body,
                    task.getScheduledAt(),
                    "UTC",
                    Map.of());

            PublishResult result = connector.publish(request);

            // 3) 结果分支处理
            if (result == null) {
                throw new BusinessException(50000, "publish_result_is_null");
            }
            if (result.manual()) {
                // ── 需人工确认：绝不写伪造的 external_content_id ──
                transactionTemplate.executeWithoutResult(tx -> {
                    publishTaskService.updateStatus(workspaceId, task.getId(),
                            PublishTask.STATUS_MANUAL_REQUIRED, null,
                            "MANUAL_CONFIRMATION_REQUIRED: 需用户在平台页面确认发布后回填外部内容 ID / 来源链接",
                            Boolean.FALSE);
                });
                publishScheduleService.markStatus(workspaceId, task.getScheduleId(),
                        com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule.STATUS_EXECUTING,
                        null, "MANUAL_CONFIRMATION_REQUIRED");
                connectorHealthService.recordSuccess(connector.providerCode(), platformCode, workspaceId);
                log.info("Publish task requires manual confirmation: taskId={} level={}",
                        taskId, task.getLevel());
                return;
            }

            if (result.errorCode() != null) {
                // ── 发布失败 ──
                String errorMessage = result.errorMessage() == null
                        ? "publish_failed: " + result.errorCode() : result.errorMessage();
                transactionTemplate.executeWithoutResult(tx -> {
                    publishTaskService.updateStatus(workspaceId, task.getId(),
                            PublishTask.STATUS_FAILED, 50201, errorMessage, Boolean.TRUE);
                });
                publishScheduleService.markStatus(workspaceId, task.getScheduleId(),
                        com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule.STATUS_FAILED,
                        50201, errorMessage);
                connectorHealthService.recordFailure(connector.providerCode(), platformCode, workspaceId,
                        ConnectorErrorCode.UNKNOWN, errorMessage);
                throw new BusinessException(50201, errorMessage);
            }

            // ── 自动发布成功：写回执（必须真实拿到 externalContentId 或 sourceUrl）──
            String externalContentId = result.externalContentId();
            String sourceUrl = result.sourceUrl();
            if ((externalContentId == null || externalContentId.isBlank())
                    && (sourceUrl == null || sourceUrl.isBlank())) {
                // 回执不完整：不得假装成功
                String errorMessage = "RECEIPT_INCOMPLETE: connector returned neither external_content_id nor source_url";
                transactionTemplate.executeWithoutResult(tx -> {
                    publishTaskService.updateStatus(workspaceId, task.getId(),
                            PublishTask.STATUS_FAILED, 40071, errorMessage, Boolean.TRUE);
                });
                publishScheduleService.markStatus(workspaceId, task.getScheduleId(),
                        com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule.STATUS_FAILED,
                        40071, errorMessage);
                throw new BusinessException(40071, errorMessage);
            }

            OffsetDateTime connectorPublishedAt = result.publishedAt();
            boolean localFallback = connectorPublishedAt == null;
            final OffsetDateTime publishedAt = localFallback ? OffsetDateTime.now() : connectorPublishedAt;

            transactionTemplate.executeWithoutResult(tx -> {
                PublishReceipt existing = receiptMapper.selectByTaskId(task.getId());
                PublishReceipt receipt = existing == null ? new PublishReceipt() : existing;
                if (existing == null) {
                    receipt.setId(UUID.randomUUID());
                    receipt.setPublishTaskId(task.getId());
                    receipt.setWorkspaceId(workspaceId);
                    receipt.setAssetId(task.getAssetId());
                    receipt.setCreatedAt(OffsetDateTime.now());
                }
                receipt.setExternalContentId(externalContentId);
                receipt.setSourceUrl(sourceUrl);
                receipt.setPublishedAt(publishedAt);
                receipt.setPublishedAtSource(localFallback
                        ? PublishReceipt.SOURCE_LOCAL : PublishReceipt.SOURCE_PLATFORM);
                receipt.setVerified(Boolean.TRUE);
                if (existing == null) {
                    receiptMapper.insert(receipt);
                } else {
                    receiptMapper.updateById(receipt);
                }
                publishTaskService.updateStatus(workspaceId, task.getId(),
                        PublishTask.STATUS_SUCCEEDED, null, null, Boolean.FALSE);
                publishTaskService.markAssetPublished(workspaceId, task.getAssetId());
                publishScheduleService.markStatus(workspaceId, task.getScheduleId(),
                        com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule.STATUS_SUCCEEDED,
                        null, null);
            });

            connectorHealthService.recordSuccess(connector.providerCode(), platformCode, workspaceId);
            log.info("Publish task succeeded: taskId={} externalContentId={} sourceUrl={}",
                    taskId, externalContentId, sourceUrl);

        } catch (BusinessException be) {
            // 业务异常已在分支内落库状态；连接器健康度统一记一次失败
            if (be.getCode() != 40071 && be.getCode() != 50201) {
                connectorHealthService.recordFailure(providerCode, platformCode, workspaceId,
                        ConnectorErrorCode.UNKNOWN, be.getMessage());
            }
            throw be;
        } catch (Exception ex) {
            log.error("Publish task failed: taskId={}", taskId, ex);
            int code = ex instanceof BusinessException be ? be.getCode() : 50000;
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            transactionTemplate.executeWithoutResult(tx -> publishTaskService.updateStatus(
                    workspaceId, task.getId(), PublishTask.STATUS_FAILED, code, message, Boolean.TRUE));
            publishScheduleService.markStatus(workspaceId, task.getScheduleId(),
                    com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule.STATUS_FAILED,
                    code, message);
            connectorHealthService.recordFailure(providerCode, platformCode, workspaceId,
                    ConnectorErrorCode.UNKNOWN, message);
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
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
