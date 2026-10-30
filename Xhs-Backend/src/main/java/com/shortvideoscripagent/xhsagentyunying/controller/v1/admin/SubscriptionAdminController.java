package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.PlanService;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.PlanDtos;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.SubscriptionDtos;
import com.shortvideoscripagent.xhsagentyunying.service.admin.PlatformAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * S4-1 管理端套餐与订阅接口。
 * <p>
 * GET   /v1/platform/admin/plans
 * POST  /v1/platform/admin/plans
 * PATCH /v1/platform/admin/plans/{id}
 * POST  /v1/platform/admin/plans/{id}/archive
 * GET   /v1/platform/admin/subscriptions
 * PATCH /v1/platform/admin/subscriptions/{id}
 * </p>
 * <p>
 * 全部走 {@code PlatformAdminService.requirePlatformAdmin()}，非管理员 403。
 * 写操作支持 {@code Idempotency-Key}。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/platform/admin")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final PlatformAdminService platformAdminService;
    private final PlanService planService;
    private final SubscriptionService subscriptionService;

    // ── 套餐 ─────────────────────────────────────────────────────────────

    /**
     * 分页查询套餐（可按 code / status 过滤）。
     */
    @GetMapping("/plans")
    public ApiResponse<PageResult<PlanDtos.PlanView>> listPlans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        platformAdminService.requirePlatformAdmin();

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<PlanDtos.PlanView> all = planService.listAllForAdmin(code, status);

        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<PlanDtos.PlanView> items = all.subList(from, to);

        return ApiResponse.ok(new PageResult<>(items, all.size(), safePage, safeSize));
    }

    /**
     * 创建套餐（幂等）。
     */
    @PostMapping("/plans")
    public ApiResponse<PlanDtos.PlanView> createPlan(
            @RequestBody PlanDtos.CreatePlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        return ApiResponse.ok(planService.createPlan(request, idempotencyKey));
    }

    /**
     * 修改套餐 —— 生成新版本（改价/改额度不 UPDATE 老版本），改价写审计。
     */
    @PatchMapping("/plans/{id}")
    public ApiResponse<PlanDtos.PlanView> updatePlan(
            @PathVariable String id,
            @RequestBody PlanDtos.UpdatePlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        return ApiResponse.ok(planService.updatePlan(parseUuid(id), request, idempotencyKey));
    }

    /**
     * 下架套餐（归档）。不影响已订阅工作区（订阅读快照）。
     */
    @PostMapping("/plans/{id}/archive")
    public ApiResponse<PlanDtos.PlanView> archivePlan(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        return ApiResponse.ok(planService.archivePlan(parseUuid(id), idempotencyKey));
    }

    // ── 订阅 ─────────────────────────────────────────────────────────────

    /**
     * 分页查询订阅（可按状态 / 工作区过滤）。
     */
    @GetMapping("/subscriptions")
    public ApiResponse<PageResult<PlanDtos.AdminSubscriptionItem>> listSubscriptions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workspaceId) {
        platformAdminService.requirePlatformAdmin();

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<PlanDtos.AdminSubscriptionItem> p =
                subscriptionService.listForAdmin(status, workspaceId, safePage, safeSize);

        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), safePage, safeSize));
    }

    /**
     * 订阅补偿 / 延期（幂等）。
     * <p>
     * P3-5：{@code Idempotency-Key} 必须透传给 Service ——
     * 否则重复提交同一个键会把延期天数叠加两次，误操作不可撤销。
     * </p>
     */
    @PatchMapping("/subscriptions/{id}")
    public ApiResponse<PlanDtos.AdminSubscriptionItem> adjustSubscription(
            @PathVariable String id,
            @RequestBody SubscriptionDtos.AdminAdjustRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        return ApiResponse.ok(subscriptionService.adminAdjust(parseUuid(id), request, idempotencyKey));
    }

    // ── private ──────────────────────────────────────────────────────────

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException(
                    40001, "invalid_id");
        }
    }

    @Data
    public static class PageResult<T> {
        private final List<T> items;
        private final long total;
        private final int page;
        private final int size;

        public PageResult(List<T> items, long total, int page, int size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }
    }
}
