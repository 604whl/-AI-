package com.shortvideoscripagent.xhsagentyunying.billing.refund;

import com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.InvoiceService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProvider;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.entity.RefundOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.mapper.RefundOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService;
import com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.refund.RefundDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RedisRateLimiter;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaRefundService;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S4-4 {@link RefundService} 单元测试。
 * <p>
 * 覆盖审核清单点名的分支：
 * <ul>
 *   <li>申请幂等：同幂等键重复提交返回同一张退款单，不重复插入；</li>
 *   <li>超额退款：累计已承诺退款 + 本次 &gt; 订单实付金额 → 拒绝；</li>
 *   <li>审核幂等：重复审核通过不会重复调用渠道退款；</li>
 *   <li>额度 / 余额冲正：按量订单回补余额，订阅订单取消订阅并降级额度；</li>
 *   <li>渠道回调幂等：同一 provider_refund_id 重复投递只生效一次。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final String WS = "ws_refund_test";
    private static final Long USER = 1001L;
    private static final Long ADMIN = 2002L;
    private static final String ORDER_NO = "SO-TEST-0001";

    @Mock
    private RefundOrderMapper refundMapper;
    @Mock
    private SubscriptionOrderMapper orderMapper;
    @Mock
    private PaymentProviderRegistry providerRegistry;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private IdempotencyReplay idempotencyReplay;
    @Mock
    private BalanceService balanceService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private QuotaRefundService quotaRefundService;
    @Mock
    private UsageLedgerMapper usageLedgerMapper;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ObjectProvider<InvoiceService> invoiceServiceProvider;

    private RefundService service() {
        lenient().when(invoiceServiceProvider.getIfAvailable()).thenReturn(invoiceService);
        return new RefundService(refundMapper, orderMapper, providerRegistry, workspaceService,
                auditWriter, rateLimiter, idempotencyReplay, balanceService, subscriptionService,
                quotaRefundService, usageLedgerMapper, transactionManager, invoiceServiceProvider);
    }

    private SubscriptionOrder order(String status, BigDecimal amount, String planId) {
        SubscriptionOrder o = new SubscriptionOrder();
        o.setId(UUID.randomUUID());
        o.setOrderNo(ORDER_NO);
        o.setWorkspaceId(WS);
        o.setAmount(amount);
        o.setCurrency("CNY");
        o.setStatus(status);
        o.setProvider("mock");
        o.setProviderOrderId("MOCK-" + ORDER_NO);
        o.setPlanId(planId == null ? null : UUID.fromString(planId));
        return o;
    }

    private RefundDtos.CreateRefundRequest request(BigDecimal amount) {
        RefundDtos.CreateRefundRequest req = new RefundDtos.CreateRefundRequest();
        req.setOrderNo(ORDER_NO);
        req.setAmount(amount);
        req.setReason("用户申请退款");
        return req;
    }

    private RefundOrder refund(String status, BigDecimal amount, UUID orderId) {
        RefundOrder r = new RefundOrder();
        r.setId(UUID.randomUUID());
        r.setRefundNo("RF-TEST");
        r.setOrderId(orderId);
        r.setOrderNo(ORDER_NO);
        r.setWorkspaceId(WS);
        r.setAmount(amount);
        r.setCurrency("CNY");
        r.setStatus(status);
        r.setProvider("mock");
        return r;
    }

    // ── 申请 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("申请退款：OWNER 可创建，退款单落库为 pending")
    void requestRefund_createsPendingOrder() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("paid", new BigDecimal("100.0000"), null));
        when(refundMapper.sumCommittedAmount(any())).thenReturn(BigDecimal.ZERO);
        when(refundMapper.insert(any(RefundOrder.class))).thenAnswer(inv -> {
            RefundOrder r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return 1;
        });

        RefundDtos.RefundView view = service().requestRefund(WS, USER,
                request(new BigDecimal("50.0000")), "idem-1");

        assertNotNull(view);
        assertEquals("pending", view.getStatus());
        assertEquals(WS, view.getWorkspaceId());
        assertEquals(ORDER_NO, view.getOrderNo());
        assertEquals(0, new BigDecimal("50.0000").compareTo(view.getAmount()));
        verify(refundMapper).insert(any(RefundOrder.class));
    }

    @Test
    @DisplayName("申请退款：非 OWNER 被拒（403）")
    void requestRefund_nonOwnerRejected() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.ADMIN);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requestRefund(WS, USER, request(null), "idem-1"));
        assertEquals(40316, ex.getCode());
        verify(refundMapper, never()).insert(any(RefundOrder.class));
    }

    @Test
    @DisplayName("申请退款：幂等键重放返回同一张退款单，不重复插入")
    void requestRefund_idempotentReplay() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        RefundOrder existing = refund("pending", new BigDecimal("100.0000"), null);
        existing.setRefundNo("RF-EXISTING");
        when(refundMapper.selectByIdempotencyKey(WS, "idem-1")).thenReturn(existing);

        RefundDtos.RefundView view = service().requestRefund(WS, USER, request(null), "idem-1");

        assertEquals("RF-EXISTING", view.getRefundNo());
        verify(refundMapper, never()).insert(any(RefundOrder.class));
        verify(orderMapper, never()).selectByOrderNo(anyString());
    }

    @Test
    @DisplayName("申请退款：跨 workspace 访问订单一律 404")
    void requestRefund_crossWorkspace404() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        SubscriptionOrder foreign = order("paid", new BigDecimal("100.0000"), null);
        foreign.setWorkspaceId("ws_other");
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(foreign);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requestRefund(WS, USER, request(null), "idem-1"));
        assertEquals(40416, ex.getCode());
    }

    @Test
    @DisplayName("申请退款：累计已承诺退款 + 本次超过订单金额 → 拒绝")
    void requestRefund_exceedsOrderAmount() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("paid", new BigDecimal("100.0000"), null));
        when(refundMapper.sumCommittedAmount(any())).thenReturn(new BigDecimal("80.0000"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requestRefund(WS, USER, request(new BigDecimal("30.0000")), "idem-1"));
        assertEquals(40016, ex.getCode());
        assertEquals("refund_amount_exceeds_order", ex.getMessage());
        verify(refundMapper, never()).insert(any(RefundOrder.class));
    }

    @Test
    @DisplayName("申请退款：订单未支付不可退")
    void requestRefund_orderNotPaid() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("pending", new BigDecimal("100.0000"), null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requestRefund(WS, USER, request(null), "idem-1"));
        assertEquals(40916, ex.getCode());
        assertEquals("order_not_refundable", ex.getMessage());
    }

    // ── 审核 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("审核通过：pending → approved → 调渠道 → refunded，并回补余额")
    void approve_refundsAndCreditsBalance() {
        SubscriptionOrder o = order("paid", new BigDecimal("100.0000"), null);
        RefundOrder pending = refund("pending", new BigDecimal("100.0000"), o.getId());
        RefundOrder done = refund("refunded", new BigDecimal("100.0000"), o.getId());
        done.setProviderRefundId("MOCK-RF-1");

        when(refundMapper.selectOne(any())).thenReturn(pending);
        when(orderMapper.selectById(o.getId())).thenReturn(o);
        when(refundMapper.compareAndSetApproved(any(), any(), any(), any())).thenReturn(1);
        when(refundMapper.compareAndSetRefunded(any(), any(), any())).thenReturn(1);
        when(refundMapper.selectById(pending.getId())).thenReturn(done);
        when(usageLedgerMapper.selectUnbilledByWorkspace(anyString(), anyInt())).thenReturn(List.of());

        PaymentProvider provider = mock(PaymentProvider.class);
        when(providerRegistry.require("mock")).thenReturn(provider);
        when(provider.refund(any(PaymentProvider.RefundRequest.class)))
                .thenReturn(new PaymentProvider.RefundResult("MOCK-RF-1", "refunded", true));

        RefundDtos.RefundResult result = service().approve("RF-TEST", ADMIN, "同意退款");

        assertEquals("refunded", result.getStatus());
        assertTrue(result.isAccepted());
        assertTrue(result.isEntitlementReversed());
        verify(balanceService).creditBalance(eq(WS), eq(new BigDecimal("100.0000")), eq("CNY"),
                eq("refund:RF-TEST"), eq("order_refund"));
        verify(subscriptionService, never()).cancelForRefund(anyString());
    }

    @Test
    @DisplayName("审核通过：订阅订单取消订阅并降级额度")
    void approve_subscriptionCancelsSubscription() {
        String planId = "11111111-1111-1111-1111-111111111111";
        SubscriptionOrder o = order("fulfilled", new BigDecimal("299.0000"), planId);
        RefundOrder pending = refund("pending", new BigDecimal("299.0000"), o.getId());
        RefundOrder done = refund("refunded", new BigDecimal("299.0000"), o.getId());

        when(refundMapper.selectOne(any())).thenReturn(pending);
        when(orderMapper.selectById(o.getId())).thenReturn(o);
        when(refundMapper.compareAndSetApproved(any(), any(), any(), any())).thenReturn(1);
        when(refundMapper.compareAndSetRefunded(any(), any(), any())).thenReturn(1);
        when(refundMapper.selectById(pending.getId())).thenReturn(done);
        when(usageLedgerMapper.selectUnbilledByWorkspace(anyString(), anyInt())).thenReturn(List.of());

        PaymentProvider provider = mock(PaymentProvider.class);
        when(providerRegistry.require("mock")).thenReturn(provider);
        when(provider.refund(any(PaymentProvider.RefundRequest.class)))
                .thenReturn(new PaymentProvider.RefundResult("MOCK-RF-2", "refunded", true));

        service().approve("RF-TEST", ADMIN, null);

        verify(subscriptionService).cancelForRefund(WS);
        verify(balanceService, never())
                .creditBalance(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("审核通过：重复审核不重复调用渠道退款（幂等）")
    void approve_idempotentNoSecondProviderCall() {
        RefundOrder approved = refund("approved", new BigDecimal("10.0000"), null);
        when(refundMapper.selectOne(any())).thenReturn(approved);

        RefundDtos.RefundResult result = service().approve("RF-TEST", ADMIN, null);

        assertEquals("approved", result.getStatus());
        verify(providerRegistry, never()).require(anyString());
        verify(refundMapper, never()).compareAndSetApproved(any(), any(), any(), any());
    }

    @Test
    @DisplayName("审核驳回：pending → rejected")
    void reject_marksRejected() {
        RefundOrder pending = refund("pending", new BigDecimal("10.0000"), null);
        RefundOrder rejected = refund("rejected", new BigDecimal("10.0000"), null);

        when(refundMapper.selectOne(any())).thenReturn(pending);
        when(refundMapper.compareAndSetRejected(any(), any(), any(), any())).thenReturn(1);
        when(refundMapper.selectById(pending.getId())).thenReturn(rejected);

        RefundDtos.RefundResult result = service().reject("RF-TEST", ADMIN, "不符合退款条件");

        assertEquals("rejected", result.getStatus());
        verify(refundMapper).compareAndSetRejected(any(), any(), any(), eq("不符合退款条件"));
    }

    @Test
    @DisplayName("审核驳回：驳回备注必填")
    void reject_requiresNote() {
        RefundOrder pending = refund("pending", new BigDecimal("10.0000"), null);
        when(refundMapper.selectOne(any())).thenReturn(pending);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().reject("RF-TEST", ADMIN, "  "));
        assertEquals("review_note_required", ex.getMessage());
    }

    // ── 渠道回调 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("渠道回调：approved → refunded 并冲正权益")
    void confirmFromCallback_marksRefunded() {
        SubscriptionOrder o = order("paid", new BigDecimal("100.0000"), null);
        RefundOrder approved = refund("approved", new BigDecimal("100.0000"), o.getId());
        RefundOrder done = refund("refunded", new BigDecimal("100.0000"), o.getId());

        when(refundMapper.selectByProviderRefundId("MOCK-RF-6")).thenReturn(approved);
        when(orderMapper.selectById(o.getId())).thenReturn(o);
        when(refundMapper.compareAndSetRefunded(any(), eq("MOCK-RF-6"), any())).thenReturn(1);
        when(refundMapper.selectById(approved.getId())).thenReturn(done);
        when(usageLedgerMapper.selectUnbilledByWorkspace(anyString(), anyInt())).thenReturn(List.of());

        RefundDtos.RefundResult result = service().confirmFromCallback("MOCK-RF-6", true, null);

        assertEquals("refunded", result.getStatus());
        assertTrue(result.isEntitlementReversed());
        verify(balanceService).creditBalance(eq(WS), any(), eq("CNY"), eq("refund:RF-TEST"), eq("order_refund"));
    }

    @Test
    @DisplayName("渠道回调：重复投递同一 provider_refund_id 只生效一次（不重复冲正）")
    void confirmFromCallback_duplicateIgnored() {
        RefundOrder done = refund("refunded", new BigDecimal("100.0000"), null);
        when(refundMapper.selectByProviderRefundId("MOCK-RF-7")).thenReturn(done);

        RefundDtos.RefundResult result = service().confirmFromCallback("MOCK-RF-7", true, null);

        assertEquals("refunded", result.getStatus());
        verify(balanceService, never())
                .creditBalance(anyString(), any(), anyString(), anyString(), anyString());
        verify(refundMapper, never()).compareAndSetRefunded(any(), any(), any());
    }

    @Test
    @DisplayName("渠道回调：未审核通过的退款单不能被确认")
    void confirmFromCallback_requiresApproved() {
        RefundOrder pending = refund("pending", new BigDecimal("100.0000"), null);
        when(refundMapper.selectByProviderRefundId("MOCK-RF-8")).thenReturn(pending);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().confirmFromCallback("MOCK-RF-8", true, null));
        assertEquals("refund_not_approved", ex.getMessage());
    }

    @Test
    @DisplayName("渠道回调：失败回调把 approved 置为 failed")
    void confirmFromCallback_failureMarksFailed() {
        RefundOrder approved = refund("approved", new BigDecimal("100.0000"), null);
        RefundOrder failed = refund("failed", new BigDecimal("100.0000"), null);

        when(refundMapper.selectByProviderRefundId("MOCK-RF-9")).thenReturn(approved);
        when(refundMapper.compareAndSetFailed(any(), anyString())).thenReturn(1);
        when(refundMapper.selectById(approved.getId())).thenReturn(failed);

        RefundDtos.RefundResult result = service().confirmFromCallback("MOCK-RF-9", false, "渠道余额不足");

        assertEquals("failed", result.getStatus());
        assertFalse(result.isAccepted());
    }
}
