package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishTaskCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishTaskResponse;
import com.shortvideoscripagent.xhsagentyunying.service.PublishTaskService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * S3-4 发布任务 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/publish-tasks}
 * → {@code /api/v1/publish-tasks}。
 * </p>
 */
@RestController
@RequestMapping("/v1/publish-tasks")
@RequiredArgsConstructor
@Tag(name = "Publish Tasks", description = "发布任务（阶段 3 S3-4）")
public class PublishTaskController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_TASK_NOT_FOUND = 40470;

    private final PublishTaskService publishTaskService;
    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "创建发布任务",
            description = "EDITOR+；必须带 Idempotency-Key 请求头（否则 409 IDEMPOTENCY_KEY_REQUIRED）；"
                    + "同 key 重复提交只产生一个任务；错误码 409 DUPLICATE_PUBLISH / "
                    + "503 CONNECTOR_UNAVAILABLE / 403 CONNECTOR_NOT_AUTHORIZED")
    public ApiResponse<PublishTaskResponse> create(
            @Valid @RequestBody PublishTaskCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(publishTaskService.create(workspaceId, userId, request, idempotencyKey));
    }

    @GetMapping
    @Operation(summary = "列出发布任务", description = "全部角色可读；按当前 workspace 过滤")
    public ApiResponse<List<PublishTaskResponse>> list(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String status) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishTaskService.list(workspaceId, assetId, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询发布任务详情",
            description = "全部角色可读；含 receipt / errorCode / errorMessage / retryable；"
                    + "错误码 404 TASK_NOT_FOUND")
    public ApiResponse<PublishTaskResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishTaskService.get(workspaceId, parseId(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消发布任务",
            description = "EDITOR+；仅 pending / manual_required / failed 可取消；"
                    + "错误码 409 TASK_NOT_CANCELLABLE")
    public ApiResponse<PublishTaskResponse> cancel(@PathVariable String id) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(publishTaskService.cancel(workspaceId, userId, parseId(id)));
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
            throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
        }
    }
}
