-- Asset tags table: many-to-many between content_asset and tag strings.

CREATE TABLE app.asset_tag (
    id          BIGSERIAL PRIMARY KEY,
    asset_id    BIGINT NOT NULL,
    tag         VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_asset_tag_asset_id ON app.asset_tag (asset_id);
CREATE INDEX idx_asset_tag_tag       ON app.asset_tag (tag);
CREATE UNIQUE INDEX uk_asset_tag_asset_tag ON app.asset_tag (asset_id, tag);