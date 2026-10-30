package com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-3 价格表实体（{@code app.price_book}，V64）。
 * <p>
 * <b>单价版本化</b>：唯一键 {@code unit + region + plan_version}。改价时<b>插入新版本行</b>
 * （{@code version + 1} / {@code plan_version + 1}），旧版本行的 {@code effective_from} 原样保留。
 * 用量台账（{@code app.usage_ledger}）在扣费时把当时的 {@code unit_price / price_version}
 * 快照写进行内，因此改价不追溯历史账单金额。
 * </p>
 * <p>
 * <b>D12 成本口径</b>：同一个计量单位同时有收入侧 {@code unit_price} 与成本侧 {@code cost_per_unit}，
 * 毛利 = 收入 − 成本。
 * </p>
 */
@Data
@TableName("price_book")
public class PriceBook {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 计量单位（BillableUnit 枚举名） */
    private String unit;

    /** 地区，默认 CN */
    private String region;

    /** 套餐版本（与 app.subscription_plan.version 语义对齐） */
    private Integer planVersion;

    /** 价格行版本，改价递增；usage_ledger.price_version 引用它 */
    private Integer version;

    /** 收入侧单价 NUMERIC(18,4) */
    private BigDecimal unitPrice;

    /** 成本侧单价 NUMERIC(18,4) */
    private BigDecimal costPerUnit;

    private String currency;

    /** 生效时间；旧版本行保留原值 */
    private OffsetDateTime effectiveFrom;

    private OffsetDateTime createdAt;
}
