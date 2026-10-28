package com.shortvideoscripagent.xhsagentyunying.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一指标键集常量。
 * <p>
 * 固定 10 个核心指标键，顺序不可变；采集到的数据缺哪个就写 0，<b>不得省略</b>。
 * <p>
 * 平台差异化字段（抖音 completion_rate、B 站 danmaku 等）统一放进
 * {@link MetricSample#metricExtra()}，不得污染核心键集。
 */
public final class MetricKeys {

    /** 曝光量 */
    public static final String IMPRESSIONS = "impressions";

    /** 播放量 */
    public static final String PLAYS = "plays";

    /** 点击量 */
    public static final String CLICKS = "clicks";

    /** 点赞数 */
    public static final String LIKES = "likes";

    /** 收藏数 */
    public static final String COLLECTS = "collects";

    /** 评论数 */
    public static final String COMMENTS = "comments";

    /** 分享数 */
    public static final String SHARES = "shares";

    /** 涨粉数 */
    public static final String FOLLOWS = "follows";

    /** 私信咨询数 */
    public static final String DM_INQUIRIES = "dm_inquiries";

    /** 成交转化数 */
    public static final String CONVERSIONS = "conversions";

    /** 核心指标键集，顺序固定，恰好 10 个 */
    public static final List<String> CORE_KEYS = List.of(
            IMPRESSIONS,
            PLAYS,
            CLICKS,
            LIKES,
            COLLECTS,
            COMMENTS,
            SHARES,
            FOLLOWS,
            DM_INQUIRIES,
            CONVERSIONS
    );

    private MetricKeys() {
    }

    /**
     * 返回包含全部 10 个核心键、值均为 0 的有序 Map。
     */
    public static Map<String, Long> emptyMetrics() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String key : CORE_KEYS) {
            map.put(key, 0L);
        }
        return Collections.unmodifiableMap(map);
    }
}
