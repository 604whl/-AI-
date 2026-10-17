-- P0-5: 导出记录表 export_log
-- 支持 MD / DOCX / ZIP / CSV 四种格式，记录导出状态、MinIO 对象、过期时间

SET search_path TO app, kb, public;

CREATE TABLE IF NOT EXISTS app.export_log (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES app.users(id),
    asset_id         UUID        REFERENCES app.content_asset(id) ON DELETE SET NULL,
    format           VARCHAR(16) NOT NULL,  -- md / docx / zip / csv
    status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    -- pending / processing / ready / failed / expired
    object_key       VARCHAR(512),
    file_size        BIGINT,
    expires_at       TIMESTAMPTZ,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at      TIMESTAMPTZ
);

-- workspace 级查询优化
CREATE INDEX IF NOT EXISTS idx_export_workspace_created
    ON app.export_log (workspace_id, created_at DESC);

-- 状态查询索引
CREATE INDEX IF NOT EXISTS idx_export_status
    ON app.export_log (status);
