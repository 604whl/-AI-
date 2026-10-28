package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishCalendarResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishScheduleCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishScheduleResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishScheduleRetryResponse;
import com.shortvideoscripagent.xhsagentyunying.service.PublishScheduleService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S3-3 排期日历 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/publish-schedules}
 * → {@code /api/v1/publish-schedules}。
 * </p>
 */
@RestController
@RequestMapping("/v1/publish-schedules")
@RequiredArgsConstructor
@Tag(name = "Publish Schedules", description = "排期日历（阶段 3 S3-3）")
public class PublishScheduleController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_SCHEDULE_NOT_FOUND = 40460;

    private final PublishScheduleService publishScheduleService;
    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "创建排期",
            description = "EDITOR+；资产必须已审核通过（否则 409 ASSET_NOT_APPROVED）；"
                    + "同工作区+同平台+同账号+同一时刻冲突 → 409 SCHEDULE_CONFLICT；"
                    + "错误码 404 ACCOUNT_NOT_FOUND / 404 ASSET_NOT_FOUND")
    public ApiResponse<PublishScheduleResponse> create(@Valid @RequestBody PublishScheduleCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(publishScheduleService.create(workspaceId, userId, request));
    }

    @GetMapping
    @Operation(summary = "列出排期",
            description = "全部角色可读；按当前 workspace 过滤；支持 platform / status / from / to 过滤")
    public ApiResponse<List<PublishScheduleResponse>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String platformCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        requireUserId();
        String workspaceId = requireWorkspace();
        String effectivePlatform = platform != null && !platform.isBlank() ? platform : platformCode;
        return ApiResponse.ok(publishScheduleService.list(workspaceId, effectivePlatform, status,
                parseDateTime(from), parseDateTime(to)));
    }

    @GetMapping("/calendar")
    @Operation(summary = "排期日历视图",
            description = "全部角色可读；按日期分组返回区间内排期（日期按每条排期自己的时区归一）")
    public ApiResponse<PublishCalendarResponse> calendar(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishScheduleService.calendar(workspaceId,
                parseDateTime(from), parseDateTime(to)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询排期详情", description = "全部角色可读；跨工作区访问按 404 处理")
    public ApiResponse<PublishScheduleResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishScheduleService.get(workspaceId, parseId(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消排期",
            description = "EDITOR+；scheduled → cancelled；资产状态回退 SCHEDULED → APPROVED；"
                    + "错误码 409 SCHEDULE_NOT_CANCELLABLE")
    public ApiResponse<PublishScheduleResponse> cancel(@PathVariable String id) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(publishScheduleService.cancel(workspaceId, userId, parseId(id)));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重试失败排期",
            description = "EDITOR+；复用原 publish_task 的幂等键，不产生新的外部发布；"
                    + "错误码 409 SCHEDULE_NOT_FAILED")
    public ApiResponse<PublishScheduleRetryResponse> retry(@PathVariable String id) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(publishScheduleService.retry(workspaceId, userId, parseId(id)));
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────

    private Long requireUserId() {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    private String requireWorkspace() {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);
        return workspaceId;
    }

    /**
     * EDITOR / ADMIN / OWNER。
     */
    private void requireEditor(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_SCHEDULE_NOT_FOUND, "publish_schedule_not_found");
        }
    }

    private OffsetDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
