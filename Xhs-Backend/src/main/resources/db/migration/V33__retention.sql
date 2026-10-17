-- P0-9: 数据保留 + 租户销毁 + 个人数据导出

SET search_path TO app, kb, public;

-- ============================================================
-- 1. user 表新增字段：软删除 + 30 天定时销毁
-- ============================================================
ALTER TABLE app.users ADD COLUMN IF NOT EXISTS scheduled_deletion_at TIMESTAMPTZ;
ALTER TABLE app.users ADD COLUMN IF NOT EXISTS deletion_status VARCHAR(16) NOT NULL DEFAULT 'active';
-- active / scheduled / cancelled / deleted
COMMENT ON COLUMN app.users.deletion_status IS '账号删除状态: active=正常, scheduled=待删除, cancelled=已取消, deleted=已删除';
ALTER TABLE app.users ADD COLUMN IF NOT EXISTS deletion_confirmed_at TIMESTAMPTZ;
ALTER TABLE app.users ADD COLUMN IF NOT EXISTS deletion_request_ip VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_user_scheduled_deletion ON app.users(scheduled_deletion_at)
    WHERE deletion_status = 'scheduled';

-- ============================================================
-- 2. workspace 销毁记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.workspace_destruction (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    VARCHAR(32) NOT NULL,
    requested_by    BIGINT      NOT NULL REFERENCES app.users(id),
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirm_phrase  VARCHAR(64) NOT NULL,  -- 必须等于 "DELETE"
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',
    -- pending / confirmed / cancelled / executed
    execute_at      TIMESTAMPTZ,  -- confirmed 后 +7 天
    confirmed_at     TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancelled_by    BIGINT REFERENCES app.users(id),
    executed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_workspace_destruction_workspace ON app.workspace_destruction(workspace_id);
CREATE INDEX IF NOT EXISTS idx_workspace_destruction_execute ON app.workspace_destruction(execute_at)
    WHERE status = 'confirmed';

-- ============================================================
-- 3. 数据保留策略表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.data_retention_policy (
    workspace_id              VARCHAR(32) PRIMARY KEY,
    asset_retention_days      INT         NOT NULL DEFAULT 365,
    log_retention_days        INT         NOT NULL DEFAULT 90,
    media_retention_days      INT         NOT NULL DEFAULT 365,
    export_retention_days     INT         NOT NULL DEFAULT 7,
    chat_session_retention_days INT      NOT NULL DEFAULT 180,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by               BIGINT REFERENCES app.users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 4. 个人数据导出记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.user_data_export (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       BIGINT      NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    format        VARCHAR(16) NOT NULL DEFAULT 'zip',
    status        VARCHAR(16) NOT NULL DEFAULT 'pending',
    -- pending / processing / ready / expired / failed
    object_key    VARCHAR(512),
    file_size     BIGINT,
    expires_at    TIMESTAMPTZ,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_user_data_export_user    ON app.user_data_export(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_data_export_expires ON app.user_data_export(expires_at) WHERE status = 'ready';
