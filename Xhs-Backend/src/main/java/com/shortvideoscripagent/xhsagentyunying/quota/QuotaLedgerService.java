package com.shortvideoscripagent.xhsagentyunying.quota;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * P0-2: 额度 Ledger 审计服务。
 * <p>
 * 封装 usage_ledger 的查询、分页、统计。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaLedgerService {

    private final UsageLedgerMapper usageLedgerMapper;

    /**
     * 分页查询 ledger 记录。
     */
    public IPage<UsageLedger> pageByWorkspace(UUID workspaceId, String action, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<UsageLedger> wrapper = new LambdaQueryWrapper<UsageLedger>()
                .eq(UsageLedger::getWorkspaceId, workspaceId)
                .orderByDesc(UsageLedger::getCreatedAt);
        if (action != null && !action.isBlank()) {
            wrapper.eq(UsageLedger::getAction, action.trim().toUpperCase());
        }
        return usageLedgerMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }

    /**
     * 按 ledger id 查询单条记录。
     */
    public UsageLedger getById(UUID ledgerId) {
        return usageLedgerMapper.selectById(ledgerId);
    }

    /**
     * 按 refId 查询关联记录。
     */
    public UsageLedger findByRefId(UUID workspaceId, UUID refId) {
        LambdaQueryWrapper<UsageLedger> wrapper = new LambdaQueryWrapper<UsageLedger>()
                .eq(UsageLedger::getWorkspaceId, workspaceId)
                .eq(UsageLedger::getRefId, refId)
                .orderByDesc(UsageLedger::getCreatedAt)
                .last("LIMIT 1");
        return usageLedgerMapper.selectOne(wrapper);
    }

    /**
     * 分页查询 ledger（兼容 WorkspaceQuotaController）。
     */
    public IPage<UsageLedger> list(UUID workspaceId, String action, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<UsageLedger> wrapper = new LambdaQueryWrapper<UsageLedger>()
                .eq(UsageLedger::getWorkspaceId, workspaceId)
                .orderByDesc(UsageLedger::getCreatedAt);
        if (action != null && !action.isBlank()) {
            wrapper.eq(UsageLedger::getAction, action.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(UsageLedger::getStatus, status.trim().toUpperCase());
        }
        return usageLedgerMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }
}
