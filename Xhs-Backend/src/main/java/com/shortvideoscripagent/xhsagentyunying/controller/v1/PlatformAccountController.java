package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.platformaccount.PlatformAccountCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.platformaccount.PlatformAccountReauthorizeRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.platformaccount.PlatformAccountResponse;
import com.shortvideoscripagent.xhsagentyunying.service.PlatformAccountService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * S3-2 平台账号连接 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/platform-accounts}
 * → {@code /api/v1/platform-accounts}。
 * </p>
 */
@RestController
@RequestMapping("/v1/platform-accounts")
@RequiredArgsConstructor
@Tag(name = "Platform Accounts", description = "平台账号连接（阶段 3 S3-2）")
public class PlatformAccountController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;

    private final PlatformAccountService platformAccountService;
    private final WorkspaceService workspaceService;

    @GetMapping
    @Operation(summary = "列出当前工作区的平台账号", description = "全部角色可读；每项含 expiryState（OK / EXPIRING_SOON / EXPIRING_TODAY / EXPIRED）")
    public ApiResponse<List<PlatformAccountResponse>> list(
            @RequestParam(required = false) String platformCode,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);
        // platform 是验收文档里的参数名别名，platformCode 是本任务约定的参数名
        String effectivePlatform = platformCode != null && !platformCode.isBlank() ? platformCode : platform;
        return ApiResponse.ok(platformAccountService.list(workspaceId, effectivePlatform, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询平台账号详情", description = "全部角色可读；按工作区隔离")
    public ApiResponse<PlatformAccountResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);
        return ApiResponse.ok(platformAccountService.get(workspaceId, parseId(id)));
    }

    @PostMapping
    @Operation(summary = "创建平台账号连接", description = "OWNER/ADMIN；仅授权矩阵内的平台×连接器组合可创建")
    public ApiResponse<PlatformAccountResponse> create(@Valid @RequestBody PlatformAccountCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(platformAccountService.create(workspaceId, userId, request));
    }

    @PostMapping("/{id}/reauthorize")
    @Operation(summary = "重新授权平台账号", description = "OWNER/ADMIN；状态 reauthorizing → connected")
    public ApiResponse<PlatformAccountResponse> reauthorize(
            @PathVariable String id,
            @Valid @RequestBody(required = false) PlatformAccountReauthorizeRequest request) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(platformAccountService.reauthorize(workspaceId, userId, parseId(id), request));
    }

    @PostMapping("/{id}/disconnect")
    @Operation(summary = "解绑平台账号", description = "OWNER/ADMIN；清空凭据引用并回到 unconnected")
    public ApiResponse<PlatformAccountResponse> disconnect(@PathVariable String id) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(platformAccountService.disconnect(workspaceId, userId, parseId(id)));
    }

    private void requireAdmin(String workspaceId, Long userId) {
        WorkspaceGuards.requireMember(workspaceId);
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "platform_account_forbidden");
        }
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40450, "platform_account_not_found");
        }
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
