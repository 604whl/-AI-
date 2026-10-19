-- V41: hot_topic normalized_title column + index for trend lookup
-- Adds a denormalized normalized_title column and a covering index
-- for the trend algorithm that joins on platform + normalized_title + fetched_at.

-- Add normalized_title column (nullable, populated by UPDATE below)
ALTER TABLE app.hot_topic ADD COLUMN normalized_title VARCHAR(256) NULL;

-- Backfill existing rows using the same LOWER+TRIM+collapse-whitespace logic as TopicNormalize
-- PostgreSQL equivalent: lower + trim + collapse multiple whitespace characters
UPDATE app.hot_topic
SET    normalized_title = LOWER(REGEXP_REPLACE(TRIM(title), E'\\s+', ' ', 'g'))
WHERE  normalized_title IS NULL;

-- Covering index: platform + normalized_title + fetched_at DESC
-- Supports the SELECT ... WHERE platform = ? AND normalized_title = ? (trend query)
-- with ORDER BY fetched_at ASC.
CREATE INDEX IF NOT EXISTS idx_hot_topic_trend_lookup
    ON app.hot_topic(platform, normalized_title, fetched_at DESC);

COMMENT ON COLUMN app.hot_topic.normalized_title IS
    '话题标题规范化后的小写字符串（去首尾空格 + 合并连续空白），用于趋势查询去重和快速匹配';