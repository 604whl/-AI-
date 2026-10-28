-- S2-6 媒体资产管理：视频上传 + 缩略图 + 病毒扫描
CREATE TABLE IF NOT EXISTS app.media_asset (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    UUID        NOT NULL,
    user_id         BIGINT      NOT NULL REFERENCES app.users(id),
    asset_id        UUID,                   -- 关联的内容资产（可选）
    media_type      VARCHAR(16) NOT NULL,   -- IMAGE / VIDEO
    object_key      VARCHAR(512) NOT NULL,  -- MinIO/Local 对象路径
    filename        VARCHAR(256),
    file_size       BIGINT,
    content_type    VARCHAR(64),
    width           INT,
    height          INT,
    duration_sec    INT,                    -- 视频时长（秒）
    thumbnail_key   VARCHAR(512),            -- 缩略图 object key
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',  -- pending / ready / failed
    scan_status     VARCHAR(16) NOT NULL DEFAULT 'scanning', -- scanning / clean / infected
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_media_workspace ON app.media_asset(workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_media_asset_link ON app.media_asset(asset_id) WHERE asset_id IS NOT NULL;