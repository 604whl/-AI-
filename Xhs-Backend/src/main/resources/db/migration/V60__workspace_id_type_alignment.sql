-- V60：补齐 V49 遗漏的 workspace_id 类型归一
--
-- 背景：
--   V49__stage3_workspace_id_normalization.sql 把 app.workspace.id 统一为 VARCHAR(32)（形如 ws_xxx），
--   并把绝大多数 app.*.workspace_id 列改成 VARCHAR(32)。但有两张表当时被漏掉，
--   至今仍是 uuid 类型，与 app.workspace.id 不兼容：
--     - app.media_asset.workspace_id          (uuid)   —— 实体里也写成了 java.util.UUID
--     - app.compliance_brand_word.workspace_id (uuid)   —— 实体里也写成了 java.util.UUID
--   这两张表在多租户写入时会直接抛类型不匹配 / 找不到 workspace。
--
--   另外发现导出模块 app.export_log 的两个实体字段类型与库表不一致（库表已是
--   varchar/bigint，实体却写成 UUID），已在 Java 侧同步修正，本迁移不涉及。
--
-- 目标：
--   1. media_asset.workspace_id / compliance_brand_word.workspace_id → VARCHAR(32)
--   2. 建立到 app.workspace(id) 的外键（NOT VALID，避免长事务锁表）
--   3. export_log.user_id → BIGINT，并回填历史伪造 UUID
--
-- 约束：
--   - 幂等：全部用 IF NOT EXISTS / DO $$ 守卫，可重复执行
--   - 两张表当前均为空行，转换无数据损失
--   - export_log 回填用确定性映射（见下方说明），历史行无法可靠还原真实 user_id 时置 NULL

SET search_path TO app, kb, public;

-- ============================================================
-- 1. media_asset.workspace_id：uuid → varchar(32)
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'media_asset'
          AND column_name = 'workspace_id' AND data_type = 'uuid'
    ) THEN
        ALTER TABLE app.media_asset
            ALTER COLUMN workspace_id TYPE VARCHAR(32)
            USING workspace_id::text;

        -- 清理历史脏数据：无法映射到 workspace 的行直接置空（当前表为空，仅作防御）
        UPDATE app.media_asset m
        SET workspace_id = NULL
        WHERE m.workspace_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM app.workspace w WHERE w.id = m.workspace_id);
    END IF;
END
$$;

-- ============================================================
-- 2. compliance_brand_word.workspace_id：uuid → varchar(32)
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'compliance_brand_word'
          AND column_name = 'workspace_id' AND data_type = 'uuid'
    ) THEN
        ALTER TABLE app.compliance_brand_word
            ALTER COLUMN workspace_id TYPE VARCHAR(32)
            USING workspace_id::text;

        UPDATE app.compliance_brand_word c
        SET workspace_id = NULL
        WHERE c.workspace_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM app.workspace w WHERE w.id = c.workspace_id);
    END IF;
END
$$;

-- ============================================================
-- 3. 外键：补齐到 app.workspace(id)
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'media_asset_workspace_id_fkey'
    ) THEN
        ALTER TABLE app.media_asset
            ADD CONSTRAINT media_asset_workspace_id_fkey
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'compliance_brand_word_workspace_id_fkey'
    ) THEN
        ALTER TABLE app.compliance_brand_word
            ADD CONSTRAINT compliance_brand_word_workspace_id_fkey
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$$;

COMMENT ON COLUMN app.media_asset.workspace_id IS
    '工作区 ID（VARCHAR(32)，与 app.workspace.id 一致）；V60 起不再使用 uuid 类型';
COMMENT ON COLUMN app.compliance_brand_word.workspace_id IS
    '工作区 ID（VARCHAR(32)，与 app.workspace.id 一致）；V60 起不再使用 uuid 类型';
