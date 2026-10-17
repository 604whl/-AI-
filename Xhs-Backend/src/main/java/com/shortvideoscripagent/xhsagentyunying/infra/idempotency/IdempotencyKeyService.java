package com.shortvideoscripagent.xhsagentyunying.infra.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

/**
 * P0-1: 幂等键服务。
 * <p>
 * 双层去重：Redis SETNX（快速拦截）+ 数据库 idempotency_key 表（持久化兜底）。
 * </p>
 * 策略：
 * <ol>
 *   <li>计算 keyHash = SHA-256(idempotencyKey)</li>
 *   <li>Redis SETNX SET key="xhs:idempotency:{keyHash}" NX EX 86400（24h）</li>
 *   <li>成功：INSERT app.idempotency_key，返回 isNew=true</li>
 *   <li>失败：SELECT task_id WHERE key_hash=?，返回已有 taskId</li>
 * </ol>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class IdempotencyKeyService {

    private static final String REDIS_KEY_PREFIX = "xhs:idempotency:";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public IdempotencyKeyService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public IdempotencyKeyService() {
        // default for testing (no-arg constructor)
        this(null, null);
    }

    /**
     * 尝试注册幂等键。
     *
     * @param idempotencyKey 客户端传入的幂等键（通常为 UUID）
     * @param taskId         本次请求关联的数据库任务 ID
     * @return 幂等结果，包含最终 taskId 和是否新建
     */
    public IdempotencyResult tryRegister(String idempotencyKey, String taskId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return IdempotencyResult.builder()
                    .taskId(taskId)
                    .isNew(true)
                    .createdAt(OffsetDateTime.now())
                    .build();
        }

        String keyHash = sha256(idempotencyKey);
        String redisKey = REDIS_KEY_PREFIX + keyHash;
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        // Step 1: Redis SETNX（原子操作，快速拦截重复请求）
        Boolean acquired = ops.setIfAbsent(redisKey, taskId, java.time.Duration.ofDays(1));

        if (Boolean.TRUE.equals(acquired)) {
            // Step 2a: 成功抢到锁，写入数据库
            try {
                jdbcTemplate.update(
                        "INSERT INTO app.idempotency_key (key_hash, task_id, created_at) VALUES (?, ?, NOW())",
                        keyHash, taskId
                );
            } catch (DuplicateKeyException ex) {
                // 并发情况：Redis SETNX 成功后 INSERT 失败（另一线程已插入）
                // 忽略，视为命中已有
                log.debug("Concurrent idempotency key registration: {}", idempotencyKey);
            }
            log.debug("Idempotency key registered: {} -> {}", idempotencyKey, taskId);
            OffsetDateTime createdAt = findTaskCreatedAt(taskId);
            return IdempotencyResult.builder()
                    .taskId(taskId)
                    .isNew(true)
                    .createdAt(createdAt)
                    .build();
        }

        // Step 2b: Redis 已被占用，查数据库获取已有 taskId
        String existingTaskId = findExistingTaskId(keyHash);
        if (existingTaskId != null) {
            log.debug("Idempotency key hit: {} -> {}", idempotencyKey, existingTaskId);
            OffsetDateTime createdAt = findTaskCreatedAt(existingTaskId);
            return IdempotencyResult.builder()
                    .taskId(existingTaskId)
                    .isNew(false)
                    .createdAt(createdAt)
                    .build();
        }

        // 边缘情况：Redis 有值但 DB 查不到，直接信任 Redis
        String cachedTaskId = ops.get(redisKey);
        return IdempotencyResult.builder()
                .taskId(cachedTaskId != null ? cachedTaskId : taskId)
                .isNew(cachedTaskId == null)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    /**
     * 反查幂等键对应的 taskId。
     */
    public String findTaskIdByKeyHash(String keyHash) {
        return findExistingTaskId(keyHash);
    }

    /**
     * 清理过期幂等键（定时 Sweeper 调用）。
     *
     * @param ttlDays 保留天数
     * @return 清理记录数
     */
    public int cleanupExpired(int ttlDays) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM app.idempotency_key WHERE created_at < NOW() - INTERVAL '1 day' * ?",
                ttlDays
        );
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency keys (ttl={}d)", deleted, ttlDays);
        }
        return deleted;
    }

    // ── private ─────────────────────────────────────────────────────────────

    private String findExistingTaskId(String keyHash) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT task_id FROM app.idempotency_key WHERE key_hash = ?",
                    String.class,
                    keyHash
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private OffsetDateTime findTaskCreatedAt(String taskId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT created_at FROM app.analysis_task WHERE id = ?",
                    OffsetDateTime.class,
                    taskId
            );
        } catch (Exception ex) {
            return OffsetDateTime.now();
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
