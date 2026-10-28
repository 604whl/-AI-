package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;

/**
 * 竞品笔记拉取请求。
 *
 * @param workspaceId  工作区 ID
 * @param platformCode 平台代码
 * @param accountName  竞品账号名
 * @param windowStart  窗口开始
 * @param windowEnd    窗口结束
 */
public record CompetitorFetchRequest(String workspaceId, String platformCode, String accountName,
                                      OffsetDateTime windowStart, OffsetDateTime windowEnd) {
}
