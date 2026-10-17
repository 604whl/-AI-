package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UsageLogMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-2: QuotaRefundService 单元测试。
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

    private QuotaRefundService service;

    @BeforeEach
    void setUp() {
        service = new QuotaRefundService(usageLedgerMapper, usageLogMapper, workspaceQuotaMapper);
    }

    @Test
    void refund_validConsumedLedger_success() {
        // given
        UUID ledgerId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setRefId(refId);
        ledger.setStatus("CONSUMED");

        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId)).thenReturn(1);
        when(workspaceQuotaMapper.refund(workspaceId)).thenReturn(1);
        when(usageLogMapper.selectById(refId.toString())).thenReturn(new UsageLog());

        // when
        boolean result = service.refund(ledgerId, "task-failed:timeout");

        // then
        assertTrue(result);
        verify(usageLedgerMapper).markRefunded(ledgerId);
        verify(workspaceQuotaMapper).refund(workspaceId);
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
        UUID workspaceId = UUID.randomUUID();
        UsageLedger ledger = new UsageLedger();
        ledger.setId(ledgerId);
        ledger.setWorkspaceId(workspaceId);
        ledger.setStatus("CONSUMED");

        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);
        when(usageLedgerMapper.markRefunded(ledgerId))
                .thenReturn(1)  // first call succeeds
                .thenReturn(0); // second call: already refunded
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
        UUID workspaceId = UUID.randomUUID();
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
        UUID workspaceId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        when(usageLedgerMapper.selectOne(any())).thenReturn(null);

        // when: should not throw, returns false
        boolean result = service.refundByRefId(workspaceId, refId, "task-failed");

        // then
        assertFalse(result);
        verify(usageLedgerMapper, never()).markRefunded(any());
    }
}
