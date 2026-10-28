package com.shortvideoscripagent.xhsagentyunying.template;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.template.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 模板库 Controller（P0-4）。
 * <p>
 * RESTful API：/v1/templates
 */
@RestController
@RequestMapping("/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final TemplateService templateService;

    // ── 查询 ──────────────────────────────────────────────────────────────

    /**
     * 分页查询模板列表。
     *
     * @param scope     筛选范围：official / workspace / personal（可选，不传则返回全部有权限模板）
     * @param category  筛选分类（可选）
     * @param page      页码（默认 1）
     * @param size      每页条数（默认 20，最大 50）
     */
    @GetMapping
    public ApiResponse<PaginatedResponse<TemplateResponse>> list(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = requireUserId();
        // scope 参数转大写：official → OFFICIAL
        String normalized = scope != null ? scope.toUpperCase() : null;
        return ApiResponse.ok(templateService.list(userId, normalized, category, page, size));
    }

    /**
     * 获取单个模板详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<TemplateResponse> getById(@PathVariable UUID id) {
        Long userId = requireUserId();
        return ApiResponse.ok(templateService.getById(userId, id));
    }

    // ── 创建 / 更新 ───────────────────────────────────────────────────────

    /**
     * 创建模板（仅 WORKSPACE / PERSONAL）。
     */
    @PostMapping
    public ApiResponse<TemplateResponse> create(@Valid @RequestBody TemplateCreateRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(templateService.create(userId, request));
    }

    /**
     * 更新模板（OFFICIAL 不可修改）。
     */
    @PatchMapping("/{id}")
    public ApiResponse<TemplateResponse> update(
            @PathVariable UUID id,
            @RequestBody TemplateUpdateRequest request
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(templateService.update(userId, id, request));
    }

    // ── 发布 / 归档 ───────────────────────────────────────────────────────

    /**
     * 发布模板（WORKSPACE/PERSONAL：生成新版本快照）。
     * OFFICIAL：由 ADMIN 角色发布（由管理员后台处理，此接口也可调用）。
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<TemplateResponse> publish(@PathVariable UUID id) {
        Long userId = requireUserId();
        return ApiResponse.ok(templateService.publish(userId, id));
    }

    /**
     * 归档模板（OFFICIAL 不可归档）。
     */
    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable UUID id) {
        Long userId = requireUserId();
        templateService.archive(userId, id);
        return ApiResponse.ok(null);
    }

    // ── 复制 ──────────────────────────────────────────────────────────────

    /**
     * 复制模板到目标工作区。
     * body: { "targetWorkspaceId": "uuid" }
     */
    @PostMapping("/{id}/duplicate")
    public ApiResponse<TemplateResponse> duplicate(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        Long userId = requireUserId();
        String targetWorkspaceId = body != null ? body.get("targetWorkspaceId") : null;
        if (targetWorkspaceId == null || targetWorkspaceId.isBlank()) {
            throw new BusinessException(40010, "targetWorkspaceId is required");
        }
        return ApiResponse.ok(templateService.duplicate(userId, id, targetWorkspaceId));
    }

    // ── 渲染 / 应用 ───────────────────────────────────────────────────────

    /**
     * 预览渲染结果。
     * body: { "variables": {"platform": "小红书", "topic": "收纳"} }
     */
    @PostMapping("/{id}/render")
    public ApiResponse<TemplateRenderResponse> render(
            @PathVariable UUID id,
            @Valid @RequestBody TemplateRenderRequest request
    ) {
        return ApiResponse.ok(templateService.render(id, request.getVariables()));
    }

    /**
     * 应用模板到资产（渲染 + 记录 apply_log + use_count++）。
     * body: { "assetId": "uuid", "variables": {...} }
     */
    @PostMapping("/{id}/apply")
    public ApiResponse<TemplateApplyResponse> apply(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        Long userId = requireUserId();
        String assetIdStr = body != null ? (String) body.get("assetId") : null;
        @SuppressWarnings("unchecked")
        Map<String, String> variables = body != null
                ? (Map<String, String>) body.get("variables")
                : Map.of();

        UUID assetId = assetIdStr != null ? UUID.fromString(assetIdStr) : null;
        TemplateService.TemplateApplyResult result = templateService.apply(userId, id, assetId, variables);

        return ApiResponse.ok(TemplateApplyResponse.builder()
                .templateId(id.toString())
                .body(result.body())
                .useCount(result.updatedUseCount())
                .build());
    }

    // ── 统计 ──────────────────────────────────────────────────────────────

    /**
     * 获取模板引用统计（最近 30 天，按日聚合）。
     */
    @GetMapping("/{id}/apply-stats")
    public ApiResponse<TemplateApplyStatsResponse> getApplyStats(@PathVariable UUID id) {
        return ApiResponse.ok(templateService.getApplyStats(id));
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
