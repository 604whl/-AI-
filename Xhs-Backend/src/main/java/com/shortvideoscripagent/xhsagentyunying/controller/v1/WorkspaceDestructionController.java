package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.DataRetentionPolicy;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceDestruction;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.DataRetentionPolicyMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.ExportRequestResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.RetentionPolicyRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.RetentionPolicyResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.WorkspaceDestructionResponse;
import com.shortvideoscripagent.xhsagentyunying.service.retention.PersonalDataExportService;
import com.shortvideoscripagent.xhsagentyunying.service.retention.WorkspaceDestructionService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * Workspace 销毁与数据保留策略接口（P0-9）。
 * <p>
 * <ul>
 *   <li>POST   /v1/workspaces/{id}/destroy             —— 申请销毁</li>
 *   <li>POST   /v1/workspaces/{id}/confirm-destroy     —— 二次确认销毁</li>
 *   <li>POST   /v1/workspaces/{id}/cancel-destroy     —— 撤销销毁</li>
 *   <li>GET    /v1/workspaces/{id}/destruction-status —— 查询销毁状态</li>
 *   <li>GET    /v1/workspaces/{id}/retention          —— 查询保留策略</li>
 *   <li>PUT    /v1/workspaces/{id}/retention          —— 更新保留策略</li>
 *   <li>GET    /v1/workspaces/{id}/export             —— 请求工作区全量导出</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceDestructionController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_NOT_FOUND = 40404;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_BAD_REQUEST = 40001;

    private final WorkspaceDestructionService destructionService;
    private final PersonalDataExportService personalDataExportService;
    private final DataRetentionPolicyMapper retentionPolicyMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceService workspaceService;

    /**
     * 申请 Workspace 销毁（7 天宽限期）。
     * POST /v1/workspaces/{id}/destroy?confirm=DELETE
     */
    @PostMapping("/{id}/destroy")
    public ApiResponse<WorkspaceDestructionResponse> requestDestruction(
            @PathVariable("id") String workspaceId,
            @RequestParam("confirm") String confirmPhrase) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER");
        WorkspaceDestructionResponse resp = destructionService.requestDestruction(workspaceId, userId, confirmPhrase);
        log.info("User {} requested workspace {} destruction", userId, workspaceId);
        return ApiResponse.ok(resp);
    }

    /**
     * 二次确认销毁（必须再次输入 DELETE）。
     * POST /v1/workspaces/{id}/confirm-destroy?confirm=DELETE
     */
    @PostMapping("/{id}/confirm-destroy")
    public ApiResponse<WorkspaceDestructionResponse> confirmDestruction(
            @PathVariable("id") String workspaceId,
            @RequestParam("confirm") String confirmPhrase) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER");
        WorkspaceDestructionResponse status = destructionService.getStatus(workspaceId);
        if (status == null || status.getId() == null) {
            throw new BusinessException(CODE_NOT_FOUND, "no_destruction_request_found");
        }
        WorkspaceDestructionResponse resp = destructionService.confirmDestruction(
                java.util.UUID.fromString(status.getId()), userId, confirmPhrase);
        log.info("User {} confirmed workspace {} destruction", userId, workspaceId);
        return ApiResponse.ok(resp);
    }

    /**
     * 撤销销毁申请。
     * POST /v1/workspaces/{id}/cancel-destroy
     */
    @PostMapping("/{id}/cancel-destroy")
    public ApiResponse<WorkspaceDestructionResponse> cancelDestruction(@PathVariable("id") String workspaceId) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER");
        WorkspaceDestructionResponse status = destructionService.getStatus(workspaceId);
        if (status == null || status.getId() == null) {
            throw new BusinessException(CODE_NOT_FOUND, "no_destruction_request_found");
        }
        WorkspaceDestructionResponse resp = destructionService.cancelDestruction(
                java.util.UUID.fromString(status.getId()), userId);
        log.info("User {} cancelled workspace {} destruction", userId, workspaceId);
        return ApiResponse.ok(resp);
    }

    /**
     * 查询销毁状态。
     * GET /v1/workspaces/{id}/destruction-status
     */
    @GetMapping("/{id}/destruction-status")
    public ApiResponse<WorkspaceDestructionResponse> getDestructionStatus(@PathVariable("id") String workspaceId) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId,
                "OWNER", "ADMIN", "REVIEWER", "EDITOR", "MEMBER");
        return ApiResponse.ok(destructionService.getStatus(workspaceId));
    }

    /**
     * 查询保留策略。
     * GET /v1/workspaces/{id}/retention
     */
    @GetMapping("/{id}/retention")
    public ApiResponse<RetentionPolicyResponse> getRetentionPolicy(@PathVariable("id") String workspaceId) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER", "ADMIN");

        DataRetentionPolicy policy = retentionPolicyMapper.selectById(workspaceId);
        RetentionPolicyResponse resp;
        if (policy == null) {
            resp = RetentionPolicyResponse.builder()
                    .workspaceId(workspaceId)
                    .assetRetentionDays(365)
                    .logRetentionDays(90)
                    .mediaRetentionDays(365)
                    .chatSessionRetentionDays(180)
                    .build();
        } else {
            resp = RetentionPolicyResponse.builder()
                    .workspaceId(workspaceId)
                    .assetRetentionDays(policy.getAssetRetentionDays())
                    .logRetentionDays(policy.getLogRetentionDays())
                    .mediaRetentionDays(policy.getMediaRetentionDays())
                    .chatSessionRetentionDays(policy.getChatSessionRetentionDays())
                    .updatedAt(policy.getUpdatedAt())
                    .updatedBy(policy.getUpdatedBy())
                    .build();
        }
        return ApiResponse.ok(resp);
    }

    /**
     * 更新保留策略。
     * PUT /v1/workspaces/{id}/retention
     */
    @PutMapping("/{id}/retention")
    @Transactional
    public ApiResponse<RetentionPolicyResponse> updateRetentionPolicy(
            @PathVariable("id") String workspaceId,
            @Valid @RequestBody RetentionPolicyRequest request) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER", "ADMIN");

        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(CODE_NOT_FOUND, "workspace_not_found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        DataRetentionPolicy policy = retentionPolicyMapper.selectById(workspaceId);

        if (policy == null) {
            policy = new DataRetentionPolicy();
            policy.setWorkspaceId(workspaceId);
            policy.setAssetRetentionDays(request.getAssetRetentionDays() != null
                    ? request.getAssetRetentionDays() : 365);
            policy.setLogRetentionDays(request.getLogRetentionDays() != null
                    ? request.getLogRetentionDays() : 90);
            policy.setMediaRetentionDays(request.getMediaRetentionDays() != null
                    ? request.getMediaRetentionDays() : 365);
            policy.setChatSessionRetentionDays(request.getChatSessionRetentionDays() != null
                    ? request.getChatSessionRetentionDays() : 180);
            policy.setUpdatedAt(now);
            policy.setUpdatedBy(userId);
            policy.setCreatedAt(now);
            retentionPolicyMapper.insert(policy);
        } else {
            if (request.getAssetRetentionDays() != null) {
                policy.setAssetRetentionDays(request.getAssetRetentionDays());
            }
            if (request.getLogRetentionDays() != null) {
                policy.setLogRetentionDays(request.getLogRetentionDays());
            }
            if (request.getMediaRetentionDays() != null) {
                policy.setMediaRetentionDays(request.getMediaRetentionDays());
            }
            if (request.getChatSessionRetentionDays() != null) {
                policy.setChatSessionRetentionDays(request.getChatSessionRetentionDays());
            }
            policy.setUpdatedAt(now);
            policy.setUpdatedBy(userId);
            retentionPolicyMapper.updateById(policy);
        }

        log.info("User {} updated retention policy for workspace {}", userId, workspaceId);

        return ApiResponse.ok(RetentionPolicyResponse.builder()
                .workspaceId(workspaceId)
                .assetRetentionDays(policy.getAssetRetentionDays())
                .logRetentionDays(policy.getLogRetentionDays())
                .mediaRetentionDays(policy.getMediaRetentionDays())
                .chatSessionRetentionDays(policy.getChatSessionRetentionDays())
                .updatedAt(policy.getUpdatedAt())
                .updatedBy(policy.getUpdatedBy())
                .build());
    }

    /**
     * 请求工作区全量导出。
     * GET /v1/workspaces/{id}/export
     */
    @GetMapping("/{id}/export")
    public ApiResponse<ExportRequestResponse> requestWorkspaceExport(@PathVariable("id") String workspaceId) {
        Long userId = requireUserId();
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER", "ADMIN");
        return ApiResponse.ok(personalDataExportService.requestWorkspaceExport(workspaceId, userId));
    }

    // ── private ─────────────────────────────────────────────────────────────

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
