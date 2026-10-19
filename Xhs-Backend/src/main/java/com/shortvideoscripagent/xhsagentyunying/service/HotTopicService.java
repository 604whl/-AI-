package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 热搜/热门话题服务。
 */
public interface HotTopicService {

    /**
     * 单次拉取：调用 GetHotTopicsTool 拉新一批，写入 hot_topic 表。
     * 若 GetHotTopicsTool 未注册，抛出 UnsupportedOperationException。
     *
     * @return 本次写入的记录数
     */
    int refreshFromAgentTool();

    /**
     * 将热门话题提升（写入）到 topic_pool。
     */
    int promoteToTopicPool(Long workspaceId, Long userId, int maxCount);

    /**
     * 分页查询热门话题列表。
     */
    PageResult<HotTopicDto> list(String platform, String category, Boolean promoted, int page, int size);

    /**
     * 计算指定话题在时间窗口内的热度趋势。
     *
     * @param title       原始标题（会规范化后匹配）
     * @param windowDays  时间窗口天数（1-30）
     * @return 趋势数据
     */
    HotTopicTrendResponse computeTrend(String title, int windowDays);

    record HotTopicDto(
            Long id,
            String platform,
            String category,
            String title,
            Integer heatScore,
            OffsetDateTime fetchedAt,
            Boolean promotedToPool
    ) {}

    record PageResult<T>(List<T> items, long total, int page, int size) {}
}