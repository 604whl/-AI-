package com.shortvideoscripagent.xhsagentyunying.infra.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.*;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.*;
import com.shortvideoscripagent.xhsagentyunying.service.retention.AccountDeletionService;
import com.shortvideoscripagent.xhsagentyunying.service.retention.WorkspaceDestructionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 数据保留清理定时任务（P0-9）。
 * <p>
 * 每天凌晨 3:00 执行以下清理：
 * 1. 清理过期的使用日志（usage_log）
 * 2. 清理过期的导出文件记录（export_request expired）
 * 3. 清理过期的聊天会话（chat_session）
 * 4. 清理过期的内容资产（content_asset 软删后物理删）
 * 5. 执行到期的账号物理删除
 * 6. 执行到期的 workspace 物理销毁
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionSweeper {

    private final AccountDeletionService accountDeletionService;
    private final WorkspaceDestructionService workspaceDestructionService;

    private final UsageLogMapper usageLogMapper;
    private final ExportRequestMapper exportRequestMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final DataRetentionPolicyMapper retentionPolicyMapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // 定时任务入口
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 每天凌晨 3:00 执行数据保留清理。
     * <p>
     * 使用 fixedDelay 确保每次执行完毕后才计算下次间隔，避免任务重叠。
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void runRetentionSweep() {
        log.info("=== RetentionSweeper started at {} ===", OffsetDateTime.now());
        long startMs = System.currentTimeMillis();

        try {
            sweepExpiredUsageLogs();
            sweepExpiredExports();
            sweepExpiredChatSessions();
            sweepExpiredContentAssets();
            executePendingAccountDeletions();
            executePendingWorkspaceDestructions();
        } catch (Exception e) {
            log.error("RetentionSweeper failed: {}", e.getMessage(), e);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("=== RetentionSweeper finished in {} ms ===", elapsedMs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 清理逻辑
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 清理过期的使用日志（默认 90 天）。
     */
    private void sweepExpiredUsageLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
        int deleted = usageLogMapper.delete(
                new LambdaQueryWrapper<UsageLog>()
                        .lt(UsageLog::getCreatedAt, cutoff)
        );
        if (deleted > 0) {
            log.info("Swept {} expired usage_log records", deleted);
        }
    }

    /**
     * 标记过期的导出请求为 expired。
     */
    private void sweepExpiredExports() {
        OffsetDateTime now = OffsetDateTime.now();

        // 查找所有 ready 且已过期的
        var expiredList = exportRequestMapper.selectList(
                new LambdaQueryWrapper<ExportRequest>()
                        .eq(ExportRequest::getStatus, ExportRequest.STATUS_READY)
                        .le(ExportRequest::getExpiresAt, now)
        );

        for (ExportRequest req : expiredList) {
            req.setStatus(ExportRequest.STATUS_EXPIRED);
            exportRequestMapper.updateById(req);
        }

        if (!expiredList.isEmpty()) {
            log.info("Marked {} export requests as expired", expiredList.size());
        }
    }

    /**
     * 清理过期的聊天会话（默认 180 天）。
     */
    private void sweepExpiredChatSessions() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(180);
        int deleted = chatSessionMapper.delete(
                new LambdaQueryWrapper<ChatSession>()
                        .lt(ChatSession::getCreatedAt, cutoff)
        );
        if (deleted > 0) {
            log.info("Swept {} expired chat_session records", deleted);
        }
    }

    /**
     * 清理过期的内容资产（按各 workspace 的保留策略）。
     */
    private void sweepExpiredContentAssets() {
        OffsetDateTime now = OffsetDateTime.now();

        // 获取所有有 policy 的 workspace
        var policies = retentionPolicyMapper.selectList(null);

        int totalDeleted = 0;

        for (DataRetentionPolicy policy : policies) {
            int retentionDays = policy.getAssetRetentionDays() != null
                    ? policy.getAssetRetentionDays() : 365;
            OffsetDateTime cutoff = now.minusDays(retentionDays);

            int deleted = contentAssetMapper.delete(
                    new LambdaQueryWrapper<ContentAsset>()
                            .eq(ContentAsset::getWorkspaceId, policy.getWorkspaceId())
                            .lt(ContentAsset::getCreatedAt, cutoff)
            );
            totalDeleted += deleted;
        }

        // 也处理没有 policy 的 workspace（使用默认 365 天，清理已归档的旧资产）
        OffsetDateTime defaultCutoff = now.minusDays(365);
        int deleted = contentAssetMapper.delete(
                new LambdaQueryWrapper<ContentAsset>()
                        .eq(ContentAsset::getStatus, "archived")
                        .lt(ContentAsset::getUpdatedAt, defaultCutoff)
        );
        totalDeleted += deleted;

        if (totalDeleted > 0) {
            log.info("Swept {} expired content_asset records", totalDeleted);
        }
    }

    /**
     * 执行到期的账号物理删除。
     */
    private void executePendingAccountDeletions() {
        try {
            accountDeletionService.executeScheduledDeletion();
        } catch (Exception e) {
            log.error("Account deletion sweep failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行到期的 workspace 物理销毁。
     */
    private void executePendingWorkspaceDestructions() {
        try {
            workspaceDestructionService.executeScheduledDestruction();
        } catch (Exception e) {
            log.error("Workspace destruction sweep failed: {}", e.getMessage(), e);
        }
    }
}
