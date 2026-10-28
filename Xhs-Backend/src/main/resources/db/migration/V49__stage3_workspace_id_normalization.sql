-- V49：阶段 3 A0 基础层 —— workspace_id 类型归一
--
-- 背景（为什么做这次归一）：
--   app.workspace.id 的主键类型是 VARCHAR(32)，实际值形如 ws_a1b2c3d4e5f6（15 字符，
--   见 WorkspaceService.generateWorkspaceId()）。app.content_asset / app.review_task /
--   app.export_log 等表也都遵循 VARCHAR(32) 并带 FK 到 app.workspace(id)。
--   但历史迁移里另外三张表把 workspace_id 建成了别的类型，且**没有 FK 约束**：
--     - app.browser_assist_session.workspace_id  UUID
--     - app.csv_import_log.workspace_id          UUID
--     - app.topic_pool.workspace_id              BIGINT
--   Java 侧同样分裂（ContentAsset.workspaceId=UUID / TopicPool.workspaceId=Long /
--   BrowserAssistSession.workspaceId=UUID / CsvImportLog.workspaceId=UUID），
--   导致运行时对 "ws_xxx" 调用 UUID.fromString / Long.parseLong 直接抛异常：
--   资产创建、选题创建、浏览器辅助会话、CSV 导入四条链路全部不可用。
--
-- 本次归一目标：全部 workspace_id 统一为 VARCHAR(32)，与 app.workspace / app.content_asset 一致。
-- 不删除任何历史数据；孤儿行只加 NOT VALID 外键（不校验存量），由后续数据治理任务清理。
--
-- 幂等：迁移文件一次性执行（Flyway 按版本号管理）；所有 ALTER 都写成可重复执行的形式。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. 类型归一：UUID / BIGINT -> VARCHAR(32)
-- ============================================================
-- USING workspace_id::text 保证已有值原样保留（UUID 转文本形如 36 位 hex，
-- BIGINT 转文本形如数字）；VARCHAR(32) 对 36 位 UUID 字符串足够。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'browser_assist_session'
          AND column_name = 'workspace_id'
          AND data_type <> 'character varying'
    ) THEN
        ALTER TABLE app.browser_assist_session
            ALTER COLUMN workspace_id TYPE VARCHAR(32) USING workspace_id::text;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'csv_import_log'
          AND column_name = 'workspace_id'
          AND data_type <> 'character varying'
    ) THEN
        ALTER TABLE app.csv_import_log
            ALTER COLUMN workspace_id TYPE VARCHAR(32) USING workspace_id::text;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'topic_pool'
          AND column_name = 'workspace_id'
          AND data_type <> 'character varying'
    ) THEN
        ALTER TABLE app.topic_pool
            ALTER COLUMN workspace_id TYPE VARCHAR(32) USING workspace_id::text;
    END IF;
END
$$;

-- ============================================================
-- 2. 补 FK 到 app.workspace(id)
--    使用 NOT VALID：只校验新写入的行，历史孤儿行不阻塞迁移（不删除任何历史数据）。
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_browser_assist_session_workspace'
          AND conrelid = 'app.browser_assist_session'::regclass
    ) THEN
        ALTER TABLE app.browser_assist_session
            ADD CONSTRAINT fk_browser_assist_session_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_csv_import_log_workspace'
          AND conrelid = 'app.csv_import_log'::regclass
    ) THEN
        ALTER TABLE app.csv_import_log
            ADD CONSTRAINT fk_csv_import_log_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_topic_pool_workspace'
          AND conrelid = 'app.topic_pool'::regclass
    ) THEN
        ALTER TABLE app.topic_pool
            ADD CONSTRAINT fk_topic_pool_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$$;

-- ============================================================
-- 3. 索引
--    已有索引（idx_browser_session_workspace / idx_csv_import_log_workspace_created /
--    idx_topic_pool_workspace_status 等）在类型变更后由 PostgreSQL 自动重建，无需重建。
--    仅补一条按 workspace_id 的通用索引，覆盖热点/竞品/连接器看板的按 workspace 查询。
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_topic_pool_workspace_id ON app.topic_pool(workspace_id);
CREATE INDEX IF NOT EXISTS idx_browser_session_workspace_id ON app.browser_assist_session(workspace_id);
CREATE INDEX IF NOT EXISTS idx_csv_import_log_workspace_id ON app.csv_import_log(workspace_id);

COMMENT ON COLUMN app.browser_assist_session.workspace_id IS
    '所属工作区（VARCHAR(32)，与 app.workspace.id 一致；V49 由 UUID 归一）';
COMMENT ON COLUMN app.csv_import_log.workspace_id IS
    '所属工作区（VARCHAR(32)，与 app.workspace.id 一致；V49 由 UUID 归一）';
COMMENT ON COLUMN app.topic_pool.workspace_id IS
    '所属工作区（VARCHAR(32)，与 app.workspace.id 一致；V49 由 BIGINT 归一）';
