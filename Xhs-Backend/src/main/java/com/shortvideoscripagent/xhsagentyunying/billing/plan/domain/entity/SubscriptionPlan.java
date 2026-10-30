package com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity;

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
 * S4-1 套餐实体（{@code app.subscription_plan}，V62）。
 * <p>
 * 套餐是<b>多版本</b>的：改价 / 改额度不 UPDATE 老版本，而是 INSERT {@code version + 1}
 * 并把老版本置 {@code archived}。订阅生效时冻结一份快照（{@code app.subscription.plan_version_snapshot}），
 * 因此套餐改价不影响存量订阅。
 * </p>
 */
@Data
@TableName(value = "subscription_plan", autoResultMap = true)
public class SubscriptionPlan {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 套餐编码：FREE / PRO / TEAM / USAGE */
    private String code;

    /** 版本号（同 code 下递增，与 code 组成唯一键） */
    private Integer version;

    /** 套餐名称 */
    private String name;

    /** 计费周期：MONTH / QUARTER / YEAR */
    private String billingPeriod;

    /** 周期价格（NUMERIC(18,4)） */
    private BigDecimal price;

    /** 币种，默认 CNY */
    private String currency;

    /** 周期内包含额度：key = BillableUnit 枚举名，value = Integer */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> includedUnits;

    /** 超额单价（NUMERIC(18,4)），USAGE 按量套餐生效 */
    private BigDecimal overagePrice;

    /** 功能开关 JSONB */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> featureFlags;

    /** draft / active / archived */
    private String status;

    /** 套餐说明 */
    private String description;

    /** 管理端写接口幂等键 */
    private String idempotencyKey;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
