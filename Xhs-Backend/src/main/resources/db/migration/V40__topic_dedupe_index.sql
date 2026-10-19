-- V40: topic dedup -- add normalized_title column and dedup index
-- Enables fast dedup lookup by workspace + source + normalized title

ALTER TABLE app.topic_pool ADD COLUMN normalized_title VARCHAR(256) NULL;
UPDATE app.topic_pool SET normalized_title = LOWER(TRIM(title)) WHERE normalized_title IS NULL;
CREATE INDEX idx_topic_pool_dedupe_key ON app.topic_pool(workspace_id, source, normalized_title);