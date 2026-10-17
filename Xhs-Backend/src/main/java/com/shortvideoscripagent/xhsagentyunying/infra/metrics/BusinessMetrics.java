package com.shortvideoscripagent.xhsagentyunying.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * XhsAgent 业务指标聚合器。
 * <p>
 * 暴露给 {@code /actuator/prometheus}，由 Prometheus Server 抓取，告警规则见
 * {@code infra/observability/prometheus-alerts.yml}。
 * <p>
 * 命名规范：
 * <ul>
 *   <li>Counter: {@code xhs_<domain>_<action>_total}</li>
 *   <li>Timer:   {@code xhs_<domain>_<action>_duration_seconds}</li>
 * </ul>
 * 关键指标：
 * <ul>
 *   <li>{@code xhs_tasks_submitted_total} — 任务提交</li>
 *   <li>{@code xhs_tasks_completed_total{status=...}} — 任务完成（success / failed / cancelled）</li>
 *   <li>{@code xhs_tasks_dlq_total} — 进入死信</li>
 *   <li>{@code xhs_quota_denied_total} — 额度拒绝</li>
 *   <li>{@code xhs_quota_refund_total} — 额度冲正</li>
 *   <li>{@code xhs_audit_writes_total} — 审计落库</li>
 *   <li>{@code xhs_workspace_members_total{action=...}} — 成员变更</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry registry;

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    /**
     * 增加一个标签化的 Counter；如已存在则复用。
     */
    public void increment(String name, String tagKey, String tagValue) {
        increment(name, List.of(Tag.of(tagKey, tagValue)));
    }

    public void increment(String name, List<Tag> tags) {
        String key = name + ":" + tags;
        counters.computeIfAbsent(key, k -> Counter.builder(name)
                        .tags(tags)
                        .register(registry))
                .increment();
    }

    /**
     * 记录一次耗时。
     */
    public void recordDuration(String name, Duration duration, String tagKey, String tagValue) {
        recordDuration(name, duration, List.of(Tag.of(tagKey, tagValue)));
    }

    public void recordDuration(String name, Duration duration, List<Tag> tags) {
        String key = name + ":" + tags;
        timers.computeIfAbsent(key, k -> Timer.builder(name)
                        .tags(tags)
                        .publishPercentileHistogram()
                        .register(registry))
                .record(duration);
    }

    // === 任务队列 ===

    public void taskSubmitted(String taskType) {
        increment("xhs_tasks_submitted_total", "taskType", taskType);
    }

    public void taskCompleted(String taskType, String status, Duration duration) {
        recordDuration("xhs_tasks_completed_duration_seconds", duration,
                List.of(Tag.of("taskType", taskType), Tag.of("status", status)));
        increment("xhs_tasks_completed_total",
                List.of(Tag.of("taskType", taskType), Tag.of("status", status)));
    }

    public void taskDeadLettered(String taskType, String reason) {
        increment("xhs_tasks_dlq_total",
                List.of(Tag.of("taskType", taskType), Tag.of("reason", reason)));
    }

    public void taskCancelled(String taskType) {
        increment("xhs_tasks_cancelled_total", "taskType", taskType);
    }

    // === 额度 ===

    public void quotaDenied(String workspaceId, String reason) {
        increment("xhs_quota_denied_total",
                List.of(Tag.of("workspace", workspaceId), Tag.of("reason", reason)));
    }

    public void quotaRefund(String workspaceId, String reason) {
        increment("xhs_quota_refund_total",
                List.of(Tag.of("workspace", workspaceId), Tag.of("reason", reason)));
    }

    // === 审计 ===

    public void auditWritten(String action) {
        increment("xhs_audit_writes_total", "action", action);
    }

    // === 成员管理 ===

    public void workspaceMemberChanged(String action) {
        increment("xhs_workspace_members_total", "action", action);
    }
}
