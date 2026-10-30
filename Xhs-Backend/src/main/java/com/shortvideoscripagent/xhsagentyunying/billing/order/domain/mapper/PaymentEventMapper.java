package com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.PaymentEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * S4-2 支付事件 Mapper（回调幂等 / 重放保护）。
 */
@Mapper
public interface PaymentEventMapper extends BaseMapper<PaymentEvent> {

    /**
     * 按（provider, event_id）取事件；命中即代表该渠道事件已处理过（重放）。
     */
    @Select("""
            SELECT * FROM app.payment_event
            WHERE provider = #{provider}
              AND event_id = #{eventId}
            LIMIT 1
            """)
    PaymentEvent selectByProviderEvent(@Param("provider") String provider,
                                       @Param("eventId") String eventId);

    /**
     * 按 ID 取事件。
     */
    @Select("""
            SELECT * FROM app.payment_event
            WHERE id = #{id}
            LIMIT 1
            """)
    PaymentEvent selectById(@Param("id") UUID id);
}
