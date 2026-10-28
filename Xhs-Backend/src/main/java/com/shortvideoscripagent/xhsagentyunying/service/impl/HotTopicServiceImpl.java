package com.shortvideoscripagent.xhsagentyunying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.web.WebSearchService;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorAuthorizationService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorRegistry;
import com.shortvideoscripagent.xhsagentyunying.connector.HotTopicFetchRequest;
import com.shortvideoscripagent.xhsagentyunying.connector.HotTopicSample;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.HotTopic;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.HotTopicCache;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TopicPool;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.HotTopicCacheMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.HotTopicMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TopicPoolMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicImportResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicPromoteResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicSamplesResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendPoint;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendResponse;
import com.shortvideoscripagent.xhsagentyunying.freeze.BillableUnit;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformCode;
import com.shortvideoscripagent.xhsagentyunying.quota.AtomicQuotaGuard;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaCheckResult;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaRefundService;
import com.shortvideoscripagent.xhsagentyunying.service.HotTopicService;
import com.shortvideoscripagent.xhsagentyunying.service.UserQuotaService;
import com.shortvideoscripagent.xhsagentyunying.util.topic.TopicNormalize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * S3-9 热点数据产品化服务实现。
 *
 * <p>核心约束（对应 STAGE3_ACCEPTANCE.md §4.1）：
 * <ol>
 *   <li><b>禁止伪装实时</b>：静态种子 {@code source_type='SEED'} + {@code is_realtime=FALSE}；
 *       联网搜索采集才是 {@code SEARCH_TOOL} + {@code is_realtime=TRUE}。</li>
 *   <li><b>去重保留历史</b>：同平台 + 归一化标题只保留最新一条 {@code status='active'}，
 *       其余标 {@code status='superseded'}（不删除，供趋势对比）。</li>
 *   <li><b>缓存</b>：同窗口命中 {@code app.hot_topic_cache} 不重复调用外部源。</li>
 *   <li><b>降级可见</b>：采集源失败 → 顶层 {@code degraded=true} + 原因 + 兜底路径，
 *       不允许静默展示陈旧数据。</li>
 *   <li><b>可做方向有证据</b>：每条建议引用真实标题 + 来源 + 采集时间。</li>
 *   <li><b>选题池透传</b>：{@code source_name} / {@code confidence} / {@code source_fetched_at}。</li>
 *   <li><b>采集计费</b>：{@code BillableUnit.SEARCH}；失败时按 ledgerId REFUND。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotTopicServiceImpl implements HotTopicService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 400 热点参数非法 */
    public static final int CODE_BAD_REQUEST = 40070;
    /** 400 CSV 表头非法 */
    public static final int CODE_CSV_SCHEMA_INVALID = 40071;
    /** 404 热点不存在 */
    public static final int CODE_HOT_TOPIC_NOT_FOUND = 40470;
    /** 403 连接器未授权 */
    public static final int CODE_CONNECTOR_NOT_AUTHORIZED = 40302;

    /** 联网搜索来源名称 */
    private static final String SOURCE_NAME_WEB_SEARCH = "web_search";
    /** 手动导入来源名称 */
    private static final String SOURCE_NAME_MANUAL_IMPORT = "manual_import";
    /** 联网搜索热点对应的选题池 source 值（保留阶段 1 语义：去重键依赖它） */
    private static final String AGENT_SEARCH_SOURCE = "agent_search";
    /** 手动导入热点对应的选题池 source 值 */
    private static final String MANUAL_IMPORT_SOURCE = "manual_import";
    /** 静态种子热点对应的选题池 source 值 */
    private static final String SEED_SOURCE = "static_seed";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String SEARCH_QUERY = "小红书 当前热门选题 趋势 内容运营";
    /** 默认平台（与既有 hot_topic.platform 取值一致：slug） */
    private static final String DEFAULT_PLATFORM = HotTopic.PLATFORM_XHS;
    /** 缓存有效期（秒） */
    private static final int CACHE_TTL_SECONDS = 3600;
    /** 降级兜底路径 */
    private static final String FALLBACK_MANUAL_IMPORT = "MANUAL_IMPORT";

    /** 允许的来源类型 */
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            HotTopic.SOURCE_TYPE_SEARCH_TOOL,
            HotTopic.SOURCE_TYPE_OFFICIAL_API,
            HotTopic.SOURCE_TYPE_AUTHORIZED_CONNECTOR,
            HotTopic.SOURCE_TYPE_MANUAL_IMPORT,
            HotTopic.SOURCE_TYPE_SEED);

    /** CSV 表头白名单 */
    private static final Set<String> CSV_ALLOWED_COLUMNS = Set.of(
            "title", "platform", "category", "heat_score", "source_name", "source_url", "fetched_at");

    private final HotTopicMapper hotTopicMapper;
    private final HotTopicCacheMapper hotTopicCacheMapper;
    private final TopicPoolMapper topicPoolMapper;
    private final WebSearchService webSearchService;
    private final UserQuotaService userQuotaService;
    private final ConnectorRegistry connectorRegistry;
    private final ConnectorAuthorizationService connectorAuthorizationService;
    private final AtomicQuotaGuard atomicQuotaGuard;
    private final QuotaRefundService quotaRefundService;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════
    // 1. 采集
    // ══════════════════════════════════════════════════════════════════

    @Override
    public int refreshFromAgentTool() {
        // 调度器入口：系统发起，无 workspace 上下文 → 不计费（与阶段 1 行为一致）
        return collect(null, DEFAULT_PLATFORM, null, null);
    }

    @Override
    public HotTopicRefreshResponse refresh(String workspaceId, Long userId, HotTopicRefreshRequest request) {
        String rawPlatform = request != null ? request.getPlatform() : null;
        String platformStorage = normalizePlatformStorage(rawPlatform);
        String platformCode = normalizePlatformCode(rawPlatform);
        String category = request != null ? trimToNull(request.getCategory()) : null;

        // ── 授权门禁：该平台必须至少有一个"已授权"的连接器实现 ──────────
        requireAuthorizedPlatform(platformCode);

        try {
            int count = collect(workspaceId, platformStorage, category, userId);
            return HotTopicRefreshResponse.builder()
                    .count(count)
                    .status("succeeded")
                    .message("refreshed")
                    .degraded(Boolean.FALSE)
                    .fallbackAvailable(null)
                    .build();
        } catch (UnsupportedOperationException ex) {
            writeDegradedCache(platformStorage, normalizeCacheCategory(category), "web_search_not_configured");
            return HotTopicRefreshResponse.builder()
                    .count(0)
                    .status("degraded")
                    .message("tool_not_configured")
                    .degraded(Boolean.TRUE)
                    .degradedReason("web_search_not_configured")
                    .fallbackAvailable(FALLBACK_MANUAL_IMPORT)
                    .build();
        } catch (BusinessException ex) {
            // 配额不足等业务异常：显式降级并保留原因
            writeDegradedCache(platformStorage, normalizeCacheCategory(category), ex.getMessage());
            return HotTopicRefreshResponse.builder()
                    .count(0)
                    .status("degraded")
                    .message(ex.getMessage())
                    .degraded(Boolean.TRUE)
                    .degradedReason(ex.getMessage())
                    .fallbackAvailable(FALLBACK_MANUAL_IMPORT)
                    .build();
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? "refresh_failed" : ex.getMessage();
            writeDegradedCache(platformStorage, normalizeCacheCategory(category), reason);
            return HotTopicRefreshResponse.builder()
                    .count(0)
                    .status("degraded")
                    .message("refresh_failed: " + reason)
                    .degraded(Boolean.TRUE)
                    .degradedReason(reason)
                    .fallbackAvailable(FALLBACK_MANUAL_IMPORT)
                    .build();
        }
    }

    /**
     * 采集主流程。
     * <p>
     * 步骤：缓存命中判定 → 授权平台解析 → 配额 DEBIT（{@code BillableUnit.SEARCH}）
     * → 外部源采集 → 写库（去重 + superseded）→ 写缓存。
     * <b>采集失败时按 ledgerId REFUND</b>，并写"降级"缓存（禁止静默展示陈旧数据）。
     * </p>
     */
    private int collect(String workspaceId, String platformStorage, String category, Long userId) {
        String cacheCategory = normalizeCacheCategory(category);

        // ── 缓存命中：同窗口不重复调用外部源 ────────────────────────────
        if (hotTopicCacheMapper.isCacheValid(platformStorage, cacheCategory)) {
            log.info("Hot topic cache hit: platform={} category={}", platformStorage, cacheCategory);
            return 0;
        }

        // ── 计费前置：BillableUnit.SEARCH（联网搜索）──────────────────
        UUID ledgerId = debitSearchQuota(workspaceId, userId);

        try {
            List<CollectedTopic> collected = fetchFromExternalSource(workspaceId, platformStorage, category);
            OffsetDateTime now = OffsetDateTime.now();
            int count = 0;
            for (CollectedTopic item : collected) {
                if (item == null || item.title() == null || item.title().isBlank()) {
                    continue;
                }
                insertWithDedupe(buildTopicEntry(platformStorage, category, item, now));
                count++;
            }
            writeSuccessCache(platformStorage, cacheCategory, count, now);
            log.info("Hot topic collect wrote {} entries (platform={}, category={})",
                    count, platformStorage, cacheCategory);
            return count;
        } catch (RuntimeException ex) {
            // ── 失败 REFUND（先冲正，再允许重新发起）────────────────────
            refundSearchQuota(ledgerId, "hot_topic_collect_failed: " + ex.getMessage());
            writeDegradedCache(platformStorage, cacheCategory,
                    ex.getMessage() == null ? "hot_topic_source_unavailable" : ex.getMessage());
            throw ex;
        }
    }

    /**
     * 外部源采集：优先使用平台已注册连接器；否则回落联网搜索。
     * <p>
     * 两条路径都产出 {@link CollectedTopic}，写入时统一打上来源标记。
     * </p>
     */
    private List<CollectedTopic> fetchFromExternalSource(String workspaceId, String platformStorage,
                                                         String category) {
        // 1) 已注册连接器（官方接口 / 授权连接器）
        String platformCode = normalizePlatformCode(platformStorage);
        List<PlatformConnector> connectors = connectorRegistry.listByPlatform(platformCode);
        for (PlatformConnector connector : connectors) {
            try {
                List<HotTopicSample> samples = connector.fetchHotTopics(
                        new HotTopicFetchRequest(workspaceId, platformCode, category));
                if (samples != null && !samples.isEmpty()) {
                    List<CollectedTopic> result = new ArrayList<>(samples.size());
                    for (HotTopicSample s : samples) {
                        result.add(new CollectedTopic(
                                s.title(),
                                s.heatScore(),
                                s.sourceUrl(),
                                s.sourceName(),
                                s.sourceType(),
                                s.confidence(),
                                Boolean.TRUE,
                                s.rawPayload()));
                    }
                    return result;
                }
            } catch (Exception ex) {
                log.warn("Connector {} failed to fetch hot topics for {}: {}",
                        connector.providerCode(), platformCode, ex.getMessage());
            }
        }

        // 2) 联网搜索（SEARCH_TOOL，实时）
        if (!webSearchService.isConfigured()) {
            throw new UnsupportedOperationException(
                    "Web search is not configured; please set web-search provider and API key");
        }
        List<Map<String, Object>> rawResults = webSearchService.search(SEARCH_QUERY, 10);
        List<CollectedTopic> result = new ArrayList<>();
        for (Map<String, Object> item : rawResults) {
            result.add(new CollectedTopic(
                    trimToNull(str(item.get("title"))),
                    coerceScore(item.get("heatScore")),
                    trimToNull(str(item.get("url"))),
                    SOURCE_NAME_WEB_SEARCH,
                    HotTopic.SOURCE_TYPE_SEARCH_TOOL,
                    HotTopic.CONFIDENCE_MEDIUM,
                    Boolean.TRUE,
                    toRawPayload(item)));
        }
        return result;
    }

    /**
     * 采集计费 DEBIT。
     * <p>
     * 有 workspace 上下文时走 {@link AtomicQuotaGuard}（可拿到 ledgerId，失败可精确 REFUND）；
     * 无 workspace 上下文（调度器系统发起）时按阶段 1 行为走 {@link UserQuotaService}。
     * </p>
     *
     * @return ledgerId（用于失败时 REFUND）；无 workspace 时返回 null
     */
    private UUID debitSearchQuota(String workspaceId, Long userId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            QuotaCheckResult result = atomicQuotaGuard.tryConsume(
                    workspaceId, BillableUnit.SEARCH.name(), null, "hot-topic-collect");
            if (!result.isOk()) {
                throw new BusinessException(42901, "web_search_quota_exceeded");
            }
            return result.ledgerId();
        }
        if (userId != null) {
            try {
                userQuotaService.consumeWebSearchQuota(userId, null);
            } catch (BusinessException ex) {
                log.warn("Failed to consume web search quota for hot topic collect: {}", ex.getMessage());
            }
        }
        return null;
    }

    /**
     * 采集失败冲正（REFUND）。
     */
    private void refundSearchQuota(UUID ledgerId, String reason) {
        if (ledgerId == null) {
            return;
        }
        try {
            boolean refunded = quotaRefundService.refund(ledgerId, reason);
            log.info("Hot topic collect quota refunded: ledgerId={} refunded={} reason={}",
                    ledgerId, refunded, reason);
        } catch (Exception ex) {
            log.warn("Failed to refund hot topic collect quota ledgerId={}: {}", ledgerId, ex.getMessage());
        }
    }

    /**
     * 平台授权门禁：平台上必须至少有一个"已授权"的连接器实现。
     * <p>
     * 未授权平台一律拒绝 → 403 {@code CONNECTOR_NOT_AUTHORIZED}，不落库。
     * </p>
     */
    private void requireAuthorizedPlatform(String platformCode) {
        if (!connectorAuthorizationService.isPlatformEnabled(platformCode)) {
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
        boolean anyAuthorized = connectorAuthorizationService.listAuthorized(platformCode)
                .stream()
                .anyMatch(auth -> auth.getAuthorized() == null || Boolean.TRUE.equals(auth.getAuthorized()));
        if (!anyAuthorized) {
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 列表
    // ══════════════════════════════════════════════════════════════════

    @Override
    public HotTopicListResponse list(String platform, String category, String sourceType,
                                     Boolean promoted, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);

        String normalizedSourceType = normalizeSourceTypeFilter(sourceType);

        Page<HotTopic> p = new Page<>(safePage, safeSize);
        IPage<HotTopic> result = hotTopicMapper.selectPage(
                p, platform, category, promoted, normalizedSourceType, HotTopic.STATUS_ACTIVE);
        long total = result.getTotal();

        List<HotTopicListItem> items = result.getRecords().stream()
                .map(this::toListItem)
                .toList();

        DegradationState degradation = resolveDegradationState(platform, category);

        return HotTopicListResponse.builder()
                .items(items)
                .total(total)
                .page(safePage)
                .size(safeSize)
                .degraded(degradation.degraded())
                .degradedReason(degradation.reason())
                .fallbackAvailable(degradation.degraded() ? FALLBACK_MANUAL_IMPORT : null)
                .fallbackHint(degradation.degraded()
                        ? "实时热点采集当前不可用，可通过 POST /hot-topics/import 手动导入兜底"
                        : null)
                .build();
    }

    /**
     * 降级状态判定：
     * <ol>
     *   <li>优先看缓存行里的降级标记（最近一次采集失败留下的显式标记）</li>
     *   <li>缓存无记录但联网搜索未配置 → 同样标降级（不允许静默展示陈旧数据）</li>
     * </ol>
     */
    private DegradationState resolveDegradationState(String platform, String category) {
        String effectivePlatform = platform == null || platform.isBlank()
                ? DEFAULT_PLATFORM : platform.trim();
        String cacheCategory = normalizeCacheCategory(category);
        HotTopicCache cache = hotTopicCacheMapper.selectByPlatformAndCategory(effectivePlatform, cacheCategory);
        if (cache != null) {
            Map<String, Object> payload = parseJsonMap(cache.getPayload());
            if (payload != null && Boolean.TRUE.equals(payload.get("degraded"))) {
                Object reason = payload.get("degradedReason");
                return new DegradationState(true,
                        reason == null ? "hot_topic_source_unavailable" : String.valueOf(reason));
            }
            return new DegradationState(false, null);
        }
        if (!webSearchService.isConfigured()) {
            return new DegradationState(true, "web_search_not_configured");
        }
        return new DegradationState(false, null);
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 样本 / 趋势
    // ══════════════════════════════════════════════════════════════════

    @Override
    public HotTopicSamplesResponse samples(Long hotTopicId) {
        HotTopic topic = requireTopic(hotTopicId);
        String normalizedTitle = normalizeTitle(topic.getTitle());
        List<HotTopic> history = hotTopicMapper.selectAllByPlatformAndNormalizedTitle(
                topic.getPlatform(), normalizedTitle);

        List<HotTopicSamplesResponse.Sample> samples = history.stream()
                .map(h -> HotTopicSamplesResponse.Sample.builder()
                        .id(h.getId())
                        .title(h.getTitle())
                        .sourceUrl(h.getSourceUrl())
                        .fetchedAt(h.getFetchedAt())
                        .heatScore(h.getHeatScore())
                        .sourceName(h.getSourceName())
                        .sourceType(h.getSourceType())
                        .confidence(h.getConfidence())
                        .isRealtime(h.getIsRealtime())
                        .status(h.getStatus())
                        .build())
                .toList();

        return HotTopicSamplesResponse.builder()
                .hotTopicId(topic.getId())
                .title(topic.getTitle())
                .platform(topic.getPlatform())
                .normalizedTitle(normalizedTitle)
                .samples(samples)
                .build();
    }

    @Override
    public HotTopicTrendResponse computeTrend(String title, int windowDays) {
        int window = Math.min(Math.max(windowDays, 1), 30);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }

        String normalizedTitle = normalizeTitle(title);
        String platform = resolveTrendPlatform(normalizedTitle);

        // Use the normalized_title column populated by V41 migration
        List<HotTopic> records = hotTopicMapper.selectByTitleAndWindow(
                platform, normalizedTitle, window);

        List<HotTopicTrendPoint> points = records.stream()
                .map(r -> HotTopicTrendPoint.builder()
                        .date(r.getFetchedAt().toLocalDate().format(DATE_FMT))
                        .heatScore(r.getHeatScore())
                        .fetchedAt(r.getFetchedAt())
                        .build())
                .toList();

        String trend;
        int delta = 0;
        int latest = 0;

        // ── 单条（或少于 3 条）快照不得输出"上升" ──────────────────────
        if (points.size() < 3) {
            trend = "new";
        } else {
            int oldest = points.get(0).getHeatScore();
            int newest = points.get(points.size() - 1).getHeatScore();
            delta = newest - oldest;
            latest = newest;

            if (oldest > 0) {
                double ratio = (double) newest / oldest;
                if (ratio > 1.2) {
                    trend = "rising";
                } else if (ratio < 0.8) {
                    trend = "falling";
                } else {
                    trend = "flat";
                }
            } else {
                trend = "flat";
            }
        }

        if (!points.isEmpty()) {
            latest = points.get(points.size() - 1).getHeatScore();
        }

        return HotTopicTrendResponse.builder()
                .title(title)
                .points(points)
                .trend(trend)
                .delta(delta)
                .latest(latest)
                .build();
    }

    /**
     * 推断趋势查询的平台：优先用最近一条同归一化标题记录的 platform，
     * 找不到则回落到默认平台。
     */
    private String resolveTrendPlatform(String normalizedTitle) {
        HotTopic latest = hotTopicMapper.selectLatestByNormalizedTitle(normalizedTitle);
        if (latest != null && latest.getPlatform() != null && !latest.getPlatform().isBlank()) {
            return latest.getPlatform();
        }
        return DEFAULT_PLATFORM;
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. 手动导入
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public HotTopicImportResponse importItems(String workspaceId, Long userId, HotTopicImportRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(CODE_BAD_REQUEST, "hot_topic_import_empty");
        }
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        int index = 0;
        for (HotTopicImportRequest.Item item : request.getItems()) {
            index++;
            String title = item == null ? null : trimToNull(item.getTitle());
            if (title == null) {
                skipped++;
                errors.add("Row " + index + ": title is required");
                continue;
            }
            String platform = item.getPlatform() == null || item.getPlatform().isBlank()
                    ? DEFAULT_PLATFORM : item.getPlatform().trim();
            String sourceName = item.getSourceName() == null || item.getSourceName().isBlank()
                    ? SOURCE_NAME_MANUAL_IMPORT : item.getSourceName().trim();

            CollectedTopic collected = new CollectedTopic(
                    title,
                    item.getHeatScore() == null ? 0 : item.getHeatScore(),
                    trimToNull(item.getSourceUrl()),
                    sourceName,
                    HotTopic.SOURCE_TYPE_MANUAL_IMPORT,
                    HotTopic.CONFIDENCE_LOW,
                    Boolean.FALSE,
                    new LinkedHashMap<>());

            insertWithDedupe(buildTopicEntry(platform, item.getCategory(), collected, now));
            imported++;
        }

        return HotTopicImportResponse.builder()
                .imported(imported)
                .skipped(skipped)
                .errorRows(errors.size())
                .errors(errors)
                .degraded(Boolean.FALSE)
                .fallbackUsed(Boolean.TRUE)
                .build();
    }

    @Override
    @Transactional
    public HotTopicImportResponse importCsv(String workspaceId, Long userId, String filename, String csvText,
                                            List<String> errorSink) {
        List<String> lines = splitLines(csvText == null ? "" : csvText);
        if (lines.size() < 2) {
            if (errorSink != null) {
                errorSink.add("Row 1: CSV_SCHEMA_INVALID: csv_must_have_header_and_data");
            }
            throw new BusinessException(CODE_CSV_SCHEMA_INVALID,
                    "CSV_SCHEMA_INVALID: csv_must_have_header_and_data");
        }
        List<String> headers = splitCsvLine(lines.get(0));

        // ── 表头白名单校验：未知列直接拒绝 ──────────────────────────────
        List<String> unknown = new ArrayList<>();
        for (String header : headers) {
            String h = header.trim();
            if (h.isEmpty()) {
                continue;
            }
            if (!CSV_ALLOWED_COLUMNS.contains(h)) {
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
        OffsetDateTime now = OffsetDateTime.now();

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
                String title = trimToNull(row.get("title"));
                if (title == null) {
                    errors.add("Row " + rowNumber + ": title is required");
                    errorRows++;
                    continue;
                }
                String platform = trimToNull(row.get("platform"));
                if (platform == null) {
                    platform = DEFAULT_PLATFORM;
                }
                String sourceName = trimToNull(row.get("source_name"));
                if (sourceName == null) {
                    sourceName = SOURCE_NAME_MANUAL_IMPORT;
                }
                Integer heatScore = parseIntOrNull(row.get("heat_score"));
                OffsetDateTime fetchedAt = parseDateTime(row.get("fetched_at"));

                CollectedTopic collected = new CollectedTopic(
                        title,
                        heatScore == null ? 0 : heatScore,
                        trimToNull(row.get("source_url")),
                        sourceName,
                        HotTopic.SOURCE_TYPE_MANUAL_IMPORT,
                        HotTopic.CONFIDENCE_LOW,
                        Boolean.FALSE,
                        new LinkedHashMap<>());

                HotTopic entry = buildTopicEntry(platform, trimToNull(row.get("category")), collected,
                        fetchedAt == null ? now : fetchedAt);
                insertWithDedupe(entry);
                imported++;
            } catch (BusinessException be) {
                errorRows++;
                addError(errors, "Row " + rowNumber + ": " + be.getMessage(), 20);
            } catch (Exception ex) {
                errorRows++;
                addError(errors, "Row " + rowNumber + ": " + ex.getMessage(), 20);
            }
        }

        return HotTopicImportResponse.builder()
                .imported(imported)
                .skipped(skipped)
                .errorRows(errorRows)
                .errors(errors)
                .degraded(Boolean.FALSE)
                .fallbackUsed(Boolean.TRUE)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. 选题池提升
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public HotTopicPromoteResponse promoteToTopicPool(String workspaceId, Long userId, Long hotTopicId,
                                                      String category, List<String> tags) {
        if (workspaceId == null || workspaceId.isBlank() || userId == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_and_user_required");
        }
        HotTopic topic = requireTopic(hotTopicId);

        String normalizedTitle = normalizeTitle(topic.getTitle());
        String source = toTopicPoolSource(topic.getSourceType());

        TopicPool existing = topicPoolMapper.selectByDedupeKey(workspaceId, source, normalizedTitle);
        if (existing != null) {
            return HotTopicPromoteResponse.builder()
                    .topicPoolId(existing.getId())
                    .hotTopicId(topic.getId())
                    .duplicate(Boolean.TRUE)
                    .sourceName(existing.getSourceName())
                    .confidence(existing.getConfidence())
                    .sourceFetchedAt(existing.getSourceFetchedAt())
                    .build();
        }

        OffsetDateTime now = OffsetDateTime.now();
        TopicPool pool = new TopicPool();
        pool.setWorkspaceId(workspaceId);
        pool.setTitle(topic.getTitle().trim());
        // ── 去重键语义不变：(workspaceId, source, normalizedTitle) ──────
        pool.setSource(source);
        pool.setHeatScore(topic.getHeatScore() == null ? 0 : topic.getHeatScore());
        pool.setStatus(TopicPool.STATUS_ACTIVE);
        pool.setTags(buildTopicTags(topic, category, tags));
        pool.setTargetPersona(trimToNull(category));
        pool.setCreatedBy(userId);
        pool.setCreatedAt(now);
        pool.setUpdatedAt(now);
        // ── 来源三列透传 ────────────────────────────────────────────
        pool.setSourceName(topic.getSourceName());
        pool.setConfidence(topic.getConfidence());
        pool.setSourceFetchedAt(topic.getFetchedAt());

        topicPoolMapper.insert(pool);

        topic.setPromotedToPool(Boolean.TRUE);
        hotTopicMapper.updateById(topic);

        log.info("Hot topic {} promoted to topic_pool {} (ws={}, source={})",
                topic.getId(), pool.getId(), workspaceId, source);

        return HotTopicPromoteResponse.builder()
                .topicPoolId(pool.getId())
                .hotTopicId(topic.getId())
                .duplicate(Boolean.FALSE)
                .sourceName(pool.getSourceName())
                .confidence(pool.getConfidence())
                .sourceFetchedAt(pool.getSourceFetchedAt())
                .build();
    }

    @Override
    @Transactional
    public int promoteToTopicPool(String workspaceId, Long userId, int maxCount) {
        if (workspaceId == null || userId == null) {
            throw new IllegalArgumentException("workspaceId and userId must not be null");
        }
        int safeMax = maxCount <= 0 ? 10 : maxCount;

        LambdaQueryWrapper<HotTopic> wrapper = new LambdaQueryWrapper<HotTopic>()
                .eq(HotTopic::getPromotedToPool, false)
                .eq(HotTopic::getStatus, HotTopic.STATUS_ACTIVE)
                .orderByDesc(HotTopic::getHeatScore)
                .last("LIMIT " + safeMax);

        List<HotTopic> candidates = hotTopicMapper.selectList(wrapper);
        int promoted = 0;

        for (HotTopic ht : candidates) {
            String normalizedTitle = normalizeTitle(ht.getTitle());
            String source = toTopicPoolSource(ht.getSourceType());

            // Reuse 1D's selectByDedupeKey: skip silently if exists
            TopicPool existing = topicPoolMapper.selectByDedupeKey(
                    workspaceId, source, normalizedTitle);
            if (existing != null) {
                log.debug("Skipping duplicate topic '{}' in workspace {}", ht.getTitle(), workspaceId);
                continue;
            }

            OffsetDateTime now = OffsetDateTime.now();
            TopicPool pool = new TopicPool();
            pool.setWorkspaceId(workspaceId);
            pool.setTitle(ht.getTitle().trim());
            pool.setSource(source);
            pool.setHeatScore(ht.getHeatScore() == null ? 0 : ht.getHeatScore());
            pool.setStatus(TopicPool.STATUS_ACTIVE);
            pool.setTags(buildTopicTags(ht, null, null));
            pool.setCreatedBy(userId);
            pool.setCreatedAt(now);
            pool.setUpdatedAt(now);
            // ── 来源三列透传 ────────────────────────────────────────────
            pool.setSourceName(ht.getSourceName());
            pool.setConfidence(ht.getConfidence());
            pool.setSourceFetchedAt(ht.getFetchedAt());

            topicPoolMapper.insert(pool);

            ht.setPromotedToPool(Boolean.TRUE);
            hotTopicMapper.updateById(ht);

            promoted++;
        }
        log.info("promoteToTopicPool: workspace={}, user={}, promoted={}", workspaceId, userId, promoted);
        return promoted;
    }

    /**
     * 来源类型 → 选题池 source 值。
     * <p>
     * 去重键是 {@code (workspaceId, source, normalizedTitle)}，因此 source 必须
     * <b>按来源类型归一</b>，保证同一热点重复提升仍命中同一条选题。
     * </p>
     */
    private String toTopicPoolSource(String sourceType) {
        if (sourceType == null) {
            return AGENT_SEARCH_SOURCE;
        }
        return switch (sourceType) {
            case HotTopic.SOURCE_TYPE_MANUAL_IMPORT -> MANUAL_IMPORT_SOURCE;
            case HotTopic.SOURCE_TYPE_SEED -> SEED_SOURCE;
            case HotTopic.SOURCE_TYPE_SEARCH_TOOL,
                 HotTopic.SOURCE_TYPE_OFFICIAL_API,
                 HotTopic.SOURCE_TYPE_AUTHORIZED_CONNECTOR -> AGENT_SEARCH_SOURCE;
            default -> AGENT_SEARCH_SOURCE;
        };
    }

    private List<String> buildTopicTags(HotTopic topic, String category, List<String> extraTags) {
        Set<String> tags = new LinkedHashSet<>();
        if (topic.getPlatform() != null) {
            tags.add(topic.getPlatform());
        }
        if (topic.getSourceType() != null) {
            tags.add(topic.getSourceType().toLowerCase(Locale.ROOT));
        }
        if (category != null && !category.isBlank()) {
            tags.add(category.trim());
        }
        if (extraTags != null) {
            for (String t : extraTags) {
                if (t != null && !t.isBlank()) {
                    tags.add(t.trim());
                }
            }
        }
        return new ArrayList<>(tags);
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. 内部工具
    // ══════════════════════════════════════════════════════════════════

    /**
     * 构造待写入的热点实体（统一打来源标记）。
     */
    private HotTopic buildTopicEntry(String platform, String category, CollectedTopic item, OffsetDateTime now) {
        HotTopic entry = new HotTopic();
        entry.setPlatform(platform);
        entry.setCategory(trimToNull(category));
        entry.setTitle(item.title().trim());
        entry.setHeatScore(item.heatScore() == null ? 0 : item.heatScore());
        entry.setFetchedAt(now);
        entry.setRawPayload(item.rawPayload() == null ? new LinkedHashMap<>() : item.rawPayload());
        entry.setPromotedToPool(Boolean.FALSE);
        entry.setSourceName(item.sourceName());
        entry.setSourceType(item.sourceType());
        entry.setConfidence(item.confidence());
        entry.setIsRealtime(item.isRealtime());
        entry.setSourceUrl(item.sourceUrl());
        entry.setDegraded(Boolean.FALSE);
        entry.setDegradedReason(null);
        entry.setStatus(HotTopic.STATUS_ACTIVE);
        entry.setSuggestedAngles(buildSuggestedAngles(
                entry.getTitle(), item.sourceName(), now, snippetOf(item.rawPayload())));
        return entry;
    }

    /**
     * 插入热点并做去重：
     * <ol>
     *   <li>先插入新记录（{@code status='active'}）</li>
     *   <li>再把同平台 + 同归一化标题的<b>其它</b>记录标为 {@code superseded}（历史保留，不删除）</li>
     * </ol>
     */
    private void insertWithDedupe(HotTopic entry) {
        hotTopicMapper.insert(entry);
        String normalizedTitle = normalizeTitle(entry.getTitle());
        int superseded = hotTopicMapper.supersedeOthers(
                entry.getPlatform(), normalizedTitle, entry.getId());
        if (superseded > 0) {
            log.debug("Hot topic dedupe: {} old rows superseded for '{}'",
                    superseded, entry.getTitle());
        }
    }

    /**
     * 构造可做方向（每条必须引用真实标题 + 来源 + 采集时间）。
     * <p>
     * 存为 JSONB：{@code [{"angle":"...","evidence":{"title":"...","sourceName":"...","fetchedAt":"..."}}]}
     * </p>
     */
    private String buildSuggestedAngles(String title, String sourceName, OffsetDateTime fetchedAt, String snippet) {
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("title", title);
            evidence.put("sourceName", sourceName);
            evidence.put("fetchedAt", fetchedAt == null ? null : fetchedAt.toString());

            String angle = snippet != null && !snippet.isBlank()
                    ? "围绕「" + title + "」的搜索摘要做观点拆解与场景化选题"
                    : "围绕「" + title + "」做人群痛点拆解，产出可验证的清单型内容";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("angle", angle);
            item.put("evidence", evidence);

            List<Map<String, Object>> angles = new ArrayList<>();
            angles.add(item);
            return objectMapper.writeValueAsString(angles);
        } catch (Exception ex) {
            log.warn("Failed to build suggested angles: {}", ex.getMessage());
            return null;
        }
    }

    private HotTopicListItem toListItem(HotTopic ht) {
        return HotTopicListItem.builder()
                .id(ht.getId())
                .platform(ht.getPlatform())
                .category(ht.getCategory())
                .title(ht.getTitle())
                .heatScore(ht.getHeatScore())
                .fetchedAt(ht.getFetchedAt())
                .promotedToPool(ht.getPromotedToPool())
                .sourceName(ht.getSourceName())
                .sourceType(ht.getSourceType())
                .confidence(ht.getConfidence())
                .isRealtime(ht.getIsRealtime())
                .sourceUrl(ht.getSourceUrl())
                .degraded(ht.getDegraded())
                .degradedReason(ht.getDegradedReason())
                .status(ht.getStatus())
                .suggestedAngles(parseSuggestedAngles(ht.getSuggestedAngles()))
                .trend(null)
                .build();
    }

    /**
     * 解析 suggested_angles JSONB → DTO 列表。
     */
    private List<HotTopicListItem.HotTopicSuggestedAngle> parseSuggestedAngles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            List<HotTopicListItem.HotTopicSuggestedAngle> result = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                if (item == null) {
                    continue;
                }
                Map<String, Object> ev = item.get("evidence") instanceof Map
                        ? (Map<String, Object>) item.get("evidence")
                        : Map.of();
                result.add(HotTopicListItem.HotTopicSuggestedAngle.builder()
                        .angle(str(item.get("angle")))
                        .evidence(HotTopicListItem.HotTopicSuggestedAngle.Evidence.builder()
                                .title(str(ev.get("title")))
                                .sourceName(str(ev.get("sourceName")))
                                .fetchedAt(parseDateTime(ev.get("fetchedAt")))
                                .build())
                        .build());
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to parse suggested angles: {}", ex.getMessage());
            return List.of();
        }
    }

    private void writeSuccessCache(String platform, String category, int count, OffsetDateTime now) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("degraded", Boolean.FALSE);
            payload.put("degradedReason", null);
            payload.put("count", count);
            payload.put("fetchedAt", now == null ? OffsetDateTime.now().toString() : now.toString());
            hotTopicCacheMapper.upsert(platform, category,
                    objectMapper.writeValueAsString(payload), CACHE_TTL_SECONDS);
        } catch (Exception ex) {
            log.warn("Failed to write hot topic cache: {}", ex.getMessage());
        }
    }

    private void writeDegradedCache(String platform, String category, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("degraded", Boolean.TRUE);
            payload.put("degradedReason", reason == null ? "hot_topic_source_unavailable" : reason);
            payload.put("count", 0);
            payload.put("fetchedAt", OffsetDateTime.now().toString());
            hotTopicCacheMapper.upsert(platform, category,
                    objectMapper.writeValueAsString(payload), CACHE_TTL_SECONDS);
        } catch (Exception ex) {
            log.warn("Failed to write degraded hot topic cache: {}", ex.getMessage());
        }
    }

    private HotTopic requireTopic(Long hotTopicId) {
        if (hotTopicId == null) {
            throw new BusinessException(CODE_HOT_TOPIC_NOT_FOUND, "HOT_TOPIC_NOT_FOUND");
        }
        HotTopic topic = hotTopicMapper.selectById(hotTopicId);
        if (topic == null) {
            throw new BusinessException(CODE_HOT_TOPIC_NOT_FOUND, "HOT_TOPIC_NOT_FOUND");
        }
        return topic;
    }

    /**
     * 平台代码归一（落库用 slug，与既有 {@code hot_topic.platform} 取值一致）。
     */
    private String normalizePlatformStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PLATFORM;
        }
        Optional<PlatformCode> parsed = PlatformCode.tryParse(raw.trim());
        return parsed.map(PlatformCode::getSlug).orElse(raw.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 平台代码归一（授权查询用大写枚举名，与 {@code app.platform.code} 一致）。
     */
    private String normalizePlatformCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlatformCode.XIAOHONGSHU.name();
        }
        Optional<PlatformCode> parsed = PlatformCode.tryParse(raw.trim());
        return parsed.map(PlatformCode::name).orElse(raw.trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeSourceTypeFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SOURCE_TYPES.contains(value)) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_source_type");
        }
        return value;
    }

    private String normalizeCacheCategory(String category) {
        return category == null || category.isBlank() ? HotTopicCache.CATEGORY_ALL : category.trim();
    }

    private String normalizeTitle(String title) {
        return TopicNormalize.normalizeTitle(title);
    }

    private int coerceScore(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0;
    }

    private Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> toRawPayload(Map<String, Object> item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (item != null) {
            payload.putAll(item);
        }
        return payload;
    }

    private String snippetOf(Map<String, Object> rawPayload) {
        if (rawPayload == null) {
            return null;
        }
        Object snippet = rawPayload.get("snippet");
        return snippet == null ? null : String.valueOf(snippet);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
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
            // try other formats
        }
        try {
            return OffsetDateTime.of(java.time.LocalDateTime.parse(text), java.time.ZoneOffset.UTC);
        } catch (Exception ignored) {
            // ignore
        }
        try {
            return OffsetDateTime.of(java.time.LocalDate.parse(text).atStartOfDay(), java.time.ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addError(List<String> errors, String message, int maxErrors) {
        if (errors != null && errors.size() < maxErrors) {
            errors.add(message);
        }
    }

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

    /** 降级状态三元组 */
    private record DegradationState(boolean degraded, String reason) {
    }

    /**
     * 采集到的单条热点（外部源统一中间态）。
     */
    private record CollectedTopic(String title, Integer heatScore, String sourceUrl,
                                  String sourceName, String sourceType, String confidence,
                                  Boolean isRealtime, Map<String, Object> rawPayload) {
    }
}
