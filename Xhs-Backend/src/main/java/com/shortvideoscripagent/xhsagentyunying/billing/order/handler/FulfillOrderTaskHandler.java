package com.shortvideoscripagent.xhsagentyunying.billing.order.handler;

import com.shortvideoscripagent.xhsagentyunying.billing.order.OrderFulfillmentService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * S4-2 订单履约任务处理器。
 * <p>
 * 处理类型：{@code BILLING_FULFILL_ORDER}
 * </p>
 * <p>
 * <b>失败语义</b>：履约失败时抛异常 → 队列指数退避重试 → 超过次数进 DLQ；
 * <b>订单状态保持 {@code paid}</b>，不丢单。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class FulfillOrderTaskHandler implements TaskHandler {

    private final SubscriptionOrderMapper orderMapper;
    private final OrderFulfillmentService fulfillmentService;

    @Override
    public String taskType() {
        return "BILLING_FULFILL_ORDER";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        UUID orderId;
        try {
            orderId = UUID.fromString(taskId);
        } catch (IllegalArgumentException ex) {
            orderId = payload != null && payload.get("orderId") != null
                    ? UUID.fromString(String.valueOf(payload.get("orderId")))
                    : null;
        }
        if (orderId == null) {
            throw new IllegalStateException("orderId required in payload");
        }

        SubscriptionOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("Fulfillment task skipped, order not found: orderId={}", orderId);
            return;
        }
        log.info("FulfillOrderTaskHandler: orderNo={} status={}", order.getOrderNo(), order.getStatus());
        fulfillmentService.fulfill(order);
    }
}
