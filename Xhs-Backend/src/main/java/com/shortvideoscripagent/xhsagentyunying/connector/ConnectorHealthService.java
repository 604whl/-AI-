package com.shortvideoscripagent.xhsagentyunying.connector;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.ConnectorHealth;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ConnectorHealthMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接器健康诊断服务。
 * <p>
 * 职责：
 * <ul>
 *   <li>把连接器的健康状态落库到 {@code app.connector_health}；</li>
 *   <li>按失败类型分类（{@link ConnectorErrorCode}）；</li>
 *   <li>连续失败 3 次 → {@code DEGRADED}；连续失败 10 次 → {@code UNAVAILABLE}
 *       并写入降级路径 + 审计事件。</li>
 * </ul>
 * <p>
 * 降级路径顺序：{@code OFFICIAL_API → AUTHORIZED_CONNECTOR → BROWSER_ASSIST
 * → MANUAL_IMPORT → EXPORT_ONLY}。
 * <p>
 * <b>重要</b>：连接器异常<b>不得</b>阻塞内容编辑、审核、手动导出。
 * 本服务所有方法都是 best-effort —— 写健康记录失败只记日志，不向上抛异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorHealthService {

    /** 连续失败达到该值 → DEGRADED */
    public static final int DEGRADED_THRESHOLD = 3;

    /** 连续失败达到该值 → UNAVAILABLE */
    public static final int UNAVAILABLE_THRESHOLD = 10;

    /** 降级路径（按优先级从高到低） */
    public static final List<String> DEGRADATION_PATH = List.of(
            "OFFICIAL_API",
            "AUTHORIZED_CONNECTOR",
            "BROWSER_ASSIST",
            "MANUAL_IMPORT",
            "EXPORT_ONLY"
    );

    private final ConnectorHealthMapper healthMapper;
    private final ConnectorAuthorizationService authorizationService;
    private final AuditWriter auditWriter;

    /**
     * 查询某连接器的健康状态记录；无记录时返回一个默认 HEALTHY 占位。
     */
    public ConnectorHealth find(String providerCode, String platformCode, String workspaceId) {
        ConnectorHealth health = safeSelect(providerCode, platformCode, workspaceId);
        if (health != null) {
            return health;
        }
        ConnectorHealth placeholder = new ConnectorHealth();
        placeholder.setConnectorProviderCode(providerCode);
        placeholder.setPlatformCode(platformCode);
        placeholder.setWorkspaceId(workspaceId);
        placeholder.setStatus(ConnectorHealthStatusEnum.HEALTHY.name());
        placeholder.setConsecutiveFailures(0);
        return placeholder;
    }

    /**
     * 查询某平台下所有连接器健康记录。
     */
    public List<ConnectorHealth> listByPlatform(String platformCode) {
        try {
            return healthMapper.selectByPlatformCode(platformCode);
        } catch (Exception e) {
            log.warn("Failed to list connector health for platform {}: {}", platformCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 记录一次成功：状态回到 HEALTHY，清零连续失败数。
     * <p>
     * best-effort：不向上抛异常，避免连接器成功却导致业务失败。
     * <p>
     * <b>注意</b>：本方法刻意<b>不加</b> {@code @Transactional}。
     * 若加@Transactional 并在方法内吞掉 SQL 异常，Spring 会把事务标记为
     * rollback-only，方法返回时提交阶段抛 {@code UnexpectedRollbackException}，
     * 与"best-effort 绝不抛异常"的意图相反。这里每次只有单条 insert/update，
     * 依赖 MyBatis-Plus 的默认自动提交即可。
     */
    public void recordSuccess(String providerCode, String platformCode, String workspaceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            ConnectorHealth existing = safeSelect(providerCode, platformCode, workspaceId);
            if (existing == null) {
                ConnectorHealth h = new ConnectorHealth();
                h.setConnectorProviderCode(providerCode);
                h.setPlatformCode(platformCode);
                h.setWorkspaceId(normalizeWorkspaceId(workspaceId));
                h.setStatus(ConnectorHealthStatusEnum.HEALTHY.name());
                h.setLastSuccessAt(now);
                h.setLastCheckedAt(now);
                h.setConsecutiveFailures(0);
                h.setUpdatedAt(now);
                healthMapper.insert(h);
                return;
            }
            existing.setStatus(ConnectorHealthStatusEnum.HEALTHY.name());
            existing.setLastSuccessAt(now);
            existing.setLastCheckedAt(now);
            existing.setLastErrorCode(null);
            existing.setLastErrorMessage(null);
            existing.setConsecutiveFailures(0);
            existing.setDegradedTo(null);
            existing.setUpdatedAt(now);
            healthMapper.updateById(existing);
        } catch (Exception e) {
            log.warn("Failed to record connector success provider={}: {}", providerCode, e.getMessage());
        }
    }

    /**
     * 记录一次失败。
     * <p>
     * 连续失败达到 {@link #DEGRADED_THRESHOLD} 记 DEGRADED；
     * 达到 {@link #UNAVAILABLE_THRESHOLD} 记 UNAVAILABLE 并写入降级路径 + 审计事件。
     * <p>
     * best-effort：不向上抛异常（同样刻意不加 {@code @Transactional}，理由见
     * {@link #recordSuccess}）。
     */
    public void recordFailure(String providerCode, String platformCode, String workspaceId,
                              ConnectorErrorCode errorCode, String errorMessage) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            ConnectorErrorCode code = errorCode == null ? ConnectorErrorCode.UNKNOWN : errorCode;

            ConnectorHealth existing = safeSelect(providerCode, platformCode, workspaceId);
            int failures = (existing != null && existing.getConsecutiveFailures() != null)
                    ? existing.getConsecutiveFailures() + 1
                    : 1;

            String status;
            String degradedTo = null;
            if (failures >= UNAVAILABLE_THRESHOLD) {
                status = ConnectorHealthStatusEnum.UNAVAILABLE.name();
                degradedTo = resolveDegradedTo(providerCode);
            } else if (failures >= DEGRADED_THRESHOLD) {
                status = ConnectorHealthStatusEnum.DEGRADED.name();
            } else {
                status = ConnectorHealthStatusEnum.HEALTHY.name();
            }

            if (existing == null) {
                ConnectorHealth h = new ConnectorHealth();
                h.setConnectorProviderCode(providerCode);
                h.setPlatformCode(platformCode);
                h.setWorkspaceId(normalizeWorkspaceId(workspaceId));
                h.setStatus(status);
                h.setLastCheckedAt(now);
                h.setLastErrorCode(code.name());
                h.setLastErrorMessage(truncate(errorMessage, 2000));
                h.setConsecutiveFailures(failures);
                h.setDegradedTo(degradedTo);
                h.setUpdatedAt(now);
                healthMapper.insert(h);
            } else {
                existing.setStatus(status);
                existing.setLastCheckedAt(now);
                existing.setLastErrorCode(code.name());
                existing.setLastErrorMessage(truncate(errorMessage, 2000));
                existing.setConsecutiveFailures(failures);
                existing.setDegradedTo(degradedTo);
                existing.setUpdatedAt(now);
                healthMapper.updateById(existing);
            }

            if (ConnectorHealthStatusEnum.UNAVAILABLE.name().equals(status)) {
                writeUnavailableAudit(providerCode, platformCode, failures, code, errorMessage, degradedTo);
            }
        } catch (Exception e) {
            log.warn("Failed to record connector failure provider={}: {}", providerCode, e.getMessage());
        }
    }

    /**
     * 记录未授权状态（授权门禁拒绝时使用）。
     * <p>
     * 未授权的调用不落业务数据，这里只更新健康状态用于诊断展示。
     * <p>
     * 同样刻意不加 {@code @Transactional}（理由见 {@link #recordSuccess}）。
     */
    public void recordNotAuthorized(String providerCode, String platformCode, String workspaceId) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            ConnectorHealth existing = safeSelect(providerCode, platformCode, workspaceId);
            if (existing == null) {
                ConnectorHealth h = new ConnectorHealth();
                h.setConnectorProviderCode(providerCode);
                h.setPlatformCode(platformCode);
                h.setWorkspaceId(normalizeWorkspaceId(workspaceId));
                h.setStatus(ConnectorHealthStatusEnum.NOT_AUTHORIZED.name());
                h.setLastCheckedAt(now);
                h.setLastErrorCode(ConnectorErrorCode.NOT_AUTHORIZED.name());
                h.setLastErrorMessage(ConnectorAuthorizationService.ERR_NOT_AUTHORIZED);
                h.setConsecutiveFailures(0);
                h.setUpdatedAt(now);
                healthMapper.insert(h);
                return;
            }
            existing.setStatus(ConnectorHealthStatusEnum.NOT_AUTHORIZED.name());
            existing.setLastCheckedAt(now);
            existing.setLastErrorCode(ConnectorErrorCode.NOT_AUTHORIZED.name());
            existing.setLastErrorMessage(ConnectorAuthorizationService.ERR_NOT_AUTHORIZED);
            existing.setUpdatedAt(now);
            healthMapper.updateById(existing);
        } catch (Exception e) {
            log.warn("Failed to record NOT_AUTHORIZED health provider={}: {}", providerCode, e.getMessage());
        }
    }

    /**
     * 解析降级目标：按降级路径顺序找到第一个比当前等级低的可用连接器。
     * <p>
     * 找不到时返回 {@code EXPORT_ONLY}（永远可用的最终兜底）。
     */
    public String resolveDegradedTo(String providerCode) {
        Integer currentIndex = indexOfLevel(providerCode);
        if (currentIndex == null) {
            return DEGRADATION_PATH.get(DEGRADATION_PATH.size() - 1);
        }
        for (int i = currentIndex + 1; i < DEGRADATION_PATH.size(); i++) {
            String candidate = DEGRADATION_PATH.get(i);
            if (isCandidateUsable(candidate)) {
                return candidate;
            }
        }
        return DEGRADATION_PATH.get(DEGRADATION_PATH.size() - 1);
    }

    /**
     * 降级路径顺序（供 Controller 展示）。
     */
    public List<String> degradationPath() {
        return DEGRADATION_PATH;
    }

    // ── 私有辅助 ────────────────────────────────────────────────────────────

    private Integer indexOfLevel(String providerCode) {
        if (providerCode == null) {
            return null;
        }
        String level = providerCode;
        if ("XHS_BROWSER_SIDEBAR".equals(providerCode)) {
            level = "BROWSER_ASSIST";
        } else if ("XHS_OFFICIAL_API".equals(providerCode)) {
            level = "OFFICIAL_API";
        } else if (providerCode.endsWith("CSV_IMPORT")) {
            level = "MANUAL_IMPORT";
        } else if (providerCode.contains("AUTHORIZED")) {
            level = "AUTHORIZED_CONNECTOR";
        }
        for (int i = 0; i < DEGRADATION_PATH.size(); i++) {
            if (DEGRADATION_PATH.get(i).equals(level)) {
                return i;
            }
        }
        return null;
    }

    /**
     * 降级目标是否可用：对应的平台必须已上线且已授权。
     */
    private boolean isCandidateUsable(String level) {
        if (level == null || "EXPORT_ONLY".equals(level)) {
            return false;
        }
        for (String platformCode : List.of("XIAOHONGSHU", "DOUYIN")) {
            if (authorizationService.isPlatformEnabled(platformCode)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnavailableAudit(String providerCode, String platformCode, int failures,
                                       ConnectorErrorCode errorCode, String errorMessage, String degradedTo) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("providerCode", providerCode);
            metadata.put("platformCode", platformCode);
            metadata.put("consecutiveFailures", failures);
            metadata.put("errorCode", errorCode == null ? null : errorCode.name());
            metadata.put("errorMessage", truncate(errorMessage, 500));
            metadata.put("degradedTo", degradedTo);
            auditWriter.record("connector.unavailable", "CONNECTOR", providerCode, metadata);
        } catch (Exception e) {
            log.warn("Failed to write connector unavailable audit provider={}: {}", providerCode, e.getMessage());
        }
    }

    private ConnectorHealth safeSelect(String providerCode, String platformCode, String workspaceId) {
        try {
            return healthMapper.selectByScope(providerCode, platformCode, workspaceId);
        } catch (Exception e) {
            log.warn("Failed to read connector health provider={}: {}", providerCode, e.getMessage());
            return null;
        }
    }

    private String normalizeWorkspaceId(String workspaceId) {
        return (workspaceId == null || workspaceId.isBlank()) ? null : workspaceId;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
