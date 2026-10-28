package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 发布请求。
 *
 * @param workspaceId  工作区 ID（VARCHAR(32)，形如 ws_xxx）
 * @param assetId      待发布内容资产 ID
 * @param platformCode 平台代码
 * @param accountId    平台账号 ID
 * @param title        标题
 * @param body         正文
 * @param scheduledAt  计划发布时间，null 表示立即发布
 * @param timezone     时区
 * @param options      连接器私有选项
 */
public record PublishRequest(String workspaceId, UUID assetId, String platformCode, String accountId,
                              String title, String body, OffsetDateTime scheduledAt, String timezone,
                              Map<String, Object> options) {
}
