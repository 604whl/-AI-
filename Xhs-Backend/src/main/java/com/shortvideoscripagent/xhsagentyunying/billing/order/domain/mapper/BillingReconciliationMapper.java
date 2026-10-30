package com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.BillingReconciliation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S4-2 对账差异 Mapper。
 */
@Mapper
public interface BillingReconciliationMapper extends BaseMapper<BillingReconciliation> {

    /**
     * 按（order_no, provider, diff_type）取差异记录，重跑对账时幂等复用。
     */
    @Select("""
            SELECT * FROM app.billing_reconciliation
            WHERE COALESCE(order_no, '') = COALESCE(CAST(#{orderNo} AS VARCHAR), '')
              AND provider = #{provider}
              AND diff_type = #{diffType}
            LIMIT 1
            """)
    BillingReconciliation selectByOrderDiff(@Param("orderNo") String orderNo,
                                            @Param("provider") String provider,
                                            @Param("diffType") String diffType);

    /**
     * 取某时间点之后创建的全部差异（对账用：本地有、渠道无的订单）。
     */
    @Select("""
            SELECT * FROM app.billing_reconciliation
            WHERE created_at >= #{since}
            LIMIT #{limit}
            """)
    List<BillingReconciliation> selectCreatedSince(@Param("since") java.time.OffsetDateTime since,
                                                     @Param("limit") int limit);

    /**
     * 按 ID 取差异。
     */
    @Select("""
            SELECT * FROM app.billing_reconciliation
            WHERE id = #{id}
            LIMIT 1
            """)
    BillingReconciliation selectById(@Param("id") UUID id);
}
