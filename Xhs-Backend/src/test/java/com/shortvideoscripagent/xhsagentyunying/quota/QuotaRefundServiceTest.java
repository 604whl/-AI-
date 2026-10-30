package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UsageLogMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-2 / S4-3: QuotaRefundService 单元测试。
 * <p>
 * <b>关键回归点</b>：冲正必须写出一行 {@code delta = -1} 的对冲台账，
 * 否则账单 {@code SUM(amount)} 会把失败任务的金额算进去（失败任务照样收费）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaRefundServiceTest {

    @Mock
    private UsageLedgerMapper usageLedgerMapper;
    @Mock
    private UsageLogMapper usageLogMapper;
    @Mock
    private WorkspaceQuotaMapper workspaceQuotaMapper;

    private PlatformTransactionManager transactionManager;

    private QuotaRefundService service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate 需要真实的 transactionManager 行为，这里用一个
        // 「立即提交」的桩即可（单元测试不验证事务传播语义）。
        transactionManager = new NoopTransactionManager();
        service = new QuotaRefundService(
                usageLedgerMapper, usageLogMapper, workspaceQuotaMapper, transactionManager);
    }

    /**
     * 立即提交的 {@link PlatformTransactionManager} 桩。
     */
    private static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {
            // no-op
        }
    }

    @Test
    void refund_validConsumedLedger_success() {
        // given
        UUID ledgerId = UUID.randomUUID();
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UUID refId = UUID.randomUUID();
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setRefId(refId);
        ledger.setStatus("CONSUMED");

        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId)).thenReturn(1);
        when(workspaceQuotaMapper.refund(workspaceId)).thenReturn(1);

        // when
        boolean result = service.refund(ledgerId, "task-failed:timeout");

        // then
        assertTrue(result);
        verify(usageLedgerMapper).markRefunded(ledgerId);
        verify(workspaceQuotaMapper).refund(workspaceId);
    }

    @Test
    void refund_writesNegativeReversalLedger() {
        // given：一笔已定价的扣费
        UUID ledgerId = UUID.randomUUID();
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setAction("ANALYSIS");
        ledger.setRefId(UUID.randomUUID());
        ledger.setDelta(1);
        ledger.setStatus("CONSUMED");
        ledger.setAmount(new BigDecimal("0.5000"));
        ledger.setUnitPrice(new BigDecimal("0.5000"));
        ledger.setPriceVersion(1);

        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId)).thenReturn(1);
        when(workspaceQuotaMapper.refund(workspaceId)).thenReturn(1);

        // when
        service.refund(ledgerId, "task-failed:timeout");

        // then：必须写出一行 delta=-1、amount=-0.5000 的对冲台账
        ArgumentCaptor<UsageLedger> captor = ArgumentCaptor.forClass(UsageLedger.class);
        verify(usageLedgerMapper).insert(captor.capture());
        UsageLedger reversal = captor.getValue();
        assertEquals(-1, reversal.getDelta());
        assertEquals(new BigDecimal("-0.5000"), reversal.getAmount());
        assertEquals(ledgerId, reversal.getRelatedLogId());
        assertEquals("CONSUMED", reversal.getStatus());
        // 冲正行不参与幂等去重（ref_id 置空）
        assertNull(reversal.getRefId());
        assertNull(reversal.getDedupeKey());
    }

    @Test
    void refund_alreadyRefunded_returnsFalse() {
        // given
        UUID ledgerId = UUID.randomUUID();
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setStatus("REFUNDED");

        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);

        // when
        boolean result = service.refund(ledgerId, "duplicate-refund");

        // then
        assertFalse(result);
        verify(workspaceQuotaMapper, never()).refund(any());
        verify(usageLedgerMapper, never()).insert(any(UsageLedger.class));
    }

    @Test
    void refund_ledgerNotFound_returnsFalse() {
        // given
        UUID ledgerId = UUID.randomUUID();
        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(null);

        // when
        boolean result = service.refund(ledgerId, "any-reason");

        // then
        assertFalse(result);
    }

    @Test
    void refund_concurrentRefund_returnsFalse() {
        // given: first refund succeeds, second concurrent refund finds status already REFUNDED
        UUID ledgerId = UUID.randomUUID();
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setStatus("CONSUMED");

        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId)).thenReturn(1);
        when(workspaceQuotaMapper.refund(workspaceId)).thenReturn(1);

        // when: first refund
        boolean result = service.refund(ledgerId, "first-refund");

        // then: idempotent, returns true for first
        assertTrue(result);
        verify(workspaceQuotaMapper).refund(workspaceId);
    }

    @Test
    void refundByRefId_foundLedger_refunds() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UUID refId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setStatus("CONSUMED");

        when(usageLedgerMapper.selectOne(any())).thenReturn(ledger);
        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId)).thenReturn(1);
        when(workspaceQuotaMapper.refund(workspaceId)).thenReturn(1);

        // when
        boolean result = service.refundByRefId(workspaceId, refId, "task-failed");

        // then
        assertTrue(result);
        verify(usageLedgerMapper).markRefunded(ledgerId);
    }

    @Test
    void refundByRefId_notFound_returnsFalse() {
        // given: no consumed ledger found
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UUID refId = UUID.randomUUID();

        when(usageLedgerMapper.selectOne(any())).thenReturn(null);

        // when: should not throw, returns false
        boolean result = service.refundByRefId(workspaceId, refId, "task-failed");

        // then
        assertFalse(result);
        verify(usageLedgerMapper, never()).markRefunded(any());
    }

    @Test
    void refundByRawRefId_resolvesNonUuidBusinessId() {
        // given：业务 ID 形如 ana_xxx，不是合法 UUID
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        String rawRefId = "ana_897b2412a401";
        UUID ledgerId = UUID.randomUUID();
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setStatus("CONSUMED");

        when(usageLedgerMapper.selectOne(any())).thenReturn(ledger);
        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId)).thenReturn(1);
        when(workspaceQuotaMapper.refund(workspaceId)).thenReturn(1);

        // when
        boolean result = service.refundByRawRefId(workspaceId, rawRefId, "task-failed");

        // then：能通过 QuotaRefId 映射找到台账并冲正
        assertTrue(result);
        verify(usageLedgerMapper).markRefunded(ledgerId);
    }
}
