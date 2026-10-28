package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.auth.dto.UserUsageVo;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UsageLogMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.AtomicQuotaGuard;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaCheckResult;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaExceededException;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 额度/用量统计（按 workspace 隔离）。
 * <p>
 * P0-2 改造：核心逻辑委托给 {@link AtomicQuotaGuard}，保留旧 API 兼容。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserQuotaService {

    public static final String ACTION_ANALYSIS = "ANALYSIS";
    public static final String ACTION_AGENT_MESSAGE = "CHAT";
    public static final String ACTION_WEB_SEARCH = "WEB_SEARCH";
    public static final String ACTION_BODY_GENERATION = "BODY";

    private static final int CODE_QUOTA_EXCEEDED = 42901;
    private static final int CODE_AGENT_QUOTA_EXCEEDED = 42904;
    private static final int CODE_WEB_SEARCH_QUOTA_EXCEEDED = 42903;
    private static final int CODE_BODY_QUOTA_EXCEEDED = 42905;

    private final UserMapper userMapper;
    private final UsageLogMapper usageLogMapper;
    private final WorkspaceMapper workspaceMapper;
    private final AppAgentProperties appAgentProperties;
    private final AtomicQuotaGuard atomicQuotaGuard;

    // ========== 兼容旧 API ==========

    public int remainingAnalysisQuota(Long userId) {
        return getAnalysisUsage(userId).getRemaining();
    }

    public UserUsageVo getAnalysisUsage(Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();

        WorkspaceQuota quota = atomicQuotaGuard.getQuota(workspaceId);
        int dailyQuota = resolveDailyQuota(workspaceId);
        int used = quota != null ? quota.getUsed() : 0;
        int remaining = Math.max(dailyQuota - used, 0);

        return UserUsageVo.builder()
                .dailyQuota(dailyQuota)
                .usedToday(used)
                .remaining(remaining)
                .build();
    }

    @Transactional
    public void consumeAnalysisQuota(Long userId, String taskId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID refId = taskId != null ? UUID.fromString(taskId) : null;

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_ANALYSIS, refId, "task-created");
        if (!result.isOk()) {
            throw new QuotaExceededException();
        }

        // 兼容旧 usage_log
        writeLegacyUsageLog(userId, workspaceId, ACTION_ANALYSIS, taskId);
    }

    public int remainingAgentMessageQuota(Long userId) {
        requireUser(userId);
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        return atomicQuotaGuard.hasQuota(workspaceId) ? 1 : 0;
    }

    @Transactional
    public void consumeAgentMessageQuota(Long userId, String sessionId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID refId = parseUuidOrNull(sessionId);

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_AGENT_MESSAGE, refId, "agent-message");
        if (!result.isOk()) {
            throw new BusinessException(CODE_AGENT_QUOTA_EXCEEDED, "agent_quota_exceeded");
        }
        writeLegacyUsageLog(userId, workspaceId, ACTION_AGENT_MESSAGE, sessionId);
    }

    public int remainingWebSearchQuota(Long userId) {
        requireUser(userId);
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        return atomicQuotaGuard.hasQuota(workspaceId) ? 1 : 0;
    }

    public int remainingBodyGenerationQuota(Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        return atomicQuotaGuard.hasQuota(workspaceId) ? 1 : 0;
    }

    @Transactional
    public void consumeBodyGenerationQuota(Long userId, String taskId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID refId = parseUuidOrNull(taskId);

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_BODY_GENERATION, refId, "body-generation");
        if (!result.isOk()) {
            throw new BusinessException(CODE_BODY_QUOTA_EXCEEDED, "body_quota_exceeded");
        }
        writeLegacyUsageLog(userId, workspaceId, ACTION_BODY_GENERATION, taskId);
    }

    @Transactional
    public void consumeWebSearchQuota(Long userId, String sessionId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID refId = parseUuidOrNull(sessionId);

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_WEB_SEARCH, refId, "web-search");
        if (!result.isOk()) {
            throw new BusinessException(CODE_WEB_SEARCH_QUOTA_EXCEEDED, "web_search_quota_exceeded");
        }
        writeLegacyUsageLog(userId, workspaceId, ACTION_WEB_SEARCH, sessionId);
    }

    // ========== 新 API ==========

    /**
     * 获取 workspace 当前配额快照。
     */
    public WorkspaceQuota getWorkspaceQuota(String workspaceId) {
        return atomicQuotaGuard.getQuota(workspaceId);
    }

    private int resolveDailyQuota(String workspaceId) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace != null && workspace.getDefaultQuota() != null) {
            return workspace.getDefaultQuota();
        }
        return appAgentProperties.getDailyMessageQuota();
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(40401, "user not found");
        }
        if (user.getDailyQuota() == null) {
            user.setDailyQuota(3);
        }
        return user;
    }

    /**
     * 把外部传入的关联 ID 解析成 UUID，解析不了就返回 {@code null}。
     * <p>
     * <b>为什么不能直接用 {@link UUID#fromString(String)}</b>：
     * {@code usage_ledger.ref_id} 是 UUID 列，但传入的关联 ID 并不总是 UUID——
     * 例如聊天会话 ID 形如 {@code sess_1a2b3c4d5e6f}（见
     * {@code ChatHistoryService#generateSessionId}）。直接调用会抛
     * {@code IllegalArgumentException: Invalid UUID string}，
     * 导致整个 Agent 对话链路 500。
     * <p>
     * refId 只用于台账关联，解析失败不影响配额扣减本身，因此降级为 {@code null}。
     */
    private static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 兼容旧 usage_log 表写入。
     */
    private void writeLegacyUsageLog(Long userId, String workspaceId, String action, String taskId) {
        try {
            UsageLog log = new UsageLog();
            log.setUserId(userId);
            log.setWorkspaceId(workspaceId);
            log.setAction(action);
            log.setTaskId(taskId);
            log.setCreatedAt(OffsetDateTime.now());
            usageLogMapper.insert(log);
        } catch (Exception ex) {
            // ledger 已写入，usage_log 失败不影响主流程
        }
    }
}
