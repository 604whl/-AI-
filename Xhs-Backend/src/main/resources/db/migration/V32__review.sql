-- V32__review.sql
-- P0-6 审核流：review_task + review_comment

BEGIN;

CREATE TABLE app.review_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES app.content_asset(id) ON DELETE CASCADE,
    version_no INT NOT NULL,
    submitted_by BIGINT NOT NULL REFERENCES app.users(id),
    reviewer_user_id BIGINT REFERENCES app.users(id),
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    -- status: pending / in_review / approved / rejected / cancelled
    is_high_risk BOOLEAN NOT NULL DEFAULT false,
    risk_score INT,  -- 0-100
    risk_tags JSONB DEFAULT '[]'::jsonb,
    comment TEXT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_workspace_status ON app.review_task(workspace_id, status, created_at DESC);
CREATE INDEX idx_review_reviewer ON app.review_task(reviewer_user_id, status);
CREATE INDEX idx_review_asset ON app.review_task(asset_id, version_no);

CREATE TABLE app.review_comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_task_id UUID NOT NULL REFERENCES app.review_task(id) ON DELETE CASCADE,
    comment TEXT NOT NULL,
    highlighted_text TEXT,  -- 正文里被批注的片段
    comment_type VARCHAR(16) NOT NULL DEFAULT 'comment',
    -- comment_type: comment / approve / reject
    created_by BIGINT NOT NULL REFERENCES app.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_comment_task ON app.review_comment(review_task_id, created_at);

COMMENT ON TABLE app.review_task IS '审核任务主表';
COMMENT ON COLUMN app.review_task.status IS 'pending=待领取, in_review=审核中, approved=已通过, rejected=已驳回, cancelled=已撤回';
COMMENT ON COLUMN app.review_task.is_high_risk IS 'true=高风险，强制要求人工审核';
COMMENT ON COLUMN app.review_task.risk_score IS '合规风险分 0-100，超过 70 视为高风险';

COMMIT;