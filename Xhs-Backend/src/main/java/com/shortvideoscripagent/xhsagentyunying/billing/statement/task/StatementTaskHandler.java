package com.shortvideoscripagent.xhsagentyunying.billing.statement.task;

import com.shortvideoscripagent.xhsagentyunying.billing.statement.StatementService;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S4-3 账单生成任务处理器。
 * <p>
 * 处理类型：{@code BILLING_STATEMENT}
 * </p>
 * <p>
 * <b>幂等</b>：实际幂等由 {@code app.billing_statement.idempotency_key} 唯一索引保证，
 * 本 handler 只负责把「工作区 + 周期」交给 {@link StatementService#generateStatement}，
 * 重复投递（任务重试 / 多 worker 抢消费）都只会得到同一张账单。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class StatementTaskHandler implements TaskHandler {

    private final StatementService statementService;

    @Override
    public String taskType() {
        return "BILLING_STATEMENT";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        String workspaceId = str(payload, "workspaceId");
        if (workspaceId == null) {
            throw new IllegalStateException("workspaceId required in payload");
        }
        OffsetDateTime periodStart = parseTime(str(payload, "periodStart"));
        OffsetDateTime periodEnd = parseTime(str(payload, "periodEnd"));
        if (periodStart == null || periodEnd == null) {
            throw new IllegalStateException("periodStart/periodEnd required in payload");
        }

        log.info("StatementTaskHandler: taskId={} ws={} period {}~{}",
                taskId, workspaceId, periodStart, periodEnd);
        var result = statementService.generateStatement(workspaceId, periodStart, periodEnd);
        if (result == null) {
            log.info("No statement generated (no unbilled usage): ws={} period {}~{}",
                    workspaceId, periodStart, periodEnd);
        } else {
            log.info("Statement ready: id={} total={}", result.getId(), result.getTotalAmount());
        }
    }

    private static String str(Map<String, Object> payload, String key) {
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

    private static OffsetDateTime parseTime(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
