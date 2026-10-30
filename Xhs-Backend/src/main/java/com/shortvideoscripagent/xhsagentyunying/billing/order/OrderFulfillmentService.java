package com.shortvideoscripagent.xhsagentyunying.billing.order;

import com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.PlanService;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingPeriod;
import com.shortvideoscripagent.xhsagentyunying.billing.support.PlanSnapshot;
import com.shortvideoscripagent.xhsagentyunying.billing.support.SubscriptionStatus;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S4-2 订单履约服务。
 * <p>
 * 支付成功后的履约动作：<b>开订阅 + 写额度</b>。
 * </p>
 * <p>
 * <b>幂等</b>：只有订单处于 {@code paid} 时才会真正履约；
 * 重复执行时若已存在同套餐的生效订阅，则只顺延周期，不重复开订阅、不重复写额度。
 * </p>
 * <p>
 * <b>失败语义</b>：履约失败时抛异常 → 任务队列重试 → 超过次数进 DLQ；
 * <b>订单状态保持 {@code paid}</b>，不丢单。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final SubscriptionOrderMapper orderMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final BalanceService balanceService;
    private final AuditWriter auditWriter;

    /**
     * 履约一个订单。
     *
     * @return true = 本次真正执行了履约；false = 已履约过（幂等跳过）
     */
    @Transactional
    public boolean fulfill(SubscriptionOrder order) {
        if (order == null) {
            throw new BusinessException(40412, "order_not_found");
        }
        if (!"paid".equals(order.getStatus())) {
            log.warn("Fulfillment skipped: orderNo={} status={}", order.getOrderNo(), order.getStatus());
            return false;
        }
        if (order.getPlanId() == null) {
            // 按量充值订单（usage）：支付成功必须给工作区余额入账，
            // 否则用户「付了钱用不了量」（余额恒为 0，永久余额不足）。
            // creditBalance 自带 orderNo 幂等检查，重复履约不会重复入账。
            log.info("Usage order fulfillment: crediting balance, orderNo={} amount={}",
                    order.getOrderNo(), order.getAmount());
            balanceService.creditBalance(order.getWorkspaceId(), order.getAmount(),
                    order.getCurrency(), order.getOrderNo(), "usage_topup");

            boolean moved = orderMapper.compareAndSetFulfilled(order.getId()) > 0;
            if (moved) {
                auditWriter.record("ORDER_FULFILLED", "SUBSCRIPTION_ORDER", order.getId().toString(),
                        Map.of("workspaceId", order.getWorkspaceId(),
                                "orderNo", order.getOrderNo(),
                                "orderType", "usage",
                                "amount", order.getAmount() == null ? "0" : order.getAmount().toPlainString()));
            }
            return moved;
        }

        SubscriptionPlan plan = planService.requirePlan(order.getPlanId());
        OffsetDateTime now = OffsetDateTime.now();
        BillingPeriod period = BillingPeriod.parse(plan.getBillingPeriod());

        Subscription live = subscriptionMapper.selectActiveByWorkspace(order.getWorkspaceId());
        if (live != null) {
            // 已有生效订阅：续费或换套餐，都<b>复用这一行</b>。
            // 注意：app.subscription 上有部分唯一索引
            // uq_subscription_workspace_active (workspace_id) WHERE status IN ('trialing','active','past_due')，
            // 同工作区不允许并存两条生效订阅 —— 早期实现在这里 insert 新行会撞唯一索引。
            // P3-1：quantity = N 时按 N 个计费周期顺延（早期只顺延 1 个月，付 3 个月只开 1 个月）。
            int months = period.getMonths() * Math.max(order.getQuantity() == null ? 1 : order.getQuantity(), 1);
            OffsetDateTime base = live.getCurrentPeriodEnd() != null && live.getCurrentPeriodEnd().isAfter(now)
                    ? live.getCurrentPeriodEnd() : now;
            boolean samePlan = live.getPlanId() != null && live.getPlanId().equals(plan.getId());

            live.setCurrentPeriodStart(live.getCurrentPeriodStart() == null ? now : live.getCurrentPeriodStart());
            live.setCurrentPeriodEnd(base.plusMonths(months));
            live.setUpdatedAt(now);
            if (!samePlan) {
                // 换套餐：切到新套餐快照（价格/额度按新套餐）
                live.setPlanId(plan.getId());
                live.setPlanVersionSnapshot(PlanSnapshot.of(plan));
                live.setCancelAtPeriodEnd(false);
                live.setPendingPlanId(null);
            }
            subscriptionMapper.updateById(live);
            subscriptionService.applyPlanQuota(order.getWorkspaceId(), plan,
                    live.getCurrentPeriodStart(), live.getCurrentPeriodEnd());
            subscriptionService.syncWorkspacePlanState(order.getWorkspaceId(), live);
        } else {
            // 首次开通
            Subscription sub = new Subscription();
            // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
            sub.setId(UUID.randomUUID());
            sub.setWorkspaceId(order.getWorkspaceId());
            sub.setPlanId(plan.getId());
            sub.setPlanVersionSnapshot(PlanSnapshot.of(plan));
            sub.setStatus(SubscriptionStatus.ACTIVE.getValue());
            int months = period.getMonths() * Math.max(order.getQuantity() == null ? 1 : order.getQuantity(), 1);
            sub.setCurrentPeriodStart(now);
            sub.setCurrentPeriodEnd(now.plusMonths(months));
            sub.setTrialEnd(null);
            sub.setCancelAtPeriodEnd(false);
            sub.setPendingPlanId(null);
            sub.setAutoRenew(true);
            sub.setGracePeriodDays(SubscriptionService.DEFAULT_GRACE_PERIOD_DAYS);
            sub.setSubscribedAt(now);
            sub.setCanceledAt(null);
            sub.setIdempotencyKey(order.getOrderNo());
            sub.setCreatedAt(now);
            sub.setUpdatedAt(now);
            subscriptionMapper.insert(sub);
            subscriptionService.applyPlanQuota(order.getWorkspaceId(), plan,
                    sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());
            subscriptionService.syncWorkspacePlanState(order.getWorkspaceId(), sub);
        }

        boolean moved = orderMapper.compareAndSetFulfilled(order.getId()) > 0;
        auditWriter.record("ORDER_FULFILLED", "SUBSCRIPTION_ORDER", order.getId().toString(),
                Map.of("workspaceId", order.getWorkspaceId(),
                        "orderNo", order.getOrderNo(),
                        "planCode", plan.getCode(),
                        "planVersion", String.valueOf(plan.getVersion())));
        log.info("Order fulfilled: orderNo={} ws={} plan={} v{}",
                order.getOrderNo(), order.getWorkspaceId(), plan.getCode(), plan.getVersion());
        return moved;
    }

    /**
     * 按订单 ID 履约。
     */
    @Transactional
    public boolean fulfillById(UUID orderId) {
        SubscriptionOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(40412, "order_not_found");
        }
        return fulfill(order);
    }
}
