package com.shortvideoscripagent.xhsagentyunying.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Workspace 业务逻辑：建工作区、成员管理、权限校验。
 * <p>
 * 注册用户时调用 {@link #ensureDefaultWorkspace(Long)} 建一个 default workspace；
 * 每次请求由 {@link WorkspaceInterceptor} 把 workspaceId 写入 {@link WorkspaceContext}，
 * 业务方法从上下文读取并在 SQL 过滤中强制带上 {@code workspace_id}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final int CODE_NOT_FOUND = 40404;
    private static final int CODE_FORBIDDEN = 40301;

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserMapper userMapper;

    /**
     * 为新注册的用户自动创建 default workspace，并赋予 OWNER 角色。
     */
    @Transactional
    public Workspace ensureDefaultWorkspace(Long userId) {
        Workspace existing = workspaceMapper.selectOne(
                new LambdaQueryWrapper<Workspace>().eq(Workspace::getOwnerUserId, userId)
        );
        if (existing != null) {
            return existing;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CODE_NOT_FOUND, "user_not_found");
        }

        String workspaceId = generateWorkspaceId();
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName(buildDisplayName(user) + " 的工作区");
        workspace.setSlug("user-" + userId);
        workspace.setOwnerUserId(userId);
        workspace.setIndustry("general");
        workspace.setRegion("CN");
        workspace.setDefaultLocale("zh-CN");
        workspace.setDefaultQuota(user.getDailyQuota() == null ? 3 : user.getDailyQuota());
        workspace.setStatus("active");
        OffsetDateTime now = OffsetDateTime.now();
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceMapper.insert(workspace);

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(WorkspaceRole.OWNER.name());
        member.setJoinedAt(now);
        member.setLastActiveAt(now);
        member.setStatus("active");
        workspaceMemberMapper.insert(member);

        log.info("Created default workspace {} for user {}", workspaceId, userId);
        return workspace;
    }

    /**
     * 校验当前请求用户对 workspace 的访问权限，并把 workspaceId 写入上下文。
     */
    public WorkspaceRole resolveAndAuthorize(Long userId, String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_required");
        }
        WorkspaceMember member = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
                        .eq(WorkspaceMember::getStatus, "active")
        );
        if (member == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        WorkspaceContext.setWorkspaceId(workspaceId);
        return WorkspaceRole.parse(member.getRole());
    }

    public Workspace requireWorkspace(String workspaceId) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || !"active".equals(workspace.getStatus())) {
            throw new BusinessException(CODE_NOT_FOUND, "workspace_not_found_or_suspended");
        }
        return workspace;
    }

    public List<Workspace> listUserWorkspaces(Long userId) {
        return workspaceMapper.selectList(
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getOwnerUserId, userId)
                        .eq(Workspace::getStatus, "active")
        );
    }

    private String generateWorkspaceId() {
        // ws_ + 12 hex chars = 15 chars, 满足 VARCHAR(32) 主键约束
        return "ws_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildDisplayName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getEmail();
    }
}