package com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbMapTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S4-2 支付订单实体（{@code app.subscription_order}，V63）。
 * <p>
 * 状态机：{@code pending → paid → (fulfilled | refunded | closed)}；
 * {@code pending} 超时 → {@code closed}。
 * </p>
 */
@Data
@TableName(value = "subscription_order", autoResultMap = true)
public class SubscriptionOrder {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 订单号，全局唯一：SO + 时间戳 + 随机 */
    private String orderNo;

    /** 所属工作区（VARCHAR(32)） */
    private String workspaceId;

    /** 关联套餐（app.subscription_plan.id） */
    private UUID planId;

    /** 下单时冻结的套餐版本号 */
    private Integer planVersion;

    /** subscription（订阅）/ usage（按量充值） */
    private String orderType;

    private Integer quantity;

    /** 订单金额 NUMERIC(18,4) */
    private BigDecimal amount;

    private String currency;

    /** alipay / wechat / mock / stripe */
    private String provider;

    /** 渠道侧订单号 */
    private String providerOrderId;

    /** pending / paid / fulfilled / refunded / closed */
    private String status;

    /** 渠道返回的支付参数（JSONB）；不含任何凭据 */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> paymentPayload;

    private OffsetDateTime paidAt;

    private OffsetDateTime fulfilledAt;

    /** 订单过期时间 */
    private OffsetDateTime expiresAt;

    private String idempotencyKey;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
