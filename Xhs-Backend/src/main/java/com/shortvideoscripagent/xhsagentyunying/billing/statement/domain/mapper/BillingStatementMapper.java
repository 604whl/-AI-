package com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * S4-3 账单头 Mapper。
 */
@Mapper
public interface BillingStatementMapper extends BaseMapper<BillingStatement> {

    /**
     * 按幂等键取账单（生成前先查，命中即幂等重放）。
     */
    @Select("""
            SELECT * FROM app.billing_statement
            WHERE idempotency_key = CAST(#{idempotencyKey} AS VARCHAR)
            LIMIT 1
            """)
    BillingStatement selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 取工作区某周期的账单。
     */
    @Select("""
            SELECT * FROM app.billing_statement
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND period_start = CAST(#{periodStart} AS TIMESTAMPTZ)
              AND period_end = CAST(#{periodEnd} AS TIMESTAMPTZ)
            LIMIT 1
            """)
    BillingStatement selectByPeriod(@Param("workspaceId") String workspaceId,
                                    @Param("periodStart") OffsetDateTime periodStart,
                                    @Param("periodEnd") OffsetDateTime periodEnd);

    /**
     * 列出工作区账单（分页，按周期倒序）。
     */
    @Select("""
            SELECT * FROM app.billing_statement
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
            ORDER BY period_start DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<BillingStatement> selectPageByWorkspace(@Param("workspaceId") String workspaceId,
                                                  @Param("limit") int limit,
                                                  @Param("offset") long offset);

    /**
     * 统计工作区账单总数。
     */
    @Select("""
            SELECT COUNT(*) FROM app.billing_statement
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
            """)
    long countByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 按工作区 + 周期范围聚合账单金额（成本毛利视图用）。
     */
    @Select("""
            SELECT COALESCE(SUM(total_amount), 0) FROM app.billing_statement
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND period_start >= CAST(#{from} AS TIMESTAMPTZ)
              AND period_start < CAST(#{to} AS TIMESTAMPTZ)
            """)
    java.math.BigDecimal sumAmountByWorkspaceAndPeriod(@Param("workspaceId") String workspaceId,
                                                        @Param("from") OffsetDateTime from,
                                                        @Param("to") OffsetDateTime to);

    /**
     * 原子状态流转：仅当当前状态符合预期时更新。
     *
     * @return 受影响行数；0 = 已被其他消费者处理（幂等跳过）
     */
    @Update("""
            UPDATE app.billing_statement
            SET status = #{toStatus}
            WHERE id = CAST(#{id} AS VARCHAR)
              AND status = #{fromStatus}
            """)
    int compareAndSetStatus(@Param("id") String id,
                            @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus);

    /**
     * 扫描待出账的工作区 + 周期（定时器用）。
     */
    @Select("""
            SELECT DISTINCT workspace_id FROM app.usage_ledger
            WHERE statement_id IS NULL
              AND amount IS NOT NULL
              AND action <> 'BALANCE'
              AND created_at >= CAST(#{from} AS TIMESTAMPTZ)
              AND created_at < CAST(#{to} AS TIMESTAMPTZ)
            LIMIT #{limit}
            """)
    List<String> selectPendingWorkspaceIds(@Param("from") OffsetDateTime from,
                                           @Param("to") OffsetDateTime to,
                                           @Param("limit") int limit);

    /**
     * 扫描订阅周期已结束、尚未出账的工作区 + 周期（定时器用）。
     * <p>
     * 按订阅周期（{@code app.subscription.current_period_start/end}）聚合，
     * 因此周期已过但还没出账的订阅会被捞出来补出账单。
     * </p>
     * <p>
     * 注意：本查询放在本 Mapper 里而不是改 {@code SubscriptionMapper}——
     * S4-1 的 Mapper 归其所有者，本任务不改动他人文件。
     * </p>
     */
    @Select("""
            SELECT s.workspace_id AS workspace_id,
                   s.current_period_start AS period_start,
                   s.current_period_end AS period_end
            FROM app.subscription s
            WHERE s.current_period_start IS NOT NULL
              AND s.current_period_end IS NOT NULL
              AND s.current_period_end <= CAST(#{now} AS TIMESTAMPTZ)
              AND s.status IN ('trialing', 'active', 'past_due', 'canceled')
            ORDER BY s.current_period_end ASC
            LIMIT #{limit}
            """)
    List<SubscriptionPeriodRow> selectEndedSubscriptionPeriods(@Param("now") OffsetDateTime now,
                                                                @Param("limit") int limit);

    /**
     * 订阅周期行（定时器投递账单任务用）。
     */
    class SubscriptionPeriodRow {
        private String workspaceId;
        private OffsetDateTime periodStart;
        private OffsetDateTime periodEnd;

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public OffsetDateTime getPeriodStart() {
            return periodStart;
        }

        public void setPeriodStart(OffsetDateTime periodStart) {
            this.periodStart = periodStart;
        }

        public OffsetDateTime getPeriodEnd() {
            return periodEnd;
        }

        public void setPeriodEnd(OffsetDateTime periodEnd) {
            this.periodEnd = periodEnd;
        }
    }
}
