package com.shortvideoscripagent.xhsagentyunying.billing.subscription.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S4-1 订阅到期扫描定时器。
 * <p>
 * 每 <b>30 分钟</b>扫描一次，把到期订阅（{@code trialing / active} 且
 * {@code current_period_end < now}）投递到持久化任务队列（{@code SUBSCRIPTION_EXPIRY}）。
 * <b>定时器本身不做状态变更</b>，只负责投递 —— 实际降级由 {@link SubscriptionExpiryTaskHandler} 执行，
 * 从而获得重试与 DLQ 能力。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class SubscriptionExpiryScheduler {

    private static final String TASK_TYPE = "SUBSCRIPTION_EXPIRY";
    private static final int SCAN_LIMIT = 200;

    private final SubscriptionMapper subscriptionMapper;
    private final TaskQueueProducer taskQueueProducer;
    private final ObjectMapper objectMapper;

    /**
     * 每 30 分钟触发一次。
     */
    @Scheduled(initialDelay = 180_000, fixedRate = 30 * 60 * 1000)
    public void scanExpiredSubscriptions() {
        log.info("[SubscriptionExpiryScheduler] 开始扫描到期订阅...");
        int dispatched = 0;
        try {
            List<Subscription> expired = subscriptionMapper.selectExpiredCandidates(
                    OffsetDateTime.now(), SCAN_LIMIT);
            for (Subscription sub : expired) {
                if (dispatch(sub)) {
                    dispatched++;
                }
            }
            // 宽限期已满的 past_due 订阅：状态机从 active → past_due → expired 的第二步，
            // 必须单独扫描，否则 past_due 订阅永远不会被置为 expired。
            List<Subscription> graceExpired = subscriptionMapper.selectGraceExpiredCandidates(
                    OffsetDateTime.now(), SCAN_LIMIT);
            for (Subscription sub : graceExpired) {
                if (dispatch(sub)) {
                    dispatched++;
                }
            }
            log.info("[SubscriptionExpiryScheduler] 到期订阅投递完成，共 {} 个", dispatched);
        } catch (Exception e) {
            log.error("[SubscriptionExpiryScheduler] 扫描到期订阅失败: {}", e.getMessage(), e);
        }
    }

    private boolean dispatch(Subscription sub) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("subscriptionId", sub.getId() == null ? null : sub.getId().toString());
            payload.put("workspaceId", sub.getWorkspaceId());
            payload.put("currentPeriodEnd", String.valueOf(sub.getCurrentPeriodEnd()));
            payload.put("autoRenew", sub.getAutoRenew());
            String json = objectMapper.writeValueAsString(payload);
            taskQueueProducer.enqueue(TASK_TYPE, sub.getId().toString(), json, 0);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to dispatch subscription expiry task for ws={}: {}",
                    sub.getWorkspaceId(), ex.getMessage());
            return false;
        }
    }
}
