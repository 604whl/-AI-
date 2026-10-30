package com.shortvideoscripagent.xhsagentyunying.billing.payment;

import com.shortvideoscripagent.xhsagentyunying.billing.order.OrderService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.PaymentEvent;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.PaymentEventMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * S4-2 支付回调服务单元测试。
 * <p>
 * <b>关键回归点</b>：
 * <ul>
 *   <li>验签失败 → 必须抛异常，<b>绝不</b>凭订单号直接改状态；</li>
 *   <li>同一事件 ID 重放 → 返回 duplicate，订单只入账一次；</li>
 *   <li>金额不符 → 拒绝，且事件必须落库为 rejected（P2-6：不能被事务回滚抹掉）；</li>
 *   <li>渠道与订单不一致 → 拒绝。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentCallbackServiceTest {

    @Mock
    private PaymentProviderRegistry providerRegistry;
    @Mock
    private PaymentEventMapper paymentEventMapper;
    @Mock
    private SubscriptionOrderMapper orderMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private PaymentProvider provider;

    private PaymentCallbackService service;

    @BeforeEach
    void setUp() {
        when(provider.provider()).thenReturn("mock");
        when(providerRegistry.require(anyString())).thenReturn(provider);
        service = new PaymentCallbackService(providerRegistry, paymentEventMapper, orderMapper,
                orderService, auditWriter, new ImmediateTxManager());
    }

    private PaymentProvider.PaymentCallback callback(String eventId, String orderNo,
                                                    BigDecimal amount, boolean paid) {
        return new PaymentProvider.PaymentCallback(eventId, "PAYMENT_SUCCESS", "prov-" + eventId,
                orderNo, amount, "CNY", paid, OffsetDateTime.now(), Map.of());
    }

    private SubscriptionOrder order(String orderNo, BigDecimal amount, String orderStatus) {
        SubscriptionOrder o = new SubscriptionOrder();
        o.setId(java.util.UUID.randomUUID());
        o.setOrderNo(orderNo);
        o.setWorkspaceId("ws_test");
        o.setAmount(amount);
        o.setStatus(orderStatus);
        o.setProvider("mock");
        o.setOrderType("subscription");
        return o;
    }

    @Test
    void verifyFailure_shouldThrowAndNotTouchOrder() {
        when(provider.verifyCallback(anyString(), any()))
                .thenThrow(new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_invalid"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleCallback("mock", "{}", Map.of()));

        assertEquals(PaymentErrors.SIGNATURE_INVALID, ex.getCode());
        // 关键：订单状态绝不能被改动
        verify(orderMapper, never()).compareAndSetPaid(any(), any(), any());
        verify(paymentEventMapper, never()).insert(any(PaymentEvent.class));
        verify(auditWriter).recordFailure(eq("PAYMENT_CALLBACK_REJECTED"), eq("PAYMENT_EVENT"),
                eq("mock"), eq(PaymentErrors.SIGNATURE_INVALID), anyString(), any());
    }

    @Test
    void duplicateEvent_shouldReturnDuplicateWithoutCrediting() {
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-1", "SO-1",
                new BigDecimal("199.0000"), true));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-1"))
                .thenReturn(new PaymentEvent());

        OrderDtos.CallbackResult result = service.handleCallback("mock", "{}", Map.of());

        assertEquals("duplicate", result.getResult());
        assertFalse(result.isCredited());
        verify(orderMapper, never()).compareAndSetPaid(any(), any(), any());
    }

    @Test
    void successfulCallback_shouldCreditOrderOnceAndEnqueueFulfillment() {
        SubscriptionOrder o = order("SO-2", new BigDecimal("199.0000"), "pending");
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-2", "SO-2",
                new BigDecimal("199.0000"), true));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-2")).thenReturn(null);
        when(orderMapper.selectByOrderNo("SO-2")).thenReturn(o);
        when(orderMapper.compareAndSetPaid(any(), any(), any())).thenReturn(1);
        when(orderMapper.selectById(o.getId())).thenReturn(o);

        OrderDtos.CallbackResult result = service.handleCallback("mock", "{}", Map.of());

        assertEquals("processed", result.getResult());
        assertTrue(result.isCredited());
        assertTrue(result.isFulfillmentEnqueued());
        verify(orderMapper).compareAndSetPaid(eq(o.getId()), any(), anyString());
        verify(orderService).enqueueFulfillment(o);
    }

    @Test
    void amountMismatch_shouldRejectAndPersistRejectedEvent() {
        SubscriptionOrder o = order("SO-3", new BigDecimal("199.0000"), "pending");
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-3", "SO-3",
                new BigDecimal("1.0000"), true));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-3")).thenReturn(null);
        when(orderMapper.selectByOrderNo("SO-3")).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleCallback("mock", "{}", Map.of()));

        assertEquals(PaymentErrors.AMOUNT_MISMATCH, ex.getCode());
        verify(orderMapper, never()).compareAndSetPaid(any(), any(), any());

        // P2-6 回归：事件必须被标记为 rejected（用独立事务提交，不被外层回滚抹掉）
        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventMapper, atLeastOnce()).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(e -> "rejected".equals(e.getResult())));
    }

    @Test
    void providerMismatch_shouldReject() {
        SubscriptionOrder o = order("SO-4", new BigDecimal("199.0000"), "pending");
        o.setProvider("alipay");
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-4", "SO-4",
                new BigDecimal("199.0000"), true));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-4")).thenReturn(null);
        when(orderMapper.selectByOrderNo("SO-4")).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleCallback("mock", "{}", Map.of()));

        assertEquals(PaymentErrors.ORDER_STATE_CONFLICT, ex.getCode());
        verify(orderMapper, never()).compareAndSetPaid(any(), any(), any());
    }

    @Test
    void orderNotFound_shouldReject() {
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-5", "SO-5",
                new BigDecimal("199.0000"), true));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-5")).thenReturn(null);
        when(orderMapper.selectByOrderNo("SO-5")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleCallback("mock", "{}", Map.of()));

        assertEquals(PaymentErrors.ORDER_NOT_FOUND, ex.getCode());
        verify(orderMapper, never()).compareAndSetPaid(any(), any(), any());
    }

    @Test
    void alreadyPaidOrder_shouldStillEnqueueFulfillment() {
        SubscriptionOrder o = order("SO-6", new BigDecimal("199.0000"), "pending");
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-6", "SO-6",
                new BigDecimal("199.0000"), true));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-6")).thenReturn(null);
        when(orderMapper.selectByOrderNo("SO-6")).thenReturn(o);
        // 已入账（重复回调）
        when(orderMapper.compareAndSetPaid(any(), any(), any())).thenReturn(0);
        when(orderMapper.selectById(o.getId())).thenReturn(o);

        OrderDtos.CallbackResult result = service.handleCallback("mock", "{}", Map.of());

        assertEquals("processed", result.getResult());
        assertTrue(result.isFulfillmentEnqueued());
        verify(orderService).enqueueFulfillment(o);
    }

    @Test
    void nonPaidEvent_shouldRecordWithoutCrediting() {
        when(provider.verifyCallback(anyString(), any())).thenReturn(callback("evt-7", "SO-7",
                new BigDecimal("199.0000"), false));
        when(paymentEventMapper.selectByProviderEvent("mock", "evt-7")).thenReturn(null);

        OrderDtos.CallbackResult result = service.handleCallback("mock", "{}", Map.of());

        assertEquals("processed", result.getResult());
        assertFalse(result.isCredited());
        verify(orderMapper, never()).compareAndSetPaid(any(), any(), any());
    }

    @Test
    void amountEquals_shouldUseCompareToNotEquals() {
        // scale 不同但数值相同 -> 必须判定为相等
        assertTrue(PaymentCallbackService.amountEquals(new BigDecimal("199.0000"),
                new BigDecimal("199.00")));
        assertFalse(PaymentCallbackService.amountEquals(new BigDecimal("199.0000"),
                new BigDecimal("199.0001")));
        assertFalse(PaymentCallbackService.amountEquals(null, new BigDecimal("1.00")));
    }

    /**
     * 立即提交的 {@link PlatformTransactionManager} 桩。
     */
    private static class ImmediateTxManager implements PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op
        }
    }
}
