package com.shortvideoscripagent.xhsagentyunying.infra.scheduler;

import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorAuthorizationService;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformAccount;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformAccountMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricCollectRequest;
import com.shortvideoscripagent.xhsagentyunying.service.MetricSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * S3-5 指标采集定时任务。
 * <p>
 * 每 <b>6 小时</b>触发一次，按 <b>工作区 × 平台 × 账号</b> 维度投递采集任务到持久化任务队列
 * （{@code METRIC_COLLECT}）。<b>不使用 {@code @Async}</b>。
 * </p>
 * <p>
 * 采集任务<b>不计费</b>（不写 {@code usage_ledger} DEBIT）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class MetricCollectScheduler {

    private final MetricSnapshotService metricSnapshotService;
    private final PlatformAccountMapper platformAccountMapper;
    private final WorkspaceMapper workspaceMapper;
    private final ConnectorAuthorizationService connectorAuthorizationService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 每 6 小时触发一次（21600000 ms）。
     */
    @Scheduled(initialDelay = 120_000, fixedRate = 6 * 60 * 60 * 1000)
    public void triggerCollect() {
        log.info("[MetricCollectScheduler] 开始按工作区 × 平台 × 账号投递指标采集任务...");
        int dispatched = 0;
        try {
            List<String> workspaceIds = jdbcTemplate.query(
                    "SELECT id FROM app.workspace WHERE status = 'active' OR status IS NULL",
                    (rs, i) -> rs.getString("id"));
            for (String workspaceId : workspaceIds) {
                dispatched += dispatchForWorkspace(workspaceId);
            }
            log.info("[MetricCollectScheduler] 指标采集任务投递完成，共 {} 个", dispatched);
        } catch (Exception e) {
            log.error("[MetricCollectScheduler] 指标采集任务投递失败: {}", e.getMessage(), e);
        }
    }

    private int dispatchForWorkspace(String workspaceId) {
        int dispatched = 0;
        List<PlatformAccount> accounts;
        try {
            accounts = platformAccountMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlatformAccount>()
                            .eq(PlatformAccount::getWorkspaceId, workspaceId));
        } catch (Exception ex) {
            log.warn("Failed to list platform accounts for workspace {}: {}", workspaceId, ex.getMessage());
            return 0;
        }
        for (PlatformAccount account : accounts) {
            String platformCode = account.getPlatformCode();
            String providerCode = account.getConnectorProviderCode();
            if (platformCode == null || providerCode == null) {
                continue;
            }
            // 授权门禁：未授权平台/连接器一律跳过
            if (!connectorAuthorizationService.isAuthorized(platformCode, providerCode)) {
                log.debug("Skip unauthorized metric collect: platform={} provider={}", platformCode, providerCode);
                continue;
            }
            try {
                MetricCollectRequest request = new MetricCollectRequest();
                request.setPlatformCode(platformCode);
                request.setAccountId(account.getExternalAccountId() != null
                        ? account.getExternalAccountId()
                        : account.getId().toString());
                metricSnapshotService.collect(workspaceId, account.getCreatedBy(), request);
                dispatched++;
            } catch (Exception ex) {
                log.warn("Failed to dispatch metric collect for ws={} platform={}: {}",
                        workspaceId, platformCode, ex.getMessage());
            }
        }
        return dispatched;
    }
}
