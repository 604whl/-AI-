package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveFeedbackRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveFeedbackResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveFeedbackReviewRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveSummaryResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.TemplateEffectStatsResponse;
import com.shortvideoscripagent.xhsagentyunying.service.RetrospectiveService;
import com.shortvideoscripagent.xhsagentyunying.service.TemplateEffectStatsService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S3-7 复盘报告 + S3-8 回写审批 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/retrospectives}
 * → {@code /api/v1/retrospectives}。
 * </p>
 */
@RestController
@RequestMapping("/v1/retrospectives")
@RequiredArgsConstructor
@Tag(name = "Retrospectives", description = "复盘报告与回写审批（阶段 3 S3-7 / S3-8）")
public class RetrospectiveController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    /** 404 复盘报告不存在 / 越权 */
    private static final int CODE_REPORT_NOT_FOUND = 40472;
    /** 404 回写记录不存在 / 越权 */
    private static final int CODE_FEEDBACK_NOT_FOUND = 40473;

    private final RetrospectiveService retrospectiveService;
    private final TemplateEffectStatsService templateEffectStatsService;
    private final WorkspaceService workspaceService;

    // ── 生成报告 ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "生成复盘报告",
            description = "ANALYST/EDITOR/ADMIN/OWNER；数据不足时返回 INSUFFICIENT_DATA（降级而非伪成功）；"
                    + "COMPLETED 报告必须含 >= 3 条带 evidenceRefs 的建议；"
                    + "错误码 400 RETROSPECTIVE_INVALID")
    public ApiResponse<RetrospectiveCreateResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RetrospectiveCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(retrospectiveService.create(workspaceId, userId, request, idempotencyKey));
    }

    // ── 列表 ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "列出复盘报告", description = "全部角色可读；按当前 workspace 过滤")
    public ApiResponse<PaginatedResponse<RetrospectiveSummaryResponse>> list(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(retrospectiveService.list(workspaceId, scopeType, platform, assetId,
                parseDateTime(from), parseDateTime(to), page, size));
    }

    // ── 详情 ────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "查询复盘报告详情",
            description = "全部角色可读；suggestions 每条带 evidenceRefs；错误码 404 REPORT_NOT_FOUND")
    public ApiResponse<RetrospectiveResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(retrospectiveService.get(workspaceId, parseUuid(id, CODE_REPORT_NOT_FOUND)));
    }

    // ── S3-8 回写审批 ───────────────────────────────────────────────────

    @PostMapping("/{id}/feedback")
    @Operation(summary = "提交复盘回写申请",
            description = "ANALYST/EDITOR/ADMIN/OWNER；初始状态 PENDING，此时<b>不</b>写入效果统计；"
                    + "错误码 400 RETROSPECTIVE_INVALID / 404 REPORT_NOT_FOUND")
    public ApiResponse<RetrospectiveFeedbackResponse> submitFeedback(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RetrospectiveFeedbackRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(retrospectiveService.submitFeedback(workspaceId, userId,
                parseUuid(id, CODE_REPORT_NOT_FOUND), request, idempotencyKey));
    }

    @GetMapping("/{id}/feedback")
    @Operation(summary = "列出复盘回写记录", description = "全部角色可读；按报告过滤")
    public ApiResponse<List<RetrospectiveFeedbackResponse>> listFeedback(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(retrospectiveService.listFeedback(workspaceId,
                parseUuid(id, CODE_REPORT_NOT_FOUND)));
    }

    @PostMapping("/feedback/{feedbackId}/approve")
    @Operation(summary = "审批通过回写申请",
            description = "ADMIN/OWNER；<b>此时才</b>写回效果统计；错误码 404 FEEDBACK_NOT_FOUND / "
                    + "409 FEEDBACK_STATE_CONFLICT")
    public ApiResponse<RetrospectiveFeedbackResponse> approve(
            @PathVariable String feedbackId,
            @Valid @RequestBody(required = false) RetrospectiveFeedbackReviewRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(retrospectiveService.approve(workspaceId, userId,
                parseUuid(feedbackId, CODE_FEEDBACK_NOT_FOUND), request));
    }

    @PostMapping("/feedback/{feedbackId}/reject")
    @Operation(summary = "驳回回写申请",
            description = "ADMIN/OWNER；驳回意见必填（400 COMMENT_REQUIRED）；"
                    + "错误码 404 FEEDBACK_NOT_FOUND / 409 FEEDBACK_STATE_CONFLICT")
    public ApiResponse<RetrospectiveFeedbackResponse> reject(
            @PathVariable String feedbackId,
            @Valid @RequestBody(required = false) RetrospectiveFeedbackReviewRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(retrospectiveService.reject(workspaceId, userId,
                parseUuid(feedbackId, CODE_FEEDBACK_NOT_FOUND), request));
    }

    // ── 内部工具 ────────────────────────────────────────────────────────

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
     * ANALYST / EDITOR / ADMIN / OWNER 可写。
     */
    private void requireAnalyst(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    /**
     * ADMIN / OWNER（回写审批需要更高权限）。
     */
    private void requireAdmin(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    private UUID parseUuid(String raw, int notFoundCode) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(notFoundCode, "not_found");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(notFoundCode, "not_found");
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
