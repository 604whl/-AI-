-- P0-4: 模板库 Template（三级范围：OFFICIAL / WORKSPACE / PERSONAL）

SET search_path TO app, kb, public;

-- 1. template 表：模板主表
CREATE TABLE IF NOT EXISTS app.template (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scope            VARCHAR(16) NOT NULL,  -- OFFICIAL / WORKSPACE / PERSONAL
    workspace_id     UUID        REFERENCES app.workspace(id) ON DELETE CASCADE,
    owner_user_id   UUID        REFERENCES app.users(id),
    category         VARCHAR(64) NOT NULL,
    scenario         VARCHAR(64),
    persona          VARCHAR(64),
    title            VARCHAR(255) NOT NULL,
    body_template    TEXT        NOT NULL,
    variables        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    -- 示例：["platform", "topic", "tone"]
    default_goal     VARCHAR(255),
    default_tone     VARCHAR(64),
    version          INT         NOT NULL DEFAULT 1,
    status           VARCHAR(16) NOT NULL DEFAULT 'active',  -- active / archived
    use_count        INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_scope CHECK (
        (scope = 'OFFICIAL' AND workspace_id IS NULL AND owner_user_id IS NULL) OR
        (scope = 'WORKSPACE' AND workspace_id IS NOT NULL) OR
        (scope = 'PERSONAL' AND owner_user_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_template_scope   ON app.template (scope, status);
CREATE INDEX IF NOT EXISTS idx_template_workspace ON app.template (workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_template_owner    ON app.template (owner_user_id, status);
CREATE INDEX IF NOT EXISTS idx_template_category  ON app.template (category);

-- 2. template_version 表：模板版本历史（快照）
CREATE TABLE IF NOT EXISTS app.template_version (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   UUID        NOT NULL REFERENCES app.template(id) ON DELETE CASCADE,
    version_no    INT         NOT NULL,
    snapshot      JSONB       NOT NULL,  -- 完整模板快照，含所有字段
    published_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_by  UUID        REFERENCES app.users(id),
    UNIQUE (template_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_tpl_ver_template ON app.template_version (template_id, version_no DESC);

-- 3. template_apply_log 表：模板应用记录（用于统计和追溯）
CREATE TABLE IF NOT EXISTS app.template_apply_log (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   UUID        NOT NULL REFERENCES app.template(id) ON DELETE CASCADE,
    asset_id      UUID        REFERENCES app.content_asset(id) ON DELETE SET NULL,
    workspace_id  UUID        NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES app.users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_apply_log_template ON app.template_apply_log (template_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apply_log_workspace ON app.template_apply_log (workspace_id, created_at DESC);
