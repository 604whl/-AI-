package com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * S4-3 账单头实体（{@code app.billing_statement}，V64）。
 * <p>
 * 按订阅周期（{@code app.subscription.current_period_start/end}）聚合
 * {@code app.usage_ledger} 生成。幂等靠 {@code idempotency_key} 唯一索引兜底：
 * 同工作区同周期重复生成时命中唯一约束 → 返回既有账单。
 * </p>
 */
@Data
@TableName("billing_statement")
public class BillingStatement {

    /** 32 位十六进制 ID（UUID 去横线），与 usage_ledger.statement_id 对齐 */
    @TableId
    private String id;

    private String workspaceId;

    private OffsetDateTime periodStart;

    private OffsetDateTime periodEnd;

    /** 账单总额 NUMERIC(18,4) */
    private BigDecimal totalAmount;

    private String currency;

    /** open（未支付，不扣费）/ charging / paid / void / failed */
    private String status;

    private OffsetDateTime generatedAt;

    /** 幂等键：workspace_id + 周期起止 */
    private String idempotencyKey;

    private OffsetDateTime createdAt;
}
