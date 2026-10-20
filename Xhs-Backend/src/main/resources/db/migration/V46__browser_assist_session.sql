-- V46__browser_assist_session.sql
-- S2-4: 浏览器辅助会话表（会话生命周期管理）

CREATE TABLE IF NOT EXISTS app.browser_assist_session (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT      NOT NULL REFERENCES app.users(id),
    workspace_id    UUID        NOT NULL,
    platform        VARCHAR(32) NOT NULL, -- XIAOHONGSHU / DOUYIN / WEIBO / BILIBILI
    account_label   VARCHAR(128),          -- 用户自定义账号标签
    status          VARCHAR(32) NOT NULL DEFAULT 'created',
    -- status: created → active → filled → confirmed → closed / expired / failed
    asset_id        UUID,                  -- 关联的内容资产
    cookie_encrypted TEXT,                 -- AES-GCM 加密存储的 Cookie（base64）
    cookie_expires_at TIMESTAMPTZ,        -- Cookie 过期时间（24h 后）
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT browser_session_status_check CHECK (status IN
        ('created','active','filled','confirmed','closed','expired','failed'))
);

CREATE INDEX IF NOT EXISTS idx_browser_session_user ON app.browser_assist_session(user_id, status);
CREATE INDEX IF NOT EXISTS idx_browser_session_workspace ON app.browser_assist_session(workspace_id, created_at DESC);