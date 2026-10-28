package com.shortvideoscripagent.xhsagentyunying.connector;

/**
 * 热点拉取请求。
 *
 * @param workspaceId  工作区 ID
 * @param platformCode 平台代码
 * @param category     分类，null 表示不限
 */
public record HotTopicFetchRequest(String workspaceId, String platformCode, String category) {
}
