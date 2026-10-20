-- V42: comment_generate_log — 记录评论生成审计日志
-- 用于审计：谁、在什么上下文下、生成了什么评论、是否触发合规

CREATE TABLE app.comment_generate_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    comment_type VARCHAR(32) NOT NULL, -- FIRST_QUESTION / ASK_FOLLOW_UP / PRIVATE_MESSAGE / CONVERSION
    platform VARCHAR(32) NOT NULL,
    persona VARCHAR(32),
    draft_context TEXT,
    generated_comment TEXT,
    requires_review BOOLEAN DEFAULT false,
    compliance_risk VARCHAR(16),
    risk_level VARCHAR(16),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 审计索引：按用户 + 时间查询
CREATE INDEX idx_comment_log_user ON app.comment_generate_log(user_id, created_at DESC);

-- 审计索引：按 workspace + 时间查询
CREATE INDEX idx_comment_log_workspace ON app.comment_generate_log(workspace_id, created_at DESC);

COMMENT ON TABLE app.comment_generate_log IS '评论生成审计日志表';
COMMENT ON COLUMN app.comment_generate_log.comment_type IS '评论类型：FIRST_QUESTION/ASK_FOLLOW_UP/PRIVATE_MESSAGE/CONVERSION';
COMMENT ON COLUMN app.comment_generate_log.generated_comment IS 'AI 生成的评论内容（合规阻断时可能为空）';
COMMENT ON COLUMN app.comment_generate_log.requires_review IS '是否需要人工审核';