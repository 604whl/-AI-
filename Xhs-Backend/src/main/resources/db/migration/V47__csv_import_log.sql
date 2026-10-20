-- V47__csv_import_log.sql
-- CSV 导入日志表
CREATE TABLE IF NOT EXISTS app.csv_import_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app.users(id),
    workspace_id    UUID        NOT NULL,
    import_type     VARCHAR(32) NOT NULL,
    filename        VARCHAR(256),
    total_rows      INT,
    imported_rows   INT DEFAULT 0,
    skipped_rows    INT DEFAULT 0,
    error_rows      INT DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'completed',
    error_detail    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_csv_import_log_workspace_created
    ON app.csv_import_log (workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_csv_import_log_user_created
    ON app.csv_import_log (user_id, created_at DESC);