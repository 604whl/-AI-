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
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 额度/用量统计（按 workspace 隔离）。
 * <p>
 * 设计目标：把"按 user 限制"升级为"按 workspace 限制"，让一个 owner 的多个 session
 * 或后续添加的团队成员能共享同一份配额。同时所有额度日志带 workspaceId 便于跨用户审计。
 */
@Service
@RequiredArgsConstructor
public class UserQuotaService {

    public static final String ACTION_ANALYSIS = "analysis";
    public static final String ACTION_AGENT_MESSAGE = "agent_message";
    public static final String ACTION_WEB_SEARCH = "web_search";
    public static final String ACTION_BODY_GENERATION = "body_generation";

    private static final int CODE_QUOTA_EXCEEDED = 42901;
    private static final int CODE_AGENT_QUOTA_EXCEEDED = 42904;
    private static final int CODE_WEB_SEARCH_QUOTA_EXCEEDED = 42903;
    private static final int CODE_BODY_QUOTA_EXCEEDED = 42905;

    private final UserMapper userMapper;
    private final UsageLogMapper usageLogMapper;
    private final WorkspaceMapper workspaceMapper;
    private final AppAgentProperties appAgentProperties;

    public int remainingAnalysisQuota(Long userId) {
        return getAnalysisUsage(userId).getRemaining();
    }

    public UserUsageVo getAnalysisUsage(Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        User user = requireUser(userId);
        int dailyQuota = resolveDailyQuota(workspaceId, user);
        int usedToday = usageLogMapper.countTodayByWorkspaceAndAction(workspaceId, ACTION_ANALYSIS);
        int remaining = Math.max(dailyQuota - usedToday, 0);
        return UserUsageVo.builder()
                .dailyQuota(dailyQuota)
                .usedToday(usedToday)
                .remaining(remaining)
                .build();
    }

    @Transactional
    public void consumeAnalysisQuota(Long userId, String taskId) {
        if (remainingAnalysisQuota(userId) <= 0) {
            throw new BusinessException(CODE_QUOTA_EXCEEDED, "quota_exceeded");
        }
        UsageLog log = new UsageLog();
        log.setUserId(userId);
        log.setWorkspaceId(WorkspaceGuards.requireCurrentWorkspaceId());
        log.setAction(ACTION_ANALYSIS);
        log.setTaskId(taskId);
        log.setCreatedAt(OffsetDateTime.now());
        usageLogMapper.insert(log);
    }

    public int remainingAgentMessageQuota(Long userId) {
        requireUser(userId);
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        int dailyQuota = appAgentProperties.getDailyMessageQuota();
        int usedToday = usageLogMapper.countTodayByWorkspaceAndAction(workspaceId, ACTION_AGENT_MESSAGE);
        return Math.max(dailyQuota - usedToday, 0);
    }

    @Transactional
    public void consumeAgentMessageQuota(Long userId, String sessionId) {
        if (remainingAgentMessageQuota(userId) <= 0) {
            throw new BusinessException(CODE_AGENT_QUOTA_EXCEEDED, "agent_quota_exceeded");
        }
        UsageLog log = new UsageLog();
        log.setUserId(userId);
        log.setWorkspaceId(WorkspaceGuards.requireCurrentWorkspaceId());
        log.setAction(ACTION_AGENT_MESSAGE);
        log.setTaskId(sessionId);
        log.setCreatedAt(OffsetDateTime.now());
        usageLogMapper.insert(log);
    }

    public int remainingWebSearchQuota(Long userId) {
        requireUser(userId);
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        int dailyQuota = appAgentProperties.getWebSearch().getDailyQuotaPerUser();
        int usedToday = usageLogMapper.countTodayByWorkspaceAndAction(workspaceId, ACTION_WEB_SEARCH);
        return Math.max(dailyQuota - usedToday, 0);
    }

    public int remainingBodyGenerationQuota(Long userId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        User user = requireUser(userId);
        int dailyQuota = resolveDailyQuota(workspaceId, user);
        int usedToday = usageLogMapper.countTodayByWorkspaceAndAction(workspaceId, ACTION_BODY_GENERATION);
        return Math.max(dailyQuota - usedToday, 0);
    }

    @Transactional
    public void consumeBodyGenerationQuota(Long userId, String taskId) {
        if (remainingBodyGenerationQuota(userId) <= 0) {
            throw new BusinessException(CODE_BODY_QUOTA_EXCEEDED, "body_quota_exceeded");
        }
        UsageLog log = new UsageLog();
        log.setUserId(userId);
        log.setWorkspaceId(WorkspaceGuards.requireCurrentWorkspaceId());
        log.setAction(ACTION_BODY_GENERATION);
        log.setTaskId(taskId);
        log.setCreatedAt(OffsetDateTime.now());
        usageLogMapper.insert(log);
    }

    @Transactional
    public void consumeWebSearchQuota(Long userId, String sessionId) {
        if (remainingWebSearchQuota(userId) <= 0) {
            throw new BusinessException(CODE_WEB_SEARCH_QUOTA_EXCEEDED, "web_search_quota_exceeded");
        }
        UsageLog log = new UsageLog();
        log.setUserId(userId);
        log.setWorkspaceId(WorkspaceGuards.requireCurrentWorkspaceId());
        log.setAction(ACTION_WEB_SEARCH);
        log.setTaskId(sessionId);
        log.setCreatedAt(OffsetDateTime.now());
        usageLogMapper.insert(log);
    }

    private int resolveDailyQuota(String workspaceId, User user) {
        // 优先 workspace 配置的 default_quota，回退到 user 配置
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace != null && workspace.getDefaultQuota() != null) {
            return workspace.getDefaultQuota();
        }
        if (user.getDailyQuota() == null) {
            return 3;
        }
        return user.getDailyQuota();
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
}