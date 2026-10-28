package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ComparisonCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ComparisonResponse;
import com.shortvideoscripagent.xhsagentyunying.service.ComparisonService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S3-6 预测 vs 实际偏差对比 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/comparisons}
 * → {@code /api/v1/comparisons}。
 * </p>
 */
@RestController
@RequestMapping("/v1/comparisons")
@RequiredArgsConstructor
@Tag(name = "Comparisons", description = "预测 vs 实际偏差对比（阶段 3 S3-6）")
public class ComparisonController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;

    private final ComparisonService comparisonService;
    private final WorkspaceService workspaceService;

    // ── 计算对比 ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "计算预测 vs 实际偏差",
            description = "ANALYST/EDITOR/ADMIN/OWNER；按统一口径 10 维度各生成一行对比；"
                    + "Idempotency-Key 幂等；错误码 400 COMPARISON_INVALID / "
                    + "404 FORECAST_OR_SNAPSHOT_NOT_FOUND")
    public ApiResponse<List<ComparisonResponse>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ComparisonCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(comparisonService.create(workspaceId, userId, request, idempotencyKey));
    }

    // ── 列表 ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "列出偏差对比行", description = "全部角色可读；按当前 workspace 过滤")
    public ApiResponse<PaginatedResponse<ComparisonResponse>> list(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String hit,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(comparisonService.list(workspaceId, assetId, dimension, hit, page, size));
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
}
