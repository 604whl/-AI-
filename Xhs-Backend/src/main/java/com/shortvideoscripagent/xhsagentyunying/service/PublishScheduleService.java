package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformAccount;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformAccountMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishScheduleMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishCalendarResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishScheduleCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishScheduleResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishScheduleRetryResponse;
import com.shortvideoscripagent.xhsagentyunying.freeze.ContentStatus;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * S3-3 排期日历服务。
 * <p>
 * 核心约束：
 * <ul>
 *   <li>排期必须建立在<b>已审核通过</b>（{@code content_asset.status='approved'}）的资产上，
 *       否则 409 {@code ASSET_NOT_APPROVED}</li>
 *   <li>状态机走 {@link ContentStatus#canTransition}：仅 {@code APPROVED → SCHEDULED} 合法；
 *       取消时回退 {@code SCHEDULED → APPROVED}</li>
 *   <li>冲突检测：同一工作区 + 同一平台 + 同一账号 + 同一时刻（精确到分钟）已有非终态排期
 *       → 409 {@code SCHEDULE_CONFLICT}</li>
 *   <li>时区：{@code scheduled_at} 一律 UTC 落库，{@code timezone} 保存 IANA 时区串（如 Asia/Shanghai）</li>
 *   <li>工作区隔离：越权访问一律按"未找到"处理（不泄露存在性）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishScheduleService {

    // ── 错误码 ────────────────────────────────────────────────────────────
    /** 400 参数非法 */
    public static final int CODE_BAD_REQUEST = 40070;
    /** 403 连接器未授权 */
    public static final int CODE_CONNECTOR_NOT_AUTHORIZED = 40302;
    /** 404 排期不存在 / 越权 */
    public static final int CODE_SCHEDULE_NOT_FOUND = 40460;
    /** 404 资产不存在 / 越权 */
    public static final int CODE_ASSET_NOT_FOUND = 40450;
    /** 404 平台账号不存在 / 越权 */
    public static final int CODE_ACCOUNT_NOT_FOUND = 40461;
    /** 409 资产未审核通过 */
    public static final int CODE_ASSET_NOT_APPROVED = 40960;
    /** 409 排期时间冲突 */
    public static final int CODE_SCHEDULE_CONFLICT = 40961;
    /** 409 排期不可取消 */
    public static final int CODE_SCHEDULE_NOT_CANCELLABLE = 40962;
    /** 409 排期不可重试（状态不是 failed） */
    public static final int CODE_SCHEDULE_NOT_FAILED = 40963;

    private final PublishScheduleMapper scheduleMapper;
    private final PublishTaskMapper publishTaskMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final PlatformAccountMapper platformAccountMapper;
    private final AuditWriter auditWriter;

    // ══════════════════════════════════════════════════════════════════
    // 1. 创建排期
    // ══════════════════════════════════════════════════════════════════

    /**
     * 创建排期。
     * <p>
     * 顺序：资产归属 → 资产已审核 → 账号归属 → 时间合法 → 冲突检测 → 落库 → 资产状态推进 → 审计。
     * </p>
     */
    @Transactional
    public PublishScheduleResponse create(String workspaceId, Long userId,
                                          PublishScheduleCreateRequest request) {
        requireWorkspace(workspaceId);
        String platformCode = normalizePlatformCode(request.getPlatformCode());
        String accountId = blankToNull(request.getAccountId());
        String timezone = normalizeTimezone(request.getTimezone());

        // 1) 资产归属校验（越权按"未找到"处理）
        UUID assetId = parseUuid(request.getAssetId(), CODE_ASSET_NOT_FOUND, "asset_not_found");
        ContentAsset asset = contentAssetMapper.selectById(assetId);
        if (asset == null || !workspaceId.equals(asset.getWorkspaceId())) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "asset_not_found");
        }

        // 2) 资产必须已审核通过（draft / in_review 一律拒绝）
        if (!ContentAsset.STATUS_APPROVED.equals(asset.getStatus())) {
            throw new BusinessException(CODE_ASSET_NOT_APPROVED,
                    "ASSET_NOT_APPROVED: asset_status=" + asset.getStatus());
        }

        // 3) 账号归属校验（账号必须属于当前 workspace）
        if (accountId != null) {
            requireAccount(workspaceId, platformCode, accountId);
        }

        // 4) 排期时间必须是未来
        OffsetDateTime scheduledAt = request.getScheduledAt();
        if (scheduledAt == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "scheduled_at_required");
        }
        OffsetDateTime utc = scheduledAt.withOffsetSameInstant(ZoneOffset.UTC);
        if (!utc.isAfter(OffsetDateTime.now())) {
            throw new BusinessException(CODE_BAD_REQUEST, "scheduled_at_must_be_in_future");
        }

        // 5) 冲突检测：同 workspace + 同平台 + 同账号 + 同一时刻已有非终态排期
        PublishSchedule conflict = scheduleMapper.selectConflicting(
                workspaceId, platformCode, accountId, utc, null);
        if (conflict != null) {
            throw new BusinessException(CODE_SCHEDULE_CONFLICT,
                    "SCHEDULE_CONFLICT: conflictScheduleId=" + conflict.getId());
        }

        // 6) 落库
        OffsetDateTime now = OffsetDateTime.now();
        PublishSchedule schedule = new PublishSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setWorkspaceId(workspaceId);
        schedule.setAssetId(assetId);
        schedule.setPlatformCode(platformCode);
        schedule.setAccountId(accountId);
        schedule.setScheduledAt(utc);
        schedule.setTimezone(timezone);
        schedule.setStatus(PublishSchedule.STATUS_SCHEDULED);
        schedule.setCreatedBy(userId);
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        scheduleMapper.insert(schedule);

        // 7) 资产状态推进：APPROVED → SCHEDULED（走状态机）
        advanceAssetStatus(asset, ContentStatus.APPROVED, ContentStatus.SCHEDULED);

        auditWriter.record("publish_schedule.create", "publish_schedule",
                String.valueOf(schedule.getId()), auditMeta(schedule, asset));

        log.info("Publish schedule created: id={} ws={} asset={} platform={} account={} at={} tz={}",
                schedule.getId(), workspaceId, assetId, platformCode, accountId, utc, timezone);

        return toResponse(schedule, asset);
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 查询
    // ══════════════════════════════════════════════════════════════════

    /**
     * 列出工作区内的排期（可按平台 / 状态 / 时间范围过滤）。
     */
    public List<PublishScheduleResponse> list(String workspaceId, String platformCode, String status,
                                               OffsetDateTime from, OffsetDateTime to) {
        requireWorkspace(workspaceId);
        List<PublishSchedule> schedules = scheduleMapper.selectListFiltered(
                workspaceId,
                blankToNull(platformCode) == null ? null : normalizePlatformCode(platformCode),
                blankToNull(status) == null ? null : status.trim().toLowerCase(Locale.ROOT),
                from == null ? null : from.withOffsetSameInstant(ZoneOffset.UTC),
                to == null ? null : to.withOffsetSameInstant(ZoneOffset.UTC));
        Map<UUID, ContentAsset> assets = loadAssets(schedules);
        List<PublishScheduleResponse> result = new ArrayList<>(schedules.size());
        for (PublishSchedule schedule : schedules) {
            result.add(toResponse(schedule, assets.get(schedule.getAssetId())));
        }
        return result;
    }

    /**
     * 查询排期详情。
     */
    public PublishScheduleResponse get(String workspaceId, UUID scheduleId) {
        requireWorkspace(workspaceId);
        PublishSchedule schedule = requireOwned(workspaceId, scheduleId);
        ContentAsset asset = contentAssetMapper.selectById(schedule.getAssetId());
        return toResponse(schedule, asset);
    }

    /**
     * 日历视图：按日期分组返回区间内的排期。
     * <p>
     * 日期按<b>每条排期自己的时区</b>归一（跨时区排期能正确落在对应自然日）。
     */
    public PublishCalendarResponse calendar(String workspaceId, OffsetDateTime from, OffsetDateTime to) {
        requireWorkspace(workspaceId);
        OffsetDateTime utcFrom = from == null
                ? OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                : from.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime utcTo = to == null
                ? OffsetDateTime.now(ZoneOffset.UTC).plusDays(90)
                : to.withOffsetSameInstant(ZoneOffset.UTC);

        List<PublishSchedule> schedules = scheduleMapper.selectListFiltered(
                workspaceId, null, null, utcFrom, utcTo);
        Map<UUID, ContentAsset> assets = loadAssets(schedules);

        Map<String, List<PublishCalendarResponse.PublishCalendarSlot>> byDate = new LinkedHashMap<>();
        for (PublishSchedule schedule : schedules) {
            ZoneId zone = zoneOf(schedule.getTimezone());
            ContentAsset asset = assets.get(schedule.getAssetId());
            PublishCalendarResponse.PublishCalendarSlot slot =
                    PublishCalendarResponse.PublishCalendarSlot.builder()
                            .scheduleId(String.valueOf(schedule.getId()))
                            .assetId(String.valueOf(schedule.getAssetId()))
                            .title(asset == null ? null : asset.getTitle())
                            .platformCode(schedule.getPlatformCode())
                            .accountId(schedule.getAccountId())
                            .status(schedule.getStatus())
                            .localTime(schedule.getScheduledAt().atZoneSameInstant(zone)
                                    .toLocalDateTime().toString())
                            .build();
            byDate.computeIfAbsent(
                    schedule.getScheduledAt().atZoneSameInstant(zone).toLocalDate().toString(),
                    k -> new ArrayList<>()).add(slot);
        }

        List<PublishCalendarResponse.PublishCalendarDay> days = new ArrayList<>();
        for (Map.Entry<String, List<PublishCalendarResponse.PublishCalendarSlot>> entry : byDate.entrySet()) {
            days.add(PublishCalendarResponse.PublishCalendarDay.builder()
                    .date(entry.getKey())
                    .slots(entry.getValue())
                    .build());
        }
        return PublishCalendarResponse.builder().days(days).build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 取消
    // ══════════════════════════════════════════════════════════════════

    /**
     * 取消排期：{@code scheduled → cancelled}，并把资产状态回退 {@code SCHEDULED → APPROVED}。
     */
    @Transactional
    public PublishScheduleResponse cancel(String workspaceId, Long userId, UUID scheduleId) {
        requireWorkspace(workspaceId);
        PublishSchedule schedule = requireOwned(workspaceId, scheduleId);

        if (!PublishSchedule.STATUS_SCHEDULED.equals(schedule.getStatus())) {
            throw new BusinessException(CODE_SCHEDULE_NOT_CANCELLABLE,
                    "SCHEDULE_NOT_CANCELLABLE: status=" + schedule.getStatus());
        }

        cancelLinkedTask(workspaceId, schedule);

        OffsetDateTime now = OffsetDateTime.now();
        scheduleMapper.update(null, new LambdaUpdateWrapper<PublishSchedule>()
                .eq(PublishSchedule::getId, schedule.getId())
                .eq(PublishSchedule::getWorkspaceId, workspaceId)
                .eq(PublishSchedule::getStatus, PublishSchedule.STATUS_SCHEDULED)
                .set(PublishSchedule::getStatus, PublishSchedule.STATUS_CANCELLED)
                .set(PublishSchedule::getUpdatedAt, now));

        schedule.setStatus(PublishSchedule.STATUS_CANCELLED);
        schedule.setUpdatedAt(now);

        ContentAsset asset = contentAssetMapper.selectById(schedule.getAssetId());
        if (asset != null && ContentAsset.STATUS_SCHEDULED.equals(asset.getStatus())) {
            advanceAssetStatus(asset, ContentStatus.SCHEDULED, ContentStatus.APPROVED);
        }

        auditWriter.record("publish_schedule.cancel", "publish_schedule",
                String.valueOf(schedule.getId()), auditMeta(schedule, asset));

        return toResponse(schedule, asset);
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. 重试（复用原幂等键）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 重试失败排期：<b>复用原发布任务的幂等键</b>，不产生新的外部发布。
     */
    @Transactional
    public PublishScheduleRetryResponse retry(String workspaceId, Long userId, UUID scheduleId) {
        requireWorkspace(workspaceId);
        PublishSchedule schedule = requireOwned(workspaceId, scheduleId);

        if (!PublishSchedule.STATUS_FAILED.equals(schedule.getStatus())) {
            throw new BusinessException(CODE_SCHEDULE_NOT_FAILED,
                    "SCHEDULE_NOT_FAILED: status=" + schedule.getStatus());
        }

        PublishTask original = publishTaskMapper.selectLatestBySchedule(workspaceId, scheduleId);
        if (original == null) {
            throw new BusinessException(CODE_SCHEDULE_NOT_FAILED,
                    "SCHEDULE_NOT_FAILED: no_publish_task_to_retry");
        }

        OffsetDateTime now = OffsetDateTime.now();
        publishTaskMapper.update(null, new LambdaUpdateWrapper<PublishTask>()
                .eq(PublishTask::getId, original.getId())
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .set(PublishTask::getStatus, PublishTask.STATUS_PENDING)
                .set(PublishTask::getErrorCode, null)
                .set(PublishTask::getErrorMessage, null)
                .set(PublishTask::getRetryable, Boolean.TRUE)
                .set(PublishTask::getFinishedAt, null));

        original.setStatus(PublishTask.STATUS_PENDING);

        scheduleMapper.update(null, new LambdaUpdateWrapper<PublishSchedule>()
                .eq(PublishSchedule::getId, schedule.getId())
                .eq(PublishSchedule::getWorkspaceId, workspaceId)
                .eq(PublishSchedule::getStatus, PublishSchedule.STATUS_FAILED)
                .set(PublishSchedule::getStatus, PublishSchedule.STATUS_SCHEDULED)
                .set(PublishSchedule::getErrorCode, null)
                .set(PublishSchedule::getErrorMessage, null)
                .set(PublishSchedule::getUpdatedAt, now));

        schedule.setStatus(PublishSchedule.STATUS_SCHEDULED);
        schedule.setErrorCode(null);
        schedule.setErrorMessage(null);
        schedule.setUpdatedAt(now);

        auditWriter.record("publish_schedule.retry", "publish_schedule",
                String.valueOf(schedule.getId()),
                Map.of("publishTaskId", String.valueOf(original.getId()),
                        "idempotencyKey", String.valueOf(original.getIdempotencyKey())));

        return PublishScheduleRetryResponse.builder()
                .id(String.valueOf(schedule.getId()))
                .status(schedule.getStatus())
                .publishTaskId(String.valueOf(original.getId()))
                .taskStatus(original.getStatus())
                .idempotencyKey(original.getIdempotencyKey())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. 过期处理（排期时间已过但未被执行 → expired，不静默丢弃）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把过期未执行的排期标记为 {@code expired}。
     *
     * @return 标记数量
     */
    @Transactional
    public int markOverdueExpired(String workspaceId) {
        requireWorkspace(workspaceId);
        List<PublishSchedule> overdue = scheduleMapper.selectOverdueScheduled(
                workspaceId, OffsetDateTime.now());
        if (overdue.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (PublishSchedule schedule : overdue) {
            scheduleMapper.update(null, new LambdaUpdateWrapper<PublishSchedule>()
                    .eq(PublishSchedule::getId, schedule.getId())
                    .eq(PublishSchedule::getWorkspaceId, workspaceId)
                    .eq(PublishSchedule::getStatus, PublishSchedule.STATUS_SCHEDULED)
                    .set(PublishSchedule::getStatus, PublishSchedule.STATUS_EXPIRED)
                    .set(PublishSchedule::getErrorCode, CODE_BAD_REQUEST)
                    .set(PublishSchedule::getErrorMessage, "SCHEDULE_EXPIRED: scheduled_time_passed")
                    .set(PublishSchedule::getUpdatedAt, now));
        }
        log.info("Marked {} overdue publish schedules as expired for ws={}", overdue.size(), workspaceId);
        return overdue.size();
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具（供 PublishTaskService / PublishTaskHandler 复用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 所有权校验：不属于当前工作区的一律按"未找到"处理。
     */
    public PublishSchedule requireOwned(String workspaceId, UUID scheduleId) {
        requireWorkspace(workspaceId);
        if (scheduleId == null) {
            throw new BusinessException(CODE_SCHEDULE_NOT_FOUND, "publish_schedule_not_found");
        }
        PublishSchedule schedule = scheduleMapper.selectByIdAndWorkspace(scheduleId, workspaceId);
        if (schedule == null) {
            throw new BusinessException(CODE_SCHEDULE_NOT_FOUND, "publish_schedule_not_found");
        }
        return schedule;
    }

    /**
     * 把排期状态推进到指定值（发布任务侧回调）。
     */
    @Transactional
    public void markStatus(String workspaceId, UUID scheduleId, String status,
                           Integer errorCode, String errorMessage) {
        if (scheduleId == null) {
            return;
        }
        scheduleMapper.update(null, new LambdaUpdateWrapper<PublishSchedule>()
                .eq(PublishSchedule::getId, scheduleId)
                .eq(PublishSchedule::getWorkspaceId, workspaceId)
                .set(PublishSchedule::getStatus, status)
                .set(PublishSchedule::getErrorCode, errorCode)
                .set(PublishSchedule::getErrorMessage, errorMessage)
                .set(PublishSchedule::getUpdatedAt, OffsetDateTime.now()));
    }

    /**
     * 关联发布任务到排期（同时把排期推进到 executing）。
     */
    @Transactional
    public void linkTask(String workspaceId, UUID scheduleId, UUID taskId) {
        if (scheduleId == null) {
            return;
        }
        scheduleMapper.update(null, new LambdaUpdateWrapper<PublishSchedule>()
                .eq(PublishSchedule::getId, scheduleId)
                .eq(PublishSchedule::getWorkspaceId, workspaceId)
                .set(PublishSchedule::getPublishTaskId, taskId)
                .set(PublishSchedule::getStatus, PublishSchedule.STATUS_EXECUTING)
                .set(PublishSchedule::getUpdatedAt, OffsetDateTime.now()));
    }

    /**
     * 时区归一：校验 IANA 时区串合法性，默认 UTC。
     */
    public static String normalizeTimezone(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UTC";
        }
        String value = raw.trim();
        try {
            ZoneId.of(value);
        } catch (java.time.DateTimeException ex) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_timezone: " + value);
        }
        return value;
    }

    // ── 私有辅助 ──────────────────────────────────────────────────────────

    private void cancelLinkedTask(String workspaceId, PublishSchedule schedule) {
        if (schedule.getPublishTaskId() == null) {
            return;
        }
        publishTaskMapper.update(null, new LambdaUpdateWrapper<PublishTask>()
                .eq(PublishTask::getId, schedule.getPublishTaskId())
                .eq(PublishTask::getWorkspaceId, workspaceId)
                .in(PublishTask::getStatus, PublishTask.STATUS_PENDING, PublishTask.STATUS_MANUAL_REQUIRED)
                .set(PublishTask::getStatus, PublishTask.STATUS_CANCELLED)
                .set(PublishTask::getFinishedAt, OffsetDateTime.now()));
    }

    /**
     * 资产状态推进（走 {@link ContentStatus#canTransition} 校验）。
     */
    private void advanceAssetStatus(ContentAsset asset, ContentStatus from, ContentStatus to) {
        if (asset == null) {
            return;
        }
        if (!ContentStatus.canTransition(from, to)) {
            throw new BusinessException(CODE_ASSET_NOT_APPROVED,
                    "invalid_asset_status_transition: " + from + " -> " + to);
        }
        asset.setStatus(to.name().toLowerCase(Locale.ROOT));
        asset.setUpdatedAt(OffsetDateTime.now());
        contentAssetMapper.updateById(asset);
    }

    /**
     * 账号归属校验：账号必须属于当前工作区（并与平台一致）。
     */
    private void requireAccount(String workspaceId, String platformCode, String accountId) {
        List<PlatformAccount> accounts = platformAccountMapper.selectByWorkspaceAndPlatform(workspaceId, platformCode);
        for (PlatformAccount account : accounts) {
            String id = account.getId() == null ? null : account.getId().toString();
            if (accountId.equals(id) || accountId.equals(account.getExternalAccountId())) {
                return;
            }
        }
        throw new BusinessException(CODE_ACCOUNT_NOT_FOUND, "account_not_found");
    }

    private Map<UUID, ContentAsset> loadAssets(List<PublishSchedule> schedules) {
        Map<UUID, ContentAsset> assets = new LinkedHashMap<>();
        for (PublishSchedule schedule : schedules) {
            if (schedule.getAssetId() == null || assets.containsKey(schedule.getAssetId())) {
                continue;
            }
            assets.put(schedule.getAssetId(), contentAssetMapper.selectById(schedule.getAssetId()));
        }
        return assets;
    }

    private Map<String, Object> auditMeta(PublishSchedule schedule, ContentAsset asset) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("assetId", String.valueOf(schedule.getAssetId()));
        meta.put("platformCode", schedule.getPlatformCode());
        meta.put("accountId", schedule.getAccountId());
        meta.put("scheduledAt", schedule.getScheduledAt() == null ? null : schedule.getScheduledAt().toString());
        meta.put("timezone", schedule.getTimezone());
        meta.put("status", schedule.getStatus());
        meta.put("assetStatus", asset == null ? null : asset.getStatus());
        return meta;
    }

    /**
     * 实体 → 响应。
     */
    private PublishScheduleResponse toResponse(PublishSchedule schedule, ContentAsset asset) {
        ZoneId zone = zoneOf(schedule.getTimezone());
        return PublishScheduleResponse.builder()
                .id(String.valueOf(schedule.getId()))
                .assetId(String.valueOf(schedule.getAssetId()))
                .title(asset == null ? null : asset.getTitle())
                .assetStatus(asset == null ? null : asset.getStatus())
                .platformCode(schedule.getPlatformCode())
                .accountId(schedule.getAccountId())
                .scheduledAt(schedule.getScheduledAt())
                .scheduledAtLocal(schedule.getScheduledAt() == null
                        ? null : schedule.getScheduledAt().atZoneSameInstant(zone).toOffsetDateTime())
                .timezone(schedule.getTimezone())
                .status(schedule.getStatus())
                .publishTaskId(schedule.getPublishTaskId() == null
                        ? null : String.valueOf(schedule.getPublishTaskId()))
                .errorCode(schedule.getErrorCode())
                .errorMessage(schedule.getErrorMessage())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    private ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
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
