package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.billing.statement.AdminMarginService;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.StatementService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.pricing.BillingDtos;
import com.shortvideoscripagent.xhsagentyunying.service.admin.PlatformAdminService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * S4-3 计费接口（账单 / 成本用量 / 管理端成本毛利）。
 * <p>
 * GET /v1/workspaces/{workspaceId}/billing/statements
 * GET /v1/workspaces/{workspaceId}/billing/statements/{id}
 * GET /v1/workspaces/{workspaceId}/billing/cost-usage
 * GET /v1/platform/admin/billing/cost-margin
 * </p>
 * <p>
 * <b>租户隔离</b>：跨 workspace 访问一律返回 404（不泄露存在性）。
 * 订阅端接口要求 OWNER / ADMIN 角色（用 {@code WorkspaceService.findRole} 自校验，
 * 不改动 S4-1 的 {@code SubscriptionService}）。
 * 管理端接口走 {@code PlatformAdminService.requirePlatformAdmin()}。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class BillingController {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_FORBIDDEN = 40310;

    private final StatementService statementService;
    private final AdminMarginService adminMarginService;
    private final WorkspaceService workspaceService;
    private final PlatformAdminService platformAdminService;
    private final com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService balanceService;

    /**
     * 分页查询工作区账单。
     */
    @GetMapping("/workspaces/{workspaceId}/billing/statements")
    public ApiResponse<BillingDtos.PageResult<BillingDtos.StatementSummary>> listStatements(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireBillingAdmin(workspaceId);
        return ApiResponse.ok(statementService.listStatements(workspaceId, page, size));
    }

    /**
     * 查询账单详情（含行项）。
     */
    @GetMapping("/workspaces/{workspaceId}/billing/statements/{id}")
    public ApiResponse<BillingDtos.StatementDetail> getStatement(
            @PathVariable String workspaceId,
            @PathVariable String id) {
        requireBillingAdmin(workspaceId);
        return ApiResponse.ok(statementService.getStatement(workspaceId, id));
    }

    /**
     * 扣费账单（幂等；状态守卫保证只扣一次）。
     * <p>
     * P2-4：早期实现 {@code chargeStatement} / {@code voidStatement} 全无调用方，
     * 账单永远停在 {@code open}，预付费模式形同虚设。这里补上用户侧入口。
     * </p>
     */
    @PostMapping("/workspaces/{workspaceId}/billing/statements/{id}/charge")
    public ApiResponse<BillingDtos.StatementDetail> chargeStatement(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @RequestBody(required = false) BillingDtos.ChargeStatementRequest request) {
        requireBillingAdmin(workspaceId);
        return ApiResponse.ok(statementService.chargeStatement(id,
                request == null ? null : request.getReason()));
    }

    /**
     * 作废账单（不扣费）。已支付的账单不允许作废。
     */
    @PostMapping("/workspaces/{workspaceId}/billing/statements/{id}/void")
    public ApiResponse<BillingDtos.StatementDetail> voidStatement(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @RequestBody(required = false) BillingDtos.ChargeStatementRequest request) {
        requireBillingAdmin(workspaceId);
        return ApiResponse.ok(statementService.voidStatement(id,
                request == null ? null : request.getReason()));
    }

    /**
     * 查询工作区余额（按量计费 USAGE 套餐）。
     * <p>
     * 早期实现只有 {@code BalanceService}，没有任何 HTTP 入口 ——
     * 用户看不到余额、按量计费链路缺一环。
     * </p>
     */
    @GetMapping("/workspaces/{workspaceId}/billing/balance")
    public ApiResponse<BillingDtos.BalanceView> getBalance(@PathVariable String workspaceId) {
        requireBillingMember(workspaceId);
        return ApiResponse.ok(balanceService.getBalance(workspaceId));
    }

    /**
     * 按时间段聚合用量 / 成本 / 毛利。
     */
    @GetMapping("/workspaces/{workspaceId}/billing/cost-usage")
    public ApiResponse<BillingDtos.CostUsageView> costUsage(
            @PathVariable String workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        requireBillingAdmin(workspaceId);
        return ApiResponse.ok(statementService.aggregateCostUsage(workspaceId, from, to));
    }

    /**
     * 管理端：按工作区 / 套餐 / 时间段聚合成本毛利。
     */
    @GetMapping("/platform/admin/billing/cost-margin")
    public ApiResponse<BillingDtos.MarginView> adminCostMargin(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        platformAdminService.requirePlatformAdmin();
        return ApiResponse.ok(adminMarginService.aggregateMargin(
                workspaceId == null || workspaceId.isBlank() ? null : workspaceId.trim(), from, to));
    }

    /**
     * 校验当前用户是该工作区的 OWNER / ADMIN。
     * <p>
     * 非成员一律按「未找到」返回 404（不泄露 workspace 是否存在）；
     * 是成员但角色不够（EDITOR / REVIEWER / VIEWER）返回 403。
     * </p>
     */
    private void requireBillingAdmin(String workspaceId) {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_NOT_FOUND, "billing_not_found");
        }
        if (!role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_admin_required");
        }
    }

    /**
     * 校验当前用户是该工作区成员（任意角色）。
     * <p>
     * 余额查询是成员级的只读能力，不要求 ADMIN —— 用户需要看到自己还有多少余额。
     * </p>
     */
    private void requireBillingMember(String workspaceId) {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_NOT_FOUND, "billing_not_found");
        }
    }
}
