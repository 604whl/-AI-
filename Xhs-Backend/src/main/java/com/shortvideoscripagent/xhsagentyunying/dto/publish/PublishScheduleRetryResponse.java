package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import lombok.Builder;
import lombok.Data;

/**
 * 排期重试响应：{@code {id, publishTaskId, status}}。
 * <p>
 * 重试<b>复用原发布任务的幂等键</b>，不产生新的外部发布。
 */
@Data
@Builder
public class PublishScheduleRetryResponse {

    /** 排期 ID */
    private String id;

    /** 排期当前状态 */
    private String status;

    /** 关联的发布任务 ID */
    private String publishTaskId;

    /** 发布任务当前状态 */
    private String taskStatus;

    /** 复用的幂等键 */
    private String idempotencyKey;
}
