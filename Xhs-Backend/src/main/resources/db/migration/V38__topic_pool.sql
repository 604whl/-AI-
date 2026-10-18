-- V38: topic_pool table
-- 选题池：存储选题名称、来源平台、热度、状态和标签
CREATE TABLE app.topic_pool (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(256) NOT NULL,
    source VARCHAR(32) NOT NULL,
    heat_score INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    tags TEXT[] NULL,
    target_persona VARCHAR(64) NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_date DATE NULL,
    CONSTRAINT chk_topic_pool_status CHECK (status IN ('active', 'used', 'archived'))
);

CREATE INDEX idx_topic_pool_workspace_status ON app.topic_pool(workspace_id, status);
CREATE INDEX idx_topic_pool_workspace_created ON app.topic_pool(workspace_id, created_at DESC);

COMMENT ON TABLE app.topic_pool IS '选题池：存储待选/已用/归档的选题';
COMMENT ON COLUMN app.topic_pool.source IS '平台/品类来源，如 xiaohongshu/career/study_abroad';
COMMENT ON COLUMN app.topic_pool.target_persona IS '目标人群，如 agency/mentor/senior';