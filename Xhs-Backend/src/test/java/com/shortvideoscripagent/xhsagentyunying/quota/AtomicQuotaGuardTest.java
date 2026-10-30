package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.billing.pricing.PricingService;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.entity.PriceBook;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P0-2: AtomicQuotaGuard 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AtomicQuotaGuardTest {

    @Mock
    private WorkspaceQuotaMapper workspaceQuotaMapper;
    @Mock
    private UsageLedgerMapper usageLedgerMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private QuotaProperties quotaProperties;
    @Mock
    private PricingService pricingService;

    private AtomicQuotaGuard guard;
    private String workspaceId;

    @BeforeEach
    void setUp() {
        guard = new AtomicQuotaGuard(
                workspaceQuotaMapper,
                usageLedgerMapper,
                workspaceMapper,
                quotaProperties,
                pricingService
        );
        workspaceId = UUID.randomUUID().toString();
    }

    @Test
    void tryConsume_singleConsume_success() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        QuotaCheckResult result = guard.tryConsume(workspaceId, "ANALYSIS", null, "test");

        assertTrue(result.isOk());
        assertNotNull(result.ledgerId());
        verify(workspaceQuotaMapper).atomicConsume(workspaceId);
    }

    @Test
    void tryConsume_quotaExceeded_returnsExceeded() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(0);
        when(workspaceQuotaMapper.resetIfExpired(workspaceId)).thenReturn(0);
        WorkspaceQuota existingQuota = createQuota(workspaceId, 100, 100, OffsetDateTime.now().plusDays(1));
        when(workspaceQuotaMapper.selectById(workspaceId)).thenReturn(existingQuota);

        QuotaCheckResult result = guard.tryConsume(workspaceId, "ANALYSIS", null, "test");

        assertFalse(result.isOk());
        assertNull(result.ledgerId());
        assertEquals("quota_exceeded", result.message());
    }

    @Test
    void tryConsume_resetExpired_thenConsume() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId))
                .thenReturn(0)
                .thenReturn(1);
        when(workspaceQuotaMapper.resetIfExpired(workspaceId)).thenReturn(1);
        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        QuotaCheckResult result = guard.tryConsume(workspaceId, "ANALYSIS", null, "test");

        assertTrue(result.isOk());
        verify(workspaceQuotaMapper).resetIfExpired(workspaceId);
        verify(workspaceQuotaMapper, times(2)).atomicConsume(workspaceId);
    }

    @Test
    void tryConsume_workspaceNotExists_lazyInit() {
        // When workspace has no quota row initially, lazyInitQuota creates one.
        // Key insight: MyBatis-Plus mock insert doesn't persist to DB, so we must make
        // selectById return the created row AFTER insert (simulating real DB behavior).
        // Sequence: atomicConsume=0 → reset=0 → selectById=null → lazyInitQuota → insert →
        //          atomicConsume=1 (after insert, selectById returns created row)
        
        // selectById: first call in tryConsume returns null (triggers lazyInitQuota),
        // subsequent calls return the created quota row (simulates DB insert)
        when(workspaceQuotaMapper.selectById(workspaceId))
                .thenReturn(null)  // first call: no row exists
                .thenReturn(createQuota(workspaceId, 100, 0)); // after insert: row exists
        when(workspaceQuotaMapper.atomicConsume(workspaceId))
                .thenReturn(0)  // first: no row yet
                .thenReturn(1); // second: row created, consume succeeds
        when(workspaceQuotaMapper.resetIfExpired(workspaceId)).thenReturn(0);
        when(workspaceMapper.selectById(workspaceId.toString())).thenReturn(new Workspace());
        when(quotaProperties.getDefaultDailyQuota()).thenReturn(100);
        when(quotaProperties.isLazyInitExpired()).thenReturn(false);
        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        QuotaCheckResult result = guard.tryConsume(workspaceId, "ANALYSIS", null, "test");

        assertTrue(result.isOk());
    }

    @Test
    void hasQuota_withQuota_returnsTrue() {
        when(workspaceQuotaMapper.selectById(workspaceId))
                .thenReturn(createQuota(workspaceId, 100, 50));

        boolean hasQuota = guard.hasQuota(workspaceId);

        assertTrue(hasQuota);
    }

    @Test
    void hasQuota_exceeded_returnsFalse() {
        when(workspaceQuotaMapper.selectById(workspaceId))
                .thenReturn(createQuota(workspaceId, 100, 100));

        boolean hasQuota = guard.hasQuota(workspaceId);

        assertFalse(hasQuota);
    }

    @Test
    void hasQuota_expired_resetPending_returnsTrue() {
        when(workspaceQuotaMapper.selectById(workspaceId))
                .thenReturn(createQuota(workspaceId, 100, 100, OffsetDateTime.now().minusDays(1)));

        boolean hasQuota = guard.hasQuota(workspaceId);

        assertTrue(hasQuota);
    }

    @Test
    void getQuota_expired_triggersReset() {
        WorkspaceQuota expired = createQuota(workspaceId, 100, 80, OffsetDateTime.now().minusDays(1));
        WorkspaceQuota reset = createQuota(workspaceId, 100, 0, OffsetDateTime.now().plusDays(1));
        when(workspaceQuotaMapper.selectById(workspaceId))
                .thenReturn(expired)
                .thenReturn(reset);
        when(workspaceQuotaMapper.resetIfExpired(workspaceId)).thenReturn(1);

        WorkspaceQuota result = guard.getQuota(workspaceId);

        assertNotNull(result);
        verify(workspaceQuotaMapper).resetIfExpired(workspaceId);
        assertEquals(0, result.getUsed());
    }

    @Test
    void ledger_writesCorrectDeltaAndStatus() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        ArgumentCaptor<UsageLedger> ledgerCaptor = ArgumentCaptor.forClass(UsageLedger.class);
        when(usageLedgerMapper.insert(ledgerCaptor.capture())).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        UUID refId = UUID.randomUUID();

        guard.tryConsume(workspaceId, "ANALYSIS", refId, "task-start");

        UsageLedger captured = ledgerCaptor.getValue();
        assertEquals(workspaceId, captured.getWorkspaceId());
        assertEquals("ANALYSIS", captured.getAction());
        assertEquals(refId, captured.getRefId());
        assertEquals(1, captured.getDelta());
        assertEquals("CONSUMED", captured.getStatus());
        assertEquals("task-start", captured.getReason());
    }

    // ── S4-3：价格快照（决定台账能否被出账） ────────────────────────────

    /**
     * 价格快照必须在<b>同一批</b>台账行里写入，否则账单出账 SQL
     * （{@code AND amount IS NOT NULL}）永远查不到这行，用量会凭空消失。
     */
    @Test
    void tryConsume_writesPriceSnapshot() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(pricingService.resolvePrice(anyString(), any())).thenReturn(price(0.5000, 3));
        ArgumentCaptor<UsageLedger> captor = ArgumentCaptor.forClass(UsageLedger.class);
        when(usageLedgerMapper.insert(captor.capture())).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        guard.tryConsume(workspaceId, "ANALYSIS", null, "task-start");

        UsageLedger captured = captor.getValue();
        assertEquals(new BigDecimal("0.5000"), captured.getUnitPrice());
        assertEquals(new BigDecimal("0.5000"), captured.getAmount());
        assertEquals(3, captured.getPriceVersion());
    }

    /**
     * 台账 action（{@code CHAT}）必须按归一后的计量单位查价格，
     * 否则价格表查不到行 → 金额为 0。
     */
    @Test
    void tryConsume_resolvesPriceByAction() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(pricingService.resolvePrice(anyString(), any())).thenReturn(price(0.0500, 1));
        ArgumentCaptor<UsageLedger> captor = ArgumentCaptor.forClass(UsageLedger.class);
        when(usageLedgerMapper.insert(captor.capture())).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        guard.tryConsume(workspaceId, "CHAT", null, "agent-message");

        // action 原样保留（前端筛选器依赖），金额按价格快照写入
        assertEquals("CHAT", captor.getValue().getAction());
        assertEquals(new BigDecimal("0.0500"), captor.getValue().getAmount());
        verify(pricingService).resolvePrice(eq("CHAT"), any());
    }

    /**
     * 价格表缺行属于配置问题，<b>不能</b>阻断扣费；此时金额按 0 计，行仍然写出来（不丢账）。
     */
    @Test
    void tryConsume_noPriceRow_stillWritesLedgerWithZeroAmount() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(pricingService.resolvePrice(anyString(), any())).thenReturn(null);
        ArgumentCaptor<UsageLedger> captor = ArgumentCaptor.forClass(UsageLedger.class);
        when(usageLedgerMapper.insert(captor.capture())).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        QuotaCheckResult result = guard.tryConsume(workspaceId, "ANALYSIS", null, "task-start");

        assertTrue(result.isOk());
        UsageLedger captured = captor.getValue();
        assertEquals(BigDecimal.ZERO.setScale(4), captured.getUnitPrice());
        assertEquals(BigDecimal.ZERO.setScale(4), captured.getAmount());
    }

    /**
     * 计价异常同样不得影响扣费主流程。
     */
    @Test
    void tryConsume_pricingThrows_stillConsumes() {
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(pricingService.resolvePrice(anyString(), any())).thenThrow(new RuntimeException("price table down"));
        ArgumentCaptor<UsageLedger> captor = ArgumentCaptor.forClass(UsageLedger.class);
        when(usageLedgerMapper.insert(captor.capture())).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        QuotaCheckResult result = guard.tryConsume(workspaceId, "ANALYSIS", null, "task-start");

        assertTrue(result.isOk());
        assertEquals(BigDecimal.ZERO.setScale(4), captor.getValue().getAmount());
    }

    // ── P0-11：幂等扣费（任务重试 / 孤儿任务恢复防重复扣费） ────────────

    /**
     * 任务重试时同一 {@code refId} 已有生效扣费 → <b>不能再扣一次额度</b>，
     * 直接复用原台账行。
     */
    @Test
    void tryConsumeIdempotent_replay_reusesExistingLedgerWithoutChargingAgain() {
        UUID refId = UUID.randomUUID();
        UsageLedger existing = new UsageLedger();
        existing.setId(UUID.randomUUID());
        existing.setWorkspaceId(workspaceId);
        existing.setAction("ANALYSIS");
        existing.setRefId(refId);
        existing.setDelta(1);
        existing.setStatus("CONSUMED");

        when(usageLedgerMapper.selectActiveByRefIdAndAction(workspaceId, refId, "ANALYSIS"))
                .thenReturn(existing);

        QuotaCheckResult result = guard.tryConsumeIdempotent(workspaceId, "ANALYSIS", refId, "task-start");

        assertTrue(result.isOk());
        assertEquals(existing.getId(), result.ledgerId());
        // 关键断言：不再扣配额、不再写台账
        verify(workspaceQuotaMapper, never()).atomicConsume(anyString());
        verify(usageLedgerMapper, never()).insert(any(UsageLedger.class));
        verify(usageLedgerMapper, never()).insertIdempotent(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * 首次扣费必须写入 {@code dedupe_key}，否则数据库唯一索引无法兜底并发重复扣费。
     */
    @Test
    void tryConsumeIdempotent_firstCall_writesDedupeKey() {
        UUID refId = UUID.randomUUID();
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(usageLedgerMapper.selectActiveByRefIdAndAction(workspaceId, refId, "ANALYSIS"))
                .thenReturn(null);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(usageLedgerMapper.insertIdempotent(
                any(), anyString(), any(), anyString(), any(), any(), anyString(), any(),
                any(), any(), any(), keyCaptor.capture(), any())).thenReturn(1);

        QuotaCheckResult result = guard.tryConsumeIdempotent(workspaceId, "ANALYSIS", refId, "task-start");

        assertTrue(result.isOk());
        assertEquals("ANALYSIS:" + refId, keyCaptor.getValue());
        verify(workspaceQuotaMapper).atomicConsume(workspaceId);
    }

    /**
     * 并发下另一线程先写入了同一 {@code dedupe_key} → 本次插入返回 0，
     * 必须回补多扣的额度并复用已存在的台账行。
     */
    @Test
    void tryConsumeIdempotent_uniqueConflict_refundsQuotaAndReusesLedger() {
        UUID refId = UUID.randomUUID();
        UsageLedger existing = new UsageLedger();
        existing.setId(UUID.randomUUID());
        existing.setWorkspaceId(workspaceId);
        existing.setAction("ANALYSIS");
        existing.setRefId(refId);
        existing.setStatus("CONSUMED");

        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(usageLedgerMapper.selectActiveByRefIdAndAction(workspaceId, refId, "ANALYSIS"))
                .thenReturn(null);
        when(usageLedgerMapper.insertIdempotent(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), anyString(), any())).thenReturn(0);
        when(usageLedgerMapper.selectByDedupeKey(workspaceId, "ANALYSIS:" + refId))
                .thenReturn(existing);

        QuotaCheckResult result = guard.tryConsumeIdempotent(workspaceId, "ANALYSIS", refId, "task-start");

        assertTrue(result.isOk());
        assertEquals(existing.getId(), result.ledgerId());
        // 多扣的额度必须回补
        verify(workspaceQuotaMapper).refund(workspaceId);
    }

    /**
     * 非幂等入口（Agent 对话）：同一 {@code sessionId} 的多条消息各扣 1 次，
     * <b>不能</b>被去重。
     */
    @Test
    void tryConsume_nonIdempotent_chargesEveryTime() {
        UUID refId = UUID.randomUUID();
        when(workspaceQuotaMapper.atomicConsume(workspaceId)).thenReturn(1);
        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> 1);

        guard.tryConsume(workspaceId, "CHAT", refId, "agent-message");
        guard.tryConsume(workspaceId, "CHAT", refId, "agent-message");

        verify(workspaceQuotaMapper, times(2)).atomicConsume(workspaceId);
        verify(usageLedgerMapper, times(2)).insert(any(UsageLedger.class));
        // 非幂等路径不写 dedupe_key
        ArgumentCaptor<UsageLedger> captor = ArgumentCaptor.forClass(UsageLedger.class);
        verify(usageLedgerMapper, times(2)).insert(captor.capture());
        assertNull(captor.getAllValues().get(0).getDedupeKey());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private PriceBook price(double unitPrice, int version) {
        PriceBook p = new PriceBook();
        p.setId(UUID.randomUUID());
        p.setUnit("ANALYSIS");
        p.setRegion("CN");
        p.setPlanVersion(1);
        p.setVersion(version);
        p.setUnitPrice(BigDecimal.valueOf(unitPrice).setScale(4, java.math.RoundingMode.HALF_UP));
        p.setCostPerUnit(BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP));
        p.setCurrency("CNY");
        p.setEffectiveFrom(OffsetDateTime.now());
        return p;
    }

    private WorkspaceQuota createQuota(String wsId, int daily, int used) {
        return createQuota(wsId, daily, used, OffsetDateTime.now().plusDays(1));
    }

    private WorkspaceQuota createQuota(String wsId, int daily, int used, OffsetDateTime resetAt) {
        WorkspaceQuota q = new WorkspaceQuota();
        q.setWorkspaceId(wsId);
        q.setDailyQuota(daily);
        q.setUsed(used);
        q.setResetAt(resetAt);
        q.setCreatedAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        return q;
    }
}
