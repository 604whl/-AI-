package com.shortvideoscripagent.xhsagentyunying.billing.subscription;

import com.shortvideoscripagent.xhsagentyunying.billing.plan.PlanService;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.SubscriptionDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RedisRateLimiter;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-8：{@link SubscriptionService} 单元测试。
 * <p>
 * 覆盖审核清单点名的分支：
 * <ul>
 *   <li>P0-1：付费套餐不得零元开通（返回 409 payment_required_create_order）；</li>
 *   <li>试用「仅一次」；</li>
 *   <li>幂等键重放返回既有订阅；</li>
 *   <li>非 OWNER 调用被拒（403 owner_required）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final String WS = "ws_sub_test";
    private static final long USER = 42L;

    @Mock
    private SubscriptionMapper subscriptionMapper;
    @Mock
    private com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper workspaceMapper;
    @Mock
    private PlanService planService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private WorkspaceQuotaMapper workspaceQuotaMapper;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private IdempotencyReplay idempotencyReplay;
    @Mock
    private com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionWriteIdempotencyMapper
            writeIdempotencyMapper;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionMapper, writeIdempotencyMapper,
                workspaceQuotaMapper, workspaceMapper,
                planService, workspaceService, auditWriter, rateLimiter, idempotencyReplay);
    }

    private SubscriptionPlan plan(String code, String price) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        p.setVersion(1);
        p.setName(code + " plan");
        p.setBillingPeriod("MONTH");
        p.setPrice(new BigDecimal(price));
        p.setCurrency("CNY");
        p.setIncludedUnits(Map.of("ANALYSIS", 100));
        p.setFeatureFlags(Map.of());
        p.setStatus("active");
        return p;
    }

    @Test
    @DisplayName("P0-1 付费套餐直接订阅被拒（409 payment_required_create_order），不建订阅")
    void subscribe_paidPlan_rejected() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(planService.requireActivePlanByCode("PRO")).thenReturn(plan("PRO", "199.0000"));

        SubscriptionDtos.SubscribeRequest req = new SubscriptionDtos.SubscribeRequest();
        req.setPlanCode("PRO");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.subscribe(WS, USER, req, "idem-1"));

        assertEquals(40910, ex.getCode());
        assertTrue(ex.getMessage().contains("payment_required_create_order"), ex.getMessage());
        verify(subscriptionMapper, never()).insert(any(Subscription.class));
    }

    @Test
    @DisplayName("免费套餐可直接开通，订阅状态为 active")
    void subscribe_freePlan_createsActiveSubscription() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(planService.requireActivePlanByCode("FREE")).thenReturn(plan("FREE", "0.0000"));
        when(subscriptionMapper.selectActiveByWorkspace(WS)).thenReturn(null);
        when(workspaceQuotaMapper.selectById(WS)).thenReturn(null);

        SubscriptionDtos.SubscribeRequest req = new SubscriptionDtos.SubscribeRequest();
        req.setPlanCode("FREE");

        var view = service.subscribe(WS, USER, req, "idem-free");

        assertEquals("active", view.getStatus());
        assertEquals("FREE", view.getPlanCode());
        verify(subscriptionMapper).insert(any(Subscription.class));
    }

    @Test
    @DisplayName("试用：同一 workspace 只能试用一次（409 trial_already_used）")
    void startTrial_secondTime_rejected() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        Subscription used = new Subscription();
        used.setId(UUID.randomUUID());
        used.setWorkspaceId(WS);
        used.setTrialEnd(java.time.OffsetDateTime.now());
        when(subscriptionMapper.selectTrialUsedByWorkspace(WS)).thenReturn(used);

        SubscriptionDtos.TrialRequest req = new SubscriptionDtos.TrialRequest();
        req.setPlanCode("PRO");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.startTrial(WS, USER, req, "idem-trial"));

        assertEquals(40910, ex.getCode());
        assertTrue(ex.getMessage().contains("trial_already_used"), ex.getMessage());
    }

    @Test
    @DisplayName("幂等键重放：返回既有订阅，不新建")
    void subscribe_idempotentReplay_returnsExisting() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setWorkspaceId(WS);
        existing.setStatus("active");
        existing.setPlanVersionSnapshot(Map.of("code", "PRO", "version", 1));
        when(subscriptionMapper.selectByIdempotencyKey(WS, "idem-x")).thenReturn(existing);

        SubscriptionDtos.SubscribeRequest req = new SubscriptionDtos.SubscribeRequest();
        req.setPlanCode("PRO");

        var view = service.subscribe(WS, USER, req, "idem-x");

        assertEquals("active", view.getStatus());
        verify(subscriptionMapper, never()).insert(any(Subscription.class));
    }

    @Test
    @DisplayName("非 OWNER 调用 subscribe 被拒（403 owner_required）")
    void subscribe_nonOwner_rejected() {
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.VIEWER);

        SubscriptionDtos.SubscribeRequest req = new SubscriptionDtos.SubscribeRequest();
        req.setPlanCode("FREE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.subscribe(WS, USER, req, "idem-2"));

        assertEquals(40310, ex.getCode());
        assertTrue(ex.getMessage().contains("owner_required"), ex.getMessage());
        verify(subscriptionMapper, never()).insert(any(Subscription.class));
    }

    @Test
    @DisplayName("非成员访问返回 404（不泄露订阅存在性）")
    void subscribe_nonMember_returns404() {
        when(workspaceService.findRole(USER, WS)).thenReturn(null);

        SubscriptionDtos.SubscribeRequest req = new SubscriptionDtos.SubscribeRequest();
        req.setPlanCode("FREE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.subscribe(WS, USER, req, "idem-3"));

        assertEquals(40410, ex.getCode());
    }

    // ══════════════════════════════════════════════════════════════════
    // P3-4 / P3-5：幂等键作用域隔离
    // ══════════════════════════════════════════════════════════════════

    private Subscription activeSub() {
        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setWorkspaceId(WS);
        sub.setStatus("active");
        sub.setPlanId(UUID.randomUUID());
        sub.setPlanVersionSnapshot(Map.of("code", "PRO", "version", 1, "price", "199.0000"));
        sub.setCurrentPeriodStart(java.time.OffsetDateTime.now().minusDays(10));
        sub.setCurrentPeriodEnd(java.time.OffsetDateTime.now().plusDays(20));
        sub.setCancelAtPeriodEnd(false);
        sub.setAutoRenew(true);
        sub.setGracePeriodDays(7);
        return sub;
    }

    @Test
    @DisplayName("P3-4 cancel 用独立 scope 绑幂等键，不再抢 subscribe 的键")
    void cancel_bindsWithCancelScope() {
        Subscription sub = activeSub();
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(subscriptionMapper.selectActiveByWorkspace(WS)).thenReturn(sub);
        // 首次绑定 -> 1
        when(writeIdempotencyMapper.insertIdempotent(eq(WS), eq("cancel"), eq("idem-cancel"), any()))
                .thenReturn(1);
        when(subscriptionMapper.updateById(any(Subscription.class))).thenReturn(1);

        SubscriptionDtos.CancelRequest req = new SubscriptionDtos.CancelRequest();
        req.setAtPeriodEnd(true);

        var view = service.cancel(WS, USER, req, "idem-cancel");

        assertEquals("active", view.getStatus());
        verify(writeIdempotencyMapper).insertIdempotent(eq(WS), eq("cancel"), eq("idem-cancel"), any());
        // cancel 不应再往 subscription.idempotency_key 上写原始键
        verify(subscriptionMapper, never()).bindIdempotencyKey(any(), any());
    }

    @Test
    @DisplayName("P3-4 cancel 幂等重放：不再重复执行取消副作用")
    void cancel_idempotentReplay_skipsSideEffects() {
        Subscription sub = activeSub();
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(subscriptionMapper.selectActiveByWorkspace(WS)).thenReturn(sub);
        when(subscriptionMapper.selectById(sub.getId())).thenReturn(sub);
        // 已绑定 -> 0（幂等重放）
        when(writeIdempotencyMapper.insertIdempotent(eq(WS), eq("cancel"), eq("idem-cancel"), any()))
                .thenReturn(0);

        SubscriptionDtos.CancelRequest req = new SubscriptionDtos.CancelRequest();
        req.setAtPeriodEnd(true);

        var view = service.cancel(WS, USER, req, "idem-cancel");

        assertEquals("active", view.getStatus());
        // 重放时绝不能再改状态 / 再降级额度
        verify(subscriptionMapper, never()).updateById(any(Subscription.class));
        verify(subscriptionMapper, never()).compareAndSetStatus(any(), any(), any());
    }

    @Test
    @DisplayName("P3-4 cancel 与 change-plan 的幂等键互不抢占（不同 scope）")
    void cancel_andChangePlan_useDifferentScopes() {
        Subscription sub = activeSub();
        when(workspaceService.findRole(USER, WS)).thenReturn(WorkspaceRole.OWNER);
        when(subscriptionMapper.selectActiveByWorkspace(WS)).thenReturn(sub);
        when(writeIdempotencyMapper.insertIdempotent(eq(WS), eq("cancel"), eq("same-key"), any()))
                .thenReturn(1);
        when(writeIdempotencyMapper.insertIdempotent(eq(WS), eq("change_plan"), eq("same-key"), any()))
                .thenReturn(1);
        when(subscriptionMapper.updateById(any(Subscription.class))).thenReturn(1);

        SubscriptionDtos.CancelRequest cancelReq = new SubscriptionDtos.CancelRequest();
        cancelReq.setAtPeriodEnd(true);
        service.cancel(WS, USER, cancelReq, "same-key");

        SubscriptionDtos.ChangePlanRequest changeReq = new SubscriptionDtos.ChangePlanRequest();
        changeReq.setPlanCode("TEAM");
        when(planService.requireActivePlanByCode("TEAM")).thenReturn(plan("TEAM", "299.0000"));
        try {
            service.changePlan(WS, USER, changeReq, "same-key");
        } catch (BusinessException ignored) {
            // 付费升级被 P1-2 拒绝（409），不影响本用例对 scope 隔离的验证
        }

        // 同一个原始键在两个 scope 下各自独立绑定，互不冲突
        verify(writeIdempotencyMapper).insertIdempotent(eq(WS), eq("cancel"), eq("same-key"), any());
        verify(writeIdempotencyMapper).insertIdempotent(eq(WS), eq("change_plan"), eq("same-key"), any());
    }

    @Test
    @DisplayName("P3-5 adminAdjust 幂等重放：延期天数不叠加")
    void adminAdjust_idempotentReplay_doesNotExtendTwice() {
        Subscription sub = activeSub();
        when(subscriptionMapper.selectById(sub.getId())).thenReturn(sub);
        // 已绑定 -> 0（幂等重放）
        when(writeIdempotencyMapper.insertIdempotent(eq(WS), eq("admin_adjust"), eq("idem-adj"), any()))
                .thenReturn(0);

        SubscriptionDtos.AdminAdjustRequest req = new SubscriptionDtos.AdminAdjustRequest();
        req.setExtendDays(30);
        req.setReason("customer_goodwill");

        var item = service.adminAdjust(sub.getId(), req, "idem-adj");

        assertEquals("PRO", item.getPlanCode());
        // 重放时绝不能再执行 updateById（否则延期天数会叠加）
        verify(subscriptionMapper, never()).updateById(any(Subscription.class));
    }

    @Test
    @DisplayName("P3-5 adminAdjust 首次调用：以当前周期末为基准延期一次")
    void adminAdjust_firstCall_extendsOnce() {
        Subscription sub = activeSub();
        java.time.OffsetDateTime before = sub.getCurrentPeriodEnd();
        when(subscriptionMapper.selectById(sub.getId())).thenReturn(sub);
        when(writeIdempotencyMapper.insertIdempotent(eq(WS), eq("admin_adjust"), eq("idem-adj"), any()))
                .thenReturn(1);
        when(subscriptionMapper.updateById(any(Subscription.class))).thenReturn(1);
        when(workspaceMapper.selectById(WS)).thenReturn(null);

        SubscriptionDtos.AdminAdjustRequest req = new SubscriptionDtos.AdminAdjustRequest();
        req.setExtendDays(30);
        req.setReason("customer_goodwill");

        service.adminAdjust(sub.getId(), req, "idem-adj");

        // 只延期一次：30 天，而不是 60 天
        assertEquals(0, sub.getCurrentPeriodEnd().compareTo(before.plusDays(30)));
        verify(subscriptionMapper, times(1)).updateById(any(Subscription.class));
    }

    @Test
    @DisplayName("adminAdjust 缺少 reason 时被拒（400 reason_required）")
    void adminAdjust_reasonRequired() {
        SubscriptionDtos.AdminAdjustRequest req = new SubscriptionDtos.AdminAdjustRequest();
        req.setExtendDays(30);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adminAdjust(UUID.randomUUID(), req, "idem-adj"));
        assertEquals(40010, ex.getCode());
    }
}
