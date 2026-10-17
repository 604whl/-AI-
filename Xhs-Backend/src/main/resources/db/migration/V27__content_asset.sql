-- P0-3: 内容资产 ContentAsset + 版本 + 多媒体 + workspace 隔离

SET search_path TO app, kb, public;

-- 1. content_asset 表：内容资产主表
CREATE TABLE IF NOT EXISTS app.content_asset (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     UUID        NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    owner_user_id   UUID        NOT NULL REFERENCES app.users(id),
    title           VARCHAR(255) NOT NULL,
    body            TEXT        NOT NULL DEFAULT '',
    persona         VARCHAR(64),
    scenario        VARCHAR(64),
    industry        VARCHAR(64),
    status          VARCHAR(32) NOT NULL DEFAULT 'draft',
    -- draft / in_review / approved / scheduled / published / archived
    source_task_id   UUID,
    platform        VARCHAR(32),
    account_id      VARCHAR(64),
    current_version INT         NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_asset_workspace ON app.content_asset (workspace_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_asset_status  ON app.content_asset (workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_asset_owner   ON app.content_asset (owner_user_id, created_at DESC);

-- 2. content_asset_version 表：内容版本历史
CREATE TABLE IF NOT EXISTS app.content_asset_version (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id    UUID        NOT NULL REFERENCES app.content_asset(id) ON DELETE CASCADE,
    version_no  INT         NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT        NOT NULL,
    edited_by   UUID        REFERENCES app.users(id),
    edit_reason VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (asset_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_asset_version_asset ON app.content_asset_version (asset_id, version_no DESC);

-- 3. content_asset_media 表：多媒体附件（封面/图片/视频）
CREATE TABLE IF NOT EXISTS app.content_asset_media (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id    UUID        NOT NULL REFERENCES app.content_asset(id) ON DELETE CASCADE,
    media_type  VARCHAR(32) NOT NULL,  -- cover / image / video
    object_key  VARCHAR(512) NOT NULL,
    role        VARCHAR(32) DEFAULT 'attachment',  -- cover / main / attachment
    sort_order  INT         NOT NULL DEFAULT 0,
    width       INT,
    height      INT,
    file_size   BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_asset_media_asset ON app.content_asset_media (asset_id, sort_order);
