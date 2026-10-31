package com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * UsageLedger Mapper。
 * <p>
 * <b>S4-3 扩展</b>：新增按周期聚合 / 出账归集 / 成本聚合的查询。
 * 所有可选参数都显式 {@code CAST(...)}，避免 PostgreSQL 无法推断参数类型。
 * </p>
 */
@Mapper
public interface UsageLedgerMapper extends BaseMapper<UsageLedger> {

    /**
     * 台账 {@code action} → 价格表 {@code unit} 的 SQL 归一表达式。
     * <p>
     * 台账 action 取值来自业务调用点（{@code ANALYSIS} / {@code CHAT} / {@code WEB_SEARCH} /
     * {@code BODY} …），而 {@code app.price_book.unit} 只收录 {@code BillableUnit} 枚举名。
     * 两边不一致时 JOIN 匹配不上 → 成本恒为 0。因此所有涉及 {@code price_book} 的
     * JOIN / GROUP BY 都必须走这个表达式归一。
     * </p>
     * <p>
     * 与 {@code PricingService#canonicalUnit(String)} 保持同一套映射规则
     * （{@code CHAT} / {@code WEB_SEARCH} → {@code SEARCH}）。
     * </p>
     */
    String CANONICAL_UNIT_SQL =
            "CASE UPPER(l.action) "
            + "WHEN 'ANALYSIS' THEN 'ANALYSIS' "
            + "WHEN 'ANALYZE' THEN 'ANALYSIS' "
            + "WHEN 'TITLE' THEN 'TITLE_GENERATION' "
            + "WHEN 'TITLE_GENERATION' THEN 'TITLE_GENERATION' "
            + "WHEN 'TITLES' THEN 'TITLE_GENERATION' "
            + "WHEN 'BODY' THEN 'BODY_GENERATION' "
            + "WHEN 'BODY_GENERATION' THEN 'BODY_GENERATION' "
            + "WHEN 'CONTENT_GENERATION' THEN 'BODY_GENERATION' "
            + "WHEN 'COVER' THEN 'COVER_ANALYSIS' "
            + "WHEN 'COVER_ANALYSIS' THEN 'COVER_ANALYSIS' "
            + "WHEN 'COMPLIANCE_SCAN' THEN 'COMPLIANCE_SCAN' "
            + "WHEN 'COMPLIANCE' THEN 'COMPLIANCE_SCAN' "
            + "WHEN 'EXPORT' THEN 'EXPORT' "
            + "WHEN 'EXPORTS' THEN 'EXPORT' "
            + "WHEN 'EMBEDDING' THEN 'EMBEDDING' "
            + "WHEN 'EMBED' THEN 'EMBEDDING' "
            + "WHEN 'CHAT' THEN 'SEARCH' "
            + "WHEN 'SEARCH' THEN 'SEARCH' "
            + "WHEN 'WEB_SEARCH' THEN 'SEARCH' "
            + "ELSE UPPER(l.action) END";

    /**
     * 按 ledger id 查询状态。
     */
    @Select("SELECT status FROM app.usage_ledger WHERE id = #{id}")
    String selectStatusById(@Param("id") java.util.UUID id);

    /**
     * 查出某工作区 + 业务引用 + 计量动作下<b>仍然生效</b>（{@code status='CONSUMED'}）的台账行。
     * <p>
     * <b>用途</b>：任务重试 / 孤儿任务恢复时避免重复扣费。
     * 同一个 {@code (workspace_id, ref_id, action)} 组合在任务链路上只应有一笔生效扣费，
     * 第二次执行时复用原台账行（不新扣配额、不新写台账）。
     * </p>
     * <p>
     * <b>注意</b>：{@code CHAT} 这类「同一会话多次计费」的动作
     * <b>不能</b>用这个方法去重 —— 同一 session 的多条消息本来就该各扣 1 次。
     * 因此只有任务类动作（{@code ANALYSIS}）才调用它。
     * </p>
     */
    @Select("""
            SELECT * FROM app.usage_ledger
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND ref_id = CAST(#{refId} AS UUID)
              AND action = CAST(#{action} AS VARCHAR)
              AND status = 'CONSUMED'
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            """)
    UsageLedger selectActiveByRefIdAndAction(@Param("workspaceId") String workspaceId,
                                              @Param("refId") java.util.UUID refId,
                                              @Param("action") String action);

    /**
     * 按去重键查出生效台账行。
     * <p>
     * 与 {@link #selectActiveByRefIdAndAction} 的区别：这里按 {@code dedupe_key} 精确匹配，
     * 是 {@link #insertIdempotent} 命中唯一冲突后的回查路径。
     * </p>
     */
    @Select("""
            SELECT * FROM app.usage_ledger
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND dedupe_key = CAST(#{dedupeKey} AS VARCHAR)
              AND status = 'CONSUMED'
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            """)
    UsageLedger selectByDedupeKey(@Param("workspaceId") String workspaceId,
                                   @Param("dedupeKey") String dedupeKey);

    /**
     * <b>幂等插入</b>台账行：命中 {@code uq_usage_ledger_dedupe_key} 唯一冲突时静默跳过。
     * <p>
     * <b>为什么用 {@code ON CONFLICT DO NOTHING} 而不是「先 insert 再 catch 异常」</b>：
     * PostgreSQL 中任何语句失败都会把整个事务置为 <i>aborted</i> 状态，
     * 后续所有语句都会报 {@code current transaction is aborted, commands ignored until
     * end of transaction block}。若靠捕获 {@code DuplicateKeyException} 来处理冲突，
     * 冲突本身已经污染了事务 —— 回查台账行的 {@code SELECT} 同样会失败，
     * 冲正 / 扣费逻辑连带崩掉（这正是本轮 P0-10 的同类问题）。
     * {@code ON CONFLICT DO NOTHING} 不产生错误，事务保持健康。
     * </p>
     * <p>
     * <b>返回行数</b>：1 = 插入成功；0 = 唯一冲突（已有同行），调用方应回查复用。
     * </p>
     */
    @Insert("""
            INSERT INTO app.usage_ledger
                (id, workspace_id, user_id, action, ref_id, delta, status,
                 reason, unit_price, amount, price_version, dedupe_key, created_at)
            VALUES
                (CAST(#{id} AS UUID),
                 CAST(#{workspaceId} AS VARCHAR),
                 #{userId},
                 CAST(#{action} AS VARCHAR),
                 CAST(#{refId} AS UUID),
                 #{delta},
                 CAST(#{status} AS VARCHAR),
                 #{reason},
                 CAST(#{unitPrice} AS NUMERIC(18,4)),
                 CAST(#{amount} AS NUMERIC(18,4)),
                 #{priceVersion},
                 CAST(#{dedupeKey} AS VARCHAR),
                 CAST(#{createdAt} AS TIMESTAMPTZ))
            ON CONFLICT (workspace_id, dedupe_key)
                WHERE dedupe_key IS NOT NULL AND status = 'CONSUMED'
                DO NOTHING
            """)
    int insertIdempotent(@Param("id") java.util.UUID id,
                         @Param("workspaceId") String workspaceId,
                         @Param("userId") Long userId,
                         @Param("action") String action,
                         @Param("refId") java.util.UUID refId,
                         @Param("delta") Integer delta,
                         @Param("status") String status,
                         @Param("reason") String reason,
                         @Param("unitPrice") java.math.BigDecimal unitPrice,
                         @Param("amount") java.math.BigDecimal amount,
                         @Param("priceVersion") Integer priceVersion,
                         @Param("dedupeKey") String dedupeKey,
                         @Param("createdAt") OffsetDateTime createdAt);

    /**
     * 将 ledger 标记为已冲正（REFUNDED）。
     */
    @Update("""
            UPDATE app.usage_ledger
            SET status = 'REFUNDED'
            WHERE id = #{id}
              AND status = 'CONSUMED'
            """)
    int markRefunded(@Param("id") java.util.UUID id);

    /**
     * 写出账归集标记（幂等：只更新尚未归集的记录）。
     *
     * @return 受影响行数
     */
    @Update("""
            UPDATE app.usage_ledger
            SET statement_id = CAST(#{statementId} AS VARCHAR)
            WHERE id = CAST(#{id} AS UUID)
              AND statement_id IS NULL
            """)
    int attachStatement(@Param("id") java.util.UUID id, @Param("statementId") String statementId);

    /**
     * 批量写出账归集标记。
     * <p>
     * <b>分批</b>：PostgreSQL 单条语句最多 65535 个绑定参数，
     * 大批量归集必须分片，否则会报 {@code "too many arguments"}。
     * </p>
     */
    default int attachStatementBatch(List<java.util.UUID> ids, String statementId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        final int CHUNK = 500;
        int total = 0;
        for (int i = 0; i < ids.size(); i += CHUNK) {
            List<java.util.UUID> chunk = ids.subList(i, Math.min(i + CHUNK, ids.size()));
            total += attachStatementChunk(chunk, statementId);
        }
        return total;
    }

    /**
     * 单批写出账归集标记（≤500 条）。
     */
    @Update("""
            UPDATE app.usage_ledger
            SET statement_id = CAST(#{statementId} AS VARCHAR)
            WHERE id IN (<foreach collection="ids" item="id" separator=",">#{id}</foreach>)
              AND statement_id IS NULL
            """)
    int attachStatementChunk(@Param("ids") List<java.util.UUID> ids,
                             @Param("statementId") String statementId);

    /**
     * 查出账周期内、指定计量单位、尚未归集账单的台账明细（按创建时间升序）。
     * <p>
     * 只取 {@code amount IS NOT NULL} 的记录——历史数据没有金额快照，不参与出账，
     * 保证账单金额可由「单价 × 数量」完整复算。
     * </p>
     * <p>
     * <b>只取 {@code status = 'CONSUMED'}</b>：被冲正（{@code REFUNDED}）且尚未出账的
     * 台账行是「已作废的扣费」——用户没有被扣过这笔钱，因此绝不能出现在账单里。
     * 冲正行（{@code delta = -1}）本身是 {@code CONSUMED}，会正常出账为一条负数账单行，
     * 用于抵扣「已出账后又被冲正」的情形。
     * </p>
     */
    @Select(""
            + "SELECT * FROM app.usage_ledger "
            + "WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR) "
            + "AND (" + CANONICAL_UNIT_SQL + ") = CAST(#{unit} AS VARCHAR) "
            + "AND created_at >= CAST(#{from} AS TIMESTAMPTZ) "
            + "AND created_at < CAST(#{to} AS TIMESTAMPTZ) "
            + "AND statement_id IS NULL "
            + "AND amount IS NOT NULL "
            + "AND status = 'CONSUMED' "
            + "AND action <> 'BALANCE' "
            + "ORDER BY created_at ASC, id ASC")
    List<UsageLedger> selectUnbilledByUnit(@Param("workspaceId") String workspaceId,
                                           @Param("unit") String unit,
                                           @Param("from") OffsetDateTime from,
                                           @Param("to") OffsetDateTime to);

    /**
     * 查出账周期内某工作区全部未归集、已定价的台账明细。
     * <p>
     * <b>只取 {@code status = 'CONSUMED'}</b>：被冲正且尚未出账的扣费行是作废行，
     * 用户没被扣过这笔钱，不能出现在账单里（否则「失败任务照样收费」）。
     * </p>
     */
    @Select("""
            SELECT * FROM app.usage_ledger
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND created_at >= CAST(#{from} AS TIMESTAMPTZ)
              AND created_at < CAST(#{to} AS TIMESTAMPTZ)
              AND statement_id IS NULL
              AND amount IS NOT NULL
              AND status = 'CONSUMED'
              AND action <> 'BALANCE'
            ORDER BY created_at ASC, id ASC
            """)
    List<UsageLedger> selectUnbilled(@Param("workspaceId") String workspaceId,
                                      @Param("from") OffsetDateTime from,
                                      @Param("to") OffsetDateTime to);

    /**
     * 查出某工作区尚未出账（{@code statement_id IS NULL}）且仍然生效的台账行。
     * <p>
     * <b>用途</b>：S4-4 退款成功后冲正该工作区未出账的扣费行。
     * 已出账的扣费属于历史账单（不可变凭证），<b>不追溯改写</b>。
     * </p>
     */
    @Select("""
            SELECT * FROM app.usage_ledger
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND statement_id IS NULL
              AND amount IS NOT NULL
              AND status = 'CONSUMED'
              AND action <> 'BALANCE'
            ORDER BY created_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<UsageLedger> selectUnbilledByWorkspace(@Param("workspaceId") String workspaceId,
                                                 @Param("limit") int limit);

    /**
     * 按工作区 + 计量单位聚合某时间段的用量（数量、收入、成本）。
     * <p>
     * 成本侧按 {@code price_version} 回查 {@code app.price_book.cost_per_unit}，
     * 这样即使价格改版，历史成本仍按当时版本计算。
     * </p>
     * <p>
     * <b>只统计 {@code status = 'CONSUMED'} 的行</b>：被冲正（{@code REFUNDED}）的扣费行
     * 对应「用户没被扣钱」的那笔用量，既不该计收入也不该计成本。
     * 冲正行本身是 {@code CONSUMED} 且 {@code delta = -1}，会作为负数正常参与聚合，
     * 从而把「已出账后又被冲正」的金额抵回来。
     * </p>
     */
    @Select(""
            + "SELECT " + CANONICAL_UNIT_SQL + " AS unit, "
            + "COALESCE(SUM(l.delta), 0) AS quantity, "
            + "COALESCE(SUM(l.amount), 0) AS revenue, "
            + "COALESCE(SUM(l.delta * COALESCE(p.cost_per_unit, 0)), 0) AS cost "
            + "FROM app.usage_ledger l "
            + "LEFT JOIN app.price_book p "
            + "ON p.unit = (" + CANONICAL_UNIT_SQL + ") "
            + "AND p.region = 'CN' "
            + "AND p.version = l.price_version "
            + "WHERE l.workspace_id = CAST(#{workspaceId} AS VARCHAR) "
            + "AND l.created_at >= CAST(#{from} AS TIMESTAMPTZ) "
            + "AND l.created_at < CAST(#{to} AS TIMESTAMPTZ) "
            + "AND l.amount IS NOT NULL "
            + "AND l.status = 'CONSUMED' "
            + "AND l.action <> 'BALANCE' "
            + "GROUP BY " + CANONICAL_UNIT_SQL + " "
            + "ORDER BY 1 ASC")
    List<CostAggregateRow> aggregateCostByUnit(@Param("workspaceId") String workspaceId,
                                                @Param("from") OffsetDateTime from,
                                                @Param("to") OffsetDateTime to);

    /**
     * 全平台按工作区聚合用量（管理端成本毛利）。
     * <p>
     * <b>只统计 {@code status = 'CONSUMED'} 的行</b>，理由同
     * {@link #aggregateCostByUnit}：被冲正的扣费行不代表真实收入。
     * </p>
     */
    @Select(""
            + "SELECT l.workspace_id AS workspace_id, "
            + "COALESCE(SUM(l.delta), 0) AS quantity, "
            + "COALESCE(SUM(l.amount), 0) AS revenue, "
            + "COALESCE(SUM(l.delta * COALESCE(p.cost_per_unit, 0)), 0) AS cost "
            + "FROM app.usage_ledger l "
            + "LEFT JOIN app.price_book p "
            + "ON p.unit = (" + CANONICAL_UNIT_SQL + ") "
            + "AND p.region = 'CN' "
            + "AND p.version = l.price_version "
            + "WHERE l.created_at >= CAST(#{from} AS TIMESTAMPTZ) "
            + "AND l.created_at < CAST(#{to} AS TIMESTAMPTZ) "
            + "AND l.amount IS NOT NULL "
            + "AND l.status = 'CONSUMED' "
            + "AND l.action <> 'BALANCE' "
            + "AND (CAST(#{workspaceId} AS VARCHAR) IS NULL OR l.workspace_id = CAST(#{workspaceId} AS VARCHAR)) "
            + "GROUP BY l.workspace_id "
            + "ORDER BY revenue DESC")
    List<WorkspaceCostRow> aggregateCostByWorkspace(@Param("workspaceId") String workspaceId,
                                                     @Param("from") OffsetDateTime from,
                                                     @Param("to") OffsetDateTime to);

    /**
     * 成本聚合行（按计量单位）。
     */
    class CostAggregateRow {
        private String unit;
        private BigDecimal quantity;
        private BigDecimal revenue;
        private BigDecimal cost;

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue(BigDecimal revenue) {
            this.revenue = revenue;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public void setCost(BigDecimal cost) {
            this.cost = cost;
        }
    }

    /**
     * 成本聚合行（按工作区）。
     */
    class WorkspaceCostRow {
        private String workspaceId;
        private BigDecimal quantity;
        private BigDecimal revenue;
        private BigDecimal cost;

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue(BigDecimal revenue) {
            this.revenue = revenue;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public void setCost(BigDecimal cost) {
            this.cost = cost;
        }
    }
}
