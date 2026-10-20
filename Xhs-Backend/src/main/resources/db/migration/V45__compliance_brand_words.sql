-- V45__compliance_brand_words.sql
-- S2-3: 品牌级禁用词表（每个 workspace 独立配置，优先级高于全局规则）

CREATE TABLE IF NOT EXISTS app.compliance_brand_word (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    UUID NOT NULL,
    rule_code       VARCHAR(64) NOT NULL,
    keyword         VARCHAR(256) NOT NULL,
    risk_level      VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    created_by      BIGINT REFERENCES app.users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT brand_word_risk_check CHECK (risk_level IN ('LOW','MEDIUM','HIGH','BLOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_brand_word_workspace ON app.compliance_brand_word(workspace_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_brand_word_unique ON app.compliance_brand_word(workspace_id, rule_code, keyword);