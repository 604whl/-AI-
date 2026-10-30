package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.PricingService;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-2: AtomicQuotaGuard 并发测试。
 * 验证 1000 并发同一 workspace 不超额。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AtomicQuotaGuardConcurrencyTest {

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
    private final int DAILY_QUOTA = 100;

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
        when(quotaProperties.getDefaultDailyQuota()).thenReturn(DAILY_QUOTA);
        when(quotaProperties.isLazyInitExpired()).thenReturn(false);
        // 价格表无数据 → 单价 0，计价不阻断扣费
        when(pricingService.resolvePrice(any(), any())).thenReturn(null);
    }

    @Test
    void concurrentConsume_1000requests_respectsQuota() throws Exception {
        // given: workspace has quota initialized
        WorkspaceQuota quota = new WorkspaceQuota();
        quota.setWorkspaceId(workspaceId);
        quota.setDailyQuota(DAILY_QUOTA);
        quota.setUsed(0);
        quota.setResetAt(OffsetDateTime.now().plusDays(1));
        when(workspaceQuotaMapper.selectById(workspaceId)).thenReturn(quota);
        when(workspaceMapper.selectById(workspaceId.toString())).thenReturn(new Workspace());

        // Mock: each atomicConsume call succeeds up to DAILY_QUOTA
        AtomicInteger consumeCount = new AtomicInteger(0);
        when(workspaceQuotaMapper.atomicConsume(any())).thenAnswer(inv -> {
            int current = consumeCount.incrementAndGet();
            return current <= DAILY_QUOTA ? 1 : 0;
        });
        when(workspaceQuotaMapper.resetIfExpired(any())).thenReturn(0);

        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        // when: 1000 concurrent requests
        int totalRequests = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalRequests);

        List<Future<QuotaCheckResult>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            Future<QuotaCheckResult> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    return guard.tryConsume(workspaceId, "ANALYSIS", null, "concurrent-test");
                } finally {
                    endLatch.countDown();
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: count successes
        int successCount = 0;
        int exceedCount = 0;
        for (Future<QuotaCheckResult> f : futures) {
            QuotaCheckResult r = f.get();
            if (r.isOk()) {
                successCount++;
            } else {
                exceedCount++;
            }
        }

        assertTrue(successCount <= DAILY_QUOTA,
                "Should have at most " + DAILY_QUOTA + " successes, got " + successCount);
        assertEquals(totalRequests, successCount + exceedCount,
                "All requests should have a result");
    }

    @Test
    void concurrentConsume_allWithinQuota_allSucceed() throws Exception {
        // given: quota is high enough for all requests
        int highQuota = 500;
        WorkspaceQuota quota = new WorkspaceQuota();
        quota.setWorkspaceId(workspaceId);
        quota.setDailyQuota(highQuota);
        quota.setUsed(0);
        quota.setResetAt(OffsetDateTime.now().plusDays(1));
        when(workspaceQuotaMapper.selectById(workspaceId)).thenReturn(quota);
        when(workspaceMapper.selectById(workspaceId.toString())).thenReturn(new Workspace());

        when(workspaceQuotaMapper.atomicConsume(any())).thenReturn(1);
        when(workspaceQuotaMapper.resetIfExpired(any())).thenReturn(0);
        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        // when: 100 concurrent requests with quota=500
        int totalRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalRequests);

        List<Future<QuotaCheckResult>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            Future<QuotaCheckResult> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    return guard.tryConsume(workspaceId, "ANALYSIS", null, "high-quota-test");
                } finally {
                    endLatch.countDown();
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: all should succeed
        int successCount = 0;
        for (Future<QuotaCheckResult> f : futures) {
            if (f.get().isOk()) {
                successCount++;
            }
        }
        assertEquals(totalRequests, successCount,
                "All requests should succeed when quota is sufficient");
    }

    @Test
    void concurrentConsume_exceed_then_reset_thenConsumeAgain() throws Exception {
        // given: quota exhausted but expired
        AtomicInteger consumeCount = new AtomicInteger(0);
        WorkspaceQuota quota = new WorkspaceQuota();
        quota.setWorkspaceId(workspaceId);
        quota.setDailyQuota(5);
        quota.setUsed(5);
        quota.setResetAt(OffsetDateTime.now().minusDays(1)); // expired
        when(workspaceQuotaMapper.selectById(workspaceId)).thenReturn(quota);
        when(workspaceMapper.selectById(workspaceId.toString())).thenReturn(new Workspace());

        when(workspaceQuotaMapper.atomicConsume(any())).thenAnswer(inv -> {
            int current = consumeCount.incrementAndGet();
            return current <= 5 ? 1 : 0;
        });
        when(workspaceQuotaMapper.resetIfExpired(any())).thenReturn(1);
        when(usageLedgerMapper.insert(any(UsageLedger.class))).thenAnswer(inv -> {
            UsageLedger l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return 1;
        });

        // when: 10 concurrent requests
        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(requests);

        List<Future<QuotaCheckResult>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            Future<QuotaCheckResult> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    return guard.tryConsume(workspaceId, "ANALYSIS", null, "reset-test");
                } finally {
                    endLatch.countDown();
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: exactly 5 should succeed (quota)
        int successCount = 0;
        for (Future<QuotaCheckResult> f : futures) {
            if (f.get().isOk()) {
                successCount++;
            }
        }
        assertEquals(5, successCount, "Exactly 5 should succeed after reset");
    }
}
