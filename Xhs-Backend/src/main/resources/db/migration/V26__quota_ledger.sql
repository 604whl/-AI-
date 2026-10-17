-- P0-2: 原子化额度扣减 + 冲正 + ledger 审计
-- 新建 workspace_quota 表和 usage_ledger 审计表

SET search_path TO app, kb, public;

-- 1. workspace_quota 表：workspace 级每日配额
CREATE TABLE IF NOT EXISTS app.workspace_quota (
    workspace_id    UUID        PRIMARY KEY REFERENCES app.workspace(id) ON DELETE CASCADE,
    daily_quota     INT         NOT NULL DEFAULT 100,
    used            INT         NOT NULL DEFAULT 0,
    reset_at        TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '1 day'),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workspace_quota_reset_at ON app.workspace_quota (reset_at);

-- 2. usage_ledger 审计表：记录每一次扣减和冲正
CREATE TABLE IF NOT EXISTS app.usage_ledger (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID        NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    user_id        UUID,
    action         VARCHAR(64) NOT NULL,          -- ANALYSIS / TITLE / BODY / COVER / CHAT
    ref_id         UUID,                          -- 关联 taskId / sessionId
    delta          INT         NOT NULL,           -- +1 扣减 / -1 冲正
    status         VARCHAR(16) NOT NULL,         -- CONSUMED / REFUNDED
    related_log_id UUID,                          -- 冲正时引用原 ledger id
    reason         VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_workspace_created ON app.usage_ledger (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_status            ON app.usage_ledger (status);
CREATE INDEX IF NOT EXISTS idx_ledger_action            ON app.usage_ledger (action);
CREATE INDEX IF NOT EXISTS idx_ledger_ref_id            ON app.usage_ledger (ref_id) WHERE ref_id IS NOT NULL;

-- 3. usage_log 表增加退费字段（兼容旧接口）
ALTER TABLE app.usage_log
    ADD COLUMN IF NOT EXISTS refund_status VARCHAR(16) DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS refunded_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS original_log_id UUID;
