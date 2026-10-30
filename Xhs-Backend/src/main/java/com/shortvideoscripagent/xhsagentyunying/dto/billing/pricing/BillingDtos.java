package com.shortvideoscripagent.xhsagentyunying.dto.billing.pricing;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * S4-3 计价 / 余额 / 成本 DTO。
 * <p>
 * 金额一律 {@link BigDecimal}（对应 DB 的 NUMERIC(18,4)），禁止 double。
 * </p>
 */
public final class BillingDtos {

    private BillingDtos() {
    }

    // ══════════════════════════════════════════════════════════════════
    // 价格表
    // ══════════════════════════════════════════════════════════════════

    /** 价格行视图。 */
    @Data
    public static class PriceView {
        private String unit;
        private String unitDisplayName;
        private String region;
        private Integer planVersion;
        private Integer version;
        /** 收入侧单价 */
        private BigDecimal unitPrice;
        /** 成本侧单价（D12 口径） */
        private BigDecimal costPerUnit;
        private String currency;
        private OffsetDateTime effectiveFrom;
    }

    // ══════════════════════════════════════════════════════════════════
    // 余额
    // ══════════════════════════════════════════════════════════════════

    /** 余额视图。 */
    @Data
    public static class BalanceView {
        private String workspaceId;
        private BigDecimal balance;
        private BigDecimal frozen;
        private BigDecimal available;
        private String currency;
        private String status;
        private OffsetDateTime updatedAt;
    }

    /** 余额入账请求（充值到账）。 */
    @Data
    public static class TopUpRequest {
        /** 入账金额，必须 > 0 */
        private BigDecimal amount;
        /** 币种，默认 CNY */
        private String currency;
        /** 业务单号（订单号），用于幂等追溯 */
        private String orderNo;
        /** 备注 / 原因 */
        private String reason;
    }

    // ══════════════════════════════════════════════════════════════════
    // 账单
    // ══════════════════════════════════════════════════════════════════

    /** 账单列表项。 */
    @Data
    public static class StatementSummary {
        private String id;
        private String workspaceId;
        private OffsetDateTime periodStart;
        private OffsetDateTime periodEnd;
        private BigDecimal totalAmount;
        private String currency;
        private String status;
        private OffsetDateTime generatedAt;
    }

    /** 账单详情。 */
    @Data
    public static class StatementDetail extends StatementSummary {
        private String idempotencyKey;
        private java.util.List<StatementItemView> items = new java.util.ArrayList<>();
    }

    /**
     * 账单扣费 / 作废请求。
     */
    @Data
    public static class ChargeStatementRequest {
        /** 扣费或作废原因（写入台账 reason 与审计） */
        private String reason;
    }

    /** 账单行。 */
    @Data
    public static class StatementItemView {
        private String id;
        private String unit;
        private String unitDisplayName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        /** 回溯锚点：首个 usage_ledger.id */
        private String ledgerRef;
        /** 回溯区间：首个..最后一个 usage_ledger.id */
        private String ledgerRange;
        /** 该行覆盖的 ledger 明细条数 */
        private Integer ledgerCount;
    }

    // ══════════════════════════════════════════════════════════════════
    // 成本 / 毛利
    // ══════════════════════════════════════════════════════════════════

    /** 按计量单位的用量 / 成本聚合行。 */
    @Data
    public static class CostUsageRow {
        private String unit;
        private String unitDisplayName;
        /** 用量（净单位数，扣减为正、冲正为负） */
        private BigDecimal quantity;
        /** 收入 */
        private BigDecimal revenue;
        /** 成本 */
        private BigDecimal cost;
        /** 毛利 = 收入 − 成本 */
        private BigDecimal margin;
    }

    /** 按时间段聚合的成本用量视图。 */
    @Data
    public static class CostUsageView {
        private String workspaceId;
        private OffsetDateTime from;
        private OffsetDateTime to;
        private BigDecimal totalRevenue;
        private BigDecimal totalCost;
        private BigDecimal totalMargin;
        private String currency;
        private java.util.List<CostUsageRow> rows = new java.util.ArrayList<>();
    }

    /** 管理端按工作区聚合的成本毛利行。 */
    @Data
    public static class WorkspaceMarginRow {
        private String workspaceId;
        /** 套餐编码（app.workspace.plan_code 冗余列） */
        private String planCode;
        private BigDecimal quantity;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal margin;
        /** 毛利率（%），收入为 0 时为 null */
        private BigDecimal marginRate;
    }

    /** 管理端成本毛利聚合视图。 */
    @Data
    public static class MarginView {
        private OffsetDateTime from;
        private OffsetDateTime to;
        private BigDecimal totalRevenue;
        private BigDecimal totalCost;
        private BigDecimal totalMargin;
        private String currency;
        private java.util.List<WorkspaceMarginRow> rows = new java.util.ArrayList<>();
    }

    // ══════════════════════════════════════════════════════════════════
    // 分页
    // ══════════════════════════════════════════════════════════════════

    /** 统一分页结果。 */
    @Data
    public static class PageResult<T> {
        private final java.util.List<T> items;
        private final long total;
        private final int page;
        private final int size;

        public PageResult(java.util.List<T> items, long total, int page, int size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }
    }
}
