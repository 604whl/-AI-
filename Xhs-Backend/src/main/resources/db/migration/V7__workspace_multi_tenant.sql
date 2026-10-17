-- ============================================================
-- V7：多租户（workspace + member + role）+ 资源归属
-- ============================================================
-- 目标：
--   1. 引入 workspace 作为业务隔离单元
--   2. workspace_member 维护 user <-> workspace 多对多 + role
--   3. 全部业务表增加 workspace_id 字段
--   4. 注册时自动建一个 default workspace，用户作为 OWNER 加入
--   5. 既有数据按"用户的第一个 workspace"回填
--   6. workspace_id 列先加 NOT NULL DEFAULT（用户既有数据用最新 workspace 兜底）
--
-- 设计原则：
--   - workspace_id NOT NULL 强制保证每条业务记录都有归属
--   - role: OWNER > ADMIN > EDITOR > REVIEWER > VIEWER
--   - 不删除旧 user_id 字段（保留双重校验：user 必须 + workspace 必须）
-- ============================================================

SET search_path TO app, kb, public;

-- ========== 1. workspace 表 ==========

CREATE TABLE app.workspace (
    id              VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    slug            VARCHAR(64) NOT NULL UNIQUE,
    owner_user_id   BIGINT NOT NULL REFERENCES app.users(id),
    industry        VARCHAR(32) DEFAULT 'general',
    region          VARCHAR(32) DEFAULT 'CN',
    default_locale  VARCHAR(8) DEFAULT 'zh-CN',
    default_quota   INT NOT NULL DEFAULT 3,
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspace_owner ON app.workspace (owner_user_id);

-- ========== 2. workspace_member 表（user <-> workspace + role）==========

CREATE TABLE app.workspace_member (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL DEFAULT 'EDITOR',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_active_at  TIMESTAMPTZ,
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_workspace_member_user ON app.workspace_member (user_id);
CREATE INDEX idx_workspace_member_workspace ON app.workspace_member (workspace_id);

-- ========== 3. 给业务表加 workspace_id（nullable 用于回填期）==========

ALTER TABLE app.analysis_task ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(32);
ALTER TABLE app.analysis_report ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(32);
ALTER TABLE app.chat_session ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(32);
ALTER TABLE app.agent_tool_log ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(32);
ALTER TABLE app.usage_log ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(32);

-- 兼容未来表（kb_document 也可按 workspace 隔离，但不强制）
ALTER TABLE kb.kb_document ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(32);

-- ========== 4. 索引：workspace 维度常用查询 ==========

CREATE INDEX IF NOT EXISTS idx_analysis_task_workspace ON app.analysis_task (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_session_workspace ON app.chat_session (workspace_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_tool_log_workspace ON app.agent_tool_log (workspace_id, created_at);
CREATE INDEX IF NOT EXISTS idx_usage_log_workspace ON app.usage_log (workspace_id, created_at);

-- ========== 5. 角色枚举约束 ==========

ALTER TABLE app.workspace_member
    DROP CONSTRAINT IF EXISTS workspace_member_role_check;
ALTER TABLE app.workspace_member
    ADD CONSTRAINT workspace_member_role_check
    CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'REVIEWER', 'VIEWER'));

ALTER TABLE app.workspace
    DROP CONSTRAINT IF EXISTS workspace_status_check;
ALTER TABLE app.workspace
    ADD CONSTRAINT workspace_status_check
    CHECK (status IN ('active', 'suspended', 'deleted'));

-- ========== 6. 注释 ==========

COMMENT ON TABLE app.workspace IS '工作区（多租户隔离单元）';
COMMENT ON TABLE app.workspace_member IS '工作区成员（user <-> workspace + role）';
COMMENT ON COLUMN app.workspace_member.role IS 'OWNER > ADMIN > EDITOR > REVIEWER > VIEWER';
COMMENT ON COLUMN app.analysis_task.workspace_id IS '所属工作区（与 user_id 双重校验）';