package com.shortvideoscripagent.xhsagentyunying.billing.statement.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementMapper;
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
 * S4-3 账单生成定时器。
 * <p>
 * 每 <b>1 小时</b>扫描一次，把<b>订阅周期已结束</b>的工作区投递到持久化任务队列
 * （{@code BILLING_STATEMENT}）。<b>定时器本身不做账单生成</b>，只负责投递，
 * 从而获得重试与 DLQ 能力。
 * </p>
 * <p>
 * 计费周期按订阅周期（{@code app.subscription.current_period_start/end}）聚合，
 * 因此出账粒度与订阅周期一致。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class StatementScheduler {

    private static final String TASK_TYPE = "BILLING_STATEMENT";
    private static final int SCAN_LIMIT = 200;

    private final BillingStatementMapper statementMapper;
    private final TaskQueueProducer taskQueueProducer;
    private final ObjectMapper objectMapper;

    /**
     * 每小时触发一次。
     */
    @Scheduled(initialDelay = 300_000, fixedRate = 60 * 60 * 1000)
    public void scanEndedPeriods() {
        log.info("[StatementScheduler] 开始扫描已结束的订阅周期...");
        int dispatched = 0;
        try {
            List<BillingStatementMapper.SubscriptionPeriodRow> rows =
                    statementMapper.selectEndedSubscriptionPeriods(OffsetDateTime.now(), SCAN_LIMIT);
            for (BillingStatementMapper.SubscriptionPeriodRow row : rows) {
                if (dispatch(row)) {
                    dispatched++;
                }
            }
            log.info("[StatementScheduler] 账单任务投递完成，共 {} 个", dispatched);
        } catch (Exception e) {
            log.error("[StatementScheduler] 扫描已结束订阅周期失败: {}", e.getMessage(), e);
        }
    }

    private boolean dispatch(BillingStatementMapper.SubscriptionPeriodRow row) {
        if (row.getWorkspaceId() == null || row.getPeriodStart() == null || row.getPeriodEnd() == null) {
            return false;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("workspaceId", row.getWorkspaceId());
            payload.put("periodStart", row.getPeriodStart().toString());
            payload.put("periodEnd", row.getPeriodEnd().toString());
            String json = objectMapper.writeValueAsString(payload);
            // taskId 用「工作区 + 周期」组合，天然去重（同周期重复投递不产生重复账单）
            // 周期统一用 UTC Instant 表示，避免同一周期因时区写法不同产生两个 taskId
            String taskId = row.getWorkspaceId() + "|"
                    + row.getPeriodStart().toInstant() + "|"
                    + row.getPeriodEnd().toInstant();
            taskQueueProducer.enqueue(TASK_TYPE, taskId, json, 0);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to dispatch statement task for ws={}: {}",
                    row.getWorkspaceId(), ex.getMessage());
            return false;
        }
    }
}
