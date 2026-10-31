package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.InvoiceDownloadSigner;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.InvoiceService;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity.Invoice;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.invoice.InvoiceDtos;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * S4-4 工作区发票接口。
 * <p>
 * GET /v1/workspaces/{workspaceId}/invoices
 * GET /v1/workspaces/{workspaceId}/invoices/{id}/download-ticket
 * GET /v1/workspaces/{workspaceId}/invoices/{id}/pdf
 * </p>
 * <p>
 * <b>下载链接签名且过期</b>：{@code /download-ticket} 返回带 HMAC 签名、
 * 5 分钟过期的 URL；{@code /pdf} 校验签名与过期时间后才返回 PDF 字节。
 * </p>
 * <p>
 * <b>租户隔离</b>：跨 workspace 访问一律 404。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InvoiceController {

    private static final int CODE_NOT_FOUND = 40417;

    private final InvoiceService invoiceService;
    private final InvoiceDownloadSigner downloadSigner;

    /**
     * 工作区发票分页列表（OWNER / ADMIN）。
     */
    @GetMapping("/v1/workspaces/{workspaceId}/invoices")
    public ApiResponse<PageResult<InvoiceDtos.InvoiceView>> listInvoices(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Long userId = RequestContext.getUserIdOrNull();
        Page<InvoiceDtos.InvoiceView> p = invoiceService.listByWorkspace(workspaceId, userId, status, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 申请发票下载票据（签名 URL，5 分钟过期；OWNER / ADMIN）。
     */
    @GetMapping("/v1/workspaces/{workspaceId}/invoices/{id}/download-ticket")
    public ApiResponse<InvoiceDtos.DownloadTicket> createDownloadTicket(
            @PathVariable String workspaceId,
            @PathVariable String id) {
        Long userId = RequestContext.getUserIdOrNull();
        Invoice invoice = requireInvoice(workspaceId, id, userId);
        String basePath = "/api/v1/workspaces/" + workspaceId + "/invoices/" + invoice.getId() + "/pdf";
        return ApiResponse.ok(invoiceService.createDownloadTicket(invoice, downloadSigner, basePath));
    }

    /**
     * 下载发票 PDF（校验签名 + 过期时间）。
     */
    @GetMapping("/v1/workspaces/{workspaceId}/invoices/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @RequestParam long expiresAt,
            @RequestParam String signature) {
        Long userId = RequestContext.getUserIdOrNull();
        Invoice invoice = requireInvoice(workspaceId, id, userId);
        if (!downloadSigner.verify(invoice.getInvoiceNo(), expiresAt, signature)) {
            throw new BusinessException(40317, "invalid_or_expired_download_signature");
        }
        byte[] pdf = invoiceService.loadPdf(invoice);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + invoice.getInvoiceNo() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * 取发票并校验工作区归属；跨 workspace 一律 404。
     */
    private Invoice requireInvoice(String workspaceId, String rawId, Long userId) {
        UUID invoiceId;
        try {
            invoiceId = UUID.fromString(rawId);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(CODE_NOT_FOUND, "invoice_not_found");
        }
        Invoice invoice = invoiceService.requireInvoiceById(invoiceId);
        if (!workspaceId.equals(invoice.getWorkspaceId())) {
            throw new BusinessException(CODE_NOT_FOUND, "invoice_not_found");
        }
        // 校验当前用户是该工作区的 OWNER / ADMIN
        invoiceService.requireMember(workspaceId, userId);
        return invoice;
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
