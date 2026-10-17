-- ============================================================
-- V35：操作审计表（app.audit_event）
-- ============================================================
-- 记录关键写操作：workspace 成员变更、模板创建、审核流、API Key 签发、
-- 配额变更、计费事件、销毁流程。给公网合规/排障提供依据。
--
-- 设计原则：
--   - 不存敏感数据（密码、token、完整 PII），只存元数据 + JSONB 描述
--   - 按 workspace + created_at 双索引，便于按工作区/时间范围检索
--   - 不级联删除：workspace 销毁后审计仍保留 N 天
-- ============================================================

SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS app.audit_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    VARCHAR(32),
    actor_user_id   BIGINT,
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    resource_id     VARCHAR(128),
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip              VARCHAR(64),
    user_agent      VARCHAR(255),
    result          VARCHAR(16) NOT NULL DEFAULT 'success',
    error_code      INT,
    error_message   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_workspace_time
    ON app.audit_event(workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_actor_time
    ON app.audit_event(actor_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_action_time
    ON app.audit_event(action, created_at DESC);

-- 保留 365 天的运维手册建议（生产再配置定时清理）
COMMENT ON TABLE app.audit_event IS '操作审计：workspace/成员/模板/审核/计费等关键写操作。保留 365 天。';
