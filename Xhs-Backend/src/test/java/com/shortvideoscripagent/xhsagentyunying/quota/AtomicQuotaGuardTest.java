package com.shortvideoscripagent.xhsagentyunying.quota;

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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    private AtomicQuotaGuard guard;
    private String workspaceId;

    @BeforeEach
    void setUp() {
        guard = new AtomicQuotaGuard(
                workspaceQuotaMapper,
                usageLedgerMapper,
                workspaceMapper,
                quotaProperties
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

    // ── helpers ─────────────────────────────────────────────────────────────

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
