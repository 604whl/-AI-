package com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S4-1 套餐 Mapper。
 */
@Mapper
public interface SubscriptionPlanMapper extends BaseMapper<SubscriptionPlan> {

    /**
     * 按 code 取所有 active 版本，按版本倒序（v1 之外取最新）。
     */
    @Select("""
            SELECT * FROM app.subscription_plan
            WHERE code = #{code}
              AND status = 'active'
            ORDER BY version DESC
            """)
    List<SubscriptionPlan> selectActiveByCode(@Param("code") String code);

    /**
     * 取全部 active 套餐（每个 code 的最新版本由 Service 层去重）。
     */
    @Select("""
            SELECT * FROM app.subscription_plan
            WHERE status = 'active'
            ORDER BY code ASC, version DESC
            """)
    List<SubscriptionPlan> selectAllActive();

    /**
     * 按幂等键取套餐（管理端创建幂等）。
     */
    @Select("""
            SELECT * FROM app.subscription_plan
            WHERE idempotency_key = CAST(#{idempotencyKey} AS VARCHAR)
            LIMIT 1
            """)
    SubscriptionPlan selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 取某 code 的最大版本号（生成新版本时用）。
     */
    @Select("""
            SELECT COALESCE(MAX(version), 0) FROM app.subscription_plan WHERE code = #{code}
            """)
    int selectMaxVersion(@Param("code") String code);

    /**
     * 归档某 code 的全部 active 版本（下架套餐）。
     */
    @Select("""
            SELECT id FROM app.subscription_plan WHERE code = #{code} AND status = 'active'
            """)
    List<UUID> selectActiveIdsByCode(@Param("code") String code);
}
