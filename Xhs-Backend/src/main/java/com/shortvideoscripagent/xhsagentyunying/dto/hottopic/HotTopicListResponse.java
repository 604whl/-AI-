package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * S3-9 热点列表响应。
 * <p>
 * 顶层同时返回<b>降级可见</b>字段：
 * <ul>
 *   <li>{@code degraded} —— 采集源是否已降级</li>
 *   <li>{@code degradedReason} —— 不可用原因（不允许静默展示陈旧数据）</li>
 *   <li>{@code fallbackAvailable} —— 降级后的兜底路径（当前固定 MANUAL_IMPORT）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicListResponse {

    /** 热点条目 */
    private List<HotTopicListItem> items;

    /** 总条数 */
    private Long total;

    private Integer page;

    private Integer size;

    /** 采集源是否处于降级状态 */
    private Boolean degraded;

    /** 降级原因（可读文案，禁止 "unknown"） */
    private String degradedReason;

    /** 降级后可用兜底路径：MANUAL_IMPORT */
    private String fallbackAvailable;

    /** 兜底路径说明（前端直接展示） */
    private String fallbackHint;
}
