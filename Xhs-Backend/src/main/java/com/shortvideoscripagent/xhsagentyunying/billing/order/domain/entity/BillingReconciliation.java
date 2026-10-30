package com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-2 对账差异实体（{@code app.billing_reconciliation}，V63）。
 */
@Data
@TableName(value = "billing_reconciliation")
public class BillingReconciliation {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID orderId;

    private String orderNo;

    private String provider;

    private String localStatus;

    private String providerStatus;

    /** local_paid_channel_unpaid / local_unpaid_channel_paid / amount_mismatch */
    private String diffType;

    /** 渠道金额 - 本地金额 */
    private BigDecimal amountDiff;

    /** open / resolved / ignored */
    private String status;

    private String resolvedBy;

    private OffsetDateTime resolvedAt;

    private String resolutionNote;

    private OffsetDateTime createdAt;
}
