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
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);

        WorkspaceQuota quota = atomicQuotaGuard.getQuota(workspaceId);
        int dailyQuota = resolveDailyQuota(workspaceIdStr);
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
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        UUID refId = taskId != null ? UUID.fromString(taskId) : null;

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_ANALYSIS, refId, "task-created");
        if (!result.isOk()) {
            throw new QuotaExceededException();
        }

        // 兼容旧 usage_log
        writeLegacyUsageLog(userId, workspaceIdStr, ACTION_ANALYSIS, taskId);
    }

    public int remainingAgentMessageQuota(Long userId) {
        requireUser(userId);
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        return atomicQuotaGuard.hasQuota(workspaceId) ? 1 : 0;
    }

    @Transactional
    public void consumeAgentMessageQuota(Long userId, String sessionId) {
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        UUID refId = sessionId != null ? UUID.fromString(sessionId) : null;

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_AGENT_MESSAGE, refId, "agent-message");
        if (!result.isOk()) {
            throw new BusinessException(CODE_AGENT_QUOTA_EXCEEDED, "agent_quota_exceeded");
        }
        writeLegacyUsageLog(userId, workspaceIdStr, ACTION_AGENT_MESSAGE, sessionId);
    }

    public int remainingWebSearchQuota(Long userId) {
        requireUser(userId);
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        return atomicQuotaGuard.hasQuota(workspaceId) ? 1 : 0;
    }

    public int remainingBodyGenerationQuota(Long userId) {
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        return atomicQuotaGuard.hasQuota(workspaceId) ? 1 : 0;
    }

    @Transactional
    public void consumeBodyGenerationQuota(Long userId, String taskId) {
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        UUID refId = taskId != null ? UUID.fromString(taskId) : null;

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_BODY_GENERATION, refId, "body-generation");
        if (!result.isOk()) {
            throw new BusinessException(CODE_BODY_QUOTA_EXCEEDED, "body_quota_exceeded");
        }
        writeLegacyUsageLog(userId, workspaceIdStr, ACTION_BODY_GENERATION, taskId);
    }

    @Transactional
    public void consumeWebSearchQuota(Long userId, String sessionId) {
        String workspaceIdStr = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID workspaceId = UUID.fromString(workspaceIdStr);
        UUID refId = sessionId != null ? UUID.fromString(sessionId) : null;

        QuotaCheckResult result = atomicQuotaGuard.tryConsume(workspaceId, ACTION_WEB_SEARCH, refId, "web-search");
        if (!result.isOk()) {
            throw new BusinessException(CODE_WEB_SEARCH_QUOTA_EXCEEDED, "web_search_quota_exceeded");
        }
        writeLegacyUsageLog(userId, workspaceIdStr, ACTION_WEB_SEARCH, sessionId);
    }

    // ========== 新 API ==========

    /**
     * 获取 workspace 当前配额快照。
     */
    public WorkspaceQuota getWorkspaceQuota(UUID workspaceId) {
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
