package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-1: TaskQueueProducer 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TaskQueueProducerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private ObjectMapper objectMapper;
    private TaskQueueProperties properties;
    private TaskQueueProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        properties = new TaskQueueProperties();
        properties.setStreamKey("xhs:tasks:stream");
        properties.setMaxRetry(3);

        producer = new TaskQueueProducer(redisTemplate, objectMapper, properties);
    }

    @Test
    void enqueue_newKey_callsXadd() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1234567890-0"));

        String payloadJson = "{\"taskId\":\"task-001\",\"scenario\":\"draft\"}";

        producer.enqueue("ANALYSIS", "task-001", payloadJson, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapRecord<String, Object, Object>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps).add(captor.capture());

        MapRecord<String, Object, Object> record = captor.getValue();
        assertEquals("xhs:tasks:stream", record.getStream());
        assertEquals("task-001", record.getValue().get("taskId"));
        assertEquals("ANALYSIS", record.getValue().get("taskType"));
        assertEquals("1", record.getValue().get("attempt"));
    }

    @Test
    void scheduleRetry_schedulesWithCorrectAttempt() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1234567890-1"));

        producer.scheduleRetry("task-002", "ANALYSIS", "{}", 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapRecord<String, Object, Object>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps).add(captor.capture());

        assertEquals("task-002", captor.getValue().getValue().get("taskId"));
        assertEquals("2", captor.getValue().getValue().get("attempt"));
    }

    @Test
    void enqueue_outsideTransaction_callsXaddImmediately() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1234567890-2"));

        producer.enqueue("TITLE_GENERATION", "task-003", "{}", 1);

        verify(streamOps, times(1)).add(any());
    }
}
