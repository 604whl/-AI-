package com.shortvideoscripagent.xhsagentyunying.billing.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.BillingReconciliation;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.BillingReconciliationMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProvider;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S4-2 对账服务。
 * <p>
 * <b>对账口径</b>：本地订单 vs 渠道账单。
 * </p>
 * <ul>
 *   <li>{@code local_paid_channel_unpaid}：本地已付，渠道账单里没有 → 可能渠道延迟 / 掉单；</li>
 *   <li>{@code local_unpaid_channel_paid}：渠道已付，本地仍非 paid → 本地漏回调，需补单；</li>
 *   <li>{@code amount_mismatch}：双方都有，但金额不一致。</li>
 * </ul>
 * <p>
 * <b>幂等</b>：差异按 {@code (order_no, provider, diff_type)} 唯一，重跑对账不产生重复记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final int CODE_NOT_FOUND = 40415;
    private static final int CODE_BAD_REQUEST = 40016;
    private static final int CODE_FORBIDDEN = 40315;

    private final BillingReconciliationMapper reconciliationMapper;
    private final SubscriptionOrderMapper orderMapper;
    private final PaymentProviderRegistry providerRegistry;
    private final AuditWriter auditWriter;

    /**
     * 执行一次对账。
     *
     * @param provider 渠道名
     * @param day      对账日期（null = 今天）
     * @return 差异条数
     */
    @Transactional
    public int reconcile(String provider, LocalDate day) {
        PaymentProvider paymentProvider = providerRegistry.require(provider);
        String providerName = paymentProvider.provider();
        LocalDate targetDay = day == null ? LocalDate.now() : day;

        List<PaymentProvider.ProviderBill> bills;
        try {
            bills = paymentProvider.queryDailyBills(targetDay);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to fetch provider bills: provider={} day={} err={}",
                    providerName, targetDay, ex.getMessage());
            throw ex;
        }

        Map<String, PaymentProvider.ProviderBill> billMap = new LinkedHashMap<>();
        for (PaymentProvider.ProviderBill bill : bills) {
            if (bill.orderNo() != null) {
                billMap.put(bill.orderNo(), bill);
            }
        }

        int diffCount = 0;

        // ── 方向 1：本地有、渠道无 ──
        // P3-6：统一用 UTC 边界。库内 created_at 是 TIMESTAMPTZ（PG 存 UTC），
        // 而 systemDefault() 会让非 UTC 部署的「当日窗口」偏移若干小时，
        // 边界订单会被误报成 local_paid_channel_unpaid。
        OffsetDateTime dayStart = targetDay.atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
        List<SubscriptionOrder> localPaid = orderMapper.selectList(
                new LambdaQueryWrapper<SubscriptionOrder>()
                        .eq(SubscriptionOrder::getProvider, providerName)
                        .in(SubscriptionOrder::getStatus, "paid", "fulfilled")
                        .ge(SubscriptionOrder::getCreatedAt, dayStart)
                        .lt(SubscriptionOrder::getCreatedAt, dayStart.plusDays(1)));
        for (SubscriptionOrder order : localPaid) {
            if (billMap.containsKey(order.getOrderNo())) {
                continue;
            }
            recordDiff(order, providerName, order.getStatus(), "absent",
                    ReconciliationDiffType.LOCAL_PAID_CHANNEL_UNPAID.getValue(),
                    BigDecimal.ZERO);
            diffCount++;
        }

        // ── 方向 2：渠道有、本地无 / 状态不一致 / 金额不一致 ──
        for (PaymentProvider.ProviderBill bill : billMap.values()) {
            SubscriptionOrder order = bill.orderNo() == null
                    ? null : orderMapper.selectByOrderNo(bill.orderNo());
            if (order == null) {
                BillingReconciliation record = new BillingReconciliation();
                // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
                record.setId(UUID.randomUUID());
                record.setOrderNo(bill.orderNo());
                record.setProvider(providerName);
                record.setLocalStatus(null);
                record.setProviderStatus(bill.paid() ? "paid" : "pending");
                record.setDiffType(ReconciliationDiffType.LOCAL_UNPAID_CHANNEL_PAID.getValue());
                record.setAmountDiff(bill.amount() == null ? BigDecimal.ZERO : bill.amount());
                record.setStatus("open");
                record.setCreatedAt(OffsetDateTime.now());
                upsertReconciliation(record);
                diffCount++;
                continue;
            }
            boolean localIsPaid = "paid".equals(order.getStatus()) || "fulfilled".equals(order.getStatus());
            if (bill.paid() && !localIsPaid) {
                recordDiff(order, providerName, order.getStatus(), "paid",
                        ReconciliationDiffType.LOCAL_UNPAID_CHANNEL_PAID.getValue(),
                        bill.amount());
                diffCount++;
                continue;
            }
            if (bill.paid() && localIsPaid && order.getAmount() != null && bill.amount() != null
                    && order.getAmount().compareTo(bill.amount()) != 0) {
                recordDiff(order, providerName, order.getStatus(), "paid",
                        ReconciliationDiffType.AMOUNT_MISMATCH.getValue(),
                        bill.amount().subtract(order.getAmount()));
                diffCount++;
            }
        }

        log.info("Reconciliation done: provider={} day={} bills={} diffs={}",
                providerName, targetDay, bills.size(), diffCount);
        return diffCount;
    }

    /**
     * 管理端分页查对账差异。
     */
    public Page<OrderDtos.ReconciliationView> listForAdmin(String status, String diffType,
                                                             String provider, int page, int size) {
        LambdaQueryWrapper<BillingReconciliation> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(BillingReconciliation::getStatus, status.trim().toLowerCase());
        }
        if (diffType != null && !diffType.isBlank()) {
            q.eq(BillingReconciliation::getDiffType, diffType.trim().toLowerCase());
        }
        if (provider != null && !provider.isBlank()) {
            q.eq(BillingReconciliation::getProvider, provider.trim().toLowerCase());
        }
        q.orderByDesc(BillingReconciliation::getCreatedAt);

        Page<BillingReconciliation> p = reconciliationMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<OrderDtos.ReconciliationView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 人工标记对账差异处理状态。
     */
    @Transactional
    public OrderDtos.ReconciliationView resolve(UUID id, OrderDtos.ResolveReconciliationRequest request,
                                                 String resolvedBy) {
        BillingReconciliation record = reconciliationMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(CODE_NOT_FOUND, "reconciliation_not_found");
        }
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "status_required");
        }
        String status = request.getStatus().trim().toLowerCase();
        if (!"resolved".equals(status) && !"ignored".equals(status)) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_reconciliation_status");
        }
        record.setStatus(status);
        record.setResolvedBy(resolvedBy);
        record.setResolvedAt(OffsetDateTime.now());
        record.setResolutionNote(request.getNote());
        reconciliationMapper.updateById(record);

        auditWriter.record("BILLING_RECONCILIATION_RESOLVED", "BILLING_RECONCILIATION",
                id.toString(),
                Map.of("orderNo", record.getOrderNo() == null ? "" : record.getOrderNo(),
                        "diffType", record.getDiffType(),
                        "status", status,
                        "note", request.getNote() == null ? "" : request.getNote()));
        log.info("Reconciliation resolved: id={} orderNo={} status={}", id, record.getOrderNo(), status);
        return toView(record);
    }

    // ── private ─────────────────────────────────────────────────────────

    private void recordDiff(SubscriptionOrder order, String provider, String localStatus,
                            String providerStatus, String diffType, BigDecimal amountDiff) {
        BillingReconciliation record = new BillingReconciliation();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        record.setId(UUID.randomUUID());
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setProvider(provider);
        record.setLocalStatus(localStatus);
        record.setProviderStatus(providerStatus);
        record.setDiffType(diffType);
        record.setAmountDiff(amountDiff == null ? BigDecimal.ZERO : amountDiff);
        record.setStatus("open");
        record.setCreatedAt(OffsetDateTime.now());
        upsertReconciliation(record);
    }

    /**
     * 幂等写入差异记录：同（order_no, provider, diff_type）已存在则更新。
     */
    private void upsertReconciliation(BillingReconciliation record) {
        BillingReconciliation existing = reconciliationMapper.selectByOrderDiff(
                record.getOrderNo(), record.getProvider(), record.getDiffType());
        if (existing != null) {
            existing.setLocalStatus(record.getLocalStatus());
            existing.setProviderStatus(record.getProviderStatus());
            existing.setAmountDiff(record.getAmountDiff());
            reconciliationMapper.updateById(existing);
            return;
        }
        try {
            reconciliationMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            log.debug("Duplicate reconciliation record ignored: orderNo={} diffType={}",
                    record.getOrderNo(), record.getDiffType());
        }
    }

    private OrderDtos.ReconciliationView toView(BillingReconciliation record) {
        if (record == null) {
            return null;
        }
        OrderDtos.ReconciliationView v = new OrderDtos.ReconciliationView();
        v.setId(record.getId() == null ? null : record.getId().toString());
        v.setOrderNo(record.getOrderNo());
        v.setProvider(record.getProvider());
        v.setLocalStatus(record.getLocalStatus());
        v.setProviderStatus(record.getProviderStatus());
        v.setDiffType(record.getDiffType());
        v.setAmountDiff(record.getAmountDiff());
        v.setStatus(record.getStatus());
        v.setResolvedBy(record.getResolvedBy());
        v.setResolvedAt(record.getResolvedAt());
        v.setResolutionNote(record.getResolutionNote());
        v.setCreatedAt(record.getCreatedAt());
        return v;
    }
}
