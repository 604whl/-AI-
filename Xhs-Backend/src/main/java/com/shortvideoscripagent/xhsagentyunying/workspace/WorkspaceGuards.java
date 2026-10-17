package com.shortvideoscripagent.xhsagentyunying.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 资源归属校验工具：在 service 层统一把"workspace + user"双重校验封装成静态助手。
 * <p>
 * 典型用法：
 * <pre>{@code
 * AnalysisTask task = WorkspaceGuards.requireOwnedTask(
 *     taskMapper, AnalysisTask::getId, AnalysisTask::getUserId, AnalysisTask::getWorkspaceId,
 *     id, userId
 * );
 * }</pre>
 */
@Component
public final class WorkspaceGuards {

    private WorkspaceGuards() {
    }

    /**
     * 返回当前请求的 workspaceId，未设置则抛错（由 WorkspaceInterceptor 负责写入）。
     */
    public static String requireCurrentWorkspaceId() {
        return WorkspaceContext.requireWorkspaceId();
    }

    /**
     * 校验实体同时满足：userId 匹配、workspaceId 匹配当前上下文。
     */
    public static <T> T requireOwned(
            T entity,
            java.util.function.Function<T, Long> userIdGetter,
            java.util.function.Function<T, String> workspaceIdGetter,
            Long userId,
            int notFoundCode,
            String notFoundMessage
    ) {
        if (entity == null) {
            throw new BusinessException(notFoundCode, notFoundMessage);
        }
        Long ownerId = userIdGetter.apply(entity);
        String entityWorkspace = workspaceIdGetter.apply(entity);
        String currentWorkspace = requireCurrentWorkspaceId();

        if (ownerId == null || !ownerId.equals(userId)) {
            // 不要直接说"无权访问"——给"未找到"避免泄露存在性
            throw new BusinessException(notFoundCode, notFoundMessage);
        }
        if (entityWorkspace == null || !entityWorkspace.equals(currentWorkspace)) {
            // 实体已存在但不属于当前 workspace——同样以"未找到"屏蔽
            throw new BusinessException(notFoundCode, notFoundMessage);
        }
        return entity;
    }

    /**
     * 给查询构造器追加 workspace 过滤。
     * <p>
     * 因为 MyBatis-Plus 的 {@code LambdaQueryWrapper#eq(SFunction, Object)} 需要 {@link com.baomidou.mybatisplus.core.toolkit.support.SFunction}，
     * 这里保留为 generic 类型，调用方负责传入正确的 SFunction。
     */
    public static <T> LambdaQueryWrapper<T> applyWorkspaceFilter(
            LambdaQueryWrapper<T> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> workspaceIdGetter,
            String workspaceId
    ) {
        return wrapper.eq(workspaceIdGetter, workspaceId);
    }
}