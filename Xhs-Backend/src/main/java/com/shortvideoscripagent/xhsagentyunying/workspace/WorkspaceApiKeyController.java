package com.shortvideoscripagent.xhsagentyunying.workspace;

import com.shortvideoscripagent.xhsagentyunying.auth.ApiKeyService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Workspace API Key 控制器：workspace 成员管理自己的 API Key。
 * <p>
 * API Key 格式：xhs_sk_ + 24 字符 base62（参考 Stripe）。
 * 校验通过后将 workspaceId 放入 WorkspaceContext。
 * </p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /v1/workspaces/{workspaceId}/api-keys — 创建</li>
 *   <li>GET /v1/workspaces/{workspaceId}/api-keys — 列表</li>
 *   <li>DELETE /v1/workspaces/{workspaceId}/api-keys/{id} — 撤销</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/api-keys")
@RequiredArgsConstructor
public class WorkspaceApiKeyController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final ApiKeyService apiKeyService;

    /**
     * 创建 API Key。
     * 返回明文 key（仅此一次）。
     */
    @PostMapping
    public ApiResponse<ApiKeyCreateResponse> create(
            @PathVariable String workspaceId,
            @RequestBody ApiKeyCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        Long userId = resolveUserId();
        ApiKeyService.GeneratedKey generated = apiKeyService.generate(
                workspaceId, userId, request.name(), request.scopes());

        return ApiResponse.ok(new ApiKeyCreateResponse(
                generated.rawKey(), // 仅此一次返回
                workspaceId,
                request.name() != null ? request.name() : "API Key"
        ));
    }

    /**
     * 列出当前 workspace 的 API Key（元数据，不含 keyHash）。
     */
    @GetMapping
    public ApiResponse<List<ApiKeyResponse>> list(
            @PathVariable String workspaceId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        Long userId = resolveUserId();
        List<ApiKey> keys = apiKeyService.listActive(workspaceId, userId);
        List<ApiKeyResponse> response = keys.stream()
                .map(k -> new ApiKeyResponse(
                        k.getId(),
                        k.getPrefix() + "****" + k.getKeyHash().substring(k.getKeyHash().length() - 4), // 脱敏展示
                        k.getName(),
                        k.getScopes(),
                        k.getCreatedAt(),
                        k.getLastUsedAt()
                ))
                .toList();
        return ApiResponse.ok(response);
    }

    /**
     * 撤销 API Key。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(
            @PathVariable String workspaceId,
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        Long userId = resolveUserId();
        apiKeyService.revoke(userId, id);
        return ApiResponse.ok(null);
    }

    private Long resolveUserId() {
        Long userId = WorkspaceContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    // --- DTOs ---

    public record ApiKeyCreateRequest(
            String name,
            List<String> scopes
    ) {
    }

    public record ApiKeyCreateResponse(
            String rawKey,       // 仅此一次
            String workspaceId,
            String name
    ) {
    }

    public record ApiKeyResponse(
            Long id,
            String displayKey,    // 脱敏
            String name,
            List<String> scopes,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime lastUsedAt
    ) {
    }
}
