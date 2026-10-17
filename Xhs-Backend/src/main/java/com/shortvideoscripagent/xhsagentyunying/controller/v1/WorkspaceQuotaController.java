package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaLedgerService;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import com.shortvideoscripagent.xhsagentyunying.service.UserQuotaService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P0-2: Workspace 配额 API。
 */
@Slf4j
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/quota")
@RequiredArgsConstructor
public class WorkspaceQuotaController {

    private final UserQuotaService userQuotaService;
    private final QuotaLedgerService quotaLedgerService;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;

    /**
     * 查询当前 workspace 的配额状态。
     * GET /v1/workspaces/{id}/quota
     */
    @GetMapping
    public ApiResponse<QuotaResponse> getQuota(
            @PathVariable String workspaceId
    ) {
        requireWorkspaceAccess(workspaceId);

        UUID wsUuid = UUID.fromString(workspaceId);
        WorkspaceQuota quota = userQuotaService.getWorkspaceQuota(wsUuid);

        int dailyQuota = quota != null ? quota.getDailyQuota() : 100;
        int used = quota != null ? quota.getUsed() : 0;
        int remaining = Math.max(dailyQuota - used, 0);

        return ApiResponse.ok(new QuotaResponse(
                workspaceId,
                dailyQuota,
                used,
                remaining,
                quota != null ? quota.getResetAt() : null
        ));
    }

    /**
     * 查询配额审计 ledger 列表。
     * GET /v1/workspaces/{id}/quota/ledger
     */
    @GetMapping("/ledger")
    public ApiResponse<IPage<UsageLedger>> getLedger(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status
    ) {
        requireWorkspaceAccess(workspaceId);

        UUID wsUuid = UUID.fromString(workspaceId);
        IPage<UsageLedger> ledgerPage = quotaLedgerService.list(wsUuid, action, status, page, size);
        return ApiResponse.ok(ledgerPage);
    }

    /**
     * 修改 workspace 每日配额（仅 ADMIN/OWNER）。
     * PATCH /v1/workspaces/{id}/quota
     */
    @PatchMapping
    public ApiResponse<QuotaResponse> updateQuota(
            @PathVariable String workspaceId,
            @RequestBody UpdateQuotaRequest request
    ) {
        requireOwnerOrAdmin(workspaceId);

        UUID wsUuid = UUID.fromString(workspaceId);
        WorkspaceQuota quota = userQuotaService.getWorkspaceQuota(wsUuid);

        if (quota == null) {
            throw new BusinessException(40403, "quota_not_found");
        }

        if (request.getDailyQuota() != null && request.getDailyQuota() < 0) {
            throw new BusinessException(40001, "invalid_daily_quota");
        }

        int newDailyQuota = request.getDailyQuota() != null ? request.getDailyQuota() : quota.getDailyQuota();

        // 更新配额
        WorkspaceQuota update = new WorkspaceQuota();
        update.setWorkspaceId(wsUuid);
        update.setDailyQuota(newDailyQuota);
        update.setUsed(quota.getUsed());
        update.setResetAt(quota.getResetAt());
        update.setCreatedAt(quota.getCreatedAt());
        update.setUpdatedAt(OffsetDateTime.now());
        workspaceQuotaMapper.updateById(update);

        return ApiResponse.ok(new QuotaResponse(
                workspaceId,
                newDailyQuota,
                quota.getUsed(),
                Math.max(newDailyQuota - quota.getUsed(), 0),
                quota.getResetAt()
        ));
    }

    private void requireWorkspaceAccess(String workspaceId) {
        String currentWs = WorkspaceGuards.requireCurrentWorkspaceId();
        if (!workspaceId.equals(currentWs)) {
            throw new BusinessException(40301, "workspace_access_denied");
        }
    }

    private void requireOwnerOrAdmin(String workspaceId) {
        // TODO: 集成角色检查（ADMIN/OWNER）
        requireWorkspaceAccess(workspaceId);
    }

    @Data
    public static class QuotaResponse {
        private String workspaceId;
        private int dailyQuota;
        private int used;
        private int remaining;
        private OffsetDateTime resetAt;

        public QuotaResponse(String workspaceId, int dailyQuota, int used, int remaining,
                            OffsetDateTime resetAt) {
            this.workspaceId = workspaceId;
            this.dailyQuota = dailyQuota;
            this.used = used;
            this.remaining = remaining;
            this.resetAt = resetAt;
        }
    }

    @Data
    public static class UpdateQuotaRequest {
        private Integer dailyQuota;
    }
}
