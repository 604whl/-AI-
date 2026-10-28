package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorAuthorizationService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorRegistry;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformAccount;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformAccountMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishReceiptMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishScheduleMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishReceiptResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishTaskCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishTaskResponse;
import com.shortvideoscripagent.xhsagentyunying.freeze.ContentStatus;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformConnectorLevel;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * S3-4 发布任务服务。
 * <p>
 * 核心约束：
 * <ul>
 *   <li><b>幂等</b>：{@code POST /publish-tasks} 必须带 {@code Idempotency-Key}；
 *       同 key 重复提交只返回同一个任务（DB 唯一索引 + 入队前查重双重保证）</li>
 *   <li><b>不伪造外部内容 ID</b>：{@code BROWSER_ASSIST} / {@code MANUAL_IMPORT} 等级连接器返回
 *       {@code manual=true} 时任务置 {@code manual_required}，绝不写 {@code external_content_id}</li>
 *   <li><b>连接器不可用不阻塞</b>：未授权 → 403 {@code CONNECTOR_NOT_AUTHORIZED}；
 *       无可用连接器 → 503 {@code CONNECTOR_UNAVAILABLE} 并附 {@code degradedTo}，
 *       内容编辑 / 审核 / 导出不受影响</li>
 *   <li><b>发布不计费</b>：不调用 {@code AtomicQuotaGuard}、不写 {@code usage_ledger} DEBIT
 *       （{@code BillableUnit} 中没有"发布"计量单位，发布也不消耗联网搜索额度）</li>
 *   <li><b>工作区隔离</b>：越权访问按"未找到"处理</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishTaskService {

    // ── 错误码 ────────────────────────────────────────────────────────────
    /** 400 参数非法 */
    public static final int CODE_BAD_REQUEST = 40070;
    /** 400 回执不完整（external_content_id / source_url 皆空） */
    public static final int CODE_RECEIPT_INCOMPLETE = 40071;
    /** 403 连接器未授权 */
    public static final int CODE_CONNECTOR_NOT_AUTHORIZED = 40302;
    /** 404 任务不存在 / 越权 */
    public static final int CODE_TASK_NOT_FOUND = 40470;
    /** 404 资产不存在 / 越权 */
    public static final int CODE_ASSET_NOT_FOUND = 40450;
    /** 404 平台账号不存在 / 越权 */
    public static final int CODE_ACCOUNT_NOT_FOUND = 40461;
    /** 409 幂等键缺失 */
    public static final int CODE_IDEMPOTENCY_KEY_REQUIRED = 40970;
    /** 409 重复发布（同一资产 + 平台 + 账号已有活跃任务） */
    public static final int CODE_DUPLICATE_PUBLISH = 40971;
    /** 409 任务不可取消 */
    public static final int CODE_TASK_NOT_CANCELLABLE = 40972;
    /** 503 连接器不可用（无可用连接器） */
    public static final int CODE_CONNECTOR_UNAVAILABLE = 50301;

    /** 任务队列类型 */
    public static final String TASK_TYPE = "PUBLISH_TASK";

    private final PublishTaskMapper publishTaskMapper;
    private final PublishScheduleMapper scheduleMapper;
    private final PublishReceiptMapper receiptMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final PlatformAccountMapper platformAccountMapper;
    private final ConnectorRegistry connectorRegistry;
    private final ConnectorAuthorizationService authorizationService;
    private final ConnectorHealthService connectorHealthService;
    private final TaskQueueProducer taskQueueProducer;
    private final ObjectMapper objectMapper;
    private final AuditWriter auditWriter;

    // ══════════════════════════════════════════════════════════════════
    // 1. 创建发布任务
    // ══════════════════════════════════════════════════════════════════

    /**
     * 创建发布任务（必须带幂等键）。
     * <p>
     * 流程：幂等键校验 → 资产归属 → 账号归属 → 重复发布检测 → 连接器解析 → 落库 → 入队。
     * </p>
     */
    @Transactional
    public PublishTaskResponse create(String workspaceId, Long userId,
                                       PublishTaskCreateRequest request, String idempotencyKey) {
        requireWorkspace(workspaceId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(CODE_IDEMPOTENCY_KEY_REQUIRED,
                    "IDEMPOTENCY_KEY_REQUIRED: Idempotency-Key header is mandatory for publish tasks");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 128) {
            throw new BusinessException(CODE_BAD_REQUEST, "idempotency_key_too_long");
        }

        // 1) 幂等：同 key 已存在 → 直接返回既有任务（不产生第二个任务）
        PublishTask existing = publishTaskMapper.selectByIdempotencyKey(key);
        if (existing != null) {
            if (!workspaceId.equals(existing.getWorkspaceId())) {
                // 跨工作区复用同一 key：按"未找到"处理，不泄露存在性
                throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
            }
            log.info("Publish task idempotent hit: key={} taskId={}", key, existing.getId());
            return toResponse(existing, null);
        }

        String platformCode = normalizePlatformCode(request.getPlatformCode());
        String accountId = blankToNull(request.getAccountId());

        // 2) 资产归属校验（越权按"未找到"处理）
        UUID assetId = parseUuid(request.getAssetId(), CODE_ASSET_NOT_FOUND, "asset_not_found");
        ContentAsset asset = contentAssetMapper.selectById(assetId);
        if (asset == null || !workspaceId.equals(asset.getWorkspaceId())) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "asset_not_found");
        }
        if (ContentAsset.STATUS_PUBLISHED.equals(asset.getStatus())) {
            throw new BusinessException(CODE_DUPLICATE_PUBLISH, "DUPLICATE_PUBLISH: asset_already_published");
        }

        // 3) 账号归属校验
        PlatformAccount account = null;
        if (accountId != null) {
            account = findAccount(workspaceId, platformCode, accountId);
        }

        // 4) 重复发布检测：同一资产 + 平台 + 账号已有活跃任务
        PublishTask active = findActiveTask(workspaceId, assetId, platformCode, accountId);
        if (active != null) {
            throw new BusinessException(CODE_DUPLICATE_PUBLISH,
                    "DUPLICATE_PUBLISH: activeTaskId=" + active.getId());
        }

        // 5) 关联排期（可选）
        PublishSchedule schedule = null;
        if (blankToNull(request.getScheduleId()) != null) {
            UUID scheduleId = parseUuid(request.getScheduleId(), CODE_TASK_NOT_FOUND, "publish_schedule_not_found");
            schedule = scheduleMapper.selectByIdAndWorkspace(scheduleId, workspaceId);
            if (schedule == null) {
                throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_schedule_not_found");
            }
        }

        // 6) 连接器解析（未授权 → 403；无可用连接器 → 503 + degradedTo）
        ResolvedConnector resolved = resolveConnector(workspaceId, platformCode, account);

        // 7) 落库
        OffsetDateTime now = OffsetDateTime.now();
        PublishTask task = new PublishTask();
        task.setId(UUID.randomUUID());
        task.setWorkspaceId(workspaceId);
        task.setScheduleId(schedule == null ? null : schedule.getId());
        task.setAssetId(assetId);
        task.setPlatformCode(platformCode);
        task.setAccountId(accountId);
        task.setConnectorProviderCode(resolved.providerCode());
        task.setLevel(resolved.level().name());
        task.setIdempotencyKey(key);
        task.setStatus(PublishTask.STATUS_PENDING);
        task.setAttempt(0);
        task.setRetryable(Boolean.FALSE);
        task.setScheduledAt(request.getScheduledAt() == null
                ? null : request.getScheduledAt().withOffsetSameInstant(ZoneOffset.UTC));
        task.setCreatedAt(now);
        try {
            publishTaskMapper.insert(task);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 并发同 key：DB 唯一索引兜底，返回既有任务
            PublishTask winner = publishTaskMapper.selectByIdempotencyKey(key);
            if (winner != null && workspaceId.equals(winner.getWorkspaceId())) {
                log.info("Publish task duplicate key caught: key={} taskId={}", key, winner.getId());
                return toResponse(winner, null);
            }
            throw new BusinessException(CODE_DUPLICATE_PUBLISH, "DUPLICATE_PUBLISH: idempotency_key_conflict");
        }

        // 8) 排期关联（排期进入 executing）
        if (schedule != null) {
            scheduleMapper.update(null, new LambdaUpdateWrapper<PublishSchedule>()
                    .eq(PublishSchedule::getId, schedule.getId())
                    .eq(PublishSchedule::getWorkspaceId, workspaceId)
                    .set(PublishSchedule::getPublishTaskId, task.getId())
                    .set(PublishSchedule::getStatus, PublishSchedule.STATUS_EXECUTING)
                    .set(PublishSchedule::getUpdatedAt, now));
        }

        // 9) 入队（事务提交后投递）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId().toString());
        payload.put("workspaceId", workspaceId);
        payload.put("assetId", assetId.toString());
        payload.put("platformCode", platformCode);
        payload.put("accountId", accountId);
        payload.put("providerCode", resolved.providerCode());
        payload.put("level", resolved.level().name());
        payload.put("scheduleId", schedule == null ? null : schedule.getId().toString());
        payload.put("userId", userId);
        taskQueueProducer.enqueue(TASK_TYPE, task.getId().toString(), writeJson(payload), 0);

        auditWriter.record("publish_task.create", "publish_task", String.valueOf(task.getId()),
                auditMeta(task, resolved));

        log.info("Publish task queued: taskId={} ws={} asset={} platform={} account={} provider={} level={}",
                task.getId(), workspaceId, assetId, platformCode, accountId,
                resolved.providerCode(), resolved.level());

        return toResponse(task, null);
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 查询
    // ══════════════════════════════════════════════════════════════════

    /**
     * 查询发布任务详情（含回执）。
     */
    public PublishTaskResponse get(String workspaceId, UUID taskId) {
        requireWorkspace(workspaceId);
        PublishTask task = requireOwned(workspaceId, taskId);
        PublishReceiptResponse receipt = receiptMapper.selectByTaskId(task.getId()) == null
                ? null
                : toReceiptResponse(receiptMapper.selectByTaskId(task.getId()));
        return toResponse(task, receipt);
    }

    /**
     * 列出工作区内的发布任务（可按资产 / 状态过滤）。
     */
    public List<PublishTaskResponse> list(String workspaceId, String assetId, String status) {
        requireWorkspace(workspaceId);
        List<PublishTask> tasks = publishTaskMapper.selectList(new LambdaQueryWrapper<PublishTask>()
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .eq(blankToNull(assetId) == null, PublishTask::getAssetId,
                        blankToNull(assetId) == null ? null : parseUuid(assetId, CODE_ASSET_NOT_FOUND, "asset_not_found"))
                .eq(blankToNull(status) == null, PublishTask::getStatus,
                        blankToNull(status) == null ? null : status.trim().toLowerCase(Locale.ROOT))
                .orderByDesc(PublishTask::getCreatedAt));
        List<PublishTaskResponse> result = new java.util.ArrayList<>(tasks.size());
        for (PublishTask task : tasks) {
            result.add(toResponse(task, null));
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 取消
    // ══════════════════════════════════════════════════════════════════

    /**
     * 取消发布任务。
     * <p>
     * 仅 {@code pending} / {@code manual_required} / {@code failed} 可取消；
     * {@code running} 任务不阻塞取消（worker 侧会检查终态并放弃执行）。
     * </p>
     */
    @Transactional
    public PublishTaskResponse cancel(String workspaceId, Long userId, UUID taskId) {
        requireWorkspace(workspaceId);
        PublishTask task = requireOwned(workspaceId, taskId);

        if (!task.isCancellable()) {
            throw new BusinessException(CODE_TASK_NOT_CANCELLABLE,
                    "TASK_NOT_CANCELLABLE: status=" + task.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        publishTaskMapper.update(null, new LambdaUpdateWrapper<PublishTask>()
                .eq(PublishTask::getId, task.getId())
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .set(PublishTask::getStatus, PublishTask.STATUS_CANCELLED)
                .set(PublishTask::getFinishedAt, now));

        task.setStatus(PublishTask.STATUS_CANCELLED);
        task.setFinishedAt(now);

        auditWriter.record("publish_task.cancel", "publish_task", String.valueOf(task.getId()),
                Map.of("status", PublishTask.STATUS_CANCELLED,
                        "platformCode", String.valueOf(task.getPlatformCode()),
                        "accountId", String.valueOf(task.getAccountId())));

        return toResponse(task, null);
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. 内部工具（供 Handler 复用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 所有权校验：不属于当前工作区的一律按"未找到"处理。
     */
    public PublishTask requireOwned(String workspaceId, UUID taskId) {
        requireWorkspace(workspaceId);
        if (taskId == null) {
            throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
        }
        PublishTask task = publishTaskMapper.selectByIdAndWorkspace(taskId, workspaceId);
        if (task == null) {
            throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
        }
        return task;
    }

    /**
     * 更新任务状态（worker 调用）。
     */
    @Transactional
    public void updateStatus(String workspaceId, UUID taskId, String status,
                             Integer errorCode, String errorMessage, Boolean retryable) {
        publishTaskMapper.update(null, new LambdaUpdateWrapper<PublishTask>()
                .eq(PublishTask::getId, taskId)
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .set(PublishTask::getStatus, status)
                .set(PublishTask::getErrorCode, errorCode)
                .set(PublishTask::getErrorMessage, errorMessage)
                .set(PublishTask::getRetryable, retryable != null && retryable)
                .set(PublishTask::getFinishedAt,
                        PublishTask.STATUS_SUCCEEDED.equals(status)
                                || PublishTask.STATUS_FAILED.equals(status)
                                || PublishTask.STATUS_CANCELLED.equals(status)
                                || PublishTask.STATUS_MANUAL_REQUIRED.equals(status)
                                ? OffsetDateTime.now() : null));
    }

    /**
     * 增加尝试次数并置为 running（worker 调用）。
     */
    @Transactional
    public void markRunning(String workspaceId, UUID taskId, int attempt) {
        publishTaskMapper.update(null, new LambdaUpdateWrapper<PublishTask>()
                .eq(PublishTask::getId, taskId)
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .set(PublishTask::getStatus, PublishTask.STATUS_RUNNING)
                .set(PublishTask::getAttempt, attempt)
                .set(PublishTask::getStartedAt, OffsetDateTime.now()));
    }

    /**
     * 把内容资产推进到 {@code published}（发布成功 / 回执回填时调用）。
     * <p>
     * 走 {@link ContentStatus#canTransition}：{@code APPROVED → SCHEDULED → PUBLISHED}。
     * </p>
     */
    @Transactional
    public void markAssetPublished(String workspaceId, UUID assetId) {
        if (assetId == null) {
            return;
        }
        ContentAsset asset = contentAssetMapper.selectById(assetId);
        if (asset == null || !workspaceId.equals(asset.getWorkspaceId())) {
            return;
        }
        ContentStatus current = ContentStatus.tryParse(asset.getStatus()).orElse(null);
        if (current == null || current.isTerminal()) {
            return;
        }
        if (current == ContentStatus.APPROVED) {
            // 绕过排期直接发布：先补 SCHEDULED 这一步，保证状态机合法
            if (ContentStatus.canTransition(current, ContentStatus.SCHEDULED)) {
                asset.setStatus(ContentStatus.SCHEDULED.name().toLowerCase(Locale.ROOT));
                current = ContentStatus.SCHEDULED;
            }
        }
        if (!ContentStatus.canTransition(current, ContentStatus.PUBLISHED)) {
            log.warn("Skip asset publish transition: asset={} status={}", assetId, asset.getStatus());
            return;
        }
        asset.setStatus(ContentStatus.PUBLISHED.name().toLowerCase(Locale.ROOT));
        asset.setUpdatedAt(OffsetDateTime.now());
        contentAssetMapper.updateById(asset);
    }

    // ══════════════════════════════════════════════════════════════════
    // 私有辅助
    // ══════════════════════════════════════════════════════════════════

    /**
     * 连接器解析结果。
     *
     * @param providerCode 连接器实现代码
     * @param level        连接等级
     */
    public record ResolvedConnector(String providerCode, PlatformConnectorLevel level) {
    }

    /**
     * 解析可用连接器。
     * <ul>
     *   <li>账号显式指定 {@code connectorProviderCode} 但未授权 → 403 CONNECTOR_NOT_AUTHORIZED</li>
     *   <li>注册表中没有该平台的连接器 → 503 CONNECTOR_UNAVAILABLE + degradedTo</li>
     * </ul>
     */
    private ResolvedConnector resolveConnector(String workspaceId, String platformCode, PlatformAccount account) {
        String configured = account == null ? null : blankToNull(account.getConnectorProviderCode());
        if (configured != null) {
            if (!authorizationService.isAuthorized(platformCode, configured)) {
                connectorHealthService.recordNotAuthorized(configured, platformCode, workspaceId);
                throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED,
                        ConnectorAuthorizationService.ERR_NOT_AUTHORIZED + ": provider=" + configured);
            }
            Optional<PlatformConnector> found = connectorRegistry.find(configured);
            if (found.isEmpty()) {
                throw new BusinessException(CODE_CONNECTOR_UNAVAILABLE,
                        "CONNECTOR_UNAVAILABLE: provider_not_registered=" + configured);
            }
            PlatformConnector connector = found.get();
            return new ResolvedConnector(connector.providerCode(), connector.level());
        }

        List<PlatformConnector> candidates = connectorRegistry.listByPlatform(platformCode);
        if (candidates.isEmpty()) {
            throw new BusinessException(CODE_CONNECTOR_UNAVAILABLE,
                    "CONNECTOR_UNAVAILABLE: degradedTo=" + defaultDegradedTo(platformCode));
        }
        PlatformConnector connector = candidates.get(0);
        return new ResolvedConnector(connector.providerCode(), connector.level());
    }

    private PublishTask findActiveTask(String workspaceId, UUID assetId, String platformCode, String accountId) {
        return publishTaskMapper.selectOne(new LambdaQueryWrapper<PublishTask>()
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .eq(PublishTask::getAssetId, assetId)
                .eq(PublishTask::getPlatformCode, platformCode)
                .in(PublishTask::getStatus, PublishTask.STATUS_PENDING, PublishTask.STATUS_RUNNING,
                        PublishTask.STATUS_MANUAL_REQUIRED)
                .last("LIMIT 1"));
    }

    private PlatformAccount findAccount(String workspaceId, String platformCode, String accountId) {
        List<PlatformAccount> accounts = platformAccountMapper.selectByWorkspaceAndPlatform(workspaceId, platformCode);
        for (PlatformAccount account : accounts) {
            String id = account.getId() == null ? null : account.getId().toString();
            if (accountId.equals(id) || accountId.equals(account.getExternalAccountId())) {
                return account;
            }
        }
        throw new BusinessException(CODE_ACCOUNT_NOT_FOUND, "account_not_found");
    }

    private Map<String, Object> auditMeta(PublishTask task, ResolvedConnector resolved) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("platformCode", task.getPlatformCode());
        meta.put("accountId", task.getAccountId());
        meta.put("assetId", String.valueOf(task.getAssetId()));
        meta.put("providerCode", resolved.providerCode());
        meta.put("level", resolved.level().name());
        meta.put("idempotencyKey", task.getIdempotencyKey());
        meta.put("scheduleId", task.getScheduleId() == null ? null : String.valueOf(task.getScheduleId()));
        return meta;
    }

    /**
     * 实体 → 响应。
     */
    private PublishTaskResponse toResponse(PublishTask task, PublishReceiptResponse receipt) {
        PlatformConnectorLevel level = parseLevel(task.getLevel());
        boolean manual = level == PlatformConnectorLevel.BROWSER_ASSIST
                || level == PlatformConnectorLevel.MANUAL_IMPORT;
        return PublishTaskResponse.builder()
                .id(String.valueOf(task.getId()))
                .status(task.getStatus())
                .scheduleId(task.getScheduleId() == null ? null : String.valueOf(task.getScheduleId()))
                .assetId(task.getAssetId() == null ? null : String.valueOf(task.getAssetId()))
                .platformCode(task.getPlatformCode())
                .accountId(task.getAccountId())
                .connectorProviderCode(task.getConnectorProviderCode())
                .level(task.getLevel())
                .idempotencyKey(task.getIdempotencyKey())
                .attempt(task.getAttempt())
                .errorCode(task.getErrorCode())
                .errorMessage(task.getErrorMessage())
                .retryable(task.getRetryable())
                .scheduledAt(task.getScheduledAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .receipt(receipt)
                .degradedTo(manual ? "MANUAL_IMPORT" : null)
                .manualNotice(manual
                        ? "用户自负发布风险：系统不代为点击发布，请在平台页面确认后回填外部内容 ID / 来源链接"
                        : null)
                .build();
    }

    /**
     * 回执实体 → 响应（供本服务复用）。
     */
    private PublishReceiptResponse toReceiptResponse(
            com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishReceipt receipt) {
        return PublishReceiptResponse.builder()
                .id(String.valueOf(receipt.getId()))
                .publishTaskId(String.valueOf(receipt.getPublishTaskId()))
                .assetId(receipt.getAssetId() == null ? null : String.valueOf(receipt.getAssetId()))
                .externalContentId(receipt.getExternalContentId())
                .sourceUrl(receipt.getSourceUrl())
                .publishedAt(receipt.getPublishedAt())
                .publishedAtSource(receipt.getPublishedAtSource())
                .publishedBy(receipt.getPublishedBy())
                .verified(receipt.getVerified())
                .createdAt(receipt.getCreatedAt())
                .build();
    }

    /**
     * 默认降级建议：连接器不可用时的兜底路径。
     * <p>
     * 逐级降级：{@code BROWSER_ASSIST} → {@code MANUAL_IMPORT} → {@code EXPORT_ONLY}；
     * 本方法返回最保守可用的 {@code MANUAL_IMPORT}（永远可用）。
     * </p>
     */
    private String defaultDegradedTo(String platformCode) {
        return "MANUAL_IMPORT";
    }

    /**
     * 解析连接等级（非法值返回 null，不抛异常 —— 等级只是展示用的元数据）。
     */
    private PlatformConnectorLevel parseLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PlatformConnectorLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> payload) {        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException(CODE_BAD_REQUEST, "cannot_serialize_publish_task_payload");
        }
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
    }

    private UUID parseUuid(String raw, int code, String message) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(code, message);
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(code, message);
        }
    }

    private String normalizePlatformCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_platform_code");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
