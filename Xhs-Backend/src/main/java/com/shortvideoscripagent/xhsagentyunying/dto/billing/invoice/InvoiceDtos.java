package com.shortvideoscripagent.xhsagentyunying.dto.billing.invoice;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * S4-4 发票 DTO。
 */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /**
     * 开票请求（平台管理员）。
     */
    @Data
    public static class IssueInvoiceRequest {
        /** 订单号（按订单开票时必填） */
        private String orderNo;
        /** 账单 ID（按账单开票时必填，与 orderNo 二选一） */
        private String statementId;
        /** personal（个人普票）/ company（企业普票） */
        private String invoiceType;
        /** 发票抬头（企业名或个人姓名） */
        private String title;
        /** 纳税人识别号；企业票必填 */
        private String taxNo;
        /** 税率；为空取默认 0.06 */
        private BigDecimal taxRate;
    }

    /**
     * 红冲请求。
     */
    @Data
    public static class RedFlushRequest {
        /** 红冲原因 */
        private String reason;
        /** 关联退款单号（留痕） */
        private String refundNo;
    }

    /**
     * 发票视图。
     */
    @Data
    public static class InvoiceView {
        private String id;
        private String invoiceNo;
        private String workspaceId;
        private String invoiceType;
        private String title;
        private String taxNo;
        private BigDecimal amount;
        private String currency;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        /** issued（已开票）/ red（红冲记录）/ red_flushed（已被红冲） */
        private String status;
        private String orderId;
        private String orderNo;
        private String statementId;
        private String redFlushInvoiceId;
        private Long issuedBy;
        private OffsetDateTime issuedAt;
        private OffsetDateTime createdAt;
    }

    /**
     * 发票下载票据。
     * <p>
     * 下载链接签名且过期（默认 5 分钟）。
     * </p>
     */
    @Data
    public static class DownloadTicket {
        private String invoiceId;
        private String invoiceNo;
        /** 签名下载地址 */
        private String url;
        /** 过期时间戳（毫秒） */
        private long expiresAt;
        /** 有效期（秒） */
        private long expiresInSeconds;
    }
}
