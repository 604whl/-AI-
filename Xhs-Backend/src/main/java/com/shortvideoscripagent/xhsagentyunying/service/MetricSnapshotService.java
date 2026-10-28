package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorAuthorizationService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorRegistry;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricKeys;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricSample;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricCollectTask;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricSnapshot;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricSyncState;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricCollectTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricSnapshotMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricSyncStateMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformAccountMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricCollectRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricCollectResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSnapshotCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSnapshotResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSnapshotReviseRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSyncStatusResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricTrendBucket;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformCode;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * S3-5 指标快照与数据回流服务。
 * <p>
 * 核心约束：
 * <ul>
 *   <li><b>统一口径</b>：{@code metrics} 固定 10 个核心键（{@link MetricKeys#CORE_KEYS}），缺失补 0，
 *       平台差异字段只进 {@code metric_extra}</li>
 *   <li><b>来源与可信度必存</b>：{@code metric_source} / {@code confidence} / {@code collected_at} /
 *       {@code is_manual} 全部落库，杜绝"伪装实时"</li>
 *   <li><b>修订链</b>：人工修订产生新快照（{@code revised_from_id}），<b>不原地覆盖</b>原快照</li>
 *   <li><b>归属校验</b>：{@code asset_id} 必须属于当前 workspace，且 platform/account 与资产一致，
 *       否则 40450 / 40952</li>
 *   <li><b>采集不计费</b>：本服务不调用 {@code AtomicQuotaGuard}，不写 {@code usage_ledger} DEBIT</li>
 *   <li><b>旧数据兼容</b>：{@code analysis_task.published_metrics} 只读映射为虚拟快照
 *       （LEGACY_JSONB / LOW），不写库、不改表</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricSnapshotService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 400 指标字段缺失 / 非法 */
    public static final int CODE_METRIC_FIELDS_MISSING = 40061;
    /** 400 CSV 表头非法（未知列） */
    public static final int CODE_CSV_SCHEMA_INVALID = 40062;
    /** 404 资产不存在 / 越权 */
    public static final int CODE_ASSET_NOT_FOUND = 40450;
    /** 404 快照不存在 */
    public static final int CODE_SNAPSHOT_NOT_FOUND = 40451;
    /** 409 平台 / 账号与资产不一致 */
    public static final int CODE_ACCOUNT_MISMATCH = 40952;
    /** 403 连接器未授权 */
    public static final int CODE_CONNECTOR_NOT_AUTHORIZED = 40302;

    /** 采集窗口长度（6 小时，与定时任务频率一致） */
    public static final long COLLECT_WINDOW_HOURS = 6L;

    /** 允许手工 / CSV 写入的 metric_source */
    private static final Set<String> WRITABLE_SOURCES = Set.of(
            MetricSnapshot.SOURCE_OFFICIAL_API,
            MetricSnapshot.SOURCE_AUTHORIZED_CONNECTOR,
            MetricSnapshot.SOURCE_BROWSER_ASSIST,
            MetricSnapshot.SOURCE_MANUAL_IMPORT,
            MetricSnapshot.SOURCE_CSV_IMPORT);

    /** 允许的 confidence */
    private static final Set<String> ALLOWED_CONFIDENCE = Set.of(
            MetricSnapshot.CONFIDENCE_HIGH,
            MetricSnapshot.CONFIDENCE_MEDIUM,
            MetricSnapshot.CONFIDENCE_LOW,
            MetricSnapshot.CONFIDENCE_UNVERIFIED);

    /**
     * CSV 表头白名单：标准字段 + 10 个核心指标键 + 平台扩展字段。
     * 未知列直接拒绝（40062 CSV_SCHEMA_INVALID）。
     */
    private static final Set<String> CSV_STANDARD_COLUMNS = Set.of(
            "asset_id", "account_id", "external_content_id", "published_at", "content_type", "collected_at");

    /** CSV 平台扩展字段白名单（这些列进 metric_extra） */
    private static final Set<String> CSV_EXTRA_COLUMNS = Set.of(
            "completion_rate", "danmaku", "avg_play_duration", "avg_watch_duration",
            "cover_ctr", "video_duration", "share_rate", "save_rate", "comment_rate",
            "like_rate", "note_url");

    private final MetricSnapshotMapper snapshotMapper;
    private final MetricCollectTaskMapper collectTaskMapper;
    private final MetricSyncStateMapper syncStateMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final PlatformAccountMapper platformAccountMapper;
    private final ConnectorRegistry connectorRegistry;
    private final ConnectorAuthorizationService connectorAuthorizationService;
    private final TaskQueueProducer taskQueueProducer;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    // ══════════════════════════════════════════════════════════════════
    // 1. 手动录入
    // ══════════════════════════════════════════════════════════════════

    /**
     * 手动录入指标快照。
     * <p>
     * {@code metric_source} 默认 {@code MANUAL_IMPORT}（调用方显式指定其它可写来源时按指定值落库）、
     * {@code confidence} 默认 {@code LOW}、{@code is_manual = TRUE}、
     * {@code collected_at} 默认 now()（用户可填平台实际统计时间）。
     * </p>
     */
    @Transactional
    public MetricSnapshotResponse createManual(String workspaceId, Long userId,
                                               MetricSnapshotCreateRequest request) {
        requireWorkspace(workspaceId);
        String platformCode = normalizePlatformCode(request.getPlatformCode());
        String accountId = blankToNull(request.getAccountId());
        String externalContentId = blankToNull(request.getExternalContentId());

        // 归属校验：资产必须属于当前 workspace，且 platform / account 与资产一致
        UUID assetId = resolveAndValidateAsset(workspaceId, request.getAssetId(), platformCode, accountId);

        String metricSource = normalizeSource(request.getMetricSource());
        String confidence = normalizeConfidence(request.getConfidence(), MetricSnapshot.CONFIDENCE_LOW);

        // 统一口径：核心 10 键缺失补 0；核心键之外的字段剔除进 metric_extra
        NormalizedMetrics normalized = normalizeMetrics(null, request.getMetrics(), request.getMetricExtra());

        OffsetDateTime collectedAt = request.getCollectedAt() != null
                ? request.getCollectedAt() : OffsetDateTime.now();

        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setWorkspaceId(workspaceId);
        snapshot.setPlatformCode(platformCode);
        snapshot.setAccountId(accountId);
        snapshot.setAssetId(assetId);
        snapshot.setExternalContentId(externalContentId);
        snapshot.setContentType(blankToNull(request.getContentType()));
        snapshot.setPublishedAt(request.getPublishedAt());
        snapshot.setMetrics(writeJson(normalized.core()));
        snapshot.setMetricExtra(writeJson(normalized.extra()));
        snapshot.setMetricSource(metricSource);
        snapshot.setConfidence(confidence);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setIsManual(Boolean.TRUE);
        snapshot.setRevisedFromId(null);
        snapshot.setRevisedBy(null);
        snapshot.setCollectionKey(collectionKey("MANUAL", platformCode, accountId, externalContentId,
                "t" + collectedAt.toInstant().toEpochMilli()));
        snapshot.setCreatedAt(OffsetDateTime.now());

        insertIgnoreDuplicate(snapshot);
        log.info("Metric snapshot created manually: id={} ws={} platform={} source={}",
                snapshot.getId(), workspaceId, platformCode, metricSource);
        return toResponse(snapshot, null, null);
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 修订（产生新快照，不覆盖原快照）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 人工修订：生成<b>新快照</b>，{@code revised_from_id} 指向原快照；原快照保持不变，可追溯。
     */
    @Transactional
    public MetricSnapshotResponse revise(String workspaceId, Long userId, UUID snapshotId,
                                         MetricSnapshotReviseRequest request) {
        requireWorkspace(workspaceId);
        MetricSnapshot original = requireSnapshot(workspaceId, snapshotId);

        String confidence = normalizeConfidence(request.getConfidence(), original.getConfidence());
        // 以原快照指标为基线合并（局部修订不会把其它指标清零），最终仍补齐 10 键
        Map<String, Long> base = readCoreMetrics(original.getMetrics());
        NormalizedMetrics normalized = normalizeMetrics(base, request.getMetrics(), request.getMetricExtra());

        MetricSnapshot revised = new MetricSnapshot();
        revised.setId(UUID.randomUUID());
        revised.setWorkspaceId(workspaceId);
        revised.setPlatformCode(original.getPlatformCode());
        revised.setAccountId(original.getAccountId());
        revised.setAssetId(original.getAssetId());
        revised.setExternalContentId(original.getExternalContentId());
        revised.setContentType(original.getContentType());
        revised.setPublishedAt(original.getPublishedAt());
        revised.setMetrics(writeJson(normalized.core()));
        revised.setMetricExtra(writeJson(normalized.extra()));
        revised.setMetricSource(original.getMetricSource());
        revised.setConfidence(confidence);
        revised.setCollectedAt(original.getCollectedAt());
        revised.setIsManual(Boolean.TRUE);
        revised.setRevisedFromId(original.getId());
        revised.setRevisedBy(userId);
        revised.setCollectionKey(collectionKey("REVISE", original.getPlatformCode(), original.getAccountId(),
                original.getExternalContentId(), original.getId() + "@" + System.currentTimeMillis()));
        revised.setCreatedAt(OffsetDateTime.now());

        insertIgnoreDuplicate(revised);
        log.info("Metric snapshot revised: newId={} revisedFrom={} user={}",
                revised.getId(), original.getId(), userId);

        MetricSnapshotResponse response = toResponse(revised, null, request.getReason());
        response.setRevisedFromId(original.getId());
        return response;
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 详情（带修订链）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 查询快照详情 + 其后续修订链（按 created_at 升序）。
     */
    public MetricSnapshotResponse getWithChain(String workspaceId, UUID snapshotId) {
        requireWorkspace(workspaceId);
        MetricSnapshot snapshot = requireSnapshot(workspaceId, snapshotId);
        List<MetricSnapshot> chain = snapshotMapper.selectRevisionChain(snapshotId, workspaceId);
        MetricSnapshotResponse response = toResponse(snapshot, null, null);
        response.setRevisions(chain.stream()
                .map(s -> toResponse(s, null, null))
                .toList());
        return response;
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. 列表
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分页列出当前工作区的指标快照。<b>必须</b>按 workspace 过滤。
     */
    public List<MetricSnapshotResponse> list(String workspaceId, UUID assetId, String platformCode,
                                             OffsetDateTime from, OffsetDateTime to, String source,
                                             int page, int size) {
        requireWorkspace(workspaceId);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 1);

        StringBuilder sql = new StringBuilder("""
                SELECT * FROM app.metric_snapshot
                 WHERE workspace_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        if (assetId != null) {
            sql.append(" AND asset_id = CAST(? AS uuid)");
            args.add(assetId);
        }
        if (platformCode != null && !platformCode.isBlank()) {
            sql.append(" AND platform_code = ?");
            args.add(normalizePlatformCode(platformCode));
        }
        if (from != null) {
            sql.append(" AND collected_at >= CAST(? AS timestamptz)");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND collected_at <= CAST(? AS timestamptz)");
            args.add(to);
        }
        if (source != null && !source.isBlank()) {
            sql.append(" AND metric_source = ?");
            args.add(source.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY collected_at DESC, created_at DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((long) (safePage - 1) * safeSize);

        List<MetricSnapshot> rows = jdbcTemplate.query(
                sql.toString(),
                (org.springframework.jdbc.core.RowMapper<MetricSnapshot>) (rs, i) -> mapSnapshot(rs),
                args.toArray());
        return rows.stream().map(s -> toResponse(s, null, null)).toList();
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. 趋势（修订后的快照不重复计入）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 趋势聚合：按 day / week 时间桶聚合。
     * <p>
     * <b>关键正确性要求</b>：聚合时每个 {@code asset + external_content_id} 只取最新一条快照
     * （{@code ROW_NUMBER() OVER (PARTITION BY ... ORDER BY collected_at DESC, created_at DESC)}），
     * 因此人工修订产生的新快照不会与原快照重复计数。
     * </p>
     */
    public List<MetricTrendBucket> trend(String workspaceId, UUID assetId, String platformCode,
                                          String granularity, OffsetDateTime from, OffsetDateTime to) {
        requireWorkspace(workspaceId);
        String gran = normalizeGranularity(granularity);

        StringBuilder sql = new StringBuilder("""
                SELECT to_char(date_trunc(?::text, s.collected_at), 'YYYY-MM-DD') AS bucket,
                       COALESCE(SUM(CAST(s.metrics ->> 'impressions' AS numeric)), 0)  AS impressions,
                       COALESCE(SUM(CAST(s.metrics ->> 'likes' AS numeric)), 0)        AS likes,
                       COALESCE(SUM(CAST(s.metrics ->> 'collects' AS numeric)), 0)     AS collects,
                       COALESCE(SUM(CAST(s.metrics ->> 'comments' AS numeric)), 0)     AS comments,
                       COALESCE(SUM(CAST(s.metrics ->> 'dm_inquiries' AS numeric)), 0) AS dm_inquiries
                  FROM (
                        SELECT m.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY m.asset_id, m.external_content_id
                                   ORDER BY m.collected_at DESC, m.created_at DESC
                               ) AS rn
                          FROM app.metric_snapshot m
                         WHERE m.workspace_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(gran);
        args.add(workspaceId);
        if (assetId != null) {
            sql.append(" AND m.asset_id = ?");
            args.add(assetId);
        }
        if (platformCode != null && !platformCode.isBlank()) {
            sql.append(" AND m.platform_code = ?");
            args.add(normalizePlatformCode(platformCode));
        }
        if (from != null) {
            sql.append(" AND m.collected_at >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND m.collected_at <= ?");
            args.add(to);
        }
        sql.append(") s WHERE s.rn = 1 GROUP BY 1 ORDER BY 1 ASC");

        List<MetricSnapshotMapper.TrendBucketRow> rows = jdbcTemplate.query(
                sql.toString(),
                (org.springframework.jdbc.core.RowMapper<MetricSnapshotMapper.TrendBucketRow>)
                        (rs, i) -> {
                            MetricSnapshotMapper.TrendBucketRow row = new MetricSnapshotMapper.TrendBucketRow();
                            row.setBucket(rs.getString("bucket"));
                            row.setImpressions(rs.getBigDecimal("impressions"));
                            row.setLikes(rs.getBigDecimal("likes"));
                            row.setCollects(rs.getBigDecimal("collects"));
                            row.setComments(rs.getBigDecimal("comments"));
                            row.setDmInquiries(rs.getBigDecimal("dm_inquiries"));
                            return row;
                        },
                args.toArray());

        return rows.stream()
                .map(r -> MetricTrendBucket.builder()
                        .bucket(r.getBucket())
                        .impressions(toLong(r.getImpressions()))
                        .likes(toLong(r.getLikes()))
                        .collects(toLong(r.getCollects()))
                        .comments(toLong(r.getComments()))
                        .dmInquiries(toLong(r.getDmInquiries()))
                        .build())
                .toList();
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. 采集任务触发
    // ══════════════════════════════════════════════════════════════════

    /**
     * 触发一次指标采集（ADMIN/OWNER 由 Controller 校验）。
     * <p>
     * 先过授权门禁（未授权 → 403 CONNECTOR_NOT_AUTHORIZED），再投递到持久化任务队列。
     * <b>采集不计费</b>：不写 usage_ledger DEBIT。
     * </p>
     */
    @Transactional
    public MetricCollectResponse collect(String workspaceId, Long userId, MetricCollectRequest request) {
        requireWorkspace(workspaceId);
        String platformCode = normalizePlatformCode(request.getPlatformCode());
        String accountId = blankToNull(request.getAccountId());

        String providerCode = resolveAuthorizedProvider(workspaceId, platformCode, accountId);

        OffsetDateTime windowEnd = OffsetDateTime.now();
        OffsetDateTime windowStart = windowEnd.minusHours(COLLECT_WINDOW_HOURS);

        MetricCollectTask task = new MetricCollectTask();
        task.setId(UUID.randomUUID());
        task.setWorkspaceId(workspaceId);
        task.setPlatformCode(platformCode);
        task.setAccountId(accountId);
        task.setStatus(MetricCollectTask.STATUS_QUEUED);
        task.setAttempt(0);
        task.setWindowStart(windowStart);
        task.setWindowEnd(windowEnd);
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        collectTaskMapper.insert(task);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId().toString());
        payload.put("workspaceId", workspaceId);
        payload.put("platformCode", platformCode);
        payload.put("accountId", accountId);
        payload.put("providerCode", providerCode);
        payload.put("windowStart", windowStart.toString());
        payload.put("windowEnd", windowEnd.toString());
        payload.put("userId", userId);

        taskQueueProducer.enqueue("METRIC_COLLECT", task.getId().toString(), writeJson(payload), 0);

        log.info("Metric collect task queued: taskId={} ws={} platform={} account={} provider={}",
                task.getId(), workspaceId, platformCode, accountId, providerCode);

        return MetricCollectResponse.builder()
                .taskId(task.getId())
                .status(MetricCollectTask.STATUS_QUEUED)
                .platformCode(platformCode)
                .accountId(accountId)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. 采集结果落库（由 MetricCollectTaskHandler 调用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把连接器返回的指标样本落库。
     * <p>
     * <b>幂等</b>：幂等键 = 平台 + 账号 + 内容外部 ID + 采集窗口；同窗口重复采集不产生重复快照
     * （唯一约束 + 插入前查重双重保证）。
     * </p>
     *
     * @return 实际新增的快照数
     */
    @Transactional
    public int persistCollectedSamples(String workspaceId, MetricCollectTask task,
                                        List<MetricSample> samples, String providerCode) {
        if (samples == null || samples.isEmpty()) {
            return 0;
        }
        String windowKey = task.getWindowStart() + "~" + task.getWindowEnd();
        int inserted = 0;
        for (MetricSample sample : samples) {
            if (sample == null) {
                continue;
            }
            String platformCode = normalizePlatformCode(
                    sample.platformCode() != null ? sample.platformCode() : task.getPlatformCode());
            String accountId = blankToNull(
                    sample.accountId() != null ? sample.accountId() : task.getAccountId());
            String externalContentId = blankToNull(sample.externalContentId());
            if (externalContentId == null) {
                // 没有外部内容 ID 的样本无法定位内容，跳过（不产生脏数据）
                continue;
            }
            NormalizedMetrics normalized = normalizeMetrics(null, toObjectMap(sample.metrics()),
                    toObjectMap(sample.metricExtra()));
            String source = WRITABLE_SOURCES.contains(upper(sample.metricSource()))
                    ? upper(sample.metricSource())
                    : MetricSnapshot.SOURCE_AUTHORIZED_CONNECTOR;
            String confidence = ALLOWED_CONFIDENCE.contains(upper(sample.confidence()))
                    ? upper(sample.confidence())
                    : MetricSnapshot.CONFIDENCE_MEDIUM;
            OffsetDateTime collectedAt = sample.collectedAt() != null ? sample.collectedAt() : OffsetDateTime.now();

            String key = collectionKey("COLLECT", platformCode, accountId, externalContentId, windowKey);
            if (existsByCollectionKey(key)) {
                log.debug("Metric collect skipped (idempotent): key={}", key);
                continue;
            }

            MetricSnapshot snapshot = new MetricSnapshot();
            snapshot.setId(UUID.randomUUID());
            snapshot.setWorkspaceId(workspaceId);
            snapshot.setPlatformCode(platformCode);
            snapshot.setAccountId(accountId);
            snapshot.setAssetId(resolveAssetByAccount(workspaceId, platformCode, accountId));
            snapshot.setExternalContentId(externalContentId);
            snapshot.setContentType(null);
            snapshot.setPublishedAt(sample.publishedAt());
            snapshot.setMetrics(writeJson(normalized.core()));
            snapshot.setMetricExtra(writeJson(normalized.extra()));
            snapshot.setMetricSource(source);
            snapshot.setConfidence(confidence);
            snapshot.setCollectedAt(collectedAt);
            snapshot.setIsManual(Boolean.FALSE);
            snapshot.setCollectionKey(key);
            snapshot.setCreatedAt(OffsetDateTime.now());
            inserted += insertIgnoreDuplicate(snapshot) ? 1 : 0;
        }
        log.info("Metric collect persisted: taskId={} samples={} inserted={}",
                task.getId(), samples.size(), inserted);
        return inserted;
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. 同步状态可见
    // ══════════════════════════════════════════════════════════════════

    /**
     * 查询采集同步状态（同步延迟 / 失败率 / 最后成功时间可见）。
     */
    public List<MetricSyncStatusResponse> syncStatus(String workspaceId, String platformCode, String accountId) {
        requireWorkspace(workspaceId);
        List<MetricSyncState> states;
        if (platformCode != null && !platformCode.isBlank()) {
            states = syncStateMapper.listByPlatform(workspaceId, normalizePlatformCode(platformCode));
            if (accountId != null && !accountId.isBlank()) {
                String target = accountId.trim();
                states = states.stream().filter(s -> target.equals(s.getAccountId())).toList();
            }
        } else {
            states = List.of();
        }
        return states.stream().map(this::toSyncStatus).toList();
    }

    /**
     * 采集成功 → 同步状态刷新。
     */
    public void markCollectSuccess(String workspaceId, String platformCode, String accountId) {
        OffsetDateTime now = OffsetDateTime.now();
        syncStateMapper.upsert(workspaceId, normalizePlatformCode(platformCode),
                accountId == null ? "" : accountId,
                MetricSyncState.STATUS_HEALTHY, 0, now, now, null, now);
    }

    /**
     * 采集失败 → 连续失败次数 +1，达到阈值标 DEGRADED。
     */
    public void markCollectFailure(String workspaceId, String platformCode, String accountId, String error) {
        OffsetDateTime now = OffsetDateTime.now();
        String normalizedAccount = accountId == null ? "" : accountId;
        MetricSyncState current = syncStateMapper.selectState(workspaceId, normalizePlatformCode(platformCode),
                normalizedAccount);
        int failures = (current == null || current.getConsecutiveFailures() == null)
                ? 1 : current.getConsecutiveFailures() + 1;
        String status = failures >= MetricSyncState.DEGRADED_THRESHOLD
                ? MetricSyncState.STATUS_DEGRADED
                : (current == null ? MetricSyncState.STATUS_UNKNOWN : current.getStatus());
        OffsetDateTime lastSuccess = current == null ? null : current.getLastSuccessAt();
        syncStateMapper.upsert(workspaceId, normalizePlatformCode(platformCode), normalizedAccount,
                status, failures, lastSuccess, now,
                error == null ? "unknown_error" : truncate(error, 1000), now);
    }

    // ══════════════════════════════════════════════════════════════════
    // 9. 旧数据兼容读取（analysis_task.published_metrics）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把旧 {@code analysis_task.published_metrics}（JSONB）映射成<b>虚拟快照</b>。
     * <p>
     * 强制 {@code metric_source = LEGACY_JSONB}、{@code confidence = LOW}、
     * {@code collected_at = publishedAt}。<b>不写库</b>、<b>不改 analysis_task 表结构</b>。
     * </p>
     */
    public List<MetricSnapshotResponse> legacySnapshots(String workspaceId, String analysisTaskId) {
        requireWorkspace(workspaceId);
        AnalysisTask task = analysisTaskMapper.selectById(analysisTaskId);
        if (task == null || !workspaceId.equals(task.getWorkspaceId())) {
            throw new BusinessException(CODE_SNAPSHOT_NOT_FOUND, "SNAPSHOT_NOT_FOUND");
        }
        String raw = task.getPublishedMetrics();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Map<String, Object> legacy;
        try {
            legacy = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse legacy published_metrics for task {}: {}", analysisTaskId, ex.getMessage());
            return List.of();
        }
        OffsetDateTime publishedAt = parseDateTime(legacy.get("publishedAt"));
        OffsetDateTime collectedAt = publishedAt != null ? publishedAt : task.getCreatedAt();

        Map<String, Object> coreInput = new LinkedHashMap<>();
        copyIfPresent(legacy, coreInput, "impressions");
        copyIfPresent(legacy, coreInput, "likes");
        copyIfPresent(legacy, coreInput, "collects");
        copyIfPresent(legacy, coreInput, "comments");
        copyIfPresent(legacy, coreInput, "dm_inquiries");
        copyIfPresent(legacy, coreInput, "dmInquiries");

        NormalizedMetrics normalized = normalizeMetrics(null, coreInput, null);

        MetricSnapshot virtual = new MetricSnapshot();
        virtual.setId(null);
        virtual.setWorkspaceId(workspaceId);
        virtual.setPlatformCode(task.getScenario());
        virtual.setAccountId(null);
        virtual.setAssetId(null);
        virtual.setExternalContentId(null);
        virtual.setContentType(null);
        virtual.setPublishedAt(publishedAt);
        virtual.setMetrics(writeJson(normalized.core()));
        virtual.setMetricExtra(writeJson(normalized.extra()));
        virtual.setMetricSource(MetricSnapshot.SOURCE_LEGACY_JSONB);
        virtual.setConfidence(MetricSnapshot.CONFIDENCE_LOW);
        virtual.setCollectedAt(collectedAt);
        virtual.setIsManual(Boolean.TRUE);
        virtual.setCreatedAt(task.getCreatedAt());

        return List.of(toResponse(virtual, null, null));
    }

    // ══════════════════════════════════════════════════════════════════
    // 10. CSV 导入支撑
    // ══════════════════════════════════════════════════════════════════

    /**
     * CSV 导入的结果统计。
     */
    public record CsvImportOutcome(int imported, int skipped, int errorRows, List<String> errors) {
    }

    /**
     * 导入 CSV 指标数据。
     * <p>
     * 表头白名单：标准字段 + 10 个核心指标键 + 平台扩展字段；<b>未知列直接拒绝</b>并抛
     * {@link BusinessException#CODE_CSV_SCHEMA_INVALID}（{@code CSV_SCHEMA_INVALID}）。
     * 失败行不影响成功行；每条错误都带行号 + 原因。
     * </p>
     */
    public CsvImportOutcome importMetricsCsv(String workspaceId, Long userId, String platformCode,
                                             String accountId, String csvText,
                                             List<String> errorSink, int maxErrors) {
        requireWorkspace(workspaceId);
        String normalizedPlatform = normalizePlatformCode(platformCode);
        String normalizedAccount = blankToNull(accountId);

        List<String> lines = splitLines(csvText);
        if (lines.size() < 2) {
            if (errorSink != null) {
                errorSink.add("Row 1: CSV_SCHEMA_INVALID: csv_must_have_header_and_data");
            }
            throw new BusinessException(CODE_CSV_SCHEMA_INVALID, "CSV_SCHEMA_INVALID: csv_must_have_header_and_data");
        }
        List<String> headers = splitCsvLine(lines.get(0));

        // ── 表头白名单校验：未知列直接拒绝 ──────────────────────────────
        List<String> unknown = new ArrayList<>();
        for (String header : headers) {
            String h = header.trim();
            if (h.isEmpty()) {
                continue;
            }
            if (!CSV_STANDARD_COLUMNS.contains(h)
                    && !MetricKeys.CORE_KEYS.contains(h)
                    && !CSV_EXTRA_COLUMNS.contains(h)) {
                unknown.add(h);
            }
        }
        if (!unknown.isEmpty()) {
            String detail = "CSV_SCHEMA_INVALID: unknown_columns=" + String.join(",", unknown);
            if (errorSink != null) {
                errorSink.add("Row 1: " + detail);
            }
            throw new BusinessException(CODE_CSV_SCHEMA_INVALID, detail);
        }

        int imported = 0;
        int skipped = 0;
        int errorRows = 0;
        List<String> errors = errorSink == null ? new ArrayList<>() : errorSink;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            int rowNumber = i + 1;
            try {
                List<String> cells = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    row.put(headers.get(c).trim(), c < cells.size() ? cells.get(c).trim() : null);
                }

                String externalContentId = blankToNull(row.get("external_content_id"));
                if (externalContentId == null) {
                    errors.add("Row " + rowNumber + ": external_content_id missing");
                    errorRows++;
                    continue;
                }
                Map<String, Object> core = new LinkedHashMap<>();
                for (String key : MetricKeys.CORE_KEYS) {
                    String raw = row.get(key);
                    if (raw == null || raw.isEmpty()) {
                        continue;
                    }
                    core.put(key, parseLong(raw, "Row " + rowNumber + ": invalid_" + key));
                }
                Map<String, Object> extra = new LinkedHashMap<>();
                for (String key : CSV_EXTRA_COLUMNS) {
                    String raw = row.get(key);
                    if (raw == null || raw.isEmpty()) {
                        continue;
                    }
                    extra.put(key, raw);
                }

                String rowAccount = blankToNull(row.get("account_id"));
                String effectiveAccount = rowAccount != null ? rowAccount : normalizedAccount;
                UUID assetId = resolveAndValidateAsset(workspaceId, row.get("asset_id"),
                        normalizedPlatform, effectiveAccount);

                OffsetDateTime publishedAt = parseDateTime(row.get("published_at"));
                OffsetDateTime collectedAt = parseDateTime(row.get("collected_at"));
                if (collectedAt == null) {
                    collectedAt = OffsetDateTime.now();
                }
                NormalizedMetrics normalized = normalizeMetrics(null, core, extra);

                MetricSnapshot snapshot = new MetricSnapshot();
                snapshot.setId(UUID.randomUUID());
                snapshot.setWorkspaceId(workspaceId);
                snapshot.setPlatformCode(normalizedPlatform);
                snapshot.setAccountId(effectiveAccount);
                snapshot.setAssetId(assetId);
                snapshot.setExternalContentId(externalContentId);
                snapshot.setContentType(blankToNull(row.get("content_type")));
                snapshot.setPublishedAt(publishedAt);
                snapshot.setMetrics(writeJson(normalized.core()));
                snapshot.setMetricExtra(writeJson(normalized.extra()));
                snapshot.setMetricSource(MetricSnapshot.SOURCE_CSV_IMPORT);
                snapshot.setConfidence(MetricSnapshot.CONFIDENCE_LOW);
                snapshot.setCollectedAt(collectedAt);
                snapshot.setIsManual(Boolean.TRUE);
                snapshot.setCollectionKey(collectionKey("CSV", normalizedPlatform, effectiveAccount,
                        externalContentId, "t" + collectedAt.toInstant().toEpochMilli()));
                snapshot.setCreatedAt(OffsetDateTime.now());

                if (insertIgnoreDuplicate(snapshot)) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (BusinessException be) {
                errorRows++;
                addError(errors, "Row " + rowNumber + ": " + be.getMessage(), maxErrors);
            } catch (Exception ex) {
                errorRows++;
                addError(errors, "Row " + rowNumber + ": " + ex.getMessage(), maxErrors);
            }
        }
        return new CsvImportOutcome(imported, skipped, errorRows, errors);
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具
    // ══════════════════════════════════════════════════════════════════

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_METRIC_FIELDS_MISSING, "METRIC_FIELDS_MISSING: workspace_id_required");
        }
    }

    private MetricSnapshot requireSnapshot(String workspaceId, UUID snapshotId) {
        if (snapshotId == null) {
            throw new BusinessException(CODE_SNAPSHOT_NOT_FOUND, "SNAPSHOT_NOT_FOUND");
        }
        MetricSnapshot snapshot = snapshotMapper.selectByIdAndWorkspace(snapshotId, workspaceId);
        if (snapshot == null) {
            throw new BusinessException(CODE_SNAPSHOT_NOT_FOUND, "SNAPSHOT_NOT_FOUND");
        }
        return snapshot;
    }

    /**
     * 归属校验：资产必须存在于当前 workspace；platform / account 与资产不一致 → 40952。
     */
    private UUID resolveAndValidateAsset(String workspaceId, String rawAssetId,
                                         String platformCode, String accountId) {
        if (rawAssetId == null || rawAssetId.isBlank()) {
            return null;
        }
        UUID assetId;
        try {
            assetId = UUID.fromString(rawAssetId.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "asset_not_found");
        }
        ContentAsset asset = contentAssetMapper.selectById(assetId);
        if (asset == null || !workspaceId.equals(asset.getWorkspaceId())) {
            // 越权一律按"未找到"处理，不泄露存在性
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "asset_not_found");
        }
        String assetPlatform = blankToNull(asset.getPlatform());
        if (assetPlatform != null && !platformMatches(assetPlatform, platformCode)) {
            throw new BusinessException(CODE_ACCOUNT_MISMATCH, "ACCOUNT_MISMATCH");
        }
        String assetAccount = blankToNull(asset.getAccountId());
        if (assetAccount != null && accountId != null && !assetAccount.equals(accountId)) {
            throw new BusinessException(CODE_ACCOUNT_MISMATCH, "ACCOUNT_MISMATCH");
        }
        return assetId;
    }

    /**
     * 平台一致性判定：兼容小写 slug 与大写枚举名两种写法。
     */
    private boolean platformMatches(String assetPlatform, String platformCode) {
        if (assetPlatform.equalsIgnoreCase(platformCode)) {
            return true;
        }
        Optional<PlatformCode> byEnum = PlatformCode.tryParse(assetPlatform);
        if (byEnum.isPresent() && byEnum.get().getSlug().equalsIgnoreCase(platformCode)) {
            return true;
        }
        Optional<PlatformCode> target = PlatformCode.tryParse(platformCode);
        return target.isPresent() && target.get().getSlug().equalsIgnoreCase(assetPlatform);
    }

    /**
     * 采集时按平台 + 账号反查资产（只有唯一匹配才关联，避免误挂）。
     */
    private UUID resolveAssetByAccount(String workspaceId, String platformCode, String accountId) {
        if (accountId == null) {
            return null;
        }
        List<UUID> matches = jdbcTemplate.query(
                "SELECT id FROM app.content_asset WHERE workspace_id = ? AND account_id = ? "
                        + "AND (platform IS NULL OR lower(platform) = lower(?)) LIMIT 2",
                (org.springframework.jdbc.core.RowMapper<UUID>) (rs, i) -> rs.getObject("id", UUID.class),
                workspaceId, accountId, platformCode);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /**
     * 解析已授权的连接器实现；没有任何授权实现时抛 403 CONNECTOR_NOT_AUTHORIZED。
     */
    private String resolveAuthorizedProvider(String workspaceId, String platformCode, String accountId) {
        List<String> candidates = new ArrayList<>();
        if (accountId != null) {
            candidates.addAll(platformAccountMapper.selectByWorkspaceAndPlatform(workspaceId, platformCode)
                    .stream()
                    .filter(a -> accountId.equals(blankToNull(a.getExternalAccountId()))
                            || accountId.equals(blankToNull(a.getId() == null ? null : a.getId().toString())))
                    .map(a -> blankToNull(a.getConnectorProviderCode()))
                    .filter(java.util.Objects::nonNull)
                    .toList());
        }
        candidates.addAll(jdbcTemplate.query(
                "SELECT code FROM app.connector_provider WHERE platform_code = ?",
                (rs, i) -> rs.getString("code"),
                platformCode));

        for (String provider : candidates) {
            if (provider != null && connectorAuthorizationService.isAuthorized(platformCode, provider)) {
                return provider;
            }
        }
        throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
    }

    /**
     * 统一口径归一：核心 10 键缺失补 0；核心键之外的字段进 metric_extra。
     */
    private NormalizedMetrics normalizeMetrics(Map<String, Long> base, Map<String, Object> input,
                                                Map<String, Object> extraInput) {
        Map<String, Long> core = new LinkedHashMap<>();
        Map<String, Object> extra = new LinkedHashMap<>();

        if (base != null) {
            for (String key : MetricKeys.CORE_KEYS) {
                Long v = base.get(key);
                core.put(key, v == null ? 0L : v);
            }
        }
        if (extraInput != null) {
            extra.putAll(extraInput);
        }
        if (input != null) {
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                if (MetricKeys.CORE_KEYS.contains(key)) {
                    core.put(key, toLongValue(entry.getValue()));
                } else {
                    // 核心键之外的字段只能进 metric_extra
                    extra.put(key, entry.getValue());
                }
            }
        }
        for (String key : MetricKeys.CORE_KEYS) {
            core.putIfAbsent(key, 0L);
        }
        return new NormalizedMetrics(core, extra);
    }

    private Map<String, Long> readCoreMetrics(String metricsJson) {
        Map<String, Long> core = new LinkedHashMap<>();
        if (metricsJson == null || metricsJson.isBlank()) {
            return core;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(metricsJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            for (String key : MetricKeys.CORE_KEYS) {
                core.put(key, toLongValue(parsed.get(key)));
            }
        } catch (Exception ex) {
            log.warn("Failed to parse stored metrics JSON: {}", ex.getMessage());
        }
        return core;
    }

    private Map<String, Object> toObjectMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.putAll(source);
        return map;
    }

    private String collectionKey(String prefix, String platformCode, String accountId,
                                 String externalContentId, String windowKey) {
        String raw = prefix + "|" + platformCode + "|" + (accountId == null ? "" : accountId)
                + "|" + (externalContentId == null ? "" : externalContentId) + "|" + windowKey;
        return raw.length() > 250 ? raw.substring(0, 250) : raw;
    }

    private boolean existsByCollectionKey(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app.metric_snapshot WHERE collection_key = ?", Integer.class, key);
        return count != null && count > 0;
    }

    /**
     * 插入快照；命中幂等唯一约束时返回 false（不抛错）。
     */
    private boolean insertIgnoreDuplicate(MetricSnapshot snapshot) {
        try {
            snapshotMapper.insert(snapshot);
            return true;
        } catch (DuplicateKeyException ex) {
            log.info("Metric snapshot duplicate skipped (idempotent): key={}", snapshot.getCollectionKey());
            return false;
        }
    }

    private MetricSnapshot mapSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        MetricSnapshot s = new MetricSnapshot();
        s.setId((UUID) rs.getObject("id"));
        s.setWorkspaceId(rs.getString("workspace_id"));
        s.setPlatformCode(rs.getString("platform_code"));
        s.setAccountId(rs.getString("account_id"));
        s.setAssetId((UUID) rs.getObject("asset_id"));
        s.setExternalContentId(rs.getString("external_content_id"));
        s.setContentType(rs.getString("content_type"));
        s.setPublishedAt(rs.getObject("published_at", OffsetDateTime.class));
        s.setMetrics(rs.getString("metrics"));
        s.setMetricExtra(rs.getString("metric_extra"));
        s.setMetricSource(rs.getString("metric_source"));
        s.setConfidence(rs.getString("confidence"));
        s.setCollectedAt(rs.getObject("collected_at", OffsetDateTime.class));
        s.setIsManual(rs.getBoolean("is_manual"));
        s.setRevisedFromId((UUID) rs.getObject("revised_from_id"));
        s.setRevisedBy(rs.getObject("revised_by", Long.class));
        s.setCollectionKey(rs.getString("collection_key"));
        s.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return s;
    }

    private MetricSnapshotResponse toResponse(MetricSnapshot s, List<MetricSnapshotResponse> revisions,
                                              String reviseReason) {
        return MetricSnapshotResponse.builder()
                .id(s.getId())
                .assetId(s.getAssetId())
                .platformCode(s.getPlatformCode())
                .accountId(s.getAccountId())
                .externalContentId(s.getExternalContentId())
                .contentType(s.getContentType())
                .publishedAt(s.getPublishedAt())
                .metrics(readCoreMetrics(s.getMetrics()))
                .metricExtra(readExtra(s.getMetricExtra()))
                .metricSource(s.getMetricSource())
                .confidence(s.getConfidence())
                .collectedAt(s.getCollectedAt())
                .isManual(s.getIsManual())
                .revisedFromId(s.getRevisedFromId())
                .revisedBy(s.getRevisedBy())
                .createdAt(s.getCreatedAt())
                .revisions(revisions)
                .reviseReason(reviseReason)
                .build();
    }

    private Map<String, Object> readExtra(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private MetricSyncStatusResponse toSyncStatus(MetricSyncState state) {
        Long delay = null;
        if (state.getLastCollectedAt() != null) {
            delay = java.time.Duration.between(state.getLastCollectedAt(), OffsetDateTime.now()).getSeconds();
            if (delay < 0) {
                delay = 0L;
            }
        }
        return MetricSyncStatusResponse.builder()
                .platformCode(state.getPlatformCode())
                .accountId(state.getAccountId())
                .status(state.getStatus())
                .lastCollectedAt(state.getLastCollectedAt())
                .lastSuccessAt(state.getLastSuccessAt())
                .consecutiveFailures(state.getConsecutiveFailures())
                .lastError(state.getLastError())
                .delaySeconds(delay)
                .build();
    }

    // ── 字符串 / 数值工具 ────────────────────────────────────────────────

    private String normalizePlatformCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_METRIC_FIELDS_MISSING, "METRIC_FIELDS_MISSING: platform_code_required");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSource(String raw) {
        String value = upper(raw);
        if (value == null) {
            // 未指定来源时默认人工导入
            return MetricSnapshot.SOURCE_MANUAL_IMPORT;
        }
        if (!WRITABLE_SOURCES.contains(value)) {
            throw new BusinessException(CODE_METRIC_FIELDS_MISSING,
                    "METRIC_FIELDS_MISSING: invalid_metric_source");
        }
        return value;
    }

    private String normalizeConfidence(String raw, String fallback) {
        String value = upper(raw);
        if (value == null) {
            return fallback == null ? MetricSnapshot.CONFIDENCE_LOW : fallback;
        }
        if (!ALLOWED_CONFIDENCE.contains(value)) {
            throw new BusinessException(CODE_METRIC_FIELDS_MISSING,
                    "METRIC_FIELDS_MISSING: invalid_confidence");
        }
        return value;
    }

    private String normalizeGranularity(String raw) {
        String value = raw == null ? "day" : raw.trim().toLowerCase(Locale.ROOT);
        if (!"day".equals(value) && !"week".equals(value)) {
            throw new BusinessException(CODE_METRIC_FIELDS_MISSING,
                    "METRIC_FIELDS_MISSING: invalid_granularity");
        }
        return value;
    }

    private String upper(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private Long toLongValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long parseLong(String raw, String errorMessage) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(CODE_METRIC_FIELDS_MISSING, errorMessage);
        }
    }

    private Long toLong(BigDecimal value) {
        return value == null ? 0L : value.longValue();
    }

    private OffsetDateTime parseDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ignored) {
            // 尝试常见格式
        }
        try {
            return OffsetDateTime.of(java.time.LocalDateTime.parse(text), ZoneOffset.UTC);
        } catch (Exception ignored) {
            // ignore
        }
        try {
            return OffsetDateTime.of(java.time.LocalDate.parse(text).atStartOfDay(), ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private void addError(List<String> errors, String message, int maxErrors) {
        if (errors != null && errors.size() < maxErrors) {
            errors.add(message);
        }
    }

    private String truncate(String raw, int max) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= max ? raw : raw.substring(0, max);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize metric JSON: {}", ex.getMessage());
            return "{}";
        }
    }

    /**
     * 轻量 CSV 行切分（支持双引号包裹与转义双引号）。
     */
    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private List<String> splitLines(String csvText) {
        List<String> lines = new ArrayList<>();
        for (String line : csvText.split("\\r?\\n")) {
            lines.add(line);
        }
        return lines;
    }

    /** 归一后的指标：core 为固定 10 键，extra 为平台差异字段 */
    private record NormalizedMetrics(Map<String, Long> core, Map<String, Object> extra) {
    }
}
