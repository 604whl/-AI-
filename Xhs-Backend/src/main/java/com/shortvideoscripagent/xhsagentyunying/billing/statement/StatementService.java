package com.shortvideoscripagent.xhsagentyunying.billing.statement;

import com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.PricingService;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatement;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatementItem;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementItemMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.pricing.BillingDtos;
import com.shortvideoscripagent.xhsagentyunying.freeze.BillableUnit;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S4-3 账单服务（用量 → 账单转化）。
 * <p>
 * <b>幂等</b>：{@code app.billing_statement.idempotency_key}（workspace_id + 周期起止）上有唯一索引。
 * 生成时<b>先查后写</b>，命中即返回既有账单；并发下由唯一索引兜底（捕获 DuplicateKeyException
 * 后回读）。因此「同工作区 + 同周期」不会产生重复账单。
 * </p>
 * <p>
 * <b>未支付账单不重复扣费</b>：账单生成后状态为 {@code open}，<b>不</b>扣余额；
 * 只有显式调用 {@link #chargeStatement}（支付回调后由 S4-2 触发）才做一次余额扣减，
 * 且用 {@code compareAndSetStatus(open -> charging)} 做状态守卫，重复调用不会重复扣。
 * </p>
 * <p>
 * <b>可复算</b>：每条账单行记录单价快照 + 数量 + 金额，并回填
 * {@code ledgerRef / ledgerRange / ledgerCount} 指回原始 {@code app.usage_ledger} 明细；
 * 台账行本身也带 {@code unit_price / amount / price_version} 快照，
 * 所以账单金额 = Σ(台账 amount)，同时账单行 amount = unit_price × quantity，可双向校验。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    /** 账单状态：未支付 */
    public static final String STATUS_OPEN = "open";
    /** 账单状态：扣费中 */
    public static final String STATUS_CHARGING = "charging";
    /** 账单状态：已支付 */
    public static final String STATUS_PAID = "paid";
    /** 账单状态：作废 */
    public static final String STATUS_VOID = "void";
    /** 账单状态：失败 */
    public static final String STATUS_FAILED = "failed";

    /** 默认币种 */
    public static final String DEFAULT_CURRENCY = "CNY";

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_BAD_REQUEST = 40010;
    private static final int CODE_CONFLICT = 40910;

    private final BillingStatementMapper statementMapper;
    private final BillingStatementItemMapper itemMapper;
    private final UsageLedgerMapper usageLedgerMapper;
    private final PricingService pricingService;
    private final BalanceService balanceService;
    private final AuditWriter auditWriter;
    private final com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay idempotencyReplay;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    // ══════════════════════════════════════════════════════════════════
    // 生成
    // ══════════════════════════════════════════════════════════════════

    /**
     * 生成账单（幂等）。
     * <p>
     * 调用方一般为任务队列 handler（{@code BILLING_STATEMENT}）。
     * </p>
     *
     * @param workspaceId 工作区
     * @param periodStart 周期起
     * @param periodEnd   周期止（开区间）
     * @return 账单（既有或新建）
     */
    @Transactional
    public BillingDtos.StatementDetail generateStatement(String workspaceId,
                                                         OffsetDateTime periodStart,
                                                         OffsetDateTime periodEnd) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_billing_period");
        }
        String idempotencyKey = buildIdempotencyKey(workspaceId, periodStart, periodEnd);

        // 1. 先查后写（幂等重放）
        BillingStatement existing = statementMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            log.info("Statement replay (idempotent): ws={} key={}", workspaceId, idempotencyKey);
            return toDetail(existing);
        }

        // 2. 聚合未出账台账
        List<UsageLedger> ledgers = usageLedgerMapper.selectUnbilled(workspaceId, periodStart, periodEnd);
        if (ledgers.isEmpty()) {
            log.info("No unbilled usage for ws={} period {}~{}, skip statement",
                    workspaceId, periodStart, periodEnd);
            return null;
        }

        // 3. 按计量单位 + 价格版本聚合
        //    注意：按「归一后的计量单位」聚合，而不是台账原始 action。
        //    否则 CHAT / WEB_SEARCH / SEARCH 会各自成行，同一计量单位被拆成多行、
        //    单价语义也不一致（价格表只收录 BillableUnit 枚举名）。
        //
        //    P2-3：必须再按 price_version 分组。价格改版落在同一计费周期内时，
        //    同一计量单位会持有不同版本的单价快照；若合成一行，unitPrice 只能取组内
    //    第一条，行金额 ≠ 单价 × 数量，违反「账单金额可复算」的自述。
        //    按版本拆分后每行都满足 amount = unit_price × quantity。
        Map<String, Map<String, List<UsageLedger>>> byUnitAndVersion = new LinkedHashMap<>();
        for (UsageLedger ledger : ledgers) {
            String unit = canonicalUnitName(ledger.getAction());
            String version = ledger.getPriceVersion() == null ? "-" : String.valueOf(ledger.getPriceVersion());
            byUnitAndVersion.computeIfAbsent(unit, k -> new LinkedHashMap<>())
                    .computeIfAbsent(version, k -> new ArrayList<>())
                    .add(ledger);
        }

        OffsetDateTime now = OffsetDateTime.now();
        BillingStatement statement = new BillingStatement();
        statement.setId(newStatementId());
        statement.setWorkspaceId(workspaceId);
        statement.setPeriodStart(periodStart);
        statement.setPeriodEnd(periodEnd);
        statement.setCurrency(DEFAULT_CURRENCY);
        statement.setStatus(STATUS_OPEN);
        statement.setGeneratedAt(now);
        statement.setIdempotencyKey(idempotencyKey);
        statement.setCreatedAt(now);

        List<BillingStatementItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
        List<UUID> ledgerIds = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<UsageLedger>>> unitEntry : byUnitAndVersion.entrySet()) {
            String unit = unitEntry.getKey();
            for (Map.Entry<String, List<UsageLedger>> versionEntry : unitEntry.getValue().entrySet()) {
                List<UsageLedger> group = versionEntry.getValue();
                if (group.isEmpty()) {
                    continue;
                }
                BigDecimal quantity = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
                BigDecimal amount = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
                BigDecimal unitPrice = null;
                for (UsageLedger ledger : group) {
                    quantity = quantity.add(BigDecimal.valueOf(ledger.getDelta() == null ? 0 : ledger.getDelta()));
                    if (ledger.getAmount() != null) {
                        amount = amount.add(ledger.getAmount());
                    }
                    if (unitPrice == null && ledger.getUnitPrice() != null) {
                        unitPrice = ledger.getUnitPrice();
                    }
                    ledgerIds.add(ledger.getId());
                }
                if (unitPrice == null) {
                    unitPrice = pricingService.unitPriceOf(unit);
                }
                unitPrice = BillingMath.scale(unitPrice);
                // 账单金额以台账快照之和为准（可复算）；数量为净单位数
                amount = BillingMath.scale(amount);

                BillingStatementItem item = new BillingStatementItem();
                // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
                item.setId(UUID.randomUUID());
                item.setStatementId(statement.getId());
                item.setUnit(unit);
                item.setQuantity(quantity);
                item.setUnitPrice(unitPrice);
                item.setAmount(amount);
                item.setLedgerRef(group.get(0).getId() == null ? null : group.get(0).getId().toString());
                item.setLedgerRange(buildLedgerRange(group));
                item.setLedgerCount(group.size());
                item.setCreatedAt(now);
                items.add(item);

                total = total.add(amount);
            }
        }
        statement.setTotalAmount(BillingMath.scale(total));

        // 4. 写账单头（唯一索引兜底幂等）
        try {
            statementMapper.insert(statement);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 唯一冲突后当前事务已被 PostgreSQL 置为 aborted，回读必须放新事务。
            BillingStatement replay = idempotencyReplay.read(
                    () -> statementMapper.selectByIdempotencyKey(idempotencyKey));
            if (replay != null) {
                log.info("Statement created concurrently, replay: ws={} key={}", workspaceId, idempotencyKey);
                return toDetail(replay);
            }
            throw new BusinessException(CODE_CONFLICT, "statement_create_conflict");
        }

        // 5. 写账单行
        for (BillingStatementItem item : items) {
            itemMapper.insert(item);
        }

        // 6. 归集台账（标记 statement_id，幂等：只更新尚未归集的）
        if (!ledgerIds.isEmpty()) {
            usageLedgerMapper.attachStatementBatch(ledgerIds, statement.getId());
        }

        auditWriter.record("BILLING_STATEMENT_GENERATED", "BILLING_STATEMENT", statement.getId(),
                Map.of("workspaceId", workspaceId,
                        "totalAmount", statement.getTotalAmount().toPlainString(),
                        "currency", statement.getCurrency(),
                        "itemCount", String.valueOf(items.size()),
                        "periodStart", String.valueOf(periodStart),
                        "periodEnd", String.valueOf(periodEnd)));

        log.info("Statement generated: ws={} id={} items={} total={}",
                workspaceId, statement.getId(), items.size(), statement.getTotalAmount());
        return toDetail(statement);
    }

    /**
     * 扣费（支付成功后调用）。<b>幂等</b>：状态守卫保证只扣一次。
     * <p>
     * 状态 {@code open → charging} 用条件更新完成；影响行数为 0 表示已被处理 → 直接返回既有状态。
     * </p>
     */
    @Transactional
    public BillingDtos.StatementDetail chargeStatement(String statementId, String reason) {
        BillingStatement statement = statementMapper.selectById(statementId);
        if (statement == null) {
            throw new BusinessException(CODE_NOT_FOUND, "statement_not_found");
        }
        if (STATUS_PAID.equals(statement.getStatus())) {
            log.info("Statement already paid, skip charge: id={}", statementId);
            return toDetail(statement);
        }
        if (STATUS_VOID.equals(statement.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "statement_voided");
        }
        int moved = statementMapper.compareAndSetStatus(statementId, STATUS_OPEN, STATUS_CHARGING);
        if (moved == 0) {
            log.info("Statement charge skipped (state conflict): id={}", statementId);
            return toDetail(statementMapper.selectById(statementId));
        }
        try {
            balanceService.debitBalance(statement.getWorkspaceId(), statement.getTotalAmount(),
                    "BALANCE", statement.getId(), reason == null ? "statement-charge" : reason);
        } catch (RuntimeException ex) {
            // 扣费失败 → 回滚状态，允许重试。
            // P2-4：状态回滚必须走<b>独立事务</b>。当前方法带 @Transactional，
            // 这里的 UPDATE 会在外层事务回滚时一起被撤销 —— 账单会永久卡在 charging，
            // 再也无法重试扣费。
            rollbackChargingToOpen(statementId);
            log.warn("Statement charge failed, state rolled back: id={} err={}", statementId, ex.getMessage());
            throw ex;
        }
        statementMapper.compareAndSetStatus(statementId, STATUS_CHARGING, STATUS_PAID);
        auditWriter.record("BILLING_STATEMENT_CHARGED", "BILLING_STATEMENT", statementId,
                Map.of("workspaceId", statement.getWorkspaceId(),
                        "amount", statement.getTotalAmount().toPlainString(),
                        "reason", reason == null ? "" : reason));
        log.info("Statement charged: id={} amount={}", statementId, statement.getTotalAmount());
        return toDetail(statementMapper.selectById(statementId));
    }

    /**
     * 把 {@code charging} 状态回滚成 {@code open}（独立事务）。
     * <p>
     * P2-4：必须独立事务，否则外层回滚会把这句 UPDATE 一起撤销。
     * </p>
     */
    private void rollbackChargingToOpen(String statementId) {
        try {
            org.springframework.transaction.support.TransactionTemplate requiresNew =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(tx ->
                    statementMapper.compareAndSetStatus(statementId, STATUS_CHARGING, STATUS_OPEN));
        } catch (RuntimeException ex) {
            log.error("Failed to roll back statement {} from charging to open: {}",
                    statementId, ex.getMessage(), ex);
        }
    }

    /**
     * 作废账单（不扣费）。已支付的账单不允许作废。
     */    @Transactional
    public BillingDtos.StatementDetail voidStatement(String statementId, String reason) {
        BillingStatement statement = statementMapper.selectById(statementId);
        if (statement == null) {
            throw new BusinessException(CODE_NOT_FOUND, "statement_not_found");
        }
        if (STATUS_PAID.equals(statement.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "statement_already_paid");
        }
        statementMapper.compareAndSetStatus(statementId, STATUS_OPEN, STATUS_VOID);
        auditWriter.record("BILLING_STATEMENT_VOIDED", "BILLING_STATEMENT", statementId,
                Map.of("workspaceId", statement.getWorkspaceId(),
                        "reason", reason == null ? "" : reason));
        return toDetail(statementMapper.selectById(statementId));
    }

    // ══════════════════════════════════════════════════════════════════
    // 读
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分页查询工作区账单。
     */
    public BillingDtos.PageResult<BillingDtos.StatementSummary> listStatements(String workspaceId,
                                                                               int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) (safePage - 1) * safeSize;
        List<BillingStatement> rows = statementMapper.selectPageByWorkspace(workspaceId, safeSize, offset);
        long total = statementMapper.countByWorkspace(workspaceId);
        List<BillingDtos.StatementSummary> items = rows.stream().map(this::toSummary).toList();
        return new BillingDtos.PageResult<>(items, total, safePage, safeSize);
    }

    /**
     * 读取账单详情（含行项）。
     */
    public BillingDtos.StatementDetail getStatement(String workspaceId, String statementId) {
        BillingStatement statement = statementMapper.selectById(statementId);
        // 跨 workspace 访问按「未找到」处理，不泄露存在性
        if (statement == null || !statement.getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(CODE_NOT_FOUND, "statement_not_found");
        }
        return toDetail(statement);
    }

    /**
     * 按工作区 + 时间段聚合用量与成本（毛利）。
     */
    public BillingDtos.CostUsageView aggregateCostUsage(String workspaceId,
                                                        OffsetDateTime from,
                                                        OffsetDateTime to) {
        OffsetDateTime safeFrom = from == null ? OffsetDateTime.now().minusMonths(1) : from;
        OffsetDateTime safeTo = to == null ? OffsetDateTime.now() : to;

        List<UsageLedgerMapper.CostAggregateRow> rows =
                usageLedgerMapper.aggregateCostByUnit(workspaceId, safeFrom, safeTo);

        BillingDtos.CostUsageView view = new BillingDtos.CostUsageView();
        view.setWorkspaceId(workspaceId);
        view.setFrom(safeFrom);
        view.setTo(safeTo);
        view.setCurrency(DEFAULT_CURRENCY);

        BigDecimal revenue = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
        for (UsageLedgerMapper.CostAggregateRow row : rows) {
            BillingDtos.CostUsageRow dto = new BillingDtos.CostUsageRow();
            dto.setUnit(row.getUnit());
            BillableUnit unit = PricingService.parseUnit(row.getUnit());
            dto.setUnitDisplayName(unit == null ? row.getUnit() : unit.getDisplayName());
            dto.setQuantity(BillingMath.scale(row.getQuantity()));
            dto.setRevenue(BillingMath.scale(row.getRevenue()));
            dto.setCost(BillingMath.scale(row.getCost()));
            dto.setMargin(BillingMath.scale(row.getRevenue().subtract(row.getCost())));
            view.getRows().add(dto);
            revenue = revenue.add(dto.getRevenue());
            cost = cost.add(dto.getCost());
        }
        view.setTotalRevenue(BillingMath.scale(revenue));
        view.setTotalCost(BillingMath.scale(cost));
        view.setTotalMargin(BillingMath.scale(revenue.subtract(cost)));
        return view;
    }

    // ══════════════════════════════════════════════════════════════════
    // private
    // ══════════════════════════════════════════════════════════════════

    /**
     * 幂等键：workspace_id + 周期起止。
     * <p>
     * <b>时区归一</b>：周期起止统一换算到 UTC（{@code OffsetDateTime#toInstant}）后再拼接。
     * 否则同一个时间点用不同偏移表示（如 {@code 2026-01-01T00:00Z} 与
     * {@code 2026-01-01T08:00+08:00}）会生成两个不同的幂等键，
     * 唯一索引拦不住，同一周期可能生成两张账单。
     * </p>
     */
    public static String buildIdempotencyKey(String workspaceId,
                                              OffsetDateTime periodStart,
                                              OffsetDateTime periodEnd) {
        return workspaceId + "|"
                + periodStart.toInstant() + "|"
                + periodEnd.toInstant();
    }

    /**
     * 账单 ID：32 位十六进制（UUID 去横线），与 usage_ledger.statement_id 的 VARCHAR(32) 对齐。
     */
    private static String newStatementId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 台账回溯区间："首个..最后一个"。
     */
    private static String buildLedgerRange(List<UsageLedger> group) {
        if (group.isEmpty()) {
            return null;
        }
        UUID first = group.get(0).getId();
        UUID last = group.get(group.size() - 1).getId();
        if (first == null && last == null) {
            return null;
        }
        return (first == null ? "" : first.toString()) + ".." + (last == null ? "" : last.toString());
    }

    /**
     * 把台账 {@code action} 归一为账单行使用的计量单位名。
     * <p>
     * 台账 action 取值来自业务调用点（{@code CHAT} / {@code BODY} / {@code WEB_SEARCH} …），
     * 而 {@code app.billing_statement_item.unit} 与 {@code app.price_book.unit} 只接受
     * {@code BillableUnit} 枚举名。此处统一归一，保证账单行单价能对上价格表。
     * </p>
     * <p>
     * 无法识别时回退为原始 action（大写），保持「不丢账」——该行金额按 0 计但仍会出现在账单里。
     * </p>
     */
    private static String canonicalUnitName(String action) {
        if (action == null || action.isBlank()) {
            return "UNKNOWN";
        }
        BillableUnit unit = PricingService.canonicalUnit(action);
        if (unit != null) {
            return unit.name();
        }
        BillableUnit direct = PricingService.parseUnit(action);
        if (direct != null) {
            return direct.name();
        }
        return action.trim().toUpperCase();
    }

    private BillingDtos.StatementSummary toSummary(BillingStatement statement) {
        BillingDtos.StatementSummary dto = new BillingDtos.StatementSummary();
        dto.setId(statement.getId());
        dto.setWorkspaceId(statement.getWorkspaceId());
        dto.setPeriodStart(statement.getPeriodStart());
        dto.setPeriodEnd(statement.getPeriodEnd());
        dto.setTotalAmount(statement.getTotalAmount());
        dto.setCurrency(statement.getCurrency());
        dto.setStatus(statement.getStatus());
        dto.setGeneratedAt(statement.getGeneratedAt());
        return dto;
    }

    private BillingDtos.StatementDetail toDetail(BillingStatement statement) {
        if (statement == null) {
            return null;
        }
        BillingDtos.StatementDetail dto = new BillingDtos.StatementDetail();
        dto.setId(statement.getId());
        dto.setWorkspaceId(statement.getWorkspaceId());
        dto.setPeriodStart(statement.getPeriodStart());
        dto.setPeriodEnd(statement.getPeriodEnd());
        dto.setTotalAmount(statement.getTotalAmount());
        dto.setCurrency(statement.getCurrency());
        dto.setStatus(statement.getStatus());
        dto.setGeneratedAt(statement.getGeneratedAt());
        dto.setIdempotencyKey(statement.getIdempotencyKey());

        for (BillingStatementItem item : itemMapper.selectByStatement(statement.getId())) {
            BillingDtos.StatementItemView view = new BillingDtos.StatementItemView();
            view.setId(item.getId() == null ? null : item.getId().toString());
            view.setUnit(item.getUnit());
            BillableUnit unit = PricingService.parseUnit(item.getUnit());
            view.setUnitDisplayName(unit == null ? item.getUnit() : unit.getDisplayName());
            view.setQuantity(item.getQuantity());
            view.setUnitPrice(item.getUnitPrice());
            view.setAmount(item.getAmount());
            view.setLedgerRef(item.getLedgerRef());
            view.setLedgerRange(item.getLedgerRange());
            view.setLedgerCount(item.getLedgerCount());
            dto.getItems().add(view);
        }
        return dto;
    }

    /**
     * 供管理端按工作区聚合（供 AdminMarginService 使用）。
     */
    public List<UsageLedgerMapper.WorkspaceCostRow> aggregateCostByWorkspace(String workspaceId,
                                                                              OffsetDateTime from,
                                                                              OffsetDateTime to) {
        return usageLedgerMapper.aggregateCostByWorkspace(workspaceId, from, to);
    }

    /**
     * 供定时器扫描未出账工作区。
     */
    public List<String> findPendingWorkspaceIds(OffsetDateTime from, OffsetDateTime to, int limit) {
        return statementMapper.selectPendingWorkspaceIds(from, to, limit);
    }
}
