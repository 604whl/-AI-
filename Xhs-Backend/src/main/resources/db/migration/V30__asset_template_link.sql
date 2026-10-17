-- P0-4: content_asset 新增 template_id 字段，关联生成该草稿的模板

SET search_path TO app, kb, public;

ALTER TABLE app.content_asset
    ADD COLUMN IF NOT EXISTS template_id UUID REFERENCES app.template(id) ON DELETE SET NULL;

COMMENT ON COLUMN app.content_asset.template_id IS '关联的模板 ID（创建草稿时可选择模板）';
