-- V38_1: hot_topic table
-- 热搜/热门话题池，Scheduler 每 4h 从 GetHotTopicsTool 拉取

CREATE TABLE app.hot_topic (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    category VARCHAR(32) NULL,
    title VARCHAR(256) NOT NULL,
    heat_score INTEGER NOT NULL DEFAULT 0,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    raw_payload JSONB NULL,
    promoted_to_pool BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_hot_topic_platform_category ON app.hot_topic(platform, category);
CREATE INDEX idx_hot_topic_fetched_at ON app.hot_topic(fetched_at DESC);

COMMENT ON TABLE app.hot_topic IS '热搜/热门话题池，Scheduler 定时从 GetHotTopicsTool 拉取';
COMMENT ON COLUMN app.hot_topic.platform IS '平台代码，如 xiaohongshu';
COMMENT ON COLUMN app.hot_topic.category IS '品类/行业代码，如 career/study_abroad';
COMMENT ON COLUMN app.hot_topic.title IS '话题标题';
COMMENT ON COLUMN app.hot_topic.heat_score IS '热度分';
COMMENT ON COLUMN app.hot_topic.raw_payload IS '原始返回数据 JSON';
COMMENT ON COLUMN app.hot_topic.promoted_to_pool IS '是否已写入 topic_pool';