package com.shortvideoscripagent.xhsagentyunying.billing.invoice;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S4-4 发票 PDF 生成器。
 * <p>
 * <b>字体</b>：使用 OpenPDF 内置的 CJK 字体（{@code STSong-Light} + {@code UniGB-UCS2-H} 编码），
 * 无需外部字体文件即可正确渲染中文。
 * </p>
 * <p>
 * <b>红冲发票</b>：{@code status = 'red'} 时，金额以负数展示并标注「红字发票」。
 * </p>
 * <p>
 * <b>失败语义</b>：PDF 生成失败抛异常，由调用方决定是否落库
 * （发票记录与 PDF 文件的写入顺序见 {@link InvoiceService}）。
 * </p>
 */
@Slf4j
@Component
public class InvoicePdfGenerator {

    /** 中文 CJK 字体 */
    private static final String CJK_FONT = "STSong-Light";
    /** CJK 编码 */
    private static final String CJK_ENCODING = "UniGB-UCS2-H";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成发票 PDF 字节流。
     *
     * @param invoice 发票记录
     * @param meta    附加信息（工作区名称等），可为 null
     * @return PDF 字节
     */
    public byte[] generate(Invoice invoice, Map<String, String> meta) {
        if (invoice == null) {
            throw new IllegalArgumentException("invoice_required");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            BaseFont baseFont = resolveCjkFont();
            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Font headFont = new Font(baseFont, 11, Font.BOLD);
            Font bodyFont = new Font(baseFont, 10, Font.NORMAL);
            Font smallFont = new Font(baseFont, 9, Font.NORMAL);

            boolean isRed = "red".equals(invoice.getStatus());
            boolean isRedFlushed = "red_flushed".equals(invoice.getStatus());

            Paragraph heading = new Paragraph(isRed ? "红字发票（红冲）" : "电子发票",
                    titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingAfter(6);
            document.add(heading);

            Paragraph sub = new Paragraph(
                    isRed ? "RED INVOICE (RED FLUSH)" : "E-INVOICE", smallFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(14);
            document.add(sub);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("发票号码", nullSafe(invoice.getInvoiceNo()));
            fields.put("发票状态", statusLabel(invoice.getStatus()));
            fields.put("发票类型", "company".equals(invoice.getInvoiceType()) ? "企业普票" : "个人普票");
            fields.put("发票抬头", nullSafe(invoice.getTitle()));
            fields.put("纳税人识别号", nullSafe(invoice.getTaxNo()));
            fields.put("关联订单号", nullSafe(invoice.getOrderNo()));
            fields.put("关联账单 ID", nullSafe(invoice.getStatementId()));
            fields.put("开票时间", invoice.getIssuedAt() == null ? "" : DATE_FMT.format(invoice.getIssuedAt()));
            if (meta != null) {
                fields.put("客户名称", nullSafe(meta.get("workspaceName")));
            }
            if (isRedFlushed) {
                fields.put("冲红状态", "本发票已被红冲，红字发票见关联记录");
            }

            document.add(buildInfoTable(fields, headFont, bodyFont));

            document.add(spacer(10));

            BigDecimal amount = invoice.getAmount() == null ? BigDecimal.ZERO : invoice.getAmount();
            BigDecimal taxAmount = invoice.getTaxAmount() == null ? BigDecimal.ZERO : invoice.getTaxAmount();
            BigDecimal taxRate = invoice.getTaxRate() == null ? BigDecimal.ZERO : invoice.getTaxRate();
            BigDecimal netAmount = amount.subtract(taxAmount);
            if (isRed) {
                amount = amount.negate();
                taxAmount = taxAmount.negate();
                netAmount = netAmount.negate();
            }

            Map<String, String> amounts = new LinkedHashMap<>();
            amounts.put("金额（不含税）", netAmount.toPlainString() + " " + nullSafe(invoice.getCurrency()));
            amounts.put("税率", taxRate.multiply(BigDecimal.valueOf(100)).toPlainString() + "%");
            amounts.put("税额", taxAmount.toPlainString() + " " + nullSafe(invoice.getCurrency()));
            amounts.put("价税合计", amount.toPlainString() + " " + nullSafe(invoice.getCurrency()));

            document.add(buildInfoTable(amounts, headFont, bodyFont));

            document.add(spacer(14));
            Paragraph footer = new Paragraph(
                    "本发票由 XhsAgent 平台自动开具，仅作为记账与报销凭证使用。"
                            + "发票红冲以新增红字发票方式记录，原发票不做物理删除。", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
        } catch (Exception ex) {
            log.error("Failed to generate invoice PDF for {}: {}",
                    invoice.getInvoiceNo(), ex.getMessage(), ex);
            throw new IllegalStateException("invoice_pdf_generation_failed", ex);
        }
        return out.toByteArray();
    }

    /**
     * 解析 CJK 字体；失败时回退到内置 Helvetica（中文会变成方框，但不会让整个开票流程失败）。
     */
    private BaseFont resolveCjkFont() {
        try {
            return BaseFont.createFont(CJK_FONT, CJK_ENCODING, BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            log.warn("CJK font unavailable, falling back to Helvetica: {}", ex.getMessage());
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (IOException | DocumentException fallbackEx) {
                // 连 Helvetica 都拿不到（字体资源缺失）：直接失败，不要返回 null 造成下游 NPE
                log.error("Failed to resolve any base font for invoice PDF", fallbackEx);
                throw new IllegalStateException("invoice_pdf_font_unavailable", fallbackEx);
            }
        }
    }

    private PdfPTable buildInfoTable(Map<String, String> fields, Font headFont, Font bodyFont) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 2.8f});
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            PdfPCell key = new PdfPCell(new Phrase(entry.getKey(), headFont));
            key.setHorizontalAlignment(Element.ALIGN_LEFT);
            key.setPadding(5);
            key.setBackgroundColor(new java.awt.Color(245, 245, 245));

            PdfPCell value = new PdfPCell(new Phrase(nullSafe(entry.getValue()), bodyFont));
            value.setHorizontalAlignment(Element.ALIGN_LEFT);
            value.setPadding(5);

            table.addCell(key);
            table.addCell(value);
        }
        return table;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        p.setSpacingAfter(0);
        return p;
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "issued" -> "已开票";
            case "red" -> "红字发票";
            case "red_flushed" -> "已红冲";
            default -> status;
        };
    }

    private String nullSafe(String raw) {
        return raw == null ? "" : raw;
    }
}
