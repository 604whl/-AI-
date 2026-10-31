package com.shortvideoscripagent.xhsagentyunying.billing.invoice;

import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity.Invoice;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.mapper.InvoiceMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.invoice.InvoiceDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S4-4 {@link InvoiceService} 单元测试。
 * <p>
 * 覆盖审核清单点名的分支：
 * <ul>
 *   <li>开票约束：已退款订单 / 未支付订单 / 重复开票 → 拒绝；</li>
 *   <li>企业普票必须带纳税人识别号；</li>
 *   <li>开票幂等：同幂等键返回同一张发票；</li>
 *   <li>红冲：原发票标记 red_flushed + 新增 red 记录，原票不删除；</li>
 *   <li>下载票据：签名 URL 且 5 分钟过期。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final String WS = "ws_invoice_test";
    private static final Long ADMIN = 2002L;
    private static final String ORDER_NO = "SO-INV-0001";

    @Mock
    private InvoiceMapper invoiceMapper;
    @Mock
    private SubscriptionOrderMapper orderMapper;
    @Mock
    private BillingStatementMapper statementMapper;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private IdempotencyReplay idempotencyReplay;
    @Mock
    private InvoicePdfGenerator pdfGenerator;
    @Mock
    private InvoiceStorageService storageService;

    private InvoiceService service() {
        return new InvoiceService(invoiceMapper, orderMapper, statementMapper, workspaceService,
                auditWriter, idempotencyReplay, pdfGenerator, storageService);
    }

    private SubscriptionOrder order(String status, BigDecimal amount) {
        SubscriptionOrder o = new SubscriptionOrder();
        o.setId(UUID.randomUUID());
        o.setOrderNo(ORDER_NO);
        o.setWorkspaceId(WS);
        o.setAmount(amount);
        o.setCurrency("CNY");
        o.setStatus(status);
        return o;
    }

    private InvoiceDtos.IssueInvoiceRequest request(String type, String title, String taxNo) {
        InvoiceDtos.IssueInvoiceRequest req = new InvoiceDtos.IssueInvoiceRequest();
        req.setOrderNo(ORDER_NO);
        req.setInvoiceType(type);
        req.setTitle(title);
        req.setTaxNo(taxNo);
        return req;
    }

    // ── 开票约束 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("开票：已支付订单可开票，税额按价税合计 × 税率 ÷ (1+税率)")
    void issue_computesTaxAndPersists() {
        SubscriptionOrder o = order("paid", new BigDecimal("106.0000"));
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(o);
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(inv -> 1);
        when(invoiceMapper.countByInvoiceNoPrefix(anyString())).thenReturn(0L);
        when(pdfGenerator.generate(any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.store(anyString(), anyString(), any())).thenReturn("invoices/ws/inv.pdf");

        InvoiceDtos.InvoiceView view = service().issue(request("company", "某某科技有限公司", "91310000XXX"),
                "idem-1", ADMIN);

        assertNotNull(view);
        assertEquals("issued", view.getStatus());
        assertEquals("company", view.getInvoiceType());
        assertEquals(0, new BigDecimal("6.0000").compareTo(view.getTaxAmount()));
        assertEquals(0, new BigDecimal("0.0600").compareTo(view.getTaxRate()));
        verify(invoiceMapper).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("开票：已退款订单不可开票")
    void issue_refundedOrderRejected() {
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("refunded", new BigDecimal("100.0000")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().issue(request("personal", "张三", null), "idem-1", ADMIN));
        assertEquals("order_refunded_cannot_invoice", ex.getMessage());
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("开票：未支付订单不可开票")
    void issue_unpaidOrderRejected() {
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("pending", new BigDecimal("100.0000")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().issue(request("personal", "张三", null), "idem-1", ADMIN));
        assertEquals("order_not_paid_cannot_invoice", ex.getMessage());
    }

    @Test
    @DisplayName("开票：同一订单已有有效发票 → 拒绝重复开票")
    void issue_duplicateOrderRejected() {
        SubscriptionOrder o = order("paid", new BigDecimal("100.0000"));
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(o);
        Invoice active = new Invoice();
        active.setId(UUID.randomUUID());
        active.setInvoiceNo("INV-EXISTING");
        active.setStatus("issued");
        when(invoiceMapper.selectActiveByOrderId(o.getId())).thenReturn(active);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().issue(request("personal", "张三", null), "idem-1", ADMIN));
        assertEquals("order_already_invoiced", ex.getMessage());
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("开票：企业普票必须带纳税人识别号")
    void issue_companyRequiresTaxNo() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().issue(request("company", "某某公司", null), "idem-1", ADMIN));
        assertEquals("tax_no_required_for_company_invoice", ex.getMessage());
    }

    @Test
    @DisplayName("开票：幂等键重放返回同一张发票")
    void issue_idempotentReplay() {
        Invoice existing = new Invoice();
        existing.setId(UUID.randomUUID());
        existing.setInvoiceNo("INV-EXIST");
        existing.setWorkspaceId(WS);
        existing.setStatus("issued");
        existing.setAmount(new BigDecimal("100.0000"));
        when(invoiceMapper.selectByIdempotencyKeyOnly("idem-1")).thenReturn(existing);

        InvoiceDtos.InvoiceView view = service().issue(request("personal", "张三", null), "idem-1", ADMIN);

        assertEquals("INV-EXIST", view.getInvoiceNo());
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("开票：orderNo 与 statementId 必须二选一")
    void issue_requiresExactlyOneSource() {
        InvoiceDtos.IssueInvoiceRequest req = request("personal", "张三", null);
        req.setStatementId("stmt-1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().issue(req, "idem-1", ADMIN));
        assertEquals("order_no_or_statement_id_required", ex.getMessage());
    }

    // ── 红冲 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("红冲：原发票标记 red_flushed + 新增 red 记录，原票不删除")
    void redFlush_marksOriginalAndInsertsRed() {
        UUID orderId = UUID.randomUUID();
        Invoice origin = new Invoice();
        origin.setId(UUID.randomUUID());
        origin.setInvoiceNo("INV-ORIGINAL");
        origin.setWorkspaceId(WS);
        origin.setInvoiceType("personal");
        origin.setTitle("张三");
        origin.setAmount(new BigDecimal("100.0000"));
        origin.setCurrency("CNY");
        origin.setTaxRate(new BigDecimal("0.0600"));
        origin.setTaxAmount(new BigDecimal("5.6600"));
        origin.setStatus("issued");
        origin.setOrderId(orderId);
        origin.setOrderNo(ORDER_NO);

        when(invoiceMapper.selectActiveByOrderId(orderId)).thenReturn(origin);
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(inv -> 1);
        when(invoiceMapper.compareAndSetRedFlushed(any(), any())).thenReturn(1);
        when(pdfGenerator.generate(any(), any())).thenReturn(new byte[]{1});
        when(storageService.store(anyString(), anyString(), any())).thenReturn("invoices/ws/red.pdf");

        boolean done = service().redFlushByOrder(orderId, "RF-1", new BigDecimal("100.0000"));

        assertTrue(done);
        // 原发票没有被删除，只是标记
        verify(invoiceMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(invoiceMapper).compareAndSetRedFlushed(eq(origin.getId()), any());
        verify(invoiceMapper).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("红冲：订单没有发票时返回 false（不报错）")
    void redFlush_noInvoiceReturnsFalse() {
        UUID orderId = UUID.randomUUID();
        when(invoiceMapper.selectActiveByOrderId(orderId)).thenReturn(null);

        assertEquals(false, service().redFlushByOrder(orderId, "RF-2", new BigDecimal("100.0000")));
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("红冲：部分退款金额不一致时跳过（交财务人工处理）")
    void redFlush_amountMismatchSkipped() {
        UUID orderId = UUID.randomUUID();
        Invoice origin = new Invoice();
        origin.setId(UUID.randomUUID());
        origin.setInvoiceNo("INV-ORIGINAL2");
        origin.setWorkspaceId(WS);
        origin.setAmount(new BigDecimal("100.0000"));
        origin.setStatus("issued");
        origin.setOrderId(orderId);
        when(invoiceMapper.selectActiveByOrderId(orderId)).thenReturn(origin);

        assertEquals(false, service().redFlushByOrder(orderId, "RF-3", new BigDecimal("50.0000")));
        verify(invoiceMapper, never()).insert(any(Invoice.class));
    }

    @Test
    @DisplayName("红冲：不得用旧快照回写原发票（会把 red_flushed 覆盖回 issued）")
    void redFlush_doesNotOverwriteStatusWithStaleSnapshot() {
        UUID orderId = UUID.randomUUID();
        Invoice origin = new Invoice();
        origin.setId(UUID.randomUUID());
        origin.setInvoiceNo("INV-STALE");
        origin.setWorkspaceId(WS);
        origin.setInvoiceType("personal");
        origin.setTitle("张三");
        origin.setAmount(new BigDecimal("100.0000"));
        origin.setCurrency("CNY");
        origin.setTaxRate(new BigDecimal("0.0600"));
        origin.setTaxAmount(new BigDecimal("5.6600"));
        origin.setStatus("issued");
        origin.setOrderId(orderId);
        origin.setOrderNo(ORDER_NO);

        when(invoiceMapper.selectActiveByOrderId(orderId)).thenReturn(origin);
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(inv -> 1);
        when(invoiceMapper.compareAndSetRedFlushed(any(), any())).thenReturn(1);
        when(pdfGenerator.generate(any(), any())).thenReturn(new byte[]{1});
        when(storageService.store(anyString(), anyString(), any())).thenReturn("invoices/ws/red.pdf");

        assertTrue(service().redFlushByOrder(orderId, "RF-STALE", new BigDecimal("100.0000")));

        // compareAndSetRedFlushed 已经把 status/red_flush_invoice_id 落库，
        // 之后绝不能再用 status='issued' 的旧快照做 updateById。
        verify(invoiceMapper, never()).updateById(eq(origin));
        assertEquals("red_flushed", origin.getStatus());
    }

    @Test
    @DisplayName("红冲：手动红冲（管理端）不依赖 orderId，按账单开票的发票也能红冲")
    void redFlush_manualWorksWithoutOrderId() {
        UUID invoiceId = UUID.randomUUID();
        Invoice origin = new Invoice();
        origin.setId(invoiceId);
        origin.setInvoiceNo("INV-STMT");
        origin.setWorkspaceId(WS);
        origin.setInvoiceType("personal");
        origin.setTitle("张三");
        origin.setAmount(new BigDecimal("100.0000"));
        origin.setCurrency("CNY");
        origin.setTaxRate(new BigDecimal("0.0600"));
        origin.setTaxAmount(new BigDecimal("5.6600"));
        origin.setStatus("issued");
        // 按账单开票：orderId 为 null
        origin.setOrderId(null);
        origin.setStatementId("stmt-1");

        when(invoiceMapper.selectById(invoiceId)).thenReturn(origin);
        when(invoiceMapper.insert(any(Invoice.class))).thenAnswer(inv -> 1);
        when(invoiceMapper.compareAndSetRedFlushed(any(), any())).thenReturn(1);
        when(pdfGenerator.generate(any(), any())).thenReturn(new byte[]{1});
        when(storageService.store(anyString(), anyString(), any())).thenReturn("invoices/ws/red.pdf");

        InvoiceDtos.RedFlushRequest req = new InvoiceDtos.RedFlushRequest();
        req.setRefundNo("RF-MANUAL");
        InvoiceDtos.InvoiceView view = service().redFlush(invoiceId, req, ADMIN);

        assertNotNull(view);
        verify(invoiceMapper).compareAndSetRedFlushed(eq(invoiceId), any());
    }

    // ── 下载票据 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("下载票据：签名 URL 且 5 分钟过期")
    void downloadTicket_signedAndExpiring() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNo("INV-DL");
        invoice.setWorkspaceId(WS);
        invoice.setStatus("issued");
        invoice.setPdfPath("invoices/ws/INV-DL.pdf");
        when(storageService.presignedDownloadUrl(anyString())).thenReturn(null);

        var props = new com.shortvideoscripagent.xhsagentyunying.config.AppAuthProperties();
        props.setJwtSecret("test-secret-for-signing");
        InvoiceDownloadSigner signer = new InvoiceDownloadSigner(props);

        InvoiceDtos.DownloadTicket ticket = service().createDownloadTicket(
                invoice, signer, "/api/v1/workspaces/ws/invoices/x/pdf");

        assertEquals(300L, ticket.getExpiresInSeconds());
        assertTrue(ticket.getUrl().contains("signature="));
        assertTrue(ticket.getUrl().contains("expiresAt="));
        assertTrue(ticket.getExpiresAt() > System.currentTimeMillis());
    }

    @Test
    @DisplayName("下载票据：MinIO 模式返回预签名 URL")
    void downloadTicket_minioPresigned() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNo("INV-DL2");
        invoice.setWorkspaceId(WS);
        invoice.setStatus("issued");
        invoice.setPdfPath("invoices/ws/INV-DL2.pdf");
        when(storageService.presignedDownloadUrl("invoices/ws/INV-DL2.pdf"))
                .thenReturn("http://minio/x?signature=abc");

        var props = new com.shortvideoscripagent.xhsagentyunying.config.AppAuthProperties();
        props.setJwtSecret("test-secret");
        InvoiceDtos.DownloadTicket ticket = service().createDownloadTicket(
                invoice, new InvoiceDownloadSigner(props), "/x");

        assertEquals("http://minio/x?signature=abc", ticket.getUrl());
    }

    @Test
    @DisplayName("签名器：过期后校验失败")
    void signer_expiredRejected() {
        var props = new com.shortvideoscripagent.xhsagentyunying.config.AppAuthProperties();
        props.setJwtSecret("test-secret");
        InvoiceDownloadSigner signer = new InvoiceDownloadSigner(props);

        long expiresAt = System.currentTimeMillis() + 60_000L;
        String sig = signer.sign("INV-1", expiresAt);
        assertTrue(signer.verify("INV-1", expiresAt, sig));
        assertEquals(false, signer.verify("INV-1", expiresAt - 120_000L, sig));
        assertEquals(false, signer.verify("INV-2", expiresAt, sig));
    }
}
