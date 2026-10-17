package com.shortvideoscripagent.xhsagentyunying.common.api;

/**
 * 工作区线程上下文（与 RequestContext 配合使用）。
 * <p>
 * 任何需要"按 workspace 隔离"的服务方法都应该从 {@link #getWorkspaceId()} 读取
 * 当前请求归属的 workspace，并在 SQL 过滤中强制带上 {@code workspace_id = ?}。
 */
public final class WorkspaceContext {

    private static final ThreadLocal<String> WORKSPACE_ID = new ThreadLocal<>();

    private WorkspaceContext() {
    }

    public static void setWorkspaceId(String workspaceId) {
        WORKSPACE_ID.set(workspaceId);
    }

    public static String getWorkspaceId() {
        return WORKSPACE_ID.get();
    }

    public static String requireWorkspaceId() {
        String id = WORKSPACE_ID.get();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "Workspace context missing. 请求未携带 X-Workspace-Id 或当前用户未加入任何 workspace。"
            );
        }
        return id;
    }

    public static void clear() {
        WORKSPACE_ID.remove();
    }
}