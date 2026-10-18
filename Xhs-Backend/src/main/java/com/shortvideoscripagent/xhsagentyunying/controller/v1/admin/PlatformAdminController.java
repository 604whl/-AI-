package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AuditEvent;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AuditEventMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import com.shortvideoscripagent.xhsagentyunying.service.admin.PlatformAdminService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/platform/admin")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final AuditEventMapper auditEventMapper;
    private final TaskQueueProducer taskQueueProducer;

    @GetMapping("/overview")
    public ApiResponse<PlatformOverview> overview() {
        platformAdminService.requirePlatformAdmin();

        long totalWorkspaces = workspaceMapper.selectCount(null);
        long totalUsers = userMapper.selectCount(null);

        Long todayAiCalls = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.usage_log WHERE action = 'analysis' AND created_at >= CURRENT_DATE",
                Long.class);
        Long recentFailures = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.task_dlq WHERE failed_at >= NOW() - INTERVAL '24 hours'",
                Long.class);

        return ApiResponse.ok(new PlatformOverview(
                totalWorkspaces, totalUsers,
                todayAiCalls != null ? todayAiCalls : 0L,
                recentFailures != null ? recentFailures : 0L));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserListItem>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        platformAdminService.requirePlatformAdmin();

        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<User> p = userMapper.selectPage(new Page<>(safePage + 1, safeSize),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));

        List<UserListItem> items = p.getRecords().stream()
                .map(u -> new UserListItem(u.getId(), u.getEmail(), u.getDisplayName(),
                        u.getDeletionStatus(), u.getCreatedAt()))
                .toList();

        return ApiResponse.ok(new PageResult<>(items, p.getTotal(), safePage + 1, safeSize));
    }

    @GetMapping("/workspaces")
    public ApiResponse<PageResult<WorkspaceListItem>> listWorkspaces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        platformAdminService.requirePlatformAdmin();

        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<Workspace> p = workspaceMapper.selectPage(new Page<>(safePage + 1, safeSize),
                new LambdaQueryWrapper<Workspace>().orderByDesc(Workspace::getCreatedAt));

        List<WorkspaceListItem> items = p.getRecords().stream()
                .map(w -> new WorkspaceListItem(w.getId(), w.getName(), w.getSlug(),
                        w.getStatus(), w.getIndustry(), w.getCreatedAt()))
                .toList();

        return ApiResponse.ok(new PageResult<>(items, p.getTotal(), safePage + 1, safeSize));
    }

    @GetMapping("/audit")
    public ApiResponse<PageResult<AuditListItem>> listAudit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        platformAdminService.requirePlatformAdmin();

        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) safePage * safeSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app.audit_event", Long.class);

        String sql = "SELECT id, workspace_id, actor_user_id, action, resource_type, resource_id, " +
                "ip, result, error_code, created_at FROM app.audit_event " +
                "ORDER BY created_at DESC LIMIT ? OFFSET ?";

        List<AuditListItem> items = jdbcTemplate.query(sql, new Object[]{safeSize, offset}, auditRowMapper());

        return ApiResponse.ok(new PageResult<>(items, total != null ? total : 0L, safePage + 1, safeSize));
    }

    @PostMapping("/audit/replay/{id}")
    public ApiResponse<Void> replayAuditEvent(@PathVariable String id) {
        platformAdminService.requirePlatformAdmin();

        AuditEvent event = auditEventMapper.selectById(id);
        if (event == null) {
            return ApiResponse.fail(40401, "audit_event_not_found");
        }

        try {
            String payload = String.format(
                    "{\"type\":\"webhook_replay\",\"original_event_id\":\"%s\",\"action\":\"%s\"," +
                            "\"resource_type\":\"%s\",\"resource_id\":\"%s\"}",
                    event.getId(), event.getAction(),
                    event.getResourceType() != null ? event.getResourceType() : "",
                    event.getResourceId() != null ? event.getResourceId() : "");

            taskQueueProducer.enqueue("WEBHOOK_REPLAY", id, payload, 0);
            log.info("Audit replay enqueued: eventId={}", id);
        } catch (Exception e) {
            log.warn("Audit replay failed for {}: {}", id, e.getMessage());
            return ApiResponse.fail(50001, "replay_failed: " + e.getMessage());
        }

        return ApiResponse.ok(null);
    }

    private RowMapper<AuditListItem> auditRowMapper() {
        return (rs, rowNum) -> new AuditListItem(
                rs.getString("id"),
                rs.getString("workspace_id"),
                rs.getString("actor_user_id"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("ip"),
                rs.getString("result"),
                rs.getObject("error_code", Integer.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    @Data
    public static class PlatformOverview {
        private final long totalWorkspaces;
        private final long totalUsers;
        private final long todayAiCalls;
        private final long recentFailures;
    }

    @Data
    public static class UserListItem {
        private final Long id;
        private final String email;
        private final String displayName;
        private final String deletionStatus;
        private final OffsetDateTime createdAt;
    }

    @Data
    public static class WorkspaceListItem {
        private final String id;
        private final String name;
        private final String slug;
        private final String status;
        private final String industry;
        private final OffsetDateTime createdAt;
    }

    @Data
    public static class AuditListItem {
        private final String id;
        private final String workspaceId;
        private final String actorUserId;
        private final String action;
        private final String resourceType;
        private final String resourceId;
        private final String ip;
        private final String result;
        private final Integer errorCode;
        private final OffsetDateTime createdAt;
    }

    @Data
    public static class PageResult<T> {
        private final List<T> items;
        private final long total;
        private final int page;
        private final int size;
    }
}
