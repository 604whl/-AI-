package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.DeadLetterQueue;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * P0-1: 管理端任务队列接口（ADMIN 角色专用）。
 * <ul>
 *   <li>GET  /v1/admin/tasks/dlq      —— 查询死信队列</li>
 *   <li>POST /v1/admin/tasks/{taskId}/retry —— 人工重试</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_NOT_FOUND = 40401;

    private final JdbcTemplate jdbcTemplate;
    /**
     * 死信队列仅在 task-queue 启用时才有 Bean（参见
     * {@code DeadLetterQueue} 上的 {@code @ConditionalOnProperty}）。
     * 用 {@code ObjectProvider} 延迟获取，避免在 task-queue 未开启的
     * profile（如单元 / 集成测试）里因缺 Bean 导致整个容器装配失败。
     */
    private final ObjectProvider<DeadLetterQueue> deadLetterQueue;

    /**
     * 查询死信队列（分页）。
     */
    @GetMapping("/dlq")
    public ApiResponse<PaginatedDlqResponse> listDlq(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireAdmin();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        // 查询总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.task_dlq",
                Long.class
        );

        // 查询列表
        String sql = """
            SELECT id, original_task_id, task_type, payload, error_message, error_code, retry_count, failed_at
            FROM app.task_dlq
            ORDER BY failed_at DESC
            LIMIT ? OFFSET ?
            """;
        List<DlqItem> items = jdbcTemplate.query(sql,
                (rs, rowNum) -> new DlqItem(
                        rs.getLong("id"),
                        rs.getString("original_task_id"),
                        rs.getString("task_type"),
                        rs.getString("payload"),
                        rs.getString("error_message"),
                        rs.getObject("error_code", Integer.class),
                        rs.getInt("retry_count"),
                        rs.getObject("failed_at", OffsetDateTime.class)
                ),
                safeSize, offset
        );

        return ApiResponse.ok(new PaginatedDlqResponse(items, total != null ? total : 0L, safePage, safeSize));
    }

    /**
     * 人工重试：从 DLQ 拉回任务并重新入队。
     */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<Void> retryFromDlq(@PathVariable String taskId) {
        requireAdmin();

        // 查询 DLQ 记录
        String sql = """
            SELECT id, original_task_id, task_type, payload, retry_count
            FROM app.task_dlq
            WHERE original_task_id = ?
            ORDER BY failed_at DESC
            LIMIT 1
            """;
        List<DlqRecord> records = jdbcTemplate.query(sql,
                (rs, rowNum) -> new DlqRecord(
                        rs.getLong("id"),
                        rs.getString("original_task_id"),
                        rs.getString("task_type"),
                        rs.getString("payload"),
                        rs.getInt("retry_count")
                ),
                taskId
        );

        if (records.isEmpty()) {
            throw new BusinessException(CODE_NOT_FOUND, "dlq_record_not_found");
        }

        DlqRecord record = records.get(0);
        DeadLetterQueue dlq = deadLetterQueue.getIfAvailable();
        if (dlq == null) {
            throw new BusinessException(CODE_NOT_FOUND, "task_queue_disabled");
        }
        dlq.retryFromDlq(record.id(), record.originalTaskId(),
                record.taskType(), record.payload(), record.retryCount());

        log.info("Admin retry triggered: taskId={} dlqId={}", taskId, record.id());
        return ApiResponse.ok(null);
    }

    // ── private ─────────────────────────────────────────────────────────────

    private void requireAdmin() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        // 管理员角色校验：查询 workspace_role 表
        String role = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(wr.role_name, 'MEMBER')
                FROM app.workspace_member wm
                JOIN app.workspace_role wr ON wm.role_id = wr.id
                WHERE wm.user_id = ?
                LIMIT 1
                """,
                String.class,
                userId
        );
        if (!"ADMIN".equalsIgnoreCase(role) && !"OWNER".equalsIgnoreCase(role)) {
            throw new BusinessException(CODE_FORBIDDEN, "admin_only");
        }
    }

    // ── DTO ─────────────────────────────────────────────────────────────────

    @Data
    public static class DlqItem {
        private final Long id;
        private final String originalTaskId;
        private final String taskType;
        private final String payload;
        private final String errorMessage;
        private final Integer errorCode;
        private final int retryCount;
        private final OffsetDateTime failedAt;
    }

    private record DlqRecord(Long id, String originalTaskId, String taskType, String payload, int retryCount) {}

    @Data
    public static class PaginatedDlqResponse {
        private final List<DlqItem> items;
        private final long total;
        private final int page;
        private final int size;
    }
}
