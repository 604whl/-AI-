package com.shortvideoscripagent.xhsagentyunying.workspace;

/**
 * 工作区角色枚举。
 * <p>
 * 权限层级：{@link #OWNER} > {@link #ADMIN} > {@link #EDITOR} > {@link #REVIEWER} > {@link #VIEWER}。
 * 任何资源操作前调用 {@link #atLeast(Role)} 判断权限。
 */
public enum WorkspaceRole {
    OWNER(5),
    ADMIN(4),
    EDITOR(3),
    REVIEWER(2),
    VIEWER(1);

    private final int rank;

    WorkspaceRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(WorkspaceRole required) {
        return this.rank >= required.rank;
    }

    public static WorkspaceRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return VIEWER;
        }
        try {
            return WorkspaceRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return VIEWER;
        }
    }
}