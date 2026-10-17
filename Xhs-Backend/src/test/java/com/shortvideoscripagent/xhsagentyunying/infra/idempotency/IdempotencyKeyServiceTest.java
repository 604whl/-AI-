package com.shortvideoscripagent.xhsagentyunying.infra.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-1: IdempotencyKeyService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyKeyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IdempotencyKeyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyKeyService(jdbcTemplate, redisTemplate);
    }

    @Test
    void tryRegister_nullKey_returnsNewWithSameTaskId() {
        IdempotencyResult result = service.tryRegister(null, "task-001");
        assertEquals("task-001", result.getTaskId());
        assertTrue(result.isNew());
    }

    @Test
    void tryRegister_blankKey_returnsNewWithSameTaskId() {
        IdempotencyResult result = service.tryRegister("  ", "task-002");
        assertEquals("task-002", result.getTaskId());
        assertTrue(result.isNew());
    }

    @Test
    void tryRegister_newKey_registersAndReturnsNew() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("task-003"), eq(Duration.ofDays(1))))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                eq("SELECT created_at FROM app.analysis_task WHERE id = ?"),
                eq(OffsetDateTime.class),
                eq("task-003")
        )).thenReturn(OffsetDateTime.now());

        IdempotencyResult result = service.tryRegister("key-003", "task-003");

        assertEquals("task-003", result.getTaskId());
        assertTrue(result.isNew());
        verify(valueOperations).setIfAbsent(
                eq("xhs:idempotency:" + sha256("key-003")),
                eq("task-003"),
                eq(Duration.ofDays(1))
        );
        verify(jdbcTemplate).update(
                contains("INSERT INTO app.idempotency_key"),
                eq(sha256("key-003")),
                eq("task-003")
        );
    }

    @Test
    void tryRegister_duplicateKey_returnsExistingTaskId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(
                eq("SELECT task_id FROM app.idempotency_key WHERE key_hash = ?"),
                eq(String.class),
                eq(sha256("key-004"))
        )).thenReturn("existing-task-004");
        when(jdbcTemplate.queryForObject(
                eq("SELECT created_at FROM app.analysis_task WHERE id = ?"),
                eq(OffsetDateTime.class),
                eq("existing-task-004")
        )).thenReturn(OffsetDateTime.now());

        IdempotencyResult result = service.tryRegister("key-004", "task-004");

        assertEquals("existing-task-004", result.getTaskId());
        assertFalse(result.isNew());
    }

    @Test
    void tryRegister_redisConflict_concurrentInsert_ignoresAndReturnsNew() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("task-005"), eq(Duration.ofDays(1))))
                .thenReturn(true);
        // INSERT 时抛 DuplicateKeyException（另一线程已插入）
        doThrow(new DuplicateKeyException("duplicate"))
                .when(jdbcTemplate).update(anyString(), any(), any());
        when(jdbcTemplate.queryForObject(
                eq("SELECT created_at FROM app.analysis_task WHERE id = ?"),
                eq(OffsetDateTime.class),
                eq("task-005")
        )).thenReturn(OffsetDateTime.now());

        IdempotencyResult result = service.tryRegister("key-005", "task-005");

        // 仍然视为成功，因为 Redis SETNX 成功了
        assertEquals("task-005", result.getTaskId());
        assertTrue(result.isNew());
    }

    @Test
    void cleanupExpired_deletesOldRecords() {
        when(jdbcTemplate.update(
                eq("DELETE FROM app.idempotency_key WHERE created_at < NOW() - INTERVAL '1 day' * ?"),
                eq(90)
        )).thenReturn(5);

        int deleted = service.cleanupExpired(90);

        assertEquals(5, deleted);
        verify(jdbcTemplate).update(
                eq("DELETE FROM app.idempotency_key WHERE created_at < NOW() - INTERVAL '1 day' * ?"),
                eq(90)
        );
    }

    // ── helper ─────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
