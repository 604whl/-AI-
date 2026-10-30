package com.shortvideoscripagent.xhsagentyunying.billing.order.scheduler;

import com.shortvideoscripagent.xhsagentyunying.billing.order.OrderService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.ReconciliationService;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S4-2 计费定时任务。
 * <ul>
 *   <li><b>订单过期扫描</b>：每 10 分钟把超时未支付的订单从 {@code pending} 置 {@code closed}；</li>
 *   <li><b>每日对账</b>：每天凌晨 3 点把对账任务投递到<b>持久化队列</b>
 *       （{@code BILLING_RECONCILIATION}），失败进 DLQ 可重试。</li>
 * </ul>
 * <p>
 * D7 首发渠道：支付宝 + 微信支付（mock 渠道仅用于本地联调，默认参与以保证链路可跑）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class BillingScheduler {

    /** 对账任务类型 */
    public static final String TASK_RECONCILIATION = "BILLING_RECONCILIATION";

    /** 参与每日对账的渠道 */
    private static final List<String> RECONCILIATION_PROVIDERS = List.of("mock", "alipay", "wechat");

    /** 单次过期扫描上限 */
    private static final int CLOSE_SCAN_LIMIT = 200;

    /** 补偿扫描上限 */
    private static final int REQUEUE_SCAN_LIMIT = 100;

    /** 已付款超过多少分钟仍为 paid 才纳入补偿扫描（避免与正常履约抢跑） */
    private static final int STALLED_FULFILL_MINUTES = 10;

    private final OrderService orderService;
    private final ReconciliationService reconciliationService;
    private final TaskQueueProducer taskQueueProducer;

    /**
     * 每 10 分钟关闭超时未支付订单。
     */
    @Scheduled(initialDelay = 120_000, fixedDelay = 10 * 60 * 1000)
    public void closeExpiredOrders() {
        try {
            int closed = orderService.closeExpiredOrders(CLOSE_SCAN_LIMIT);
            if (closed > 0) {
                log.info("[BillingScheduler] closed {} expired orders", closed);
            }
        } catch (Exception ex) {
            log.error("[BillingScheduler] close expired orders failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 每 5 分钟补偿扫描「已付款但未履约」的订单并重新投递履约任务。
     * <p>
     * P2-7：履约任务投递失败（Redis 不可用等）时订单会永久停在 {@code paid}，
     * 用户已付款却收不到订阅 —— 必须有兜底扫描。
     * </p>
     */
    @Scheduled(initialDelay = 180_000, fixedDelay = 5 * 60 * 1000)
    public void requeueStalledFulfillments() {
        try {
            int requeued = orderService.requeueUnfulfilledOrders(STALLED_FULFILL_MINUTES, REQUEUE_SCAN_LIMIT);
            if (requeued > 0) {
                log.warn("[BillingScheduler] re-queued {} stalled fulfillment tasks", requeued);
            }
        } catch (Exception ex) {
            log.error("[BillingScheduler] requeue stalled fulfillments failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 每日对账：投递到持久化队列，失败进 DLQ 可重试。
     */
    @Scheduled(cron = "0 15 3 * * ?")
    public void dailyReconciliation() {
        LocalDate day = LocalDate.now().minusDays(1);
        for (String provider : RECONCILIATION_PROVIDERS) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("provider", provider);
                payload.put("day", day.toString());
                String json = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(payload);
                taskQueueProducer.enqueue(TASK_RECONCILIATION,
                        provider + ":" + day, json, 0);
                log.info("[BillingScheduler] reconciliation enqueued: provider={} day={}", provider, day);
            } catch (Exception ex) {
                log.error("[BillingScheduler] enqueue reconciliation failed: provider={} err={}",
                        provider, ex.getMessage());
            }
        }
    }

    /**
     * 手动触发一次对账（同步执行，供管理端「重跑」用）。
     */
    public int runReconciliationNow(String provider, LocalDate day) {
        return reconciliationService.reconcile(provider, day);
    }
}
