-- XhsAgent 初始化 Schema
-- 需要 PostgreSQL 14+ 且 CREATE EXTENSION vector

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS kb;

-- pgvector 安装在 public；Flyway default-schema=app 会把 search_path 限为 app
SET search_path TO app, kb, public;

-- ========== app：业务 ==========

CREATE TABLE app.users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(128) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(64),
    default_persona VARCHAR(32) DEFAULT 'agency',
    daily_quota     INT NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE app.analysis_task (
    id                VARCHAR(32) PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES app.users(id),
    scenario          VARCHAR(32) NOT NULL,
    persona           VARCHAR(32) NOT NULL DEFAULT 'agency',
    title             TEXT,
    body              TEXT,
    cover_image_url   TEXT,
    status            VARCHAR(32) NOT NULL DEFAULT 'pending',
    failure_reason    VARCHAR(64),
    failure_code      INT,
    prompt_version    VARCHAR(32) NOT NULL DEFAULT 'rubric-1.0.0',
    model_provider    VARCHAR(32) NOT NULL DEFAULT 'dashscope',
    model_name        VARCHAR(64),
    processing_ms     INT,
    published_metrics JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_task_user_created ON app.analysis_task (user_id, created_at DESC);
CREATE INDEX idx_analysis_task_status ON app.analysis_task (status);

CREATE TABLE app.analysis_report (
    task_id              VARCHAR(32) PRIMARY KEY REFERENCES app.analysis_task(id) ON DELETE CASCADE,
    report_json          JSONB NOT NULL,
    compliance_warnings  JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE app.usage_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app.users(id),
    action     VARCHAR(64) NOT NULL,
    task_id    VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_log_user_day ON app.usage_log (user_id, created_at);

-- ========== kb：RAG ==========

CREATE TABLE kb.kb_document (
    id            BIGSERIAL PRIMARY KEY,
    doc_id        VARCHAR(64) NOT NULL,
    doc_type      VARCHAR(32) NOT NULL,
    content_type  VARCHAR(32),
    persona       TEXT[],
    tags          TEXT[],
    title         TEXT,
    chunk_type    VARCHAR(32) NOT NULL DEFAULT 'full',
    content       TEXT NOT NULL,
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding     vector(1024),
    tsv           tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title, '') || ' ' || content)) STORED,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doc_id, chunk_type)
);

CREATE INDEX idx_kb_doc_type ON kb.kb_document (doc_type, content_type);
CREATE INDEX idx_kb_doc_embedding ON kb.kb_document USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_kb_doc_tsv ON kb.kb_document USING gin (tsv);
