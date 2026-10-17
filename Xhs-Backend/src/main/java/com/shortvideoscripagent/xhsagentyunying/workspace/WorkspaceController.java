package com.shortvideoscripagent.xhsagentyunying.workspace;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import com.shortvideoscripagent.xhsagentyunying.workspace.dto.WorkspaceDtos;
import com.shortvideoscripagent.xhsagentyunying.workspace.dto.WorkspaceDtosV2;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.Audited;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workspace 管理接口。
 * <p>
 * 前缀 {@code /api/v1/workspaces}，登录后用户可访问。
 * - {@code GET    /}：列出当前用户作为 member 的所有 workspace。
 * - {@code POST   /}：创建新 workspace（自己成为 OWNER）。
 * - {@code GET    /{id}}：查看 workspace 详情（必须为 member）。
 * - {@code GET    /{id}/members}：列出成员（必须为 member）。
 * - {@code POST   /{id}/members}：邀请成员（OWNER/ADMIN）。
 * - {@code PATCH  /{id}/members/{userId}}：更新成员角色（OWNER/ADMIN）。
 * - {@code DELETE /{id}/members/{userId}}：移除成员（OWNER/ADMIN）。
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private static final int CODE_FORBIDDEN = 40301;

    private final WorkspaceService workspaceService;
    private final UserMapper userMapper;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMine() {
        Long userId = RequestContext.requireUserId();
        List<Workspace> workspaces = workspaceService.listUserWorkspaces(userId);
        List<Map<String, Object>> items = workspaces.stream()
                .map(w -> {
                    WorkspaceRole role = workspaceService.findRole(userId, w.getId());
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", w.getId());
                    m.put("name", w.getName());
                    m.put("slug", w.getSlug());
                    m.put("industry", w.getIndustry());
                    m.put("region", w.getRegion());
                    m.put("defaultLocale", w.getDefaultLocale());
                    m.put("defaultQuota", w.getDefaultQuota());
                    m.put("ownerUserId", w.getOwnerUserId());
                    m.put("role", role == null ? null : role.name());
                    return m;
                })
                .toList();
        return ApiResponse.ok(items);
    }

    @PostMapping
    @Audited(action = "workspace.create", resourceType = "workspace", resourceId = "#request.name")
    public ApiResponse<WorkspaceDtosV2.WorkspaceDetailVo> create(@Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request) {
        Long userId = RequestContext.requireUserId();
        Workspace workspace = workspaceService.createWorkspace(userId, request);
        return ApiResponse.ok(WorkspaceDtosV2.WorkspaceDetailVo.from(workspace, WorkspaceRole.OWNER.name()));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkspaceDtosV2.WorkspaceDetailVo> detail(@PathVariable("id") String workspaceId) {
        Long userId = RequestContext.requireUserId();
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        Workspace workspace = workspaceService.requireWorkspace(workspaceId);
        return ApiResponse.ok(WorkspaceDtosV2.WorkspaceDetailVo.from(workspace, role.name()));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<WorkspaceDtosV2.MemberVo>> listMembers(@PathVariable("id") String workspaceId) {
        Long userId = RequestContext.requireUserId();
        requireMember(userId, workspaceId);
        List<WorkspaceMember> members = workspaceService.listMembers(workspaceId);
        List<WorkspaceDtosV2.MemberVo> items = members.stream()
                .map(m -> {
                    User u = userMapper.selectById(m.getUserId());
                    return WorkspaceDtosV2.MemberVo.from(m, u);
                })
                .toList();
        return ApiResponse.ok(items);
    }

    @PostMapping("/{id}/members")
    @Audited(action = "workspace.member.invite", resourceType = "workspace_member", resourceId = "#workspaceId + ':' + #request.userId")
    public ApiResponse<WorkspaceDtosV2.MemberVo> inviteMember(
            @PathVariable("id") String workspaceId,
            @Valid @RequestBody WorkspaceDtos.InviteMemberRequest request
    ) {
        Long userId = RequestContext.requireUserId();
        requireRoleAtLeast(userId, workspaceId, WorkspaceRole.ADMIN);
        WorkspaceMember member = workspaceService.inviteMember(workspaceId, request);
        User u = userMapper.selectById(member.getUserId());
        return ApiResponse.ok(WorkspaceDtosV2.MemberVo.from(member, u));
    }

    @PatchMapping("/{id}/members/{userId}")
    @Audited(action = "workspace.member.role_update", resourceType = "workspace_member", resourceId = "#workspaceId + ':' + #memberUserId")
    public ApiResponse<WorkspaceDtosV2.MemberVo> updateMemberRole(
            @PathVariable("id") String workspaceId,
            @PathVariable("userId") Long memberUserId,
            @Valid @RequestBody WorkspaceDtos.UpdateMemberRoleRequest request
    ) {
        Long actingUserId = RequestContext.requireUserId();
        requireRoleAtLeast(actingUserId, workspaceId, WorkspaceRole.ADMIN);
        WorkspaceMember member = workspaceService.updateMemberRole(workspaceId, memberUserId, request.getRole());
        User u = userMapper.selectById(member.getUserId());
        return ApiResponse.ok(WorkspaceDtosV2.MemberVo.from(member, u));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Audited(action = "workspace.member.remove", resourceType = "workspace_member", resourceId = "#workspaceId + ':' + #memberUserId")
    public ApiResponse<Void> removeMember(
            @PathVariable("id") String workspaceId,
            @PathVariable("userId") Long memberUserId
    ) {
        Long actingUserId = RequestContext.requireUserId();
        requireRoleAtLeast(actingUserId, workspaceId, WorkspaceRole.ADMIN);
        workspaceService.removeMember(workspaceId, actingUserId, memberUserId);
        return ApiResponse.ok();
    }

    /**
     * PATCH 工作区设置（行业/区域/语言/配额/名称）。
     * <p>仅 OWNER/ADMIN 可调用。null 字段不会被修改。</p>
     */
    @PatchMapping("/{id}")
    @Audited(action = "workspace.update", resourceType = "workspace", resourceId = "#workspaceId")
    public ApiResponse<WorkspaceDtosV2.WorkspaceDetailVo> update(
            @PathVariable("id") String workspaceId,
            @Valid @RequestBody WorkspaceDtos.UpdateWorkspaceRequest request
    ) {
        Long userId = RequestContext.requireUserId();
        requireRoleAtLeast(userId, workspaceId, WorkspaceRole.ADMIN);
        Workspace workspace = workspaceService.updateWorkspace(workspaceId, request);
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        return ApiResponse.ok(WorkspaceDtosV2.WorkspaceDetailVo.from(workspace, role == null ? null : role.name()));
    }

    private void requireMember(Long userId, String workspaceId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
    }

    private void requireRoleAtLeast(Long userId, String workspaceId, WorkspaceRole required) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(required)) {
            throw new BusinessException(CODE_FORBIDDEN, "insufficient_role");
        }
    }
}