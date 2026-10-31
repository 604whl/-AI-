package com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-4 发票实体（{@code app.invoice}，V67）。
 * <p>
 * D10：仅个人普票 + 企业普票（{@code invoiceType = personal / company}），不支持专票。
 * </p>
 * <p>
 * <b>红冲语义</b>：已开票订单退款时，对原发票开一张红字发票：
 * <ul>
 *   <li>原发票 {@code status} → {@code red_flushed}，{@code redFlushInvoiceId} 指向红冲记录；</li>
 *   <li>红冲记录 {@code status = red}，{@code redFlushInvoiceId} 指向原发票；</li>
 *   <li><b>原发票不物理删除</b>（留档要求）。</li>
 * </ul>
 * </p>
 */
@Data
@TableName(value = "invoice", autoResultMap = true)
public class Invoice {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 发票号，全局唯一：INV + 年月 + 序列 */
    private String invoiceNo;

    private String workspaceId;

    /** personal（个人普票）/ company（企业普票） */
    private String invoiceType;

    /** 发票抬头（企业名或个人姓名） */
    private String title;

    /** 纳税人识别号；企业票必填 */
    private String taxNo;

    /** 开票金额 NUMERIC(18,4) */
    private BigDecimal amount;

    private String currency;

    /** 税率（普票默认 0.06） */
    private BigDecimal taxRate;

    /** 税额 */
    private BigDecimal taxAmount;

    /** issued（已开票）/ red（红冲记录）/ red_flushed（已被红冲） */
    private String status;

    /** 关联订单 id（按账单开票时为空） */
    private UUID orderId;

    private String orderNo;

    /** 关联账单 id（app.billing_statement.id） */
    private String statementId;

    /** PDF 存储路径（MinIO object key） */
    private String pdfPath;

    /** 红冲关联：红冲记录与原发票互指 */
    private UUID redFlushInvoiceId;

    /** 开票人（平台管理员 user id） */
    private Long issuedBy;

    private OffsetDateTime issuedAt;

    private String idempotencyKey;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
