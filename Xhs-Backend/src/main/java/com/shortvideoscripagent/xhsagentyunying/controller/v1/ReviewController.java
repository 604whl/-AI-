package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.review.*;
import com.shortvideoscripagent.xhsagentyunying.service.ReviewService;
import com.shortvideoscripagent.xhsagentyunying.service.RiskAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 审核流 REST 接口（P0-6）。
 * <p>
 * 前缀：/v1
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReviewController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final ReviewService reviewService;
    private final RiskAssessmentService riskAssessmentService;

    // ═══════════════════════════════════════════════════════════════════════════
    // Reviewer 端
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /v1/reviews/queue?status=pending
     * Reviewer 待办队列。
     */
    @GetMapping("/reviews/queue")
    public ApiResponse<PaginatedResponse<ReviewQueueItem>> getQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(reviewService.getQueue(userId, status, page, size));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Editor 端
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /v1/reviews/submissions
     * Editor 自己的提交记录。
     */
    @GetMapping("/reviews/submissions")
    public ApiResponse<PaginatedResponse<ReviewQueueItem>> getMySubmissions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(reviewService.getMySubmissions(userId, page, size));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 通用
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /v1/reviews/{taskId}
     * 审核任务详情。
     */
    @GetMapping("/reviews/{taskId}")
    public ApiResponse<ReviewTaskResponse> getById(@PathVariable String taskId) {
        Long userId = requireUserId();
        UUID id = parseUuid(taskId);
        return ApiResponse.ok(reviewService.getById(id, userId));
    }

    /**
     * POST /v1/reviews/{taskId}/assign
     * ADMIN/OWNER 指派审核人。
     */
    @PostMapping("/reviews/{taskId}/assign")
    public ApiResponse<ReviewTaskResponse> assignReviewer(
            @PathVariable String taskId,
            @Valid @RequestBody ReviewAssignRequest req
    ) {
        Long userId = requireUserId();
        UUID id = parseUuid(taskId);
        return ApiResponse.ok(reviewService.assignReviewer(id, req.getReviewerId(), userId));
    }

    /**
     * POST /v1/reviews/{taskId}/approve
     * Reviewer/ADMIN/OWNER 批准。
     */
    @PostMapping("/reviews/{taskId}/approve")
    public ApiResponse<ReviewTaskResponse> approve(
            @PathVariable String taskId,
            @RequestBody(required = false) ReviewDecisionRequest req
    ) {
        Long userId = requireUserId();
        UUID id = parseUuid(taskId);
        String comment = req != null ? req.getComment() : null;
        return ApiResponse.ok(reviewService.approve(id, userId, comment));
    }

    /**
     * POST /v1/reviews/{taskId}/reject
     * Reviewer/ADMIN/OWNER 驳回（必须附原因）。
     */
    @PostMapping("/reviews/{taskId}/reject")
    public ApiResponse<ReviewTaskResponse> reject(
            @PathVariable String taskId,
            @Valid @RequestBody ReviewDecisionRequest req
    ) {
        Long userId = requireUserId();
        UUID id = parseUuid(taskId);
        return ApiResponse.ok(reviewService.reject(id, userId, req.getComment()));
    }

    /**
     * POST /v1/reviews/{taskId}/cancel
     * Editor 撤销自己的提交。
     */
    @PostMapping("/reviews/{taskId}/cancel")
    public ApiResponse<ReviewTaskResponse> cancel(@PathVariable String taskId) {
        Long userId = requireUserId();
        UUID id = parseUuid(taskId);
        return ApiResponse.ok(reviewService.cancel(id, userId));
    }

    /**
     * POST /v1/reviews/{taskId}/comments
     * 添加批注。
     */
    @PostMapping("/reviews/{taskId}/comments")
    public ApiResponse<ReviewTaskResponse> addComment(
            @PathVariable String taskId,
            @Valid @RequestBody ReviewCommentRequest req
    ) {
        Long userId = requireUserId();
        UUID id = parseUuid(taskId);
        return ApiResponse.ok(reviewService.addComment(
                id, req.getComment(), req.getHighlightedText(), userId));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 风险评估（由合规扫描调用，或手动预览）
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /v1/assets/{assetId}/assess-risk
     * 对资产进行风险评估（触发高风险自动审核）。
     */
    @PostMapping("/assets/{assetId}/assess-risk")
    public ApiResponse<RiskAssessmentService.RiskResult> assessRisk(@PathVariable String assetId) {
        requireUserId();
        UUID id = parseUuid(assetId);
        RiskAssessmentService.RiskResult result = riskAssessmentService.assessAsset(id);
        return ApiResponse.ok(result);
    }

    /**
     * GET /v1/assets/{assetId}/risk-score
     * 仅计算风险分（不触发审核，用于预览）。
     */
    @GetMapping("/assets/{assetId}/risk-score")
    public ApiResponse<Integer> getRiskScore(@PathVariable String assetId) {
        requireUserId();
        UUID id = parseUuid(assetId);
        return ApiResponse.ok(riskAssessmentService.assessAsset(id).riskScore());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 私有
    // ═══════════════════════════════════════════════════════════════════════════

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new BusinessException(40010, "invalid_id");
        }
    }
}
