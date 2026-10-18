-- Add UUID column to asset_tag for correct type mapping with content_asset.id (UUID).
-- asset_id BIGINT column is kept for backward compatibility but not used.

ALTER TABLE app.asset_tag ADD COLUMN asset_uuid VARCHAR(64) NOT NULL DEFAULT '';
CREATE INDEX idx_asset_tag_asset_uuid ON app.asset_tag (asset_uuid);