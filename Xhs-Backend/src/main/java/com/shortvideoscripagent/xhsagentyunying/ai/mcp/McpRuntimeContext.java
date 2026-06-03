package com.shortvideoscripagent.xhsagentyunying.ai.mcp;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;

import java.util.Map;

/**
 * MCP 请求线程上下文。
 * <p>
 * MCP 工具调用由 Spring AI MCP Server 在独立 HTTP 线程中触发，无法像 Chat API 那样
 * 通过 {@code ChatSession} 传递 {@link ToolContext}。因此在 {@link McpAuthFilter}
 * 鉴权成功后，将 userId / sessionId / persona 写入 {@link ThreadLocal}，
 * 供 {@link McpAgentToolCallbackProvider} 在执行工具时读取。
 * </p>
 * <p>
 * 注意：必须在请求结束时调用 {@link #clear()}，避免线程池复用导致用户串号。
 * </p>
 */
public final class McpRuntimeContext {

    private static final ThreadLocal<Holder> CONTEXT = new ThreadLocal<>();

    private McpRuntimeContext() {
    }

    /**
     * 绑定当前 MCP 请求的调用者身份。
     *
     * @param userId    已通过 JWT 或 API Key 校验的用户 ID
     * @param sessionId 虚拟会话 ID，写入 chat/agent 审计日志
     * @param persona   运营人设（agency / mentor / senior）
     */
    public static void bind(Long userId, String sessionId, String persona) {
        CONTEXT.set(new Holder(userId, sessionId, persona));
    }

    /**
     * 读取当前线程的 MCP 上下文；未绑定时抛出 {@link IllegalStateException}。
     */
    public static Holder requireCurrent() {
        Holder holder = CONTEXT.get();
        if (holder == null) {
            throw new IllegalStateException("mcp_context_not_bound");
        }
        return holder;
    }

    /**
     * 转换为 Agent 层统一的 {@link ToolContext}（无草稿附件、无 linkedTaskId）。
     */
    public static ToolContext toToolContext() {
        Holder holder = requireCurrent();
        return new ToolContext(
                holder.userId(),
                holder.sessionId(),
                holder.persona(),
                null,
                Map.of()
        );
    }

    /** 请求结束时清理 ThreadLocal。 */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * MCP 调用者快照。
     *
     * @param userId    用户主键
     * @param sessionId 虚拟会话 ID
     * @param persona   人设
     */
    public record Holder(Long userId, String sessionId, String persona) {
    }
}
