package com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbMapTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S4-1 订阅实体（{@code app.subscription}，V62）。
 * <p>
 * 状态机：{@code trialing -> active -> past_due -> (active | canceled)}；
 * 到期未续费 → {@code expired}。
 * <br>
 * {@code planVersionSnapshot} 是订阅生效时冻结的套餐快照，套餐后续改价不影响存量订阅。
 * </p>
 */
@Data
@TableName(value = "subscription", autoResultMap = true)
public class Subscription {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 关联套餐（app.subscription_plan.id） */
    private UUID planId;

    /** 订阅生效时冻结的套餐快照 */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> planVersionSnapshot;

    /** trialing / active / past_due / canceled / expired / suspended */
    private String status;

    private OffsetDateTime currentPeriodStart;

    private OffsetDateTime currentPeriodEnd;

    /** 试用截止时间（非空即表示该工作区已用过试用） */
    private OffsetDateTime trialEnd;

    /** 取消/降级是否在周期末生效 */
    private Boolean cancelAtPeriodEnd;

    /** 降级目标套餐（周期末生效） */
    private UUID pendingPlanId;

    /** 是否自动续费 */
    private Boolean autoRenew;

    /** past_due 宽限期天数，D9 决策默认 7 天 */
    private Integer gracePeriodDays;

    private OffsetDateTime subscribedAt;

    private OffsetDateTime canceledAt;

    /** 写接口幂等键 */
    private String idempotencyKey;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
