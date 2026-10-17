package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P0-1: 任务注册表。
 * <p>
 * 管理 taskType -> TaskHandler 的路由表，在 Spring 容器初始化完成后自动注册。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TaskRegistry {

    private static final int CODE_NOT_FOUND = 40401;

    private final List<TaskHandler> handlers;
    private final Map<String, TaskHandler> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (TaskHandler handler : handlers) {
            String taskType = handler.taskType();
            if (registry.putIfAbsent(taskType, handler) != null) {
                log.warn("Duplicate TaskHandler registered for taskType={}: {}",
                        taskType, handler.getClass().getName());
            } else {
                log.info("TaskHandler registered: {} -> {}",
                        taskType, handler.getClass().getSimpleName());
            }
        }
    }

    /**
     * 根据 taskType 获取对应的处理器。
     *
     * @throws BusinessException (40401) 当找不到对应 handler 时
     */
    public TaskHandler getHandler(String taskType) {
        TaskHandler handler = registry.get(taskType);
        if (handler == null) {
            throw new BusinessException(CODE_NOT_FOUND, "task_handler_not_found: " + taskType);
        }
        return handler;
    }

    /**
     * 判断 taskType 是否已注册。
     */
    public boolean hasHandler(String taskType) {
        return registry.containsKey(taskType);
    }

    /**
     * 获取所有已注册的 taskType。
     */
    public List<String> registeredTypes() {
        return List.copyOf(registry.keySet());
    }
}
