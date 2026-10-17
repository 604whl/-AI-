package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

/**
 * P0-1: 任务处理器接口。
 * 所有任务类型必须实现此接口，由 {@link TaskRegistry} 统一路由调度。
 */
public interface TaskHandler {

    /**
     * 任务类型标识，与 Redis Stream 消息中的 taskType 对应。
     */
    String taskType();

    /**
     * 处理任务。
     * <ul>
     *   <li>进入时更新状态为 running</li>
     *   <li>成功时更新状态为 completed</li>
     *   <li>失败时抛出异常，由 {@link TaskQueueConsumer} 捕获并重试</li>
     *   <li>重试超过 maxRetry 后由 DeadLetterQueue 入 DLQ</li>
     * </ul>
     *
     * @param taskId  数据库中的任务 ID
     * @param payload 任务载荷（JSON 反序列化的 Map）
     * @throws Exception 处理失败时抛出
     */
    void handle(String taskId, java.util.Map<String, Object> payload) throws Exception;
}
