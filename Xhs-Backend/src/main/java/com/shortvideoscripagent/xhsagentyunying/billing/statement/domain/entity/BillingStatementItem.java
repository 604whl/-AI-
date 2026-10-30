package com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-3 账单行实体（{@code app.billing_statement_item}，V64）。
 * <p>
 * 按计量单位聚合 ledger 明细。<b>可回溯</b>：{@code ledgerRef} 记首个 ledger id，
 * {@code ledgerRange} 记「首个..最后一个」闭区间，{@code ledgerCount} 记明细条数。
 * </p>
 */
@Data
@TableName("billing_statement_item")
public class BillingStatementItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String statementId;

    /** BillableUnit 枚举名 */
    private String unit;

    /** 数量 NUMERIC(18,4) */
    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    /** 首个 usage_ledger.id */
    private String ledgerRef;

    /** 首个..最后一个 usage_ledger.id 闭区间 */
    private String ledgerRange;

    /** 该行覆盖的 ledger 明细条数 */
    private Integer ledgerCount;

    private OffsetDateTime createdAt;
}
