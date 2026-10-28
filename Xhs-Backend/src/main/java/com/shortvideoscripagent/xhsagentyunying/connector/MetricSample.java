package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 指标采样点。
 *
 * @param platformCode       平台代码
 * @param accountId          平台账号 ID
 * @param externalContentId  平台侧内容 ID
 * @param publishedAt        内容发布时间
 * @param metrics            核心指标键值，键集见 {@link MetricKeys#CORE_KEYS}，缺失键写 0
 * @param metricExtra        平台差异化字段（如抖音 completion_rate、B 站 danmaku）
 * @param collectedAt        采集时间
 * @param metricSource       指标来源
 * @param confidence         置信度
 */
public record MetricSample(String platformCode, String accountId, String externalContentId,
                           OffsetDateTime publishedAt, Map<String, Long> metrics,
                           Map<String, Object> metricExtra, OffsetDateTime collectedAt,
                           String metricSource, String confidence) {
}
