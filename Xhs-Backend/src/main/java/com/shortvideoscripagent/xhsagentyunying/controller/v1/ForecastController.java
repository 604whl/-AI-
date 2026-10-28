package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ForecastCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ForecastResponse;
import com.shortvideoscripagent.xhsagentyunying.service.ForecastService;
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

import java.util.UUID;

/**
 * S3-6 预测 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/forecasts}
 * → {@code /api/v1/forecasts}。
 * </p>
 * <p>
 * <b>预测侧与实际侧分字段</b>：本接口只返回预测值（{@code predictedMetrics}），
 * 实际值在 {@code /v1/metric-snapshots}，两者不混在同一列。
 * </p>
 */
@RestController
@RequestMapping("/v1/forecasts")
@RequiredArgsConstructor
@Tag(name = "Forecasts", description = "发布前指标预测（阶段 3 S3-6）")
public class ForecastController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    /** 404 预测不存在 / 越权 */
    private static final int CODE_FORECAST_NOT_FOUND = 40470;

    private final ForecastService forecastService;
    private final WorkspaceService workspaceService;

    // ── 创建预测 ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "创建指标预测",
            description = "ANALYST/EDITOR/ADMIN/OWNER；统一口径 10 键缺失补 0，非核心键剔除；"
                    + "Idempotency-Key 幂等；错误码 400 FORECAST_FIELDS_INVALID / 404 ASSET_NOT_FOUND")
    public ApiResponse<ForecastResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ForecastCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(forecastService.create(workspaceId, userId, request, idempotencyKey));
    }

    // ── 列表 ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "列出预测", description = "全部角色可读；按当前 workspace 过滤")
    public ApiResponse<PaginatedResponse<ForecastResponse>> list(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String forecastType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(forecastService.list(workspaceId, assetId, platform, forecastType, page, size));
    }

    // ── 详情 ────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "查询预测详情", description = "全部角色可读；错误码 404 FORECAST_NOT_FOUND")
    public ApiResponse<ForecastResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(forecastService.get(workspaceId, parseUuid(id)));
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
     * <p>
     * 项目现有 {@link WorkspaceRole} 枚举只有 OWNER / ADMIN / EDITOR / REVIEWER / VIEWER，
     * ANALYST 尚未纳入枚举，因此这里按"角色名包含 ANALYST 或层级 >= EDITOR"判定。
     * </p>
     */
    private void requireAnalyst(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_FORECAST_NOT_FOUND, "FORECAST_NOT_FOUND");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_FORECAST_NOT_FOUND, "FORECAST_NOT_FOUND");
        }
    }
}
