package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 旧版用量日志表 {@code app.usage_log}。
 * <p>
 * <b>与计费台账的关系</b>：本表是阶段 0 的「额度计数」遗留表，
 * 计费口径以 {@code app.usage_ledger} 为准（见 {@code UsageLedger}）。
 * 本表保留是因为 {@code RetentionSweeper} / {@code WorkspaceDestructionService}
 * 仍按租户清理它。
 * </p>
 * <p>
 * <b>{@code originalLogId} 的语义</b>：记录<b>本次用量对应的台账行 id</b>
 * （{@code app.usage_ledger.id}）。写入时即落库，冲正时按它反查回本行，
 * 标记 {@code refund_status = 'REFUNDED'}。
 * <p>
 * <b>为什么不能按 {@code usage_log.id} 反查台账</b>：{@code usage_log.id} 是
 * {@code BIGSERIAL}，而 {@code usage_ledger.ref_id} 是 {@code UUID}，
 * 两者做 {@code WHERE id = '...'} 会触发 PostgreSQL
 * {@code operator does not exist: bigint = character varying}，
 * 该异常会让整个事务进入 aborted 状态，导致冲正回滚 —— 失败任务仍然被计费。
 * </p>
 */
@Data
@TableName("usage_log")
public class UsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String workspaceId;

    private String action;

    private String taskId;

    private OffsetDateTime createdAt;

    /**
     * 退费状态：NONE（默认）/ REFUNDED。
     */
    private String refundStatus;

    /**
     * 退费时间。
     */
    private OffsetDateTime refundedAt;

    /**
     * 本次用量对应的 {@code app.usage_ledger.id}（UUID）。
     */
    private UUID originalLogId;
}
