package com.shortvideoscripagent.xhsagentyunying.billing.statement;

import com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.PricingService;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatement;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatementItem;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementItemMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * P2-8：{@link StatementService} 单元测试。
 * <p>
 * 覆盖审核清单点名的分支：
 * <ul>
 *   <li>幂等键时区归一（同一时刻不同偏移生成同一个键）；</li>
 *   <li>同周期重复生成只出一张账（幂等重放）；</li>
 *   <li>账单行按「计量单位 + 价格版本」拆分，每行 amount = unitPrice × quantity（P2-3）；</li>
 *   <li>无未出账台账时不生成账单。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    private static final String WS = "ws_stmt_test";

    @Mock
    private BillingStatementMapper statementMapper;
    @Mock
    private BillingStatementItemMapper itemMapper;
    @Mock
    private UsageLedgerMapper usageLedgerMapper;
    @Mock
    private PricingService pricingService;
    @Mock
    private BalanceService balanceService;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private IdempotencyReplay idempotencyReplay;
    @Mock
    private PlatformTransactionManager transactionManager;

    private StatementService service() {
        return new StatementService(statementMapper, itemMapper, usageLedgerMapper, pricingService,
                balanceService, auditWriter, idempotencyReplay, transactionManager);
    }

    private UsageLedger ledger(String action, int delta, String unitPrice, String amount, Integer priceVersion) {
        UsageLedger l = new UsageLedger();
        l.setId(UUID.randomUUID());
        l.setWorkspaceId(WS);
        l.setAction(action);
        l.setDelta(delta);
        l.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        l.setAmount(amount == null ? null : new BigDecimal(amount));
        l.setPriceVersion(priceVersion);
        l.setStatus("CONSUMED");
        return l;
    }

    @Test
    @DisplayName("幂等键时区归一：同一时刻不同偏移生成同一个键")
    void buildIdempotencyKey_timezoneNormalized() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime plus8 = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.ofHours(8));
        OffsetDateTime endUtc = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime endPlus8 = OffsetDateTime.of(2026, 2, 1, 8, 0, 0, 0, ZoneOffset.ofHours(8));

        String keyA = StatementService.buildIdempotencyKey(WS, utc, endUtc);
        String keyB = StatementService.buildIdempotencyKey(WS, plus8, endPlus8);

        assertEquals(keyA, keyB);
    }

    @Test
    @DisplayName("幂等键区分不同周期")
    void buildIdempotencyKey_differsAcrossPeriods() {
        OffsetDateTime s = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime e1 = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime e2 = OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        assertNotEquals(StatementService.buildIdempotencyKey(WS, s, e1),
                StatementService.buildIdempotencyKey(WS, s, e2));
    }

    @Test
    @DisplayName("周期非法时抛 40010")
    void generateStatement_invalidPeriod_throws() {
        OffsetDateTime now = OffsetDateTime.now();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().generateStatement(WS, now, now));
        assertEquals(40010, ex.getCode());
    }

    @Test
    @DisplayName("无未出账台账时不生成账单")
    void generateStatement_noLedger_returnsNull() {
        OffsetDateTime s = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime e = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        when(usageLedgerMapper.selectUnbilled(eq(WS), any(), any())).thenReturn(List.of());

        assertEquals(null, service().generateStatement(WS, s, e));
        verify(statementMapper, never()).insert(any(BillingStatement.class));
    }

    @Test
    @DisplayName("幂等重放：已存在同键账单时直接返回，不新建")
    void generateStatement_replay_returnsExisting() {
        OffsetDateTime s = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime e = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        BillingStatement existing = new BillingStatement();
        existing.setId("stmt-existing");
        existing.setWorkspaceId(WS);
        existing.setStatus("open");
        existing.setCurrency("CNY");
        when(statementMapper.selectByIdempotencyKey(anyString())).thenReturn(existing);

        var detail = service().generateStatement(WS, s, e);

        assertEquals("stmt-existing", detail.getId());
        verify(statementMapper, never()).insert(any(BillingStatement.class));
    }

    @Test
    @DisplayName("P2-3：同一计量单位的不同价格版本拆成两行，每行 amount = unitPrice × quantity")
    void generateStatement_splitsByPriceVersion() {
        OffsetDateTime s = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime e = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        when(statementMapper.selectByIdempotencyKey(anyString())).thenReturn(null);
        when(usageLedgerMapper.selectUnbilled(eq(WS), any(), any())).thenReturn(List.of(
                ledger("ANALYSIS", 10, "1.0000", "10.0000", 1),
                ledger("ANALYSIS", 5, "2.0000", "10.0000", 2)
        ));

        service().generateStatement(WS, s, e);

        ArgumentCaptor<BillingStatementItem> captor =
                ArgumentCaptor.forClass(BillingStatementItem.class);
        verify(itemMapper, times(2)).insert(captor.capture());

        List<BillingStatementItem> items = captor.getAllValues();
        assertEquals(2, items.size());

        // v1：10 单位 × 1.0000 = 10.0000
        BillingStatementItem v1 = items.stream()
                .filter(i -> "1".equals(String.valueOf(ledger("ANALYSIS", 1, null, null, 1).getPriceVersion())))
                .findFirst().orElse(items.get(0));
        for (BillingStatementItem item : items) {
            BigDecimal expected = item.getUnitPrice().multiply(item.getQuantity());
            assertEquals(0, expected.compareTo(item.getAmount()),
                    "行金额必须等于 单价 × 数量: " + item.getUnitPrice() + " × " + item.getQuantity()
                            + " != " + item.getAmount());
        }
        // 两行单价不同
        assertNotEquals(0, items.get(0).getUnitPrice().compareTo(items.get(1).getUnitPrice()));
    }
}
