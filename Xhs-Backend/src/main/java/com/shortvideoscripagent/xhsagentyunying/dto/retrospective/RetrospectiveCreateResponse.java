package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * S3-7 复盘报告生成结果响应。
 * <p>
 * 报告生成是<b>同步</b>完成的（本批次未接入持久化任务队列），因此直接返回报告 ID 与状态；
 * 状态为 {@code INSUFFICIENT_DATA} 时 {@code degraded=true} 且 {@code degradedReason} 给出降级说明。
 * </p>
 */
@Data
@Builder
public class RetrospectiveCreateResponse {

    /** 报告 ID */
    private UUID reportId;

    /** 任务 ID（本批次同步生成，与报告 ID 相同，保留字段以便后续接入任务队列） */
    private UUID taskId;

    /** 状态：COMPLETED / DEGRADED / INSUFFICIENT_DATA */
    private String status;

    /** 是否降级 */
    private Boolean degraded;

    /** 降级原因 / 数据不足说明 */
    private String degradedReason;

    /** 建议条数 */
    private Integer suggestionCount;

    /** 命中的快照数 */
    private Integer snapshotCount;
}
