-- =====================================================================
-- V66：阶段 4 S4-4 订阅写操作幂等键表（修复 P3-4 / P3-5）
--
-- 背景：
--   app.subscription.idempotency_key 只有一列，且带唯一索引
--   (workspace_id, idempotency_key)。订阅行上会先后发生多种写操作：
--     subscribe / trial / cancel / change-plan / admin-adjust
--   早期实现把「原始键」写进这一列，SQL 守卫是 idempotency_key IS NULL，
--   于是先到的操作占住这一列，后到的操作永远绑不上：
--     - subscribe 写入 K1 后，cancel 想绑 K2 时 UPDATE 影响 0 行，
--       K2 从未落库 -> 同一 K2 重复提交不会被识别为重放；
--     - K1 也因此在 cancel 之后彻底丢失。
--   P3-5：管理端 adminAdjust 的 Idempotency-Key 被完全忽略，
--   extendDays 以「当前 current_period_end」为基准累加，
--   重复提交会重复叠加延期天数，误操作不可撤销。
--
-- 方案：
--   新建 app.subscription_write_idempotency 表，
--   主键 = (workspace_id, scope, idempotency_key)，
--   scope 区分操作种类（subscribe / trial / cancel / change_plan / admin_adjust），
--   不同 scope 的同一个原始键互不抢占，各操作都能独立识别重放。
--   resource_id 记录被作用的订阅行 id，便于回溯与定向判定。
--
-- 幂等判定：INSERT ... ON CONFLICT DO NOTHING
--   返回 1 = 本次首次写入（真正执行）；0 = 已存在（幂等重放，直接返回既有结果）。
--   使用 ON CONFLICT 而不是 catch DuplicateKeyException：
--   PostgreSQL 中唯一冲突会把当前事务置为 aborted，
--   之后任何 SQL 都会抛 current transaction is aborted，
--   导致后续的 SELECT 回读也失败（与 V65 dedupe_key 同一教训）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS app.subscription_write_idempotency (
    workspace_id    VARCHAR(32)  NOT NULL,
    scope           VARCHAR(32)  NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    resource_id     UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_subscription_write_idempotency
        PRIMARY KEY (workspace_id, scope, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_subscription_write_idempotency_created
    ON app.subscription_write_idempotency (created_at);

COMMENT ON TABLE app.subscription_write_idempotency IS
    '阶段4 订阅写操作幂等键：按 (workspace_id, scope, idempotency_key) 唯一，scope 区分操作种类';
COMMENT ON COLUMN app.subscription_write_idempotency.scope IS
    '操作作用域：subscribe / trial / cancel / change_plan / admin_adjust';
COMMENT ON COLUMN app.subscription_write_idempotency.resource_id IS
    '被本幂等键作用的订阅行 id（app.subscription.id），用于回溯';