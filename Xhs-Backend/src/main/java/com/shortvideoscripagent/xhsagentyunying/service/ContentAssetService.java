package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetMedia;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetVersion;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMediaMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetVersionMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.*;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内容资产 Service（P0-3）。
 * <p>
 * 核心能力：CRUD + 版本管理 + 自动保存 + 回滚 + 多媒体附件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAssetService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_CONFLICT = 40910;
    private static final int CODE_BAD_REQUEST = 40010;

    /** 自动保存窗口：30 秒内的多次 update 合并为 1 个版本 */
    private static final Duration AUTOSAVE_WINDOW = Duration.ofSeconds(30);

    /** Redis key 前缀：记录上次保存时间戳 */
    private static final String AUTOSAVE_KEY_PREFIX = "asset:autosave:";

    private final ContentAssetMapper assetMapper;
    private final ContentAssetVersionMapper versionMapper;
    private final ContentAssetMediaMapper mediaMapper;
    private final WorkspaceService workspaceService;
    private final ReviewService reviewService;
    private final RiskAssessmentService riskAssessmentService;
    private final StringRedisTemplate redisTemplate;

    // ── 创建（内部/Worker 调用，无 WorkspaceContext）────────────────────────────────

    /**
     * 创建草稿资产（Worker 线程内部调用，不依赖 WorkspaceContext）。
     */
    @Transactional
    public AssetResponse createDraftInternal(Long ownerId, UUID workspaceId, AssetCreateRequest request) {
        if (workspaceId == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
        if (ownerId == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "owner_id_required");
        }
        UUID sourceTaskId = parseTaskId(request.getSourceTaskId());
        UUID templateId = parseTaskId(request.getTemplateId());
        OffsetDateTime now = OffsetDateTime.now();

        ContentAsset asset = new ContentAsset();
        asset.setId(UUID.randomUUID());
        asset.setWorkspaceId(workspaceId);
        asset.setOwnerUserId(ownerId);
        asset.setTitle(trimOrThrow(request.getTitle(), "title is required"));
        asset.setBody(request.getBody() == null ? "" : request.getBody());
        asset.setPersona(request.getPersona());
        asset.setScenario(request.getScenario());
        asset.setIndustry(request.getIndustry());
        asset.setStatus(ContentAsset.STATUS_DRAFT);
        asset.setSourceTaskId(sourceTaskId);
        asset.setTemplateId(templateId);
        asset.setPlatform(request.getPlatform());
        asset.setAccountId(request.getAccountId());
        asset.setCurrentVersion(1);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        assetMapper.insert(asset);

        // 同时创建 v1 版本快照
        saveVersionSnapshotInternal(asset, ownerId, "initial draft");

        return toResponse(asset);
    }

    // ── 创建 ──────────────────────────────────────────────────────────────

    /**
     * 创建草稿资产，自动创建 v1 版本记录。
     */
    @Transactional
    public AssetResponse createDraft(Long ownerId, AssetCreateRequest request) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(ownerId, workspaceId);

        UUID wsUuid = parseUuid(workspaceId, "workspace_id");
        UUID sourceTaskId = parseTaskId(request.getSourceTaskId());
        UUID templateId = parseTaskId(request.getTemplateId());
        OffsetDateTime now = OffsetDateTime.now();

        ContentAsset asset = new ContentAsset();
        asset.setId(UUID.randomUUID());
        asset.setWorkspaceId(wsUuid);
        asset.setOwnerUserId(ownerId);
        asset.setTitle(trimOrThrow(request.getTitle(), "title is required"));
        asset.setBody(request.getBody() == null ? "" : request.getBody());
        asset.setPersona(request.getPersona());
        asset.setScenario(request.getScenario());
        asset.setIndustry(request.getIndustry());
        asset.setStatus(ContentAsset.STATUS_DRAFT);
        asset.setSourceTaskId(sourceTaskId);
        asset.setTemplateId(templateId);
        asset.setPlatform(request.getPlatform());
        asset.setAccountId(request.getAccountId());
        asset.setCurrentVersion(1);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        assetMapper.insert(asset);

        // 同时创建 v1 版本快照
        saveVersionSnapshot(asset, ownerId, "initial draft");

        return toResponse(asset);
    }

    // ── 读取 ──────────────────────────────────────────────────────────────

    /**
     * 分页查询资产列表（workspace 隔离）。
     */
    public PaginatedResponse<AssetResponse> list(
            Long userId,
            int page,
            int size,
            String status
    ) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long offset = (safePage - 1L) * safeSize;

        Page<ContentAsset> p = new Page<>(safePage, safeSize);
        IPage<ContentAsset> result = assetMapper.selectPageByWorkspace(
                p, parseUuid(workspaceId, "workspace_id"), status, offset, safeSize);
        long total = result.getTotal();

        List<AssetResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .toList();

        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 获取单个资产详情（workspace 隔离）。
     */
    public AssetResponse getById(Long userId, UUID assetId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);
        return toResponse(asset);
    }

    // ── 更新 ──────────────────────────────────────────────────────────────

    /**
     * 更新资产，自动创建新版本（自动保存节流：30s 内的多次更新合并为 1 个版本）。
     */
    @Transactional
    public AssetResponse update(Long editorId, UUID assetId, AssetUpdateRequest request) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(editorId, workspaceId);

        ContentAsset asset = requireOwnedAsset(editorId, assetId, workspaceId);
        if (!asset.isEditable()) {
            throw new BusinessException(CODE_CONFLICT, "asset_not_editable");
        }

        String autosaveKey = AUTOSAVE_KEY_PREFIX + assetId;
        Long ttl = redisTemplate.getExpire(autosaveKey);

        // 字段更新
        boolean dirty = false;
        if (request.getTitle() != null) {
            asset.setTitle(request.getTitle().trim());
            dirty = true;
        }
        if (request.getBody() != null) {
            asset.setBody(request.getBody());
            dirty = true;
        }
        if (request.getPersona() != null) {
            asset.setPersona(request.getPersona());
            dirty = true;
        }
        if (request.getScenario() != null) {
            asset.setScenario(request.getScenario());
            dirty = true;
        }
        if (request.getIndustry() != null) {
            asset.setIndustry(request.getIndustry());
            dirty = true;
        }
        if (request.getPlatform() != null) {
            asset.setPlatform(request.getPlatform());
            dirty = true;
        }
        if (request.getAccountId() != null) {
            asset.setAccountId(request.getAccountId());
            dirty = true;
        }

        // 状态变更（仅允许状态机迁移）
        if (request.getStatus() != null && !request.getStatus().equals(asset.getStatus())) {
            validateStatusTransition(asset.getStatus(), request.getStatus());
            asset.setStatus(request.getStatus());
            dirty = true;
        }

        if (!dirty) {
            return toResponse(asset);
        }

        // 自动保存节流：如果上次保存距今 < 30s，不产生新版本
        boolean createVersion;
        if (ttl != null && ttl > 0) {
            log.debug("Autosave throttled for asset {} (ttl={}s)", assetId, ttl);
            createVersion = false;
        } else {
            createVersion = true;
            // 写入 autosave 标记，30s 后自动失效
            redisTemplate.opsForValue().set(autosaveKey, "1", AUTOSAVE_WINDOW);
        }

        asset.setCurrentVersion(asset.getCurrentVersion() + 1);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetMapper.updateById(asset);

        if (createVersion) {
            String reason = request.getEditReason() != null
                    ? request.getEditReason()
                    : "manual edit";
            saveVersionSnapshot(asset, editorId, reason);
        }

        return toResponse(asset);
    }

    // ── 删除 ──────────────────────────────────────────────────────────────

    /**
     * 删除资产（级联删除版本和媒体）。
     */
    @Transactional
    public void delete(Long userId, UUID assetId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(userId, workspaceId);

        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);
        assetMapper.deleteById(asset);
        // 外键 ON DELETE CASCADE 自动清理 version 和 media
        log.info("Asset {} deleted by user {}", assetId, userId);
    }

    // ── 版本管理 ──────────────────────────────────────────────────────────

    /**
     * 查询所有历史版本。
     */
    public List<AssetVersionResponse> listVersions(UUID assetId) {
        return versionMapper.selectByAssetId(assetId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    /**
     * 回滚到指定版本（产生新版本号，不覆盖历史）。
     */
    @Transactional
    public AssetResponse rollbackToVersion(Long editorId, UUID assetId, int targetVersionNo) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(editorId, workspaceId);

        ContentAsset asset = requireOwnedAsset(editorId, assetId, workspaceId);
        if (!asset.isEditable()) {
            throw new BusinessException(CODE_CONFLICT, "asset_not_editable");
        }

        ContentAssetVersion target = versionMapper
                .selectByAssetIdAndVersion(assetId, targetVersionNo)
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND, "version_not_found"));

        // 写入新版本号，复制快照内容
        asset.setTitle(target.getTitle());
        asset.setBody(target.getBody());
        asset.setCurrentVersion(asset.getCurrentVersion() + 1);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetMapper.updateById(asset);

        saveVersionSnapshot(asset, editorId, "rollback to v" + targetVersionNo);

        return toResponse(asset);
    }

    /**
     * 简单 line-based diff：返回两版本的行差异（add/remove/unchanged）。
     */
    public List<Map<String, Object>> diffVersions(UUID assetId, int v1, int v2) {
        ContentAssetVersion a = versionMapper.selectByAssetIdAndVersion(assetId, v1)
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND, "version_not_found"));
        ContentAssetVersion b = versionMapper.selectByAssetIdAndVersion(assetId, v2)
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND, "version_not_found"));

        return computeLineDiff(a.getBody(), b.getBody());
    }

    // ── 状态机迁移 ─────────────────────────────────────────────────────────

    /**
     * 提交审核：draft → in_review。
     * 委托给 ReviewService 处理审核任务创建、风险评估和状态机迁移。
     */
    @Transactional
    public AssetResponse submitReview(Long userId, UUID assetId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(userId, workspaceId);
        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);
        if (!asset.canSubmitReview()) {
            throw new BusinessException(CODE_CONFLICT, "cannot_submit_review_from_status: " + asset.getStatus());
        }

        // 风险评估（不阻塞提交流程）
        RiskAssessmentService.RiskResult riskResult;
        try {
            riskResult = riskAssessmentService.assessAsset(assetId);
        } catch (Exception e) {
            log.warn("Risk assessment failed for asset {}, proceeding without risk data: {}",
                    assetId, e.getMessage());
            riskResult = new RiskAssessmentService.RiskResult(0, List.of(), false);
        }

        // 委托审核服务处理完整流程（创建 ReviewTask + 状态机迁移）
        reviewService.submitForReview(
                assetId, userId,
                riskResult.riskScore(), riskResult.riskTags(), riskResult.isHighRisk());

        // 重新查询最新资产状态返回
        asset = assetMapper.selectById(assetId);
        return toResponse(asset);
    }

    // ── 多媒体附件 ──────────────────────────────────────────────────────────

    /**
     * 添加媒体附件。
     */
    @Transactional
    public AssetResponse addMedia(UUID assetId, AssetMediaRequest request, Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);

        ContentAssetMedia media = new ContentAssetMedia();
        media.setId(UUID.randomUUID());
        media.setAssetId(assetId);
        media.setMediaType(request.getMediaType());
        media.setObjectKey(request.getObjectKey());
        media.setRole(request.getRole() != null ? request.getRole() : ContentAssetMedia.ROLE_ATTACHMENT);
        media.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        media.setWidth(request.getWidth());
        media.setHeight(request.getHeight());
        media.setFileSize(request.getFileSize());
        media.setCreatedAt(OffsetDateTime.now());
        mediaMapper.insert(media);

        return toResponse(asset);
    }

    /**
     * 删除媒体附件。
     */
    @Transactional
    public AssetResponse removeMedia(UUID assetId, UUID mediaId, Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);

        ContentAssetMedia media = mediaMapper.selectById(mediaId);
        if (media == null || !media.getAssetId().equals(assetId)) {
            throw new BusinessException(CODE_NOT_FOUND, "media_not_found");
        }
        mediaMapper.deleteById(mediaId);
        return toResponse(asset);
    }

    /**
     * 更新资产标签。
     */
    @Transactional
    public AssetResponse updateTags(Long userId, UUID assetId,
                                     List<String> addTags, List<String> removeTags, List<String> replaceTags) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(userId, workspaceId);
        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);
        if (!asset.isEditable()) {
            throw new BusinessException(CODE_CONFLICT, "asset_not_editable");
        }

        List<String> currentTags = parseTags(asset.getTags());

        if (replaceTags != null && !replaceTags.isEmpty()) {
            currentTags = replaceTags.stream()
                    .filter(t -> t != null && t.length() <= 20)
                    .distinct()
                    .limit(20)
                    .toList();
        } else {
            if (addTags != null && !addTags.isEmpty()) {
                for (String t : addTags) {
                    if (t != null && !currentTags.contains(t)
                            && currentTags.size() < 20 && t.length() <= 20) {
                        currentTags.add(t);
                    }
                }
            }
            if (removeTags != null && !removeTags.isEmpty()) {
                currentTags.removeAll(removeTags);
            }
        }

        asset.setTags(toJson(currentTags));
        asset.setUpdatedAt(OffsetDateTime.now());
        assetMapper.updateById(asset);
        return toResponse(asset);
    }

    /**
     * 批量归档资产（不产生版本快照）。
     */
    @Transactional
    public Map<String, Object> batchArchive(Long userId, List<UUID> assetIds) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(userId, workspaceId);
        UUID wsUuid = parseUuid(workspaceId, "workspace_id");

        int archived = 0, skipped = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        for (UUID assetId : assetIds) {
            try {
                ContentAsset asset = assetMapper.selectById(assetId);
                if (asset == null || !asset.getWorkspaceId().equals(wsUuid)) {
                    skipped++;
                    continue;
                }
                asset.setStatus(ContentAsset.STATUS_ARCHIVED);
                asset.setUpdatedAt(OffsetDateTime.now());
                assetMapper.updateById(asset);
                archived++;
            } catch (Exception e) {
                errors.add(Map.of("id", assetId.toString(), "error", e.getMessage()));
            }
        }
        return Map.of("archived", archived, "skipped", skipped, "errors", errors);
    }

    /**
     * Autosave 专用端点（不产生版本快照，30s 节流）。
     */
    @Transactional
    public AssetResponse autosave(Long userId, UUID assetId, String title, String body) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireEditorRole(userId, workspaceId);

        ContentAsset asset = requireOwnedAsset(userId, assetId, workspaceId);
        if (!asset.isEditable()) {
            throw new BusinessException(CODE_CONFLICT, "asset_not_editable");
        }

        String autosaveKey = AUTOSAVE_KEY_PREFIX + assetId;
        Long ttl = redisTemplate.getExpire(autosaveKey);
        if (ttl != null && ttl > 0) {
            log.debug("Autosave throttled for asset {} (ttl={}s)", assetId, ttl);
            return toResponse(asset);
        }

        boolean dirty = false;
        if (title != null && !title.equals(asset.getTitle())) {
            asset.setTitle(title.trim());
            dirty = true;
        }
        if (body != null && !body.equals(asset.getBody())) {
            asset.setBody(body);
            dirty = true;
        }

        if (!dirty) {
            return toResponse(asset);
        }

        asset.setCurrentVersion(asset.getCurrentVersion() + 1);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetMapper.updateById(asset);
        redisTemplate.opsForValue().set(autosaveKey, "1", AUTOSAVE_WINDOW);

        return toResponse(asset);
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────

    /**
     * 要求用户具有 EDITOR 及以上权限（OWNER / ADMIN / EDITOR 可写）。
     */
    private void requireEditorRole(Long userId, String workspaceId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        if (!role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "editor_role_required");
        }
    }

    private ContentAsset requireOwnedAsset(Long userId, UUID assetId, String workspaceId) {
        UUID wsUuid = parseUuid(workspaceId, "workspace_id");
        ContentAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }
        // workspace 隔离检查
        if (!asset.getWorkspaceId().equals(wsUuid)) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }
        return asset;
    }

    private void saveVersionSnapshotInternal(ContentAsset asset, Long editedBy, String reason) {
        ContentAssetVersion version = new ContentAssetVersion();
        version.setId(UUID.randomUUID());
        version.setAssetId(asset.getId());
        version.setVersionNo(asset.getCurrentVersion());
        version.setTitle(asset.getTitle());
        version.setBody(asset.getBody());
        version.setEditedBy(editedBy);
        version.setEditReason(reason);
        version.setCreatedAt(OffsetDateTime.now());
        versionMapper.insert(version);
    }

    private void saveVersionSnapshot(ContentAsset asset, Long editedBy, String reason) {
        ContentAssetVersion version = new ContentAssetVersion();
        version.setId(UUID.randomUUID());
        version.setAssetId(asset.getId());
        version.setVersionNo(asset.getCurrentVersion());
        version.setTitle(asset.getTitle());
        version.setBody(asset.getBody());
        version.setEditedBy(editedBy);
        version.setEditReason(reason);
        version.setCreatedAt(OffsetDateTime.now());
        versionMapper.insert(version);
    }

    private void validateStatusTransition(String current, String target) {
        // draft / archived 可进入 in_review
        if (("draft".equals(current) || "archived".equals(current))
                && "in_review".equals(target)) {
            return;
        }
        // in_review → approved / draft（驳回）
        if ("in_review".equals(current) && (
                "approved".equals(target) || "draft".equals(target))) {
            return;
        }
        // approved → scheduled / archived
        if ("approved".equals(current) && (
                "scheduled".equals(target) || "archived".equals(target))) {
            return;
        }
        // scheduled → published / archived
        if ("scheduled".equals(current) && (
                "published".equals(target) || "archived".equals(target))) {
            return;
        }
        throw new BusinessException(CODE_BAD_REQUEST,
                "invalid_status_transition: " + current + " → " + target);
    }

    /**
     * 简单 line-based diff。
     */
    private List<Map<String, Object>> computeLineDiff(String textA, String textB) {
        List<String> linesA = textA != null ? Arrays.asList(textA.split("\n")) : List.of();
        List<String> linesB = textB != null ? Arrays.asList(textB.split("\n")) : List.of();

        Map<String, Integer> countA = linesA.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        Map<String, Integer> countB = linesB.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String line : linesA) {
            if (!seen.contains(line)) {
                seen.add(line);
                int aCount = countA.getOrDefault(line, 0);
                int bCount = countB.getOrDefault(line, 0);
                String type;
                if (bCount == 0) {
                    type = "removed";
                } else if (bCount < aCount) {
                    type = "modified";
                } else {
                    type = "unchanged";
                }
                result.add(Map.of("type", type, "content", line, "v", 1));
            }
        }
        for (String line : linesB) {
            if (!seen.contains(line)) {
                seen.add(line);
                int bCount = countB.getOrDefault(line, 0);
                int aCount = countA.getOrDefault(line, 0);
                if (aCount == 0) {
                    result.add(Map.of("type", "added", "content", line, "v", 2));
                }
            }
        }
        return result;
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tags JSON, returning empty list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> tags) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("Failed to serialize tags list, returning empty: {}", e.getMessage());
            return "[]";
        }
    }

    private AssetResponse toResponse(ContentAsset asset) {
        List<ContentAssetMedia> mediaList = mediaMapper.selectByAssetId(asset.getId());
        List<MediaItem> mediaItems = mediaList.stream()
                .map(m -> {
                    MediaItem item = new MediaItem();
                    item.setId(m.getId());
                    item.setMediaType(m.getMediaType());
                    item.setObjectKey(m.getObjectKey());
                    item.setRole(m.getRole());
                    item.setSortOrder(m.getSortOrder());
                    item.setWidth(m.getWidth());
                    item.setHeight(m.getHeight());
                    item.setFileSize(m.getFileSize());
                    item.setCreatedAt(m.getCreatedAt());
                    return item;
                })
                .toList();

        return AssetResponse.builder()
                .id(asset.getId())
                .workspaceId(asset.getWorkspaceId())
                .ownerUserId(asset.getOwnerUserId())
                .title(asset.getTitle())
                .body(asset.getBody())
                .persona(asset.getPersona())
                .scenario(asset.getScenario())
                .industry(asset.getIndustry())
                .status(asset.getStatus())
                .sourceTaskId(asset.getSourceTaskId())
                .templateId(asset.getTemplateId())
                .platform(asset.getPlatform())
                .accountId(asset.getAccountId())
                .currentVersion(asset.getCurrentVersion())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .tags(parseTags(asset.getTags()))
                .media(mediaItems)
                .build();
    }

    private AssetVersionResponse toVersionResponse(ContentAssetVersion v) {
        return AssetVersionResponse.builder()
                .id(v.getId())
                .assetId(v.getAssetId())
                .versionNo(v.getVersionNo())
                .title(v.getTitle())
                .body(v.getBody())
                .editedBy(v.getEditedBy())
                .editReason(v.getEditReason())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_" + field);
        }
    }

    private UUID parseTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        try {
            // 分析任务 ID 格式是 ana_xxx，存储时取 hash 转 UUID 兼容外键
            return UUID.nameUUIDFromBytes(taskId.getBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private String trimOrThrow(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, message);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(CODE_BAD_REQUEST, message);
        }
        return trimmed;
    }
}
