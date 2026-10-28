package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ReviewComment;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ReviewTask;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ReviewCommentMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ReviewTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.review.*;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审核服务（P0-6）。
 * <p>
 * 核心流程：Editor 提交 → Reviewer 审批 → 状态机迁移。
 * 高风险内容（合规分 > 70）自动 require review。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int CODE_NOT_FOUND = 40420;
    private static final int CODE_FORBIDDEN = 40310;
    private static final int CODE_CONFLICT = 40910;
    private static final int CODE_BAD_REQUEST = 40020;

    private final ReviewTaskMapper taskMapper;
    private final ReviewCommentMapper commentMapper;
    private final ContentAssetMapper assetMapper;
    private final UserMapper userMapper;
    private final WorkspaceService workspaceService;

    // ═══════════════════════════════════════════════════════════════════════════
    // 核心业务方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Editor 提交审核：创建审核任务 + 迁移 ContentAsset 状态机 draft → in_review。
     *
     * @param assetId     资产 ID
     * @param submitterId 提交人 userId
     * @param riskScore   风险分（来自 RiskAssessmentService，可为 null）
     * @param riskTags    风险标签
     * @param isHighRisk  是否高风险
     * @return 创建的审核任务
     */
    @Transactional
    public ReviewTaskResponse submitForReview(UUID assetId, Long submitterId,
                                              Integer riskScore, List<String> riskTags, boolean isHighRisk) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        // 角色校验：EDITOR 及以上可提交
        requireEditorOrAbove(submitterId, workspaceId);

        // 获取并校验资产
        ContentAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !asset.getWorkspaceId().equals(wsUuid)) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }

        // 状态机校验：只能从 draft/archived 提交
        if (!asset.canSubmitReview()) {
            throw new BusinessException(CODE_CONFLICT,
                    "cannot_submit_review_from_status: " + asset.getStatus());
        }

        // 迁移资产状态 draft → in_review
        asset.setStatus(ContentAsset.STATUS_IN_REVIEW);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetMapper.updateById(asset);

        // 创建审核任务
        OffsetDateTime now = OffsetDateTime.now();
        ReviewTask task = new ReviewTask();
        task.setId(UUID.randomUUID());
        task.setWorkspaceId(wsUuid);
        task.setAssetId(assetId);
        task.setVersionNo(asset.getCurrentVersion());
        task.setSubmittedBy(submitterId);
        task.setStatus(ReviewTask.STATUS_PENDING);
        task.setIsHighRisk(isHighRisk);
        task.setRiskScore(riskScore);
        task.setRiskTags(riskTags != null ? riskTags : List.of());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        log.info("Review task {} created for asset {} by user {}, high_risk={}",
                task.getId(), assetId, submitterId, isHighRisk);

        return toResponse(task, asset);
    }

    /**
     * ADMIN/OWNER 指派审核人。
     */
    @Transactional
    public ReviewTaskResponse assignReviewer(UUID taskId, Long reviewerId, Long assignerId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        // 角色校验：ADMIN 或 OWNER
        requireAdminOrAbove(assignerId, workspaceId);

        ReviewTask task = requireTask(taskId, wsUuid);

        if (task.isDecided() || task.isCancelled()) {
            throw new BusinessException(CODE_CONFLICT, "task_already_finished");
        }

        // 校验 reviewer 是工作区成员
        WorkspaceRole reviewerRole = workspaceService.findRole(reviewerId, workspaceId);
        if (reviewerRole == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "reviewer_not_workspace_member");
        }

        OffsetDateTime now = OffsetDateTime.now();
        taskMapper.assignReviewer(taskId, reviewerId, now);

        task.setReviewerUserId(reviewerId);
        task.setStatus(ReviewTask.STATUS_IN_REVIEW);
        task.setUpdatedAt(now);

        ContentAsset asset = assetMapper.selectById(task.getAssetId());
        return toResponse(task, asset);
    }

    /**
     * Reviewer/ADMIN/OWNER 批准。
     */
    @Transactional
    public ReviewTaskResponse approve(UUID taskId, Long reviewerId, String comment) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        requireReviewerOrAbove(reviewerId, workspaceId);

        ReviewTask task = requireTask(taskId, wsUuid);

        if (!task.isInReview() && !task.isPending()) {
            throw new BusinessException(CODE_CONFLICT, "task_not_in_review");
        }

        // 迁移 ContentAsset in_review → approved
        migrateAssetStatus(task.getAssetId(), wsUuid,
                ContentAsset.STATUS_IN_REVIEW, ContentAsset.STATUS_APPROVED);

        // 更新审核任务
        OffsetDateTime now = OffsetDateTime.now();
        int updated = taskMapper.updateStatusIfMatch(
                taskId, task.getStatus(),
                ReviewTask.STATUS_APPROVED, comment, now, now);

        if (updated == 0) {
            throw new BusinessException(CODE_CONFLICT, "concurrent_status_change");
        }

        // 写批注（可选）
        if (comment != null && !comment.isBlank()) {
            addCommentInternal(taskId, comment, null, ReviewComment.TYPE_APPROVE, reviewerId);
        }

        log.info("Review task {} approved by user {}", taskId, reviewerId);

        task.setStatus(ReviewTask.STATUS_APPROVED);
        task.setComment(comment);
        task.setDecidedAt(now);
        ContentAsset asset = assetMapper.selectById(task.getAssetId());
        return toResponse(task, asset);
    }

    /**
     * Reviewer/ADMIN/OWNER 驳回（必须附 reason）。
     */
    @Transactional
    public ReviewTaskResponse reject(UUID taskId, Long reviewerId, String comment) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        requireReviewerOrAbove(reviewerId, workspaceId);

        if (comment == null || comment.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "reject_comment_required");
        }

        ReviewTask task = requireTask(taskId, wsUuid);

        if (!task.isInReview() && !task.isPending()) {
            throw new BusinessException(CODE_CONFLICT, "task_not_in_review");
        }

        // 迁移 ContentAsset in_review → draft
        migrateAssetStatus(task.getAssetId(), wsUuid,
                ContentAsset.STATUS_IN_REVIEW, ContentAsset.STATUS_DRAFT);

        // 更新审核任务
        OffsetDateTime now = OffsetDateTime.now();
        int updated = taskMapper.updateStatusIfMatch(
                taskId, task.getStatus(),
                ReviewTask.STATUS_REJECTED, comment, now, now);

        if (updated == 0) {
            throw new BusinessException(CODE_CONFLICT, "concurrent_status_change");
        }

        // 写批注
        addCommentInternal(taskId, comment, null, ReviewComment.TYPE_REJECT, reviewerId);

        log.info("Review task {} rejected by user {}: {}", taskId, reviewerId, comment);

        task.setStatus(ReviewTask.STATUS_REJECTED);
        task.setComment(comment);
        task.setDecidedAt(now);
        ContentAsset asset = assetMapper.selectById(task.getAssetId());
        return toResponse(task, asset);
    }

    /**
     * Editor 撤销自己的提交。
     */
    @Transactional
    public ReviewTaskResponse cancel(UUID taskId, Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        ReviewTask task = requireTask(taskId, wsUuid);

        // 只能是提交人本人，或 ADMIN/OWNER
        boolean isSubmitter = task.getSubmittedBy().equals(userId);
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        boolean isAdmin = role != null && role.atLeast(WorkspaceRole.ADMIN);

        if (!isSubmitter && !isAdmin) {
            throw new BusinessException(CODE_FORBIDDEN, "only_submitter_or_admin_can_cancel");
        }

        if (!task.isPending() && !task.isInReview()) {
            throw new BusinessException(CODE_CONFLICT, "task_already_finished");
        }

        // 恢复资产状态 in_review → draft
        migrateAssetStatus(task.getAssetId(), wsUuid,
                ContentAsset.STATUS_IN_REVIEW, ContentAsset.STATUS_DRAFT);

        OffsetDateTime now = OffsetDateTime.now();
        taskMapper.updateStatusIfMatch(
                taskId, task.getStatus(),
                ReviewTask.STATUS_CANCELLED, null, now, now);

        log.info("Review task {} cancelled by user {}", taskId, userId);

        task.setStatus(ReviewTask.STATUS_CANCELLED);
        task.setDecidedAt(now);
        ContentAsset asset = assetMapper.selectById(task.getAssetId());
        return toResponse(task, asset);
    }

    /**
     * 添加批注（审核人或被审核人都可加）。
     */
    @Transactional
    public ReviewTaskResponse addComment(UUID taskId, String comment,
                                         String highlightedText, Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        ReviewTask task = requireTask(taskId, wsUuid);

        // 必须是工作区成员
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }

        addCommentInternal(taskId, comment, highlightedText, ReviewComment.TYPE_COMMENT, userId);

        List<ReviewComment> comments = commentMapper.selectByTaskId(taskId);
        ContentAsset asset = assetMapper.selectById(task.getAssetId());
        return toResponseWithComments(task, asset, comments);
    }

    /**
     * Reviewer 待办队列。
     */
    public PaginatedResponse<ReviewQueueItem> getQueue(
            Long reviewerId, String status, int page, int size) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long offset = (safePage - 1L) * safeSize;

        List<ReviewTask> tasks;
        long total;

        if (status != null && !status.isBlank()) {
            tasks = taskMapper.selectByReviewerAndStatus(
                    wsUuid, reviewerId, status, safeSize, offset);
            total = tasks.isEmpty() ? 0 : 1; // 简化：超过一页不精确，前端翻页需求少
        } else {
            tasks = taskMapper.selectQueueByReviewer(wsUuid, reviewerId, safeSize, offset);
            total = taskMapper.countQueueByReviewer(wsUuid, reviewerId);
        }

        List<ReviewQueueItem> items = tasks.stream()
                .map(this::toQueueItem)
                .toList();

        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * Editor 自己的提交记录。
     */
    public PaginatedResponse<ReviewQueueItem> getMySubmissions(Long submitterId, int page, int size) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long offset = (safePage - 1L) * safeSize;

        List<ReviewTask> tasks = taskMapper.selectBySubmitter(
                wsUuid, submitterId, safeSize, offset);
        long total = taskMapper.countBySubmitter(wsUuid, submitterId);

        List<ReviewQueueItem> items = tasks.stream()
                .map(this::toQueueItem)
                .toList();

        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 获取审核任务详情。
     */
    public ReviewTaskResponse getById(UUID taskId, Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        ReviewTask task = requireTask(taskId, wsUuid);
        List<ReviewComment> comments = commentMapper.selectByTaskId(taskId);
        ContentAsset asset = assetMapper.selectById(task.getAssetId());

        return toResponseWithComments(task, asset, comments);
    }

    /**
     * 获取审核任务批注列表。
     */
    public List<ReviewCommentResponse> getComments(UUID taskId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        String wsUuid = workspaceId;

        requireTask(taskId, wsUuid);

        return commentMapper.selectByTaskId(taskId).stream()
                .map(c -> ReviewCommentResponse.builder()
                        .id(c.getId())
                        .reviewTaskId(c.getReviewTaskId())
                        .comment(c.getComment())
                        .highlightedText(c.getHighlightedText())
                        .commentType(c.getCommentType())
                        .createdBy(c.getCreatedBy())
                        .creatorNickname(resolveNickname(c.getCreatedBy()))
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 为高风险资产自动创建审核任务（由合规扫描调用，不阻塞扫描流程）。
     */
    @Transactional
    public void createHighRiskTask(UUID assetId, Long actorId,
                                   Integer riskScore, List<String> riskTags) {
        try {
            String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
            String wsUuid = workspaceId;

            ContentAsset asset = assetMapper.selectById(assetId);
            if (asset == null) {
                log.warn("createHighRiskTask: asset {} not found", assetId);
                return;
            }

            // 已有同版本未完成审核任务，跳过
            Optional<ReviewTask> existing = taskMapper.selectByAssetAndVersion(
                    assetId, asset.getCurrentVersion());
            if (existing.isPresent()) {
                ReviewTask t = existing.get();
                if (!t.isDecided() && !t.isCancelled()) {
                    log.debug("High-risk task already exists for asset {} v{}",
                            assetId, asset.getCurrentVersion());
                    return;
                }
            }

            // 高风险强制进入审核流
            submitForReview(assetId, actorId, riskScore, riskTags, true);
            log.info("High-risk review task auto-created for asset {}", assetId);

        } catch (Exception e) {
            // 不阻塞合规扫描流程，只记录错误
            log.error("Failed to create high-risk review task for asset {}: {}",
                    assetId, e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 私有辅助方法
    // ═══════════════════════════════════════════════════════════════════════════

    private void requireEditorOrAbove(Long userId, String workspaceId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        if (!role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "editor_role_required");
        }
    }

    private void requireReviewerOrAbove(Long userId, String workspaceId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        if (!role.atLeast(WorkspaceRole.REVIEWER)) {
            throw new BusinessException(CODE_FORBIDDEN, "reviewer_role_required");
        }
    }

    private void requireAdminOrAbove(Long userId, String workspaceId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        if (!role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "admin_role_required");
        }
    }

    private ReviewTask requireTask(UUID taskId, String workspaceId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(CODE_NOT_FOUND, "review_task_not_found");
        }
        return task;
    }

    private void migrateAssetStatus(UUID assetId, String workspaceId,
                                    String from, String to) {
        ContentAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !asset.getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }
        if (!from.equals(asset.getStatus())) {
            // 已经是目标状态，安全幂等
            if (!to.equals(asset.getStatus())) {
                log.warn("Asset {} status is {}, expected {} for review transition",
                        assetId, asset.getStatus(), from);
            }
            return;
        }
        asset.setStatus(to);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetMapper.updateById(asset);
    }

    private void addCommentInternal(UUID taskId, String comment,
                                    String highlightedText, String commentType, Long userId) {
        ReviewComment c = new ReviewComment();
        c.setId(UUID.randomUUID());
        c.setReviewTaskId(taskId);
        c.setComment(comment);
        c.setHighlightedText(highlightedText);
        c.setCommentType(commentType);
        c.setCreatedBy(userId);
        c.setCreatedAt(OffsetDateTime.now());
        commentMapper.insert(c);
    }

    private ReviewQueueItem toQueueItem(ReviewTask task) {
        String submitterNickname = resolveNickname(task.getSubmittedBy());
        return ReviewQueueItem.builder()
                .id(task.getId())
                .assetId(task.getAssetId())
                .status(task.getStatus())
                .isHighRisk(task.getIsHighRisk())
                .riskScore(task.getRiskScore())
                .versionNo(task.getVersionNo())
                .submittedBy(task.getSubmittedBy())
                .submitterNickname(submitterNickname)
                .createdAt(task.getCreatedAt())
                .decidedAt(task.getDecidedAt())
                .build();
    }

    private ReviewTaskResponse toResponse(ReviewTask task, ContentAsset asset) {
        return toResponseWithComments(task, asset, List.of());
    }

    private ReviewTaskResponse toResponseWithComments(
            ReviewTask task, ContentAsset asset, List<ReviewComment> comments) {

        // 填充资产快照
        ReviewTaskResponse.AssetSnapshot assetSnapshot = null;
        String submitterNickname = null;
        String reviewerNickname = null;

        if (asset != null) {
            assetSnapshot = ReviewTaskResponse.AssetSnapshot.builder()
                    .id(asset.getId())
                    .title(asset.getTitle())
                    .body(asset.getBody())
                    .persona(asset.getPersona())
                    .platform(asset.getPlatform())
                    .accountId(asset.getAccountId())
                    .currentVersion(asset.getCurrentVersion())
                    .build();
        }

        // 填充提交人/审核人昵称
        Map<Long, String> nicknameCache = new HashMap<>();
        Function<Long, String> resolve = userId -> {
            if (userId == null) return null;
            return nicknameCache.computeIfAbsent(userId,
                    id -> resolveNickname(id));
        };

        submitterNickname = resolve.apply(task.getSubmittedBy());
        reviewerNickname = resolve.apply(task.getReviewerUserId());

        // 批注列表
        List<ReviewTaskResponse.ReviewCommentItem> commentItems = comments.stream()
                .map(c -> ReviewTaskResponse.ReviewCommentItem.builder()
                        .id(c.getId())
                        .comment(c.getComment())
                        .highlightedText(c.getHighlightedText())
                        .commentType(c.getCommentType())
                        .createdBy(c.getCreatedBy())
                        .creatorNickname(resolve.apply(c.getCreatedBy()))
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();

        return ReviewTaskResponse.builder()
                .id(task.getId())
                .workspaceId(task.getWorkspaceId())
                .assetId(task.getAssetId())
                .versionNo(task.getVersionNo())
                .submittedBy(task.getSubmittedBy())
                .submitterNickname(submitterNickname)
                .reviewerUserId(task.getReviewerUserId())
                .reviewerNickname(reviewerNickname)
                .status(task.getStatus())
                .isHighRisk(task.getIsHighRisk())
                .riskScore(task.getRiskScore())
                .riskTags(task.getRiskTags())
                .comment(task.getComment())
                .decidedAt(task.getDecidedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .asset(assetSnapshot)
                .comments(commentItems)
                .build();
    }

    private String resolveNickname(Long userId) {
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        return user != null ? user.getDisplayName() : null;
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_" + field);
        }
    }
}
