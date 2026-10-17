package com.shortvideoscripagent.xhsagentyunying.infra.idempotency;

import lombok.Builder;
import lombok.Data;

/**
 * P0-1: 幂等操作结果。
 *
 * @param taskId   本次请求关联的任务 ID（新建或已有）
 * @param isNew    true=本次新建任务，false=幂等命中返回已有任务
 * @param createdAt 任务创建时间
 */
@Data
@Builder
public class IdempotencyResult {

    private final String taskId;
    private final boolean isNew;
    private final java.time.OffsetDateTime createdAt;
}
