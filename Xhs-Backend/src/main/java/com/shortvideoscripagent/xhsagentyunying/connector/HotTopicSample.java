package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 热点选题采样。
 *
 * @param platformCode 平台代码
 * @param category     分类
 * @param title        标题
 * @param heatScore    热度分
 * @param sourceName   来源名称
 * @param sourceType   来源类型
 * @param confidence   置信度
 * @param sourceUrl    来源链接
 * @param fetchedAt    抓取时间
 * @param rawPayload   原始载荷
 */
public record HotTopicSample(String platformCode, String category, String title, Integer heatScore,
                             String sourceName, String sourceType, String confidence, String sourceUrl,
                             OffsetDateTime fetchedAt, Map<String, Object> rawPayload) {
}
