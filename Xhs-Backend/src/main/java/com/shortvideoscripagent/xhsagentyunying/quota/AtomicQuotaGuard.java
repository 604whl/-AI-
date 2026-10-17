package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P0-2: 原子化额度守卫。
 * <p>
 * 单 SQL UPDATE 原子扣减，保证并发下不超额。
 * 成功后写入 usage_ledger 审计记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicQuotaGuard {

    private static final int CODE_QUOTA_EXCEEDED = 42901;

    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final UsageLedgerMapper usageLedgerMapper;
    private final QuotaProperties quotaProperties;

    /**
     * 原子扣减 workspace 额度。
     * <p>
     * 1. 尝试单 SQL UPDATE 扣减
     * 2. 若 affected=0 且 reset_at 过期 → 重置后再试一次
     * 3. 成功后插入一条 ledger CONSUMED 记录
     * </p>
     *
     * @return QuotaCheckResult 成功返回 ledgerId，失败返回 exceeded 原因
     */
    @Transactional
    public QuotaCheckResult tryConsume(UUID workspaceId, String action, UUID refId, String reason) {
        // 1. 首次原子扣减
        int affected = workspaceQuotaMapper.atomicConsume(workspaceId);

        if (affected == 0) {
            // 2. 检查是否因为 reset_at 过期导致扣减失败
            int resetRows = workspaceQuotaMapper.resetIfExpired(workspaceId);
            if (resetRows > 0) {
                // 重置后重试一次
                affected = workspaceQuotaMapper.atomicConsume(workspaceId);
            }
        }

        if (affected == 0) {
            // 仍然失败：配额不足
            log.warn("Quota exceeded for workspace {} action {}", workspaceId, action);
            return QuotaCheckResult.exceeded("quota_exceeded");
        }

        // 3. 写入 ledger CONSUMED
        UsageLedger ledger = new UsageLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setWorkspaceId(workspaceId);
        ledger.setAction(action);
        ledger.setRefId(refId);
        ledger.setDelta(1);
        ledger.setStatus("CONSUMED");
        ledger.setReason(reason);
        ledger.setCreatedAt(OffsetDateTime.now());
        usageLedgerMapper.insert(ledger);

        log.debug("Quota consumed: workspace={} action={} ledgerId={}", workspaceId, action, ledger.getId());
        return QuotaCheckResult.ok(ledger.getId());
    }

    /**
     * 懒创建 workspace 配额记录（首次访问时自动创建）。
     */
    public void ensureQuotaExists(UUID workspaceId) {
        if (workspaceQuotaMapper.selectById(workspaceId) != null) {
            return;
        }
        WorkspaceQuota quota = new WorkspaceQuota();
        quota.setWorkspaceId(workspaceId);
        quota.setDailyQuota(quotaProperties.getDefaultDailyQuota());
        quota.setUsed(0);
        if (quotaProperties.isLazyInitExpired()) {
            // 立即过期，下次请求会重置
            quota.setResetAt(OffsetDateTime.now().minusSeconds(1));
        } else {
            quota.setResetAt(OffsetDateTime.now().plusDays(1));
        }
        quota.setCreatedAt(OffsetDateTime.now());
        quota.setUpdatedAt(OffsetDateTime.now());
        try {
            workspaceQuotaMapper.insert(quota);
        } catch (Exception ex) {
            // 幂等：并发创建时 ignore
            log.debug("Quota already exists for workspace {}", workspaceId);
        }
    }

    /**
     * 查询 workspace 配额状态（自动创建记录）。
     */
    public WorkspaceQuota getOrCreate(UUID workspaceId) {
        ensureQuotaExists(workspaceId);
        return workspaceQuotaMapper.selectById(workspaceId);
    }

    /**
     * 查询 workspace 配额（不自动创建）。
     */
    public WorkspaceQuota getQuota(UUID workspaceId) {
        return workspaceQuotaMapper.selectById(workspaceId);
    }

    /**
     * 检查 workspace 是否还有剩余配额（不自动创建）。
     */
    public boolean hasQuota(UUID workspaceId) {
        WorkspaceQuota quota = workspaceQuotaMapper.selectById(workspaceId);
        if (quota == null) {
            return true; // 无记录视为有配额
        }
        return quota.getUsed() < quota.getDailyQuota() || quota.getResetAt().isBefore(OffsetDateTime.now());
    }
}
