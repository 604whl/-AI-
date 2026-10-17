-- P0-1: 持久化任务队列 + 幂等 + 死信
-- 新增字段到 analysis_task + 新建 task_dlq + 新建 idempotency_key 表

SET search_path TO app, kb, public;

-- 1. analysis_task 新增字段
ALTER TABLE app.analysis_task
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS started_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS finished_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retry_count       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dead_letter_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS worker_id         VARCHAR(64);

-- idempotency_key 唯一索引（允许 NULL，允许多个 NULL 并存）
CREATE UNIQUE INDEX IF NOT EXISTS idx_analysis_task_idempotency_key
    ON app.analysis_task (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 2. 任务状态兼容映射注释
-- 旧 status -> 新状态机
-- pending   -> queued
-- processing -> running
-- completed  -> succeeded
-- failed    -> failed
-- 新增: cancelled, dead_letter

-- 3. 死信队列表
CREATE TABLE IF NOT EXISTS app.task_dlq (
    id                BIGSERIAL PRIMARY KEY,
    original_task_id  VARCHAR(64) NOT NULL,
    task_type         VARCHAR(32) NOT NULL,
    payload           JSONB       NOT NULL,
    error_message     TEXT,
    error_code        INT,
    retry_count       INT         NOT NULL DEFAULT 0,
    failed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_dlq_original_task_id ON app.task_dlq (original_task_id);
CREATE INDEX IF NOT EXISTS idx_task_dlq_task_type       ON app.task_dlq (task_type);
CREATE INDEX IF NOT EXISTS idx_task_dlq_failed_at       ON app.task_dlq (failed_at DESC);

-- 4. 幂等键表（用于 Redis SETNX + DB 双层去重）
CREATE TABLE IF NOT EXISTS app.idempotency_key (
    key_hash   CHAR(64)      PRIMARY KEY,
    task_id    VARCHAR(64)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 90 天后清理（由 TaskRecoveryService 或定时任务 Sweeper 处理）
CREATE INDEX IF NOT EXISTS idx_idempotency_key_created_at ON app.idempotency_key (created_at);
