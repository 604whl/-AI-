package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishReceiptCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishReceiptResponse;
import com.shortvideoscripagent.xhsagentyunying.service.PublishReceiptService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * S3-4 发布回执 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/publish-receipts}
 * → {@code /api/v1/publish-receipts}；资产维度查询：{@code /v1/assets/{id}/receipts}
 * → {@code /api/v1/assets/{id}/receipts}。
 * </p>
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Publish Receipts", description = "发布回执与外部内容 ID（阶段 3 S3-4）")
public class PublishReceiptController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_ASSET_NOT_FOUND = 40450;
    private static final int CODE_RECEIPT_NOT_FOUND = 40480;

    private final PublishReceiptService publishReceiptService;
    private final WorkspaceService workspaceService;

    @PostMapping("/publish-receipts")
    @Operation(summary = "回填发布回执",
            description = "EDITOR+；externalContentId 与 sourceUrl 至少有一项（否则 400 RECEIPT_INCOMPLETE）；"
                    + "回填后 content_asset.status 推进到 published；"
                    + "错误码 409 TASK_NOT_AWAITING_RECEIPT")
    public ApiResponse<PublishReceiptResponse> create(@Valid @RequestBody PublishReceiptCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(publishReceiptService.create(workspaceId, userId, request));
    }

    @GetMapping("/publish-receipts")
    @Operation(summary = "列出当前工作区的全部回执", description = "全部角色可读")
    public ApiResponse<List<PublishReceiptResponse>> list() {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishReceiptService.listByWorkspace(workspaceId));
    }

    @GetMapping("/publish-receipts/{id}")
    @Operation(summary = "查询回执详情", description = "全部角色可读；跨工作区访问按 404 处理")
    public ApiResponse<PublishReceiptResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishReceiptService.get(workspaceId, parseId(id)));
    }

    @GetMapping("/assets/{assetId}/receipts")
    @Operation(summary = "列出某资产的回执",
            description = "全部角色可读；按当前 workspace 过滤；错误码 404 ASSET_NOT_FOUND")
    public ApiResponse<List<PublishReceiptResponse>> listByAsset(@PathVariable String assetId) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(publishReceiptService.listByAsset(workspaceId, parseAssetId(assetId)));
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
            throw new BusinessException(CODE_RECEIPT_NOT_FOUND, "publish_receipt_not_found");
        }
    }

    private UUID parseAssetId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "asset_not_found");
        }
    }
}
