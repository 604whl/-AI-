package com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-2 支付事件实体（{@code app.payment_event}，V63）。
 * <p>
 * 回调幂等 / 重放保护的核心：{@code (provider, event_id)} 唯一，
 * 同一渠道事件只处理一次。
 * </p>
 */
@Data
@TableName(value = "payment_event")
public class PaymentEvent {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String provider;

    /** 渠道事件 ID */
    private String eventId;

    private String orderNo;

    private String eventType;

    private BigDecimal amount;

    private String currency;

    private OffsetDateTime occurredAt;

    private OffsetDateTime receivedAt;

    private OffsetDateTime processedAt;

    /** processed / rejected / duplicate / failed */
    private String result;

    /** 失败原因（不含凭据） */
    private String reason;
}
