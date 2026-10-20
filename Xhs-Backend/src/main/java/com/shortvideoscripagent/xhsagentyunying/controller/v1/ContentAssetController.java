package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.*;
import com.shortvideoscripagent.xhsagentyunying.service.ContentAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内容资产 Controller（P0-3）。
 * <p>
 * 前缀：/v1/assets
 */
@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
public class ContentAssetController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_BAD_REQUEST = 40010;

    private final ContentAssetService contentAssetService;

    // ── CRUD ────────────────────────────────────────────────────────────────

    /**
     * 创建内容资产（草稿）。
     */
    @PostMapping
    public ApiResponse<AssetResponse> create(@Valid @RequestBody AssetCreateRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(contentAssetService.createDraft(userId, request));
    }

    /**
     * 分页查询资产列表（支持状态过滤）。
     */
    @GetMapping
    public ApiResponse<PaginatedResponse<AssetResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(contentAssetService.list(userId, page, size, status));
    }

    /**
     * 获取单个资产详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AssetResponse> getById(@PathVariable String id) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        return ApiResponse.ok(contentAssetService.getById(userId, assetId));
    }

    /**
     * 更新资产（自动创建新版本，支持自动保存节流）。
     */
    @PatchMapping("/{id}")
    public ApiResponse<AssetResponse> update(
            @PathVariable String id,
            @RequestBody AssetUpdateRequest request
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        return ApiResponse.ok(contentAssetService.update(userId, assetId, request));
    }

    /**
     * 删除资产。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        contentAssetService.delete(userId, assetId);
        return ApiResponse.ok(null);
    }

    // ── 版本管理 ───────────────────────────────────────────────────────────

    /**
     * 查询所有历史版本。
     */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<AssetVersionResponse>> listVersions(@PathVariable String id) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        // 先校验资产所有权
        contentAssetService.getById(userId, assetId);
        return ApiResponse.ok(contentAssetService.listVersions(assetId));
    }

    /**
     * 回滚到指定版本（产生新版本号）。
     */
    @PostMapping("/{id}/rollback/{versionNo}")
    public ApiResponse<AssetResponse> rollback(
            @PathVariable String id,
            @PathVariable int versionNo
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        return ApiResponse.ok(contentAssetService.rollbackToVersion(userId, assetId, versionNo));
    }

    /**
     * 对比两个版本的正文差异（line-based diff）。
     */
    @GetMapping("/{id}/diff")
    public ApiResponse<List<Map<String, Object>>> diffVersions(
            @PathVariable String id,
            @RequestParam int v1,
            @RequestParam int v2
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        // 校验资产所有权
        contentAssetService.getById(userId, assetId);
        return ApiResponse.ok(contentAssetService.diffVersions(assetId, v1, v2));
    }

    // ── 状态机迁移 ─────────────────────────────────────────────────────────

    /**
     * 提交审核（draft → in_review），预留给 P0-6 审核流。
     */
    @PostMapping("/{id}/submit-review")
    public ApiResponse<AssetResponse> submitReview(@PathVariable String id) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        return ApiResponse.ok(contentAssetService.submitReview(userId, assetId));
    }

    // ── 多媒体附件 ─────────────────────────────────────────────────────────

    /**
     * 添加媒体附件。
     */
    @PostMapping("/{id}/media")
    public ApiResponse<AssetResponse> addMedia(
            @PathVariable String id,
            @Valid @RequestBody AssetMediaRequest request
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        return ApiResponse.ok(contentAssetService.addMedia(assetId, request, userId));
    }

    /**
     * 删除媒体附件。
     */
    @DeleteMapping("/{id}/media/{mediaId}")
    public ApiResponse<AssetResponse> removeMedia(
            @PathVariable String id,
            @PathVariable String mediaId
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        UUID mediaIdVal = parseUuid(mediaId);
        return ApiResponse.ok(contentAssetService.removeMedia(assetId, mediaIdVal, userId));
    }

    /**
     * 更新资产标签。
     */
    @PatchMapping("/{id}/tags")
    public ApiResponse<AssetResponse> updateTags(
            @PathVariable String id,
            @Valid @RequestBody AssetTagsRequest body
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);

        return ApiResponse.ok(contentAssetService.updateTags(userId, assetId,
                body.getAddTags(), body.getRemoveTags(), body.getTags()));
    }

    /**
     * 批量归档资产。
     */
    @PostMapping("/batch-archive")
    public ApiResponse<Map<String, Object>> batchArchive(@RequestBody Map<String, Object> body) {
        Long userId = requireUserId();

        @SuppressWarnings("unchecked")
        List<String> rawIds = body.containsKey("assetIds")
                ? (List<String>) body.get("assetIds") : List.of();
        if (rawIds.size() > 100) {
            throw new BusinessException(CODE_BAD_REQUEST, "max_100_assets_per_request");
        }
        List<UUID> ids = rawIds.stream().map(this::parseUuid).toList();

        return ApiResponse.ok(contentAssetService.batchArchive(userId, ids));
    }

    /**
     * Autosave 专用端点（不产生版本快照）。
     */
    @PostMapping("/{id}/autosave")
    public ApiResponse<AssetResponse> autosave(
            @PathVariable String id,
            @Valid @RequestBody AssetAutosaveRequest body
    ) {
        Long userId = requireUserId();
        UUID assetId = parseUuid(id);
        return ApiResponse.ok(contentAssetService.autosave(userId, assetId,
                body.getTitle(), body.getBody()));
    }

    // ── 私有 ──────────────────────────────────────────────────────────────

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
