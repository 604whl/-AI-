-- API Key 实体：workspace 级 API Key，用于 MCP/CLI 集成
-- 参考 Stripe 设计：前缀 xhs_sk_ + 24 字符 base62

SET search_path TO app, kb, public;

CREATE TABLE app.api_key (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    VARCHAR(64) NOT NULL REFERENCES app.workspace(id),
    -- 存储 SHA-256 哈希，不存明文
    key_hash        VARCHAR(64) NOT NULL,
    -- 前缀：xhs_sk_（便于用户识别 + 安全展示）
    prefix          VARCHAR(16) NOT NULL DEFAULT 'xhs_sk_',
    -- 可选名称：用于识别 key 用途
    name            VARCHAR(128),
    -- 权限范围：JSONB 数组
    scopes          JSONB NOT NULL DEFAULT '["chat","analysis"]'::jsonb,
    -- 创建人
    created_by      BIGINT NOT NULL REFERENCES app.users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT api_key_workspace_fk FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE
);

CREATE INDEX idx_api_key_workspace ON app.api_key (workspace_id);
CREATE INDEX idx_api_key_hash ON app.api_key (key_hash) WHERE revoked_at IS NULL;
CREATE INDEX idx_api_key_created_by ON app.api_key (created_by);
