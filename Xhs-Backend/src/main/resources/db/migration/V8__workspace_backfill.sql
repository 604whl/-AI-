-- ============================================================
-- V8：工作区数据回填 + workspace_id NOT NULL 收紧
-- ============================================================
-- 策略：
--   1. 为每个用户创建一个 default workspace（如果还没有）
--   2. 用户自动成为该 workspace 的 OWNER
--   3. 把用户的所有历史数据归属到其 default workspace
--   4. workspace_id 改为 NOT NULL
--   5. 最后一步才加 FK，避免迁移期报错
-- ============================================================

SET search_path TO app, kb, public;

-- ========== 1. 为每个用户创建 default workspace ==========

INSERT INTO app.workspace (id, name, slug, owner_user_id, default_quota)
SELECT
    'ws_user_' || u.id,
    COALESCE(NULLIF(u.display_name, ''), u.email) || ' 的工作区',
    'user-' || u.id,
    u.id,
    u.daily_quota
FROM app.users u
WHERE NOT EXISTS (
    SELECT 1 FROM app.workspace w WHERE w.owner_user_id = u.id
);

-- ========== 2. 把用户加入自己的 default workspace（OWNER）==========

INSERT INTO app.workspace_member (workspace_id, user_id, role)
SELECT w.id, u.id, 'OWNER'
FROM app.users u
JOIN app.workspace w ON w.owner_user_id = u.id
WHERE NOT EXISTS (
    SELECT 1 FROM app.workspace_member m
    WHERE m.workspace_id = w.id AND m.user_id = u.id
);

-- ========== 3. 历史数据回填 ==========

UPDATE app.analysis_task t
SET workspace_id = w.id
FROM app.workspace w
WHERE w.owner_user_id = t.user_id
  AND t.workspace_id IS NULL;

UPDATE app.analysis_report r
SET workspace_id = w.id
FROM app.analysis_task t
JOIN app.workspace w ON w.owner_user_id = t.user_id
WHERE r.task_id = t.id
  AND r.workspace_id IS NULL;

UPDATE app.chat_session s
SET workspace_id = w.id
FROM app.workspace w
WHERE w.owner_user_id = s.user_id
  AND s.workspace_id IS NULL;

UPDATE app.agent_tool_log l
SET workspace_id = w.id
FROM app.workspace w
WHERE w.owner_user_id = l.user_id
  AND l.workspace_id IS NULL;

UPDATE app.usage_log l
SET workspace_id = w.id
FROM app.workspace w
WHERE w.owner_user_id = l.user_id
  AND l.workspace_id IS NULL;

-- ========== 4. 兜底：仍未归属的记录（防御性，正常应为空）==========

UPDATE app.analysis_task SET workspace_id = (SELECT id FROM app.workspace LIMIT 1) WHERE workspace_id IS NULL;
UPDATE app.analysis_report SET workspace_id = (SELECT id FROM app.workspace LIMIT 1) WHERE workspace_id IS NULL;
UPDATE app.chat_session SET workspace_id = (SELECT id FROM app.workspace LIMIT 1) WHERE workspace_id IS NULL;
UPDATE app.agent_tool_log SET workspace_id = (SELECT id FROM app.workspace LIMIT 1) WHERE workspace_id IS NULL;
UPDATE app.usage_log SET workspace_id = (SELECT id FROM app.workspace LIMIT 1) WHERE workspace_id IS NULL;

-- ========== 5. workspace_id NOT NULL 收紧 ==========

ALTER TABLE app.analysis_task ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE app.analysis_report ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE app.chat_session ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE app.agent_tool_log ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE app.usage_log ALTER COLUMN workspace_id SET NOT NULL;

-- ========== 6. 加 FK 约束（用户/workspace 协同删除级联）==========

ALTER TABLE app.analysis_task
    DROP CONSTRAINT IF EXISTS fk_analysis_task_workspace;
ALTER TABLE app.analysis_task
    ADD CONSTRAINT fk_analysis_task_workspace
        FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE;

ALTER TABLE app.chat_session
    DROP CONSTRAINT IF EXISTS fk_chat_session_workspace;
ALTER TABLE app.chat_session
    ADD CONSTRAINT fk_chat_session_workspace
        FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE;

ALTER TABLE app.usage_log
    DROP CONSTRAINT IF EXISTS fk_usage_log_workspace;
ALTER TABLE app.usage_log
    ADD CONSTRAINT fk_usage_log_workspace
        FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE;

-- ========== 7. workspace 软删除：deleted 状态对应的数据保留 30 天后清理 ==========
-- 软删除不在 SQL 层处理，由 service 层判断

COMMENT ON COLUMN app.analysis_task.workspace_id IS '所属工作区（与 user_id 双重校验）';
COMMENT ON COLUMN app.usage_log.workspace_id IS '所属工作区';