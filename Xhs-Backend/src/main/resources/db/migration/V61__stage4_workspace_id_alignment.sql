-- V61：app.api_key.workspace_id 宽度归一 VARCHAR(64) → VARCHAR(32)
--
-- 背景：
--   阶段三统一了「所有 workspace_id 一律 VARCHAR(32)」的规则，app.workspace.id 也是 VARCHAR(32)。
--   但 V23 建表时 app.api_key.workspace_id 声明为 VARCHAR(64)，是唯一残留的宽度不一致。
--   该列带两个外键（内联 REFERENCES app.workspace(id) 与命名 CONSTRAINT api_key_workspace_fk），
--   PostgreSQL 的 ALTER COLUMN TYPE 会自动重建依赖的外键，不丢语义。
--
-- 约束：
--   - 幂等：用 information_schema 守卫，可重复执行
--   - 宽度只收窄不改变取值；孤儿数据（workspace 不存在）先删除再转换

SET search_path TO app, kb, public;

DO $$
DECLARE
    v_overlong BIGINT := 0;
    v_orphan   BIGINT := 0;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'api_key'
          AND column_name = 'workspace_id'
          AND data_type = 'character varying'
          AND character_maximum_length <> 32
    ) THEN
        -- 孤儿统计：workspace_id 在 app.workspace 中不存在
        SELECT COUNT(*) INTO v_orphan
        FROM app.api_key a
        WHERE NOT EXISTS (SELECT 1 FROM app.workspace w WHERE w.id = a.workspace_id);

        IF v_orphan > 0 THEN
            RAISE NOTICE 'V61: deleting % orphan app.api_key rows', v_orphan;
            DELETE FROM app.api_key a
            WHERE NOT EXISTS (SELECT 1 FROM app.workspace w WHERE w.id = a.workspace_id);
        END IF;

        -- 超长值统计（必须在孤儿清理之后统计，否则会把刚删掉的行算进来）
        -- workspace.id 本身就是 VARCHAR(32)，FK 校验通过的行必然不超过 32 字符；
        -- 仍有超长值说明存在绕过 FK 的脏数据，必须人工处理。
        SELECT COUNT(*) INTO v_overlong
        FROM app.api_key
        WHERE length(workspace_id) > 32;

        IF v_overlong > 0 THEN
            RAISE EXCEPTION 'V61: % app.api_key rows exceed 32 chars, manual fix required', v_overlong;
        END IF;

        ALTER TABLE app.api_key
            ALTER COLUMN workspace_id TYPE VARCHAR(32)
            USING workspace_id::text;
    END IF;
END
$$;

COMMENT ON COLUMN app.api_key.workspace_id IS
    '工作区 ID（VARCHAR(32)，与 app.workspace.id 一致）；V61 起归一宽度';
