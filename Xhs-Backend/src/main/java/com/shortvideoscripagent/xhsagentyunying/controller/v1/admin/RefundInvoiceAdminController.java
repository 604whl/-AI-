package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.InvoiceService;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.RefundService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.invoice.InvoiceDtos;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.refund.RefundDtos;
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

import java.util.List;
import java.util.UUID;

/**
 * S4-4 管理端退款与发票接口。
 * <p>
 * GET  /v1/platform/admin/refunds
 * POST /v1/platform/admin/refunds/{refundNo}/approve
 * POST /v1/platform/admin/refunds/{refundNo}/reject
 * POST /v1/platform/admin/invoices
 * GET  /v1/platform/admin/invoices
 * POST /v1/platform/admin/invoices/{id}/red-flush
 * </p>
 * <p>
 * 全部走 {@code PlatformAdminService.requirePlatformAdmin()}，非管理员 403。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/platform/admin")
@RequiredArgsConstructor
public class RefundInvoiceAdminController {

    private final PlatformAdminService platformAdminService;
    private final RefundService refundService;
    private final InvoiceService invoiceService;

    /**
     * 分页查询退款单（可按状态 / 工作区筛选）。
     */
    @GetMapping("/refunds")
    public ApiResponse<PageResult<RefundDtos.RefundView>> listRefunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workspaceId) {
        platformAdminService.requirePlatformAdmin();
        Page<RefundDtos.RefundView> p = refundService.listForAdmin(status, workspaceId, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 审核通过退款单 → 调用渠道退款 → 权益冲正。
     */
    @PostMapping("/refunds/{refundNo}/approve")
    public ApiResponse<RefundDtos.RefundResult> approveRefund(
            @PathVariable String refundNo,
            @RequestBody(required = false) RefundDtos.ReviewRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        Long userId = RequestContext.getUserIdOrNull();
        String note = request == null ? null : request.getNote();
        return ApiResponse.ok(refundService.approve(refundNo, userId, note));
    }

    /**
     * 审核驳回退款单。
     */
    @PostMapping("/refunds/{refundNo}/reject")
    public ApiResponse<RefundDtos.RefundResult> rejectRefund(
            @PathVariable String refundNo,
            @RequestBody(required = false) RefundDtos.ReviewRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        Long userId = RequestContext.getUserIdOrNull();
        String note = request == null ? null : request.getNote();
        return ApiResponse.ok(refundService.reject(refundNo, userId, note));
    }

    /**
     * 开具发票（幂等键）。
     */
    @PostMapping("/invoices")
    public ApiResponse<InvoiceDtos.InvoiceView> issueInvoice(
            @RequestBody InvoiceDtos.IssueInvoiceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        platformAdminService.requirePlatformAdmin();
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(invoiceService.issue(request, idempotencyKey, userId));
    }

    /**
     * 分页查询发票。
     */
    @GetMapping("/invoices")
    public ApiResponse<PageResult<InvoiceDtos.InvoiceView>> listInvoices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String invoiceType) {
        platformAdminService.requirePlatformAdmin();
        Page<InvoiceDtos.InvoiceView> p = invoiceService.listForAdmin(
                status, workspaceId, invoiceType, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 手动红冲发票（原发票不物理删除）。
     */
    @PostMapping("/invoices/{id}/red-flush")
    public ApiResponse<InvoiceDtos.InvoiceView> redFlushInvoice(
            @PathVariable String id,
            @RequestBody(required = false) InvoiceDtos.RedFlushRequest request) {
        platformAdminService.requirePlatformAdmin();
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(invoiceService.redFlush(parseUuid(id), request, userId));
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
}
