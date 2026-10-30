package com.shortvideoscripagent.xhsagentyunying.billing.balance;

import com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.entity.WorkspaceBalance;
import com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.mapper.WorkspaceBalanceMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-8：{@link BalanceService} 单元测试。
 * <p>
 * 覆盖审核清单点名的三个分支：
 * <ul>
 *   <li>余额不足时抛 {@code insufficient_balance}（40201）；</li>
 *   <li>冻结金额参与可用余额判定（口径与 {@code atomicDebit} 一致）；</li>
 *   <li>同一 {@code orderNo} 幂等重放不重复入账 / 扣款。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final String WS = "ws_balance_test";
    private static final String ORDER_NO = "SO-BAL-0001";

    @Mock
    private WorkspaceBalanceMapper balanceMapper;
    @Mock
    private UsageLedgerMapper usageLedgerMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private AuditWriter auditWriter;

    private BalanceService service;

    @BeforeEach
    void setUp() {
        service = new BalanceService(balanceMapper, usageLedgerMapper, workspaceMapper, auditWriter);
    }

    private WorkspaceBalance balance(String bal, String frozen) {
        WorkspaceBalance b = new WorkspaceBalance();
        b.setWorkspaceId(WS);
        b.setBalance(new BigDecimal(bal));
        b.setFrozen(new BigDecimal(frozen));
        b.setCurrency("CNY");
        b.setStatus("active");
        return b;
    }

    @Test
    @DisplayName("余额充足时扣减成功并写一条 -1 流水")
    void debitBalance_success_writesLedger() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "0.0000"));
        when(balanceMapper.atomicDebit(eq(WS), any())).thenReturn(1);
        when(usageLedgerMapper.selectCount(any())).thenReturn(0L);

        service.debitBalance(WS, new BigDecimal("30"), "ANALYSIS", ORDER_NO, "usage");

        verify(usageLedgerMapper, times(1)).insert(any(com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger.class));
        verify(balanceMapper, times(1)).atomicDebit(eq(WS), eq(new BigDecimal("30.0000")));
    }

    @Test
    @DisplayName("余额不足时抛 40201 insufficient_balance 且不写流水")
    void debitBalance_insufficient_throwsAndNoLedger() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("10.0000", "0.0000"));
        when(balanceMapper.atomicDebit(eq(WS), any())).thenReturn(0);
        when(usageLedgerMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.debitBalance(WS, new BigDecimal("50"), "ANALYSIS", ORDER_NO, "usage"));

        assertEquals(40201, ex.getCode());
        assertTrue(ex.getMessage().contains("insufficient_balance"));
        verify(usageLedgerMapper, never()).insert(any(com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger.class));
    }

    @Test
    @DisplayName("扣减金额非法（<=0）时抛 40010")
    void debitBalance_invalidAmount_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.debitBalance(WS, BigDecimal.ZERO, "ANALYSIS", ORDER_NO, "usage"));
        assertEquals(40010, ex.getCode());
    }

    @Test
    @DisplayName("可用余额口径含冻结：balance=100 frozen=80 时扣 50 应失败")
    void debitBalance_frozenCountsAgainstAvailable() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "80.0000"));
        when(balanceMapper.atomicDebit(eq(WS), any())).thenReturn(0);
        when(usageLedgerMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.debitBalance(WS, new BigDecimal("50"), "ANALYSIS", ORDER_NO, "usage"));

        assertEquals(40201, ex.getCode());
        // 报错里的可用余额必须是 balance - frozen = 20
        assertTrue(ex.getMessage().contains("available=20"), ex.getMessage());
    }

    @Test
    @DisplayName("hasSufficientBalance 与 atomicDebit 同口径：含冻结")
    void hasSufficientBalance_consistentWithAtomicDebit() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "80.0000"));
        assertFalse(service.hasSufficientBalance(WS, new BigDecimal("50")));

        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "0.0000"));
        assertTrue(service.hasSufficientBalance(WS, new BigDecimal("50")));
        assertTrue(service.hasSufficientBalance(WS, new BigDecimal("100")));
        assertFalse(service.hasSufficientBalance(WS, new BigDecimal("100.01")));
    }

    @Test
    @DisplayName("入账幂等：同 orderNo 已有流水时跳过入账")
    void creditBalance_idempotentReplay_skipsCredit() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "0.0000"));
        // 已有同 orderNo 的余额流水
        when(usageLedgerMapper.selectCount(any())).thenReturn(1L);

        service.creditBalance(WS, new BigDecimal("50"), "CNY", ORDER_NO, "usage_topup");

        verify(balanceMapper, never()).atomicCredit(anyString(), any());
        verify(usageLedgerMapper, never()).insert(any(com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger.class));
    }

    @Test
    @DisplayName("扣减幂等：同 orderNo 已有流水时跳过扣减")
    void debitBalance_idempotentReplay_skipsDebit() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "0.0000"));
        when(usageLedgerMapper.selectCount(any())).thenReturn(1L);

        service.debitBalance(WS, new BigDecimal("30"), "ANALYSIS", ORDER_NO, "usage");

        verify(balanceMapper, never()).atomicDebit(anyString(), any());
        verify(usageLedgerMapper, never()).insert(any(com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger.class));
    }

    @Test
    @DisplayName("入账金额非法（<=0）时抛 40010，不动余额")
    void creditBalance_invalidAmount_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.creditBalance(WS, new BigDecimal("-1"), "CNY", ORDER_NO, "usage_topup"));
        assertEquals(40010, ex.getCode());
        verify(balanceMapper, never()).atomicCredit(anyString(), any());
    }

    @Test
    @DisplayName("入账成功：写一条 +1 流水，流水 amount 带正号")
    void creditBalance_success_writesPositiveLedger() {
        when(balanceMapper.selectByWorkspace(WS)).thenReturn(balance("100.0000", "0.0000"));
        when(usageLedgerMapper.selectCount(any())).thenReturn(0L);
        when(balanceMapper.atomicCredit(eq(WS), any())).thenReturn(1);

        service.creditBalance(WS, new BigDecimal("50"), "CNY", ORDER_NO, "usage_topup");

        ArgumentCaptor<com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger> captor =
                ArgumentCaptor.forClass(com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger.class);
        verify(usageLedgerMapper, times(1)).insert(captor.capture());
        var ledger = captor.getValue();
        assertEquals(1, ledger.getDelta());
        assertEquals(0, ledger.getAmount().compareTo(new BigDecimal("50.0000")));
        // 流水 reason 必须带 orderNo= 片段，否则幂等检查匹配不到
        assertTrue(ledger.getReason().contains("orderNo=" + ORDER_NO), ledger.getReason());
    }
}
