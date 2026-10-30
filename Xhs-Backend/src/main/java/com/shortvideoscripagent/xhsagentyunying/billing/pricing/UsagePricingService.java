package com.shortvideoscripagent.xhsagentyunying.billing.pricing;

import com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.entity.PriceBook;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.freeze.BillableEvent;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-3 用量计价服务：把一次用量与价格表快照绑定，写入 {@code app.usage_ledger}。
 * <p>
 * <b>账单可复算的关键</b>：扣费时把当时的 {@code unitPrice / amount / priceVersion}
 * 一起写进台账行，之后即使价格表改版（插入新版本行），
 * 历史台账的金额也不会变化，账单金额 = Σ(台账 amount) 始终可复算。
 * </p>
 * <p>
 * <b>delta 语义不变</b>（沿用阶段 0 冻结口径）：
 * <ul>
 *   <li>{@code DEBIT}      → delta = +1（扣减 1 个计费单元）</li>
 *   <li>{@code REFUND}     → delta = -1（冲正）</li>
 *   <li>{@code WRITE_OFF}  → delta = 0</li>
 *   <li>{@code ADJUSTMENT} → delta = ±1，必须带 reason</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsagePricingService {

    /** 入参错误（全局异常处理映射为 400） */
    private static final int CODE_BAD_REQUEST = 40010;

    private final PricingService pricingService;
    private final UsageLedgerMapper usageLedgerMapper;

    /**
     * 记一笔<b>已定价</b>的用量（DEBIT / REFUND / WRITE_OFF / ADJUSTMENT）。
     *
     * @param workspaceId 工作区
     * @param unit        计量单位（BillableUnit 枚举名）
     * @param event       计费事件类型
     * @param refId       关联任务 / 会话 ID
     * @param userId      用户 ID，可空
     * @param reason      原因
     * @param provider    供应商 / 模型来源，可空
     * @return 写入的台账行（含价格快照）
     */
    @Transactional
    public UsageLedger recordUsage(String workspaceId, String unit, BillableEvent event,
                                   UUID refId, Long userId, String reason, String provider) {
        BillableEvent resolved = event == null ? BillableEvent.DEBIT : event;
        int delta = switch (resolved) {
            case DEBIT -> 1;
            case REFUND -> -1;
            case WRITE_OFF -> 0;
            case ADJUSTMENT -> -1;
        };
        if (resolved == BillableEvent.ADJUSTMENT) {
            // 调整必须带 reason（BILLING-RULES.md 强制规则）
            // P3-7：用 BusinessException 而不是 IllegalArgumentException ——
            // 后者会被全局异常处理当成未知异常返回 500，而这是入参错误，应返回 400。
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(CODE_BAD_REQUEST, "reason_required_for_adjustment");
            }
        }

        PriceBook price = pricingService.resolvePrice(unit, OffsetDateTime.now());
        BigDecimal unitPrice = price == null || price.getUnitPrice() == null
                ? BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP)
                : BillingMath.scale(price.getUnitPrice());

        UsageLedger ledger = new UsageLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setWorkspaceId(workspaceId);
        ledger.setUserId(userId);
        ledger.setAction(unit);
        ledger.setRefId(refId);
        ledger.setDelta(delta);
        ledger.setStatus("CONSUMED");
        ledger.setReason(reason);
        ledger.setUnitPrice(unitPrice);
        ledger.setAmount(BillingMath.scale(unitPrice.multiply(BigDecimal.valueOf(delta))));
        ledger.setPriceVersion(price == null ? null : price.getVersion());
        ledger.setProvider(provider);
        ledger.setCreatedAt(OffsetDateTime.now());
        usageLedgerMapper.insert(ledger);

        log.debug("Usage priced: ws={} unit={} event={} delta={} amount={} priceVersion={}",
                workspaceId, unit, resolved, delta, ledger.getAmount(), ledger.getPriceVersion());
        return ledger;
    }

    /**
     * 给一条<b>已有</b>台账补价格快照（用于历史数据按当前价补齐）。
     * <p>
     * 只补 {@code unit_price / amount / price_version}，<b>不改 delta 语义</b>；
     * 幂等：已有 amount 的行不会被覆盖。
     * </p>
     *
     * @return 受影响行数；0 = 已有快照或行不存在
     */
    @Transactional
    public int attachPriceSnapshot(UUID ledgerId) {
        UsageLedger ledger = usageLedgerMapper.selectById(ledgerId);
        if (ledger == null || ledger.getAmount() != null) {
            return 0;
        }
        PriceBook price = pricingService.resolvePrice(ledger.getAction(), ledger.getCreatedAt());
        if (price == null) {
            return 0;
        }
        BigDecimal unitPrice = BillingMath.scale(price.getUnitPrice());
        UsageLedger update = new UsageLedger();
        update.setId(ledgerId);
        update.setUnitPrice(unitPrice);
        update.setAmount(BillingMath.scale(unitPrice.multiply(
                BigDecimal.valueOf(ledger.getDelta() == null ? 0 : ledger.getDelta()))));
        update.setPriceVersion(price.getVersion());
        return usageLedgerMapper.updateById(update);
    }
}
