package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.order.OrderService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.ReconciliationService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import com.shortvideoscripagent.xhsagentyunying.service.admin.PlatformAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * S4-2 管理端订单与对账接口。
 * <p>
 * GET  /v1/platform/admin/orders
 * POST /v1/platform/admin/orders/{id}/refund
 * GET  /v1/platform/admin/billing/reconciliation
 * PATCH /v1/platform/admin/billing/reconciliation/{id}
 * </p>
 * <p>
 * 全部走 {@code PlatformAdminService.requirePlatformAdmin()}，非管理员 403。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/platform/admin")
@RequiredArgsConstructor
public class BillingAdminController {

    private final PlatformAdminService platformAdminService;
    private final OrderService orderService;
    private final ReconciliationService reconciliationService;

    /**
     * 分页查询订单（可按状态 / 渠道 / 工作区筛选）。
     */
    @GetMapping("/orders")
    public ApiResponse<PageResult<OrderDtos.OrderView>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String workspaceId) {
        platformAdminService.requirePlatformAdmin();
        Page<OrderDtos.OrderView> p = orderService.listForAdmin(status, provider, workspaceId, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 订单退款（幂等键）。
     */
    @PostMapping("/orders/{id}/refund")
    public ApiResponse<OrderDtos.RefundResult> refundOrder(
            @PathVariable String id,
            @RequestBody OrderDtos.RefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        return ApiResponse.ok(orderService.refund(parseUuid(id), request, idempotencyKey));
    }

    /**
     * 分页查询对账差异。
     */
    @GetMapping("/billing/reconciliation")
    public ApiResponse<PageResult<OrderDtos.ReconciliationView>> listReconciliation(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String diffType,
            @RequestParam(required = false) String provider) {
        platformAdminService.requirePlatformAdmin();
        Page<OrderDtos.ReconciliationView> p = reconciliationService.listForAdmin(
                status, diffType, provider, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 人工标记对账差异处理状态。
     */
    @PostMapping("/billing/reconciliation/{id}/resolve")
    public ApiResponse<OrderDtos.ReconciliationView> resolveReconciliation(
            @PathVariable String id,
            @RequestBody OrderDtos.ResolveReconciliationRequest request) {
        platformAdminService.requirePlatformAdmin();
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(reconciliationService.resolve(parseUuid(id), request,
                userId == null ? "platform_admin" : String.valueOf(userId)));
    }

    /**
     * 手动重跑对账（同步执行；失败由调用方感知）。
     */
    @PostMapping("/billing/reconciliation/run")
    public ApiResponse<ReconciliationRunResult> runReconciliation(
            @RequestParam(defaultValue = "mock") String provider,
            @RequestParam(required = false) String day) {
        platformAdminService.requirePlatformAdmin();
        LocalDate targetDay = day == null || day.isBlank() ? LocalDate.now() : LocalDate.parse(day);
        int diffs = reconciliationService.reconcile(provider, targetDay);
        ReconciliationRunResult result = new ReconciliationRunResult();
        result.setProvider(provider);
        result.setDay(targetDay.toString());
        result.setDiffs(diffs);
        return ApiResponse.ok(result);
    }

    // ── private ─────────────────────────────────────────────────────────

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException(
                    40001, "invalid_id");
        }
    }

    /**
     * 分页结果。
     */
    @Data
    public static class PageResult<T> {
        private final List<T> items;
        private final long total;
        private final long page;
        private final long size;

        public PageResult(List<T> items, long total, long page, long size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }
    }

    /**
     * 对账执行结果。
     */
    @Data
    public static class ReconciliationRunResult {
        private String provider;
        private String day;
        private int diffs;
    }
}
