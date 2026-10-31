package com.shortvideoscripagent.xhsagentyunying.billing.invoice;

import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity.Invoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4-4 {@link InvoicePdfGenerator} 单元测试。
 * <p>
 * 回归重点：{@code resolveCjkFont()} 的兜底分支此前在 catch 里直接调用
 * {@code BaseFont.createFont(...)}（会抛 IOException）而没有处理，
 * 导致 CJK 字体不可用时抛「Unresolved compilation problem」而不是优雅降级。
 * </p>
 */
class InvoicePdfGeneratorTest {

    private final InvoicePdfGenerator generator = new InvoicePdfGenerator();

    private Invoice invoice(String status) {
        Invoice invoice = new Invoice();
        invoice.setId(java.util.UUID.randomUUID());
        invoice.setInvoiceNo("INV-PDF-0001");
        invoice.setWorkspaceId("ws_pdf_test");
        invoice.setInvoiceType("personal");
        invoice.setTitle("张三");
        invoice.setAmount(new BigDecimal("106.0000"));
        invoice.setCurrency("CNY");
        invoice.setTaxRate(new BigDecimal("0.0600"));
        invoice.setTaxAmount(new BigDecimal("6.0000"));
        invoice.setStatus(status);
        invoice.setOrderNo("SO-PDF-0001");
        invoice.setIssuedAt(OffsetDateTime.now());
        return invoice;
    }

    @Test
    @DisplayName("PDF 生成：正常发票产出合法 PDF 字节")
    void generate_producesValidPdf() {
        byte[] pdf = generator.generate(invoice("issued"), java.util.Map.of());

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String head = new String(pdf, 0, Math.min(8, pdf.length), StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF-"), "PDF 头非法: " + head);
        String tail = new String(pdf, Math.max(0, pdf.length - 32), Math.min(32, pdf.length),
                StandardCharsets.ISO_8859_1);
        assertTrue(tail.contains("%%EOF"), "PDF 缺少 EOF 标记");
    }

    @Test
    @DisplayName("PDF 生成：红冲发票产出合法 PDF 字节")
    void generate_redInvoiceProducesValidPdf() {
        byte[] pdf = generator.generate(invoice("red"), java.util.Map.of());

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String head = new String(pdf, 0, Math.min(8, pdf.length), StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF-"), "PDF 头非法: " + head);
    }

    @Test
    @DisplayName("PDF 生成：发票为 null 时抛 IllegalArgumentException（不返回空字节）")
    void generate_nullInvoiceRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> generator.generate(null, java.util.Map.of()));
    }
}
