-- V43__asset_tags.sql
-- Asset tags: multi-tag support via PostgreSQL JSONB

ALTER TABLE app.content_asset ADD COLUMN IF NOT EXISTS tags JSONB DEFAULT '[]'::jsonb;
CREATE INDEX IF NOT EXISTS idx_asset_tags ON app.content_asset USING GIN (tags);