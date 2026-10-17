package com.shortvideoscripagent.xhsagentyunying.workspace;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 启动期保证：每个用户至少有一个 workspace。
 * <p>
 * 设计目的：
 * - 老系统迁移后无 workspace 的孤儿用户，需要 lazy 补齐；
 * - 单租户期间（个人体验）保证 user 永远有归属。
 */
@Service
@RequiredArgsConstructor
public class WorkspaceBootstrap {

    private final WorkspaceService workspaceService;
    private final UserMapper userMapper;

    /**
     * 返回用户的 default workspace（没有就创建）。
     */
    @Transactional
    public Workspace getOrCreateDefault(Long userId) {
        List<Workspace> workspaces = workspaceService.listUserWorkspaces(userId);
        if (!workspaces.isEmpty()) {
            return workspaces.get(0);
        }
        return workspaceService.ensureDefaultWorkspace(userId);
    }
}