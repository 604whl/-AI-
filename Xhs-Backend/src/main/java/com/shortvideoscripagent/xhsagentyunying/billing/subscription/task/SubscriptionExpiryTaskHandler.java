package com.shortvideoscripagent.xhsagentyunying.billing.subscription.task;

import com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.SubscriptionStatus;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S4-1 订阅到期处理任务。
 * <p>
 * 处理类型：{@code SUBSCRIPTION_EXPIRY}
 * </p>
 * <p>
 * <b>走持久化队列</b>（Redis Streams + DLQ + 指数退避重试），不用 {@code @Async}。
 * 定时器只负责把到期订阅投递到队列，实际状态流转与降级由本 handler 执行。
 * </p>
 * <p>
 * <b>幂等</b>：
 * <ol>
 *   <li>状态流转用条件更新 {@code UPDATE ... WHERE status = ?}，受影响行数为 0 说明已被其他
 *       worker 处理 → 直接返回，不重复降级；</li>
 *   <li>额度写入用 {@code plan_id} 作为乐观锁条件（{@code applyDowngradeQuota}），
 *       重复执行不会重复写额度。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class SubscriptionExpiryTaskHandler implements TaskHandler {

    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionService subscriptionService;
    private final AuditWriter auditWriter;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String taskType() {
        return "SUBSCRIPTION_EXPIRY";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        String subscriptionId = str(payload, "subscriptionId");
        if (subscriptionId == null) {
            throw new IllegalStateException("subscriptionId required in payload");
        }
        Subscription sub = subscriptionMapper.selectById(java.util.UUID.fromString(subscriptionId));
        if (sub == null) {
            log.warn("Subscription not found for expiry task: subscriptionId={}", subscriptionId);
            return;
        }

        String currentStatus = sub.getStatus();
        if (SubscriptionStatus.EXPIRED.getValue().equals(currentStatus)
                || SubscriptionStatus.CANCELED.getValue().equals(currentStatus)
                || SubscriptionStatus.SUSPENDED.getValue().equals(currentStatus)) {
            // 已终态：幂等跳过
            log.debug("Subscription already terminal, skip: id={} status={}", subscriptionId, currentStatus);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int graceDays = sub.getGracePeriodDays() == null ? SubscriptionService.DEFAULT_GRACE_PERIOD_DAYS
                : sub.getGracePeriodDays();
        OffsetDateTime graceDeadline = sub.getCurrentPeriodEnd() == null
                ? now : sub.getCurrentPeriodEnd().plusDays(graceDays);

        if (now.isBefore(graceDeadline)) {
            // ── 宽限期内 → past_due，功能不降级 ──
            int updated = subscriptionMapper.compareAndSetPastDue(sub.getId());
            if (updated == 0) {
                log.debug("Subscription already handled, skip past_due: id={}", subscriptionId);
                return;
            }
            Subscription refreshed = subscriptionMapper.selectById(sub.getId());
            subscriptionService.syncWorkspacePlanState(sub.getWorkspaceId(), refreshed);
            auditWriter.record("SUBSCRIPTION_PAST_DUE", "SUBSCRIPTION", sub.getId().toString(),
                    Map.of("workspaceId", sub.getWorkspaceId(),
                            "gracePeriodDays", String.valueOf(graceDays),
                            "graceDeadline", String.valueOf(graceDeadline),
                            "autoRenew", String.valueOf(refreshed.getAutoRenew())));
            log.info("Subscription marked past_due: id={} ws={} autoRenew={} graceDeadline={}",
                    sub.getId(), sub.getWorkspaceId(), refreshed.getAutoRenew(), graceDeadline);
            return;
        }

        // ── 宽限期满 → 终态处理 ──
        // P1-4：早期实现在这里一律 expire + 降级 FREE，完全没读
        // cancel_at_period_end / pending_plan_id，导致：
        //   1) 「周期末取消」的用户到期后走 past_due → expired，而不是 canceled；
        //   2) 「降级到某套餐（周期末生效）」的 pending_plan_id 永远是死数据。
        // 现在按用户意图分流。
        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(sub.getCancelAtPeriodEnd());
        UUID pendingPlanId = sub.getPendingPlanId();

        if (cancelAtPeriodEnd) {
            // 周期末取消：置 canceled（不降级到 FREE 的 paid 口径由 downgradeToFree 统一收窄额度）
            int canceled = subscriptionMapper.compareAndSetCanceled(sub.getId());
            if (canceled == 0) {
                log.debug("Subscription already terminal by another worker, skip canceled: id={}", subscriptionId);
                return;
            }
            Subscription canceledSub = subscriptionMapper.selectById(sub.getId());
            transactionTemplate.executeWithoutResult(tx ->
                    subscriptionService.downgradeToFree(sub.getWorkspaceId(), canceledSub));
            subscriptionService.syncWorkspacePlanState(sub.getWorkspaceId(), canceledSub);

            auditWriter.record("SUBSCRIPTION_CANCELED", "SUBSCRIPTION", sub.getId().toString(),
                    Map.of("workspaceId", sub.getWorkspaceId(),
                            "reason", "cancel_at_period_end",
                            "downgradedTo", SubscriptionService.FREE_PLAN_CODE));
            log.info("Subscription canceled at period end & downgraded to FREE: id={} ws={}",
                    sub.getId(), sub.getWorkspaceId());
            return;
        }

        if (pendingPlanId != null) {
            // 降级到目标套餐在周期末生效：切 plan_id + 按新套餐写额度 + 顺延一个周期
            Subscription refreshed = subscriptionMapper.selectById(sub.getId());
            if (refreshed != null) {
                subscriptionService.applyPendingPlanAtPeriodEnd(refreshed, pendingPlanId);
                auditWriter.record("SUBSCRIPTION_PLAN_CHANGED", "SUBSCRIPTION", sub.getId().toString(),
                        Map.of("workspaceId", sub.getWorkspaceId(),
                                "pendingPlanId", pendingPlanId.toString(),
                                "effective", "period_end"));
                log.info("Subscription plan changed at period end: id={} ws={} pendingPlan={}",
                        sub.getId(), sub.getWorkspaceId(), pendingPlanId);
            }
            return;
        }

        // ── 宽限期满 → expired + 降级到 FREE（不删数据） ──
        // 注意：这里的状态源可能是 trialing / active / past_due 三种。
        // 正常路径是 active → past_due → expired；但若调度器漏扫（服务停机 / Redis 不可用），
        // 订阅可能跨过整个宽限期仍停留在 active，此时必须仍能被置为 expired，
        // 否则会永久卡在 active 状态、额度不降级。compareAndSetExpired 覆盖三种来源状态。
        int expired = subscriptionMapper.compareAndSetExpired(sub.getId());
        if (expired == 0) {
            log.debug("Subscription already expired by another worker, skip: id={}", subscriptionId);
            return;
        }
        Subscription expiredSub = subscriptionMapper.selectById(sub.getId());
        transactionTemplate.executeWithoutResult(tx ->
                subscriptionService.downgradeToFree(sub.getWorkspaceId(), expiredSub));
        subscriptionService.syncWorkspacePlanState(sub.getWorkspaceId(), expiredSub);

        auditWriter.record("SUBSCRIPTION_EXPIRED", "SUBSCRIPTION", sub.getId().toString(),
                Map.of("workspaceId", sub.getWorkspaceId(),
                        "gracePeriodDays", String.valueOf(graceDays),
                        "downgradedTo", SubscriptionService.FREE_PLAN_CODE,
                        "autoRenew", String.valueOf(expiredSub.getAutoRenew())));
        log.info("Subscription expired & downgraded to FREE: id={} ws={}", sub.getId(), sub.getWorkspaceId());
    }

    private String str(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? null : text;
    }
}
