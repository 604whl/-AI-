-- V54: Stage 3 S3-11 - KB document management indexes for kb.kb_document
--
-- Background:
--   S3-11 requires the kb_document entity + mapper, document list / search / delete APIs,
--   per-document index status, and lifting the @Profile("local") restriction on AdminKbController.
--   The table itself already exists (V1__init_schema.sql); this migration adds **no columns**,
--   only indexes:
--     - list aggregates by doc_id then paginates -> doc_id index helps grouping / ordering
--     - detail / delete / index-status all filter by WHERE doc_id = ?
--     - index status needs MAX(updated_at) filtered by doc_id
--     - workspace_id is nullable (KB is globally shared); index it so a future
--       workspace-scoped filter does not degrade into a full table scan
--
-- Idempotent: every statement is CREATE INDEX IF NOT EXISTS.
-- V1__init_schema.sql and all historical migrations are left untouched.

SET search_path TO app, kb, public;

-- Point lookup + aggregate pagination by doc_id
CREATE INDEX IF NOT EXISTS idx_kb_doc_document_doc_id ON kb.kb_document(doc_id);

-- Per-document index status: filter by doc_id, take MAX(updated_at)
CREATE INDEX IF NOT EXISTS idx_kb_doc_document_doc_id_updated_at
    ON kb.kb_document(doc_id, updated_at DESC);

-- Filter by content_type only (V1 already has a composite (doc_type, content_type) index)
CREATE INDEX IF NOT EXISTS idx_kb_doc_document_content_type ON kb.kb_document(content_type);

-- Reserved for a future workspace-scoped filter (KB is globally shared today)
CREATE INDEX IF NOT EXISTS idx_kb_doc_document_workspace_id ON kb.kb_document(workspace_id);

COMMENT ON INDEX kb.idx_kb_doc_document_doc_id IS
    'S3-11 KB management: aggregate pagination and point lookup by doc_id';
COMMENT ON INDEX kb.idx_kb_doc_document_doc_id_updated_at IS
    'S3-11 KB management: per-document index status (MAX(updated_at))';
COMMENT ON INDEX kb.idx_kb_doc_document_content_type IS
    'S3-11 KB management: filter by content_type';
COMMENT ON INDEX kb.idx_kb_doc_document_workspace_id IS
    'S3-11 KB management: reserved for workspace-scoped filtering (KB is globally shared)';
