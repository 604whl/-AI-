package com.shortvideoscripagent.xhsagentyunying.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserProfileVo user;
    /** 当前用户已激活的 workspace 列表（前端用来切换工作区） */
    private List<WorkspaceVo> workspaces;
    /** 默认 workspaceId，前端启动时塞到 X-Workspace-Id */
    private String defaultWorkspaceId;
}
