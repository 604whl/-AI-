-- V53：阶段 3 S3-9 热点数据产品化
--
-- 目标（对应 STAGE3_ACCEPTANCE.md §4.1 "热点数据产品化"）：
--   1. app.hot_topic 补齐来源 / 可信度 / 是否实时 / 降级 / 状态 / 可做方向字段
--   2. 新建 app.hot_topic_cache（同窗口命中缓存，不重复调用外部源）
--   3. app.topic_pool 补齐 source_name / confidence / source_fetched_at（热点来源透传）
--   4. normalized_title 由触发器自动维护（与 V41 的 lower+trim+合并空白逻辑一致）
--
-- 硬性约束：
--   - 静态种子必须 source_type='SEED' + is_realtime=FALSE，禁止伪装成实时热点
--   - 去重必须保留历史（旧记录标 superseded，不删除），供趋势对比
--   - workspace_id 列由 V49 负责归一，本迁移完全不触碰 app.topic_pool.workspace_id
--
-- 幂等：全部使用 IF NOT EXISTS / DO $$ 形式，可重复执行。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.hot_topic 加列
-- ============================================================
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS source_name VARCHAR(64) NULL;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NULL;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS confidence VARCHAR(16) NULL;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS is_realtime BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS source_url TEXT NULL;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS degraded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS degraded_reason TEXT NULL;
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE app.hot_topic ADD COLUMN IF NOT EXISTS suggested_angles JSONB NULL;

COMMENT ON COLUMN app.hot_topic.source_name IS
    '来源名称（如 web_search / static_seed / 连接器 providerCode），用于界面展示来源';
COMMENT ON COLUMN app.hot_topic.source_type IS
    '来源类型：SEARCH_TOOL(联网搜索) / OFFICIAL_API(官方接口) / AUTHORIZED_CONNECTOR(授权连接器) / MANUAL_IMPORT(手动导入) / SEED(静态种子)';
COMMENT ON COLUMN app.hot_topic.confidence IS
    '可信度分级：HIGH / MEDIUM / LOW / UNVERIFIED';
COMMENT ON COLUMN app.hot_topic.is_realtime IS
    '是否实时采集；静态种子必须为 FALSE，界面需显式标注"非实时"';
COMMENT ON COLUMN app.hot_topic.source_url IS
    '原始来源链接（可为空）';
COMMENT ON COLUMN app.hot_topic.degraded IS
    '该条数据是否处于降级状态（采集源不可用时的显式标记）';
COMMENT ON COLUMN app.hot_topic.degraded_reason IS
    '降级原因（不可用原因说明，禁止静默展示陈旧数据）';
COMMENT ON COLUMN app.hot_topic.status IS
    '去重状态：active(当前最新) / superseded(已被更新取代，历史保留供趋势对比)';
COMMENT ON COLUMN app.hot_topic.suggested_angles IS
    '可做方向数组 JSON：[{"angle":"...","evidence":{"title":"...","sourceName":"...","fetchedAt":"..."}}]';

-- 枚举约束（NOT VALID：不校验存量行，避免历史脏数据阻塞迁移）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_hot_topic_status' AND conrelid = 'app.hot_topic'::regclass
    ) THEN
        ALTER TABLE app.hot_topic
            ADD CONSTRAINT chk_hot_topic_status
            CHECK (status IN ('active', 'superseded')) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_hot_topic_source_type' AND conrelid = 'app.hot_topic'::regclass
    ) THEN
        ALTER TABLE app.hot_topic
            ADD CONSTRAINT chk_hot_topic_source_type
            CHECK (source_type IS NULL OR source_type IN (
                'SEARCH_TOOL', 'OFFICIAL_API', 'AUTHORIZED_CONNECTOR', 'MANUAL_IMPORT', 'SEED'
            )) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_hot_topic_confidence' AND conrelid = 'app.hot_topic'::regclass
    ) THEN
        ALTER TABLE app.hot_topic
            ADD CONSTRAINT chk_hot_topic_confidence
            CHECK (confidence IS NULL OR confidence IN ('HIGH', 'MEDIUM', 'LOW', 'UNVERIFIED')) NOT VALID;
    END IF;
END
$$;

-- 列表默认只取 active；按平台+品类+状态过滤
CREATE INDEX IF NOT EXISTS idx_hot_topic_active_lookup
    ON app.hot_topic(platform, category, status, fetched_at DESC);

-- 按来源类型 / 是否实时过滤
CREATE INDEX IF NOT EXISTS idx_hot_topic_source_type
    ON app.hot_topic(source_type, is_realtime);

-- ============================================================
-- 2. normalized_title 触发器（与 V41 回填逻辑保持一致）
--    lower + trim + 合并连续空白；保证去重/趋势查询永远命中
-- ============================================================
CREATE OR REPLACE FUNCTION app.fn_hot_topic_normalized_title()
RETURNS TRIGGER AS $$
BEGIN
    NEW.normalized_title := LOWER(REGEXP_REPLACE(TRIM(NEW.title), E'\s+', ' ', 'g'));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_hot_topic_normalized_title ON app.hot_topic;
CREATE TRIGGER trg_hot_topic_normalized_title
    BEFORE INSERT OR UPDATE OF title ON app.hot_topic
    FOR EACH ROW EXECUTE FUNCTION app.fn_hot_topic_normalized_title();

-- 存量行补齐（触发器只对新增/更新生效）
UPDATE app.hot_topic
SET normalized_title = LOWER(REGEXP_REPLACE(TRIM(title), E'\s+', ' ', 'g'))
WHERE normalized_title IS NULL;

-- ============================================================
-- 3. app.hot_topic_cache 新建
--    同窗口命中缓存不重复调用外部源
--    category 统一用 '' 表示"不限品类"，避免 NULL 在唯一约束中互不相等
-- ============================================================
CREATE TABLE IF NOT EXISTS app.hot_topic_cache (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT '',
    payload JSONB NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ttl_seconds INT NOT NULL DEFAULT 3600,
    CONSTRAINT uq_hot_topic_cache UNIQUE (platform, category)
);

CREATE INDEX IF NOT EXISTS idx_hot_topic_cache_fetched_at
    ON app.hot_topic_cache(fetched_at DESC);

COMMENT ON TABLE app.hot_topic_cache IS
    '热点采集缓存：同窗口命中缓存时不重复调用外部源；payload 内含 degraded 标记与采集摘要';
COMMENT ON COLUMN app.hot_topic_cache.platform IS '平台代码';
COMMENT ON COLUMN app.hot_topic_cache.category IS '品类代码，'' 表示不限品类';
COMMENT ON COLUMN app.hot_topic_cache.payload IS
    '缓存载荷 JSON：{"degraded":bool,"degradedReason":"...","items":[...]}';
COMMENT ON COLUMN app.hot_topic_cache.fetched_at IS '本次采集/写入时间';
COMMENT ON COLUMN app.hot_topic_cache.ttl_seconds IS '缓存有效期（秒），默认 3600';

-- ============================================================
-- 4. app.topic_pool 加列（热点来源透传）
--    注意：不触碰 workspace_id（类型与 FK 归 V49 负责）
-- ============================================================
ALTER TABLE app.topic_pool ADD COLUMN IF NOT EXISTS source_name VARCHAR(64) NULL;
ALTER TABLE app.topic_pool ADD COLUMN IF NOT EXISTS confidence VARCHAR(16) NULL;
ALTER TABLE app.topic_pool ADD COLUMN IF NOT EXISTS source_fetched_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN app.topic_pool.source_name IS
    '来源名称（透传自 hot_topic.source_name），用于选题来源可信度追溯';
COMMENT ON COLUMN app.topic_pool.confidence IS
    '可信度分级（透传自 hot_topic.confidence）：HIGH / MEDIUM / LOW / UNVERIFIED';
COMMENT ON COLUMN app.topic_pool.source_fetched_at IS
    '来源热点采集时间（透传自 hot_topic.fetched_at），用于判断选题时效性';
