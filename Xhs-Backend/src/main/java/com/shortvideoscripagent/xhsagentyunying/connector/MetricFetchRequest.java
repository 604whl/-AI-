package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;

/**
 * 指标拉取请求。
 *
 * @param workspaceId        工作区 ID
 * @param platformCode       平台代码
 * @param accountId          平台账号 ID
 * @param externalContentId  平台侧内容 ID
 * @param windowStart        统计窗口开始
 * @param windowEnd          统计窗口结束
 */
public record MetricFetchRequest(String workspaceId, String platformCode, String accountId,
                                  String externalContentId, OffsetDateTime windowStart, OffsetDateTime windowEnd) {
}
