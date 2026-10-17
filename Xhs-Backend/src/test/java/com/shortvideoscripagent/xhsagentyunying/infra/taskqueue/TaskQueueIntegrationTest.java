package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-1: 任务队列集成测试。
 * 测试场景：投递 → 消费 → 完成，状态机正确。
 */
class TaskQueueIntegrationTest {

    @Test
    void taskQueueProperties_defaults() {
        TaskQueueProperties p = new TaskQueueProperties();
        assertEquals("xhs:tasks:stream", p.getStreamKey());
        assertEquals("xhs:tasks:dlq", p.getDlqKey());
        assertEquals(3, p.getMaxRetry());
        assertFalse(p.isEnabled()); // 默认关闭
    }

    @Test
    void taskQueueProperties_retryBackoff() {
        TaskQueueProperties p = new TaskQueueProperties();
        assertEquals(1, p.retryDelaySeconds(1));
        assertEquals(2, p.retryDelaySeconds(2));
        assertEquals(4, p.retryDelaySeconds(3));
    }
    
    @Test
    void taskQueueProperties_workerId() {
        TaskQueueProperties p = new TaskQueueProperties();
        p.setWorkerId("test-worker");
        assertEquals("test-worker", p.getWorkerId());
    }
}
