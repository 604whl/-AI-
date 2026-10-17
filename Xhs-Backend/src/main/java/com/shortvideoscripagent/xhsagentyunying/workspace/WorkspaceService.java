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
import com.shortvideoscripagent.xhsagentyunying.workspace.dto.WorkspaceDtos;
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
    private static final int CODE_CONFLICT = 40904;
    private static final int CODE_BAD_REQUEST = 40001;

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserMapper userMapper;

    /**
     * 为新注册的用户自动创建 default workspace，并赋予 OWNER 角色。
     */
    @Transactional
    public Workspace ensureDefaultWorkspace(Long userId) {
        Workspace existing = findOwnedWorkspace(userId);
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

    /**
     * 列出当前用户作为 member 加入的所有 workspace。
     */
    public List<Workspace> listUserWorkspaces(Long userId) {
        // 收集当前用户作为 member 的 workspaceId
        List<WorkspaceMember> memberships = workspaceMemberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, userId)
                        .eq(WorkspaceMember::getStatus, "active")
        );
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<String> workspaceIds = memberships.stream().map(WorkspaceMember::getWorkspaceId).toList();
        return workspaceMapper.selectBatchIds(workspaceIds).stream()
                .filter(w -> "active".equals(w.getStatus()))
                .toList();
    }

    /**
     * 查询当前用户对指定 workspace 的角色（找不到返回 null）。
     */
    public WorkspaceRole findRole(Long userId, String workspaceId) {
        WorkspaceMember member = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
                        .eq(WorkspaceMember::getStatus, "active")
        );
        return member == null ? null : WorkspaceRole.parse(member.getRole());
    }

    @Transactional
    public Workspace createWorkspace(Long userId, WorkspaceDtos.CreateWorkspaceRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        String workspaceId = generateWorkspaceId();
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName(request.getName().trim());
        workspace.setSlug(request.getSlug() == null || request.getSlug().isBlank()
                ? ("ws-" + workspaceId.substring(3))
                : request.getSlug().trim());
        workspace.setOwnerUserId(userId);
        workspace.setIndustry(request.getIndustry() == null || request.getIndustry().isBlank()
                ? "general" : request.getIndustry().trim());
        workspace.setRegion(request.getRegion() == null || request.getRegion().isBlank()
                ? "CN" : request.getRegion().trim());
        workspace.setDefaultLocale(request.getDefaultLocale() == null || request.getDefaultLocale().isBlank()
                ? "zh-CN" : request.getDefaultLocale().trim());
        workspace.setDefaultQuota(request.getDefaultQuota() == null ? 3 : request.getDefaultQuota());
        workspace.setStatus("active");
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

        log.info("User {} created workspace {}", userId, workspaceId);
        return workspace;
    }

    public List<WorkspaceMember> listMembers(String workspaceId) {
        return workspaceMemberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .orderByAsc(WorkspaceMember::getJoinedAt)
        );
    }

    @Transactional
    public WorkspaceMember inviteMember(String workspaceId, WorkspaceDtos.InviteMemberRequest request) {
        User invitee = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail().trim().toLowerCase())
        );
        if (invitee == null) {
            throw new BusinessException(CODE_NOT_FOUND, "invitee_not_found");
        }
        // 已存在则更新角色
        WorkspaceMember existing = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, invitee.getId())
        );
        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null) {
            if ("removed".equals(existing.getStatus())) {
                existing.setStatus("active");
            }
            existing.setRole(WorkspaceRole.parse(request.getRole()).name());
            existing.setJoinedAt(now);
            existing.setLastActiveAt(now);
            workspaceMemberMapper.updateById(existing);
            return existing;
        }
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(invitee.getId());
        member.setRole(WorkspaceRole.parse(request.getRole()).name());
        member.setJoinedAt(now);
        member.setLastActiveAt(now);
        member.setStatus("active");
        workspaceMemberMapper.insert(member);
        return member;
    }

    @Transactional
    public WorkspaceMember updateMemberRole(String workspaceId, Long memberUserId, String roleStr) {
        if (memberUserId == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "userId_required");
        }
        WorkspaceRole role = WorkspaceRole.parse(roleStr);
        if (role == WorkspaceRole.OWNER) {
            throw new BusinessException(CODE_BAD_REQUEST, "cannot_grant_owner_via_api");
        }
        WorkspaceMember member = requireMember(workspaceId, memberUserId);
        if (WorkspaceRole.OWNER.name().equals(member.getRole())) {
            throw new BusinessException(CODE_CONFLICT, "cannot_modify_owner");
        }
        member.setRole(role.name());
        member.setLastActiveAt(OffsetDateTime.now());
        workspaceMemberMapper.updateById(member);
        return member;
    }

    @Transactional
    public void removeMember(String workspaceId, Long actingUserId, Long memberUserId) {
        if (actingUserId.equals(memberUserId)) {
            throw new BusinessException(CODE_BAD_REQUEST, "cannot_remove_self");
        }
        WorkspaceMember member = requireMember(workspaceId, memberUserId);
        if (WorkspaceRole.OWNER.name().equals(member.getRole())) {
            throw new BusinessException(CODE_CONFLICT, "cannot_remove_owner");
        }
        member.setStatus("removed");
        member.setLastActiveAt(OffsetDateTime.now());
        workspaceMemberMapper.updateById(member);
    }

    private WorkspaceMember requireMember(String workspaceId, Long userId) {
        WorkspaceMember member = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
        );
        if (member == null) {
            throw new BusinessException(CODE_NOT_FOUND, "member_not_found");
        }
        return member;
    }

    private Workspace findOwnedWorkspace(Long userId) {
        return workspaceMapper.selectOne(
                new LambdaQueryWrapper<Workspace>().eq(Workspace::getOwnerUserId, userId)
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

    /**
     * 获取 workspace 的第一个成员 userId（用于 API Key 场景）。
     */
    public Long getFirstMemberUserId(String workspaceId) {
        WorkspaceMember member = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .orderByAsc(WorkspaceMember::getJoinedAt)
                        .last("LIMIT 1")
        );
        return member != null ? member.getUserId() : null;
    }

    /**
     * 校验用户是否为 workspace 指定角色。
     *
     * @throws BusinessException (403) 无权限
     */
    public void requireWorkspaceMember(Long userId, String workspaceId, String... allowedRoles) {
        if (userId == null || workspaceId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
        WorkspaceMember member = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
        );
        if (member == null) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
        for (String role : allowedRoles) {
            if (role.equals(member.getRole())) {
                return;
            }
        }
        throw new BusinessException(CODE_FORBIDDEN, "forbidden");
    }
}