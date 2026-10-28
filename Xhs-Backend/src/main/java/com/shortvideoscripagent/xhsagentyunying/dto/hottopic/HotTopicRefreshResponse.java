package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3-9 热点采集刷新响应。
 * <p>
 * {@code taskId} 非空表示已投递到持久化任务队列；为空表示同步执行完成（返回 count / message）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicRefreshResponse {

    /** 任务 ID（异步采集时非空） */
    private String taskId;

    /** 任务状态：queued / succeeded / failed / degraded */
    private String status;

    /** 本次写入条数 */
    private Integer count;

    /** 结果说明 */
    private String message;

    /** 是否处于降级状态 */
    private Boolean degraded;

    /** 降级原因 */
    private String degradedReason;

    /** 兜底路径 */
    private String fallbackAvailable;
}
