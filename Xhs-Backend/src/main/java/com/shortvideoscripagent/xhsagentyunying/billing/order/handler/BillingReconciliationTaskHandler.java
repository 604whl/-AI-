package com.shortvideoscripagent.xhsagentyunying.billing.order.handler;

import com.shortvideoscripagent.xhsagentyunying.billing.order.ReconciliationService;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S4-2 对账任务处理器。
 * <p>
 * 处理类型：{@code BILLING_RECONCILIATION}
 * </p>
 * <p>
 * 失败时抛异常 → 队列重试 → 超过次数进 DLQ。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class BillingReconciliationTaskHandler implements TaskHandler {

    private final ReconciliationService reconciliationService;

    @Override
    public String taskType() {
        return "BILLING_RECONCILIATION";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        Object providerObj = payload == null ? null : payload.get("provider");
        Object dayObj = payload == null ? null : payload.get("day");
        String provider = providerObj == null ? "mock" : String.valueOf(providerObj);
        LocalDate day = dayObj == null ? LocalDate.now() : LocalDate.parse(String.valueOf(dayObj));

        log.info("BillingReconciliationTaskHandler: provider={} day={} at={}",
                provider, day, OffsetDateTime.now());
        int diffs = reconciliationService.reconcile(provider, day);
        log.info("Billing reconciliation finished: provider={} day={} diffs={}", provider, day, diffs);
    }
}
