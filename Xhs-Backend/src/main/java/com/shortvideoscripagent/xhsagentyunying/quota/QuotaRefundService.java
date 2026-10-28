package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UsageLogMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P0-2: 额度冲正服务。
 * <p>
 * 处理任务失败、超时、重复请求时的配额回补。
 * 同时更新 usage_ledger 和 usage_log 两条记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaRefundService {

    private static final int CODE_NOT_FOUND = 40401;
    private static final int CODE_ALREADY_REFUNDED = 40903;

    private final UsageLedgerMapper usageLedgerMapper;
    private final UsageLogMapper usageLogMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;

    /**
     * 冲正指定 ledger 记录。
     * <p>
     * 1. 标记 ledger 为 REFUNDED
     * 2. workspace_quota used - 1
     * 3. 更新关联的 usage_log refund_status / refunded_at / original_log_id
     * </p>
     *
     * @param ledgerId 要冲正的 ledger id
     * @param reason  冲正原因
     * @return true=成功冲正，false=记录不存在或已冲正
     */
    @Transactional
    public boolean refund(UUID ledgerId, String reason) {
        UsageLedger ledger = usageLedgerMapper.selectById(ledgerId);
        if (ledger == null) {
            log.warn("Refund target ledger not found: {}", ledgerId);
            return false;
        }
        if ("REFUNDED".equals(ledger.getStatus())) {
            log.debug("Ledger already refunded: {}", ledgerId);
            return false;
        }

        // 1. 标记 ledger 为 REFUNDED
        int updated = usageLedgerMapper.markRefunded(ledgerId);
        if (updated == 0) {
            log.warn("Ledger already refunded (concurrent): {}", ledgerId);
            return false;
        }

        // 2. workspace_quota used - 1
        if (ledger.getWorkspaceId() != null) {
            workspaceQuotaMapper.refund(ledger.getWorkspaceId());
        }

        // 3. 更新关联的 usage_log（如有）—— 写入退费标记（字段由 ALTER TABLE 迁移添加）
        if (ledger.getRefId() != null) {
            try {
                UsageLog usageLog = usageLogMapper.selectById(ledger.getRefId().toString());
                if (usageLog != null) {
                    // 反射设置字段（字段由 V26 迁移添加，此处兼容字段缺失）
                    try {
                        java.lang.reflect.Method setRefundStatus = UsageLog.class.getMethod("setRefundStatus", String.class);
                        setRefundStatus.invoke(usageLog, "REFUNDED");
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        java.lang.reflect.Method setRefundedAt = UsageLog.class.getMethod("setRefundedAt", OffsetDateTime.class);
                        setRefundedAt.invoke(usageLog, OffsetDateTime.now());
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        java.lang.reflect.Method setOriginalLogId = UsageLog.class.getMethod("setOriginalLogId", UUID.class);
                        setOriginalLogId.invoke(usageLog, ledgerId);
                    } catch (NoSuchMethodException ignored) {}
                    usageLogMapper.updateById(usageLog);
                }
            } catch (Exception ex) {
                log.debug("Failed to update usage_log refund fields for ledger {}: {}", ledgerId, ex.getMessage());
            }
        }

        log.info("Quota refunded: ledgerId={} reason={}", ledgerId, reason);
        return true;
    }

    /**
     * 按 refId 查找 ledger 并冲正（用于 handler catch block）。
     */
    @Transactional
    public boolean refundByRefId(String workspaceId, UUID refId, String reason) {
        UsageLedger ledger = usageLedgerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UsageLedger>()
                        .eq(UsageLedger::getWorkspaceId, workspaceId)
                        .eq(UsageLedger::getRefId, refId)
                        .eq(UsageLedger::getStatus, "CONSUMED")
                        .orderByDesc(UsageLedger::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (ledger == null) {
            log.debug("No CONSUMED ledger found for refId {} workspace {}", refId, workspaceId);
            return false;
        }
        return refund(ledger.getId(), reason);
    }
}
