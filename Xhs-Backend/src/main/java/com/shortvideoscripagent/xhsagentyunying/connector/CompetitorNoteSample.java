package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 竞品笔记采样。
 *
 * @param platformCode      平台代码
 * @param accountName       竞品账号名
 * @param noteTitle         笔记标题
 * @param noteUrl           笔记链接
 * @param publishedAt       发布时间
 * @param contentStructure  内容结构拆解
 * @param engagement        互动数据
 * @param evidenceText      证据文本片段
 * @param sourceType        来源类型
 */
public record CompetitorNoteSample(String platformCode, String accountName, String noteTitle, String noteUrl,
                                   OffsetDateTime publishedAt, Map<String, Object> contentStructure,
                                   Map<String, Object> engagement, String evidenceText, String sourceType) {
}
