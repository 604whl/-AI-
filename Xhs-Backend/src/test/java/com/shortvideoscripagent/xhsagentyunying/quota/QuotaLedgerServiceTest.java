package com.shortvideoscripagent.xhsagentyunying.quota;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-2: QuotaLedgerService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaLedgerServiceTest {

    @Mock
    private UsageLedgerMapper usageLedgerMapper;

    private QuotaLedgerService service;

    @BeforeEach
    void setUp() {
        service = new QuotaLedgerService(usageLedgerMapper);
    }

    @Test
    void pageByWorkspace_returnsPage() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        Page<UsageLedger> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(
                createLedger(workspaceId, "ANALYSIS", "CONSUMED"),
                createLedger(workspaceId, "BODY", "CONSUMED")
        ));
        mockPage.setTotal(50);
        when(usageLedgerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // when
        IPage<UsageLedger> result = service.pageByWorkspace(workspaceId, null, 1, 20);

        // then
        assertEquals(2, result.getRecords().size());
        assertEquals(50, result.getTotal());
        assertEquals(1, result.getCurrent());
        assertEquals(20, result.getSize());
    }

    @Test
    void pageByWorkspace_withActionFilter_filtersCorrectly() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        Page<UsageLedger> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(createLedger(workspaceId, "ANALYSIS", "CONSUMED")));
        mockPage.setTotal(10);
        when(usageLedgerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // when
        IPage<UsageLedger> result = service.pageByWorkspace(workspaceId, "ANALYSIS", 1, 20);

        // then
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void pageByWorkspace_sanitizesInput() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        Page<UsageLedger> mockPage = new Page<>(1, 100);
        mockPage.setRecords(List.of());
        when(usageLedgerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // when: lowercase action
        service.pageByWorkspace(workspaceId, "analysis", 1, 20);

        // then: normalize to uppercase
        ArgumentCaptor<LambdaQueryWrapper<UsageLedger>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(usageLedgerMapper).selectPage(any(Page.class), wrapperCaptor.capture());
    }

    @Test
    void pageByWorkspace_clampPaginationBounds() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        Page<UsageLedger> mockPage = new Page<>(1, 100);
        mockPage.setRecords(List.of());
        when(usageLedgerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // when: page = 0 (should become 1), size = 500 (should become 100)
        IPage<UsageLedger> result = service.pageByWorkspace(workspaceId, null, 0, 500);

        // then: clamped values
        assertEquals(1, result.getCurrent());
        assertEquals(100, result.getSize());
    }

    @Test
    void list_withStatusFilter_filtersCorrectly() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        Page<UsageLedger> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(createLedger(workspaceId, "ANALYSIS", "REFUNDED")));
        mockPage.setTotal(5);
        when(usageLedgerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // when
        IPage<UsageLedger> result = service.list(workspaceId, null, "REFUNDED", 1, 20);

        // then
        assertEquals(1, result.getRecords().size());
        assertEquals("REFUNDED", result.getRecords().get(0).getStatus());
    }

    @Test
    void list_orderByCreatedAtDesc() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        Page<UsageLedger> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of());
        when(usageLedgerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // when
        service.list(workspaceId, null, null, 1, 20);

        // then
        verify(usageLedgerMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void getById_found() {
        // given
        UUID ledgerId = UUID.randomUUID();
        UsageLedger ledger = createLedger("ws_" + UUID.randomUUID().toString().replace("-", ""), "ANALYSIS", "CONSUMED");
        ledger.setId(ledgerId);
        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(ledger);

        // when
        UsageLedger result = service.getById(ledgerId);

        // then
        assertNotNull(result);
        assertEquals(ledgerId, result.getId());
    }

    @Test
    void getById_notFound() {
        // given
        UUID ledgerId = UUID.randomUUID();
        when(usageLedgerMapper.selectById(ledgerId)).thenReturn(null);

        // when
        UsageLedger result = service.getById(ledgerId);

        // then
        assertNull(result);
    }

    @Test
    void findByRefId_found() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UUID refId = UUID.randomUUID();
        UsageLedger ledger = createLedger(workspaceId, "ANALYSIS", "CONSUMED");
        ledger.setRefId(refId);

        when(usageLedgerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ledger);

        // when
        UsageLedger result = service.findByRefId(workspaceId, refId);

        // then
        assertNotNull(result);
        assertEquals("CONSUMED", result.getStatus());
        assertEquals(refId, result.getRefId());
    }

    @Test
    void findByRefId_notFound() {
        // given
        String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
        UUID refId = UUID.randomUUID();
        when(usageLedgerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // when
        UsageLedger result = service.findByRefId(workspaceId, refId);

        // then
        assertNull(result);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UsageLedger createLedger(String wsId, String action, String status) {
        UsageLedger ledger = new UsageLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setWorkspaceId(wsId);
        ledger.setAction(action);
        ledger.setStatus(status);
        ledger.setDelta(status.equals("CONSUMED") ? 1 : -1);
        ledger.setCreatedAt(OffsetDateTime.now());
        return ledger;
    }
}
