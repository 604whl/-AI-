package com.shortvideoscripagent.xhsagentyunying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.web.WebSearchService;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.HotTopic;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TopicPool;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.HotTopicMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TopicPoolMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendPoint;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendResponse;
import com.shortvideoscripagent.xhsagentyunying.service.HotTopicService;
import com.shortvideoscripagent.xhsagentyunying.service.UserQuotaService;
import com.shortvideoscripagent.xhsagentyunying.util.topic.TopicNormalize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hot topic service implementation.
 * Handles refresh-from-agent-tool, promote-to-pool, list, and trend computation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotTopicServiceImpl implements HotTopicService {

    private static final String AGENT_SEARCH_SOURCE = "agent_search";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String SEARCH_QUERY = "小红书 当前热门选题 趋势 内容运营";

    private final HotTopicMapper hotTopicMapper;
    private final TopicPoolMapper topicPoolMapper;
    private final WebSearchService webSearchService;
    private final UserQuotaService userQuotaService;

    // -------------------------------------------------------------------------
    // 1. refreshFromAgentTool
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public int refreshFromAgentTool() {
        if (!webSearchService.isConfigured()) {
            throw new UnsupportedOperationException(
                    "Web search is not configured; please set web-search provider and API key");
        }
        // Use a default userId of 1L for system-initiated refresh
        if (userQuotaService.remainingWebSearchQuota(1L) <= 0) {
            throw new BusinessException(42901, "web_search_quota_exceeded");
        }

        List<Map<String, Object>> rawResults;
        try {
            rawResults = webSearchService.search(SEARCH_QUERY, 10);
        } catch (BusinessException ex) {
            log.warn("WebSearchService.search failed: {}", ex.getMessage());
            throw new RuntimeException("get_hot_topics_failed: " + ex.getMessage());
        }

        OffsetDateTime now = OffsetDateTime.now();
        int count = 0;
        for (Map<String, Object> item : rawResults) {
            // Transform to tool-compatible format (same as GetHotTopicsTool.toTopic)
            Map<String, Object> topic = new LinkedHashMap<>();
            topic.put("tag", item.getOrDefault("title", ""));
            topic.put("direction", item.getOrDefault("snippet", ""));
            topic.put("keywords", List.of());
            topic.put("url", item.getOrDefault("url", ""));

            String title = (String) topic.getOrDefault("tag", "");
            if (title == null || title.isBlank()) {
                continue;
            }

            String platform = HotTopic.PLATFORM_XHS;
            HotTopic existing = findSameDayEntry(platform, null, title.trim(), now.toLocalDate());
            if (existing != null) {
                existing.setHeatScore(coerceScore(topic.get("heatScore")));
                existing.setRawPayload(topic);
                hotTopicMapper.updateById(existing);
            } else {
                HotTopic entry = new HotTopic();
                entry.setPlatform(platform);
                entry.setCategory(null);
                entry.setTitle(title.trim());
                entry.setHeatScore(coerceScore(topic.get("heatScore")));
                entry.setFetchedAt(now);
                entry.setRawPayload(topic);
                entry.setPromotedToPool(false);
                hotTopicMapper.insert(entry);
            }
            count++;
        }

        userQuotaService.consumeWebSearchQuota(1L, null);
        log.info("refreshFromAgentTool wrote {} entries", count);
        return count;
    }

    private HotTopic findSameDayEntry(String platform, String category, String title, java.time.LocalDate date) {
        LambdaQueryWrapper<HotTopic> wrapper = new LambdaQueryWrapper<HotTopic>()
                .eq(HotTopic::getPlatform, platform)
                .eq(category != null, HotTopic::getCategory, category)
                .eq(HotTopic::getTitle, title)
                .apply(date != null, "DATE(fetched_at) = DATE({0})",
                        date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC).toInstant())
                .last("LIMIT 1");
        return hotTopicMapper.selectOne(wrapper);
    }

    private int coerceScore(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // 2. promoteToTopicPool
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public int promoteToTopicPool(Long workspaceId, Long userId, int maxCount) {
        if (workspaceId == null || userId == null) {
            throw new IllegalArgumentException("workspaceId and userId must not be null");
        }
        int safeMax = maxCount <= 0 ? 10 : maxCount;

        LambdaQueryWrapper<HotTopic> wrapper = new LambdaQueryWrapper<HotTopic>()
                .eq(HotTopic::getPromotedToPool, false)
                .orderByDesc(HotTopic::getHeatScore)
                .last("LIMIT " + safeMax);

        List<HotTopic> candidates = hotTopicMapper.selectList(wrapper);
        int promoted = 0;

        for (HotTopic ht : candidates) {
            String normalizedTitle = TopicNormalize.normalizeTitle(ht.getTitle());

            // Reuse 1D's selectByDedupeKey: skip silently if exists
            TopicPool existing = topicPoolMapper.selectByDedupeKey(
                    workspaceId, AGENT_SEARCH_SOURCE, normalizedTitle);
            if (existing != null) {
                log.debug("Skipping duplicate topic '{}' in workspace {}", ht.getTitle(), workspaceId);
                continue;
            }

            TopicPool pool = new TopicPool();
            pool.setWorkspaceId(workspaceId);
            pool.setTitle(ht.getTitle().trim());
            pool.setSource(AGENT_SEARCH_SOURCE);
            pool.setHeatScore(ht.getHeatScore());
            pool.setStatus(TopicPool.STATUS_ACTIVE);
            pool.setTags(List.of("agent_search", ht.getPlatform()));
            pool.setCreatedBy(userId);
            pool.setCreatedAt(OffsetDateTime.now());
            pool.setUpdatedAt(OffsetDateTime.now());

            topicPoolMapper.insert(pool);

            ht.setPromotedToPool(true);
            hotTopicMapper.updateById(ht);

            promoted++;
        }
        log.info("promoteToTopicPool: workspace={}, user={}, promoted={}", workspaceId, userId, promoted);
        return promoted;
    }

    // -------------------------------------------------------------------------
    // 3. list
    // -------------------------------------------------------------------------
    @Override
    public PageResult<HotTopicDto> list(String platform, String category, Boolean promoted, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long offset = (safePage - 1L) * safeSize;

        Page<HotTopic> p = new Page<>(safePage, safeSize);
        IPage<HotTopic> result = hotTopicMapper.selectPage(p, platform, category, promoted, offset, safeSize);
        long total = result.getTotal();

        List<HotTopicDto> items = result.getRecords().stream()
                .map(this::toDto)
                .toList();

        return new PageResult<>(items, total, safePage, safeSize);
    }

    // -------------------------------------------------------------------------
    // 4. computeTrend
    // -------------------------------------------------------------------------
    @Override
    public HotTopicTrendResponse computeTrend(String title, int windowDays) {
        int window = Math.min(Math.max(windowDays, 1), 30);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }

        String platform = HotTopic.PLATFORM_XHS;
        String normalizedTitle = TopicNormalize.normalizeTitle(title);

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

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------
    private HotTopicDto toDto(HotTopic ht) {
        return new HotTopicDto(
                ht.getId(),
                ht.getPlatform(),
                ht.getCategory(),
                ht.getTitle(),
                ht.getHeatScore(),
                ht.getFetchedAt(),
                ht.getPromotedToPool()
        );
    }
}
