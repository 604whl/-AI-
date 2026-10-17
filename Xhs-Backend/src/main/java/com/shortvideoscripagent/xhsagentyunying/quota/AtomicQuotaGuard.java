package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
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
public class AtomicQuotaGuard {

    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final UsageLedgerMapper usageLedgerMapper;
    private final WorkspaceMapper workspaceMapper;
    private final QuotaProperties quotaProperties;

    public AtomicQuotaGuard(
            WorkspaceQuotaMapper workspaceQuotaMapper,
            UsageLedgerMapper usageLedgerMapper,
            WorkspaceMapper workspaceMapper,
            QuotaProperties quotaProperties
    ) {
        this.workspaceQuotaMapper = workspaceQuotaMapper;
        this.usageLedgerMapper = usageLedgerMapper;
        this.workspaceMapper = workspaceMapper;
        this.quotaProperties = quotaProperties;
    }

    /**
     * 原子扣减 workspace 额度。
     */
    @Transactional
    public QuotaCheckResult tryConsume(String workspaceId, String action, UUID refId, String reason) {
        // 1. 首次原子扣减
        int affected = workspaceQuotaMapper.atomicConsume(workspaceId);

        if (affected == 0) {
            // 2. 检查是否因为 reset_at 过期导致扣减失败
            int resetRows = workspaceQuotaMapper.resetIfExpired(workspaceId);
            if (resetRows > 0) {
                log.info("Auto-reset quota for workspace {}", workspaceId);
                affected = workspaceQuotaMapper.atomicConsume(workspaceId);
            }
        }

        if (affected == 0) {
            // 3. 仍失败：workspace 不存在或配额耗尽，尝试懒创建
            if (workspaceQuotaMapper.selectById(workspaceId) == null) {
                lazyInitQuota(workspaceId);
                affected = workspaceQuotaMapper.atomicConsume(workspaceId);
            }
        }

        if (affected == 0) {
            log.warn("Quota exceeded for workspace {} action {}", workspaceId, action);
            return QuotaCheckResult.exceeded("quota_exceeded");
        }

        // 4. 写入 ledger CONSUMED
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
     * 懒创建 workspace 配额记录。
     */
    private void lazyInitQuota(String workspaceId) {
        var ws = workspaceMapper.selectById(workspaceId);
        int dailyQuota = quotaProperties.getDefaultDailyQuota();
        if (ws != null && ws.getDefaultQuota() != null) {
            dailyQuota = ws.getDefaultQuota();
        }
        OffsetDateTime resetAt = quotaProperties.isLazyInitExpired()
                ? OffsetDateTime.now().minusSeconds(1)
                : OffsetDateTime.now().plusDays(1);

        WorkspaceQuota q = new WorkspaceQuota();
        q.setWorkspaceId(workspaceId);
        q.setDailyQuota(dailyQuota);
        q.setUsed(0);
        q.setResetAt(resetAt);
        q.setCreatedAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        try {
            workspaceQuotaMapper.insert(q);
            log.info("Lazy-initialized workspace_quota for {}", workspaceId);
        } catch (Exception ex) {
            log.debug("workspace_quota already exists for {}", workspaceId);
        }
    }

    /**
     * 查询 workspace 配额状态。
     */
    public WorkspaceQuota getQuota(String workspaceId) {
        WorkspaceQuota q = workspaceQuotaMapper.selectById(workspaceId);
        if (q != null && q.getResetAt() != null && q.getResetAt().isBefore(OffsetDateTime.now())) {
            workspaceQuotaMapper.resetIfExpired(workspaceId);
            q = workspaceQuotaMapper.selectById(workspaceId);
        }
        return q;
    }

    /**
     * 检查是否还有剩余配额。
     */
    public boolean hasQuota(String workspaceId) {
        WorkspaceQuota q = workspaceQuotaMapper.selectById(workspaceId);
        if (q == null) return true;
        if (q.getResetAt() != null && q.getResetAt().isBefore(OffsetDateTime.now())) return true;
        return q.getUsed() < q.getDailyQuota();
    }
}
