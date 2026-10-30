-- V62：阶段 4 S4-1 套餐与订阅
--
-- 对应 STAGE4_PLAN.md「子能力 1：套餐、订阅与试用」+「数据模型总表」。
--
-- 目标：
--   1. 新建 app.subscription_plan —— 套餐定义（多版本，改价生成新版本）
--   2. 新建 app.subscription       —— 工作区订阅（状态机 + 套餐快照）
--   3. ALTER app.workspace_quota   —— 加周期额度列（原 daily_quota/used/reset_at 语义不变）
--   4. ALTER app.workspace         —— 加 subscription_status / plan_code（便于列表查询与鉴权）
--   5. 预置 4 个套餐：FREE / PRO / TEAM / USAGE（幂等，可重复执行）
--
-- 冻结决策：
--   D6 混合计费 → 必须同时有订阅套餐（FREE/PRO/TEAM）与按量套餐（USAGE）
--   D8 机构 + 代运营 → TEAM 套餐带 team_seats / webhook / open_api 开关
--   D9 预付 + 宽限期 7 天 → grace_period_days 默认 7
--
-- 约束：
--   - 幂等：全部 IF NOT EXISTS / DO $$ / ON CONFLICT 守卫，可重复执行
--   - 金额一律 NUMERIC(18,4)
--   - workspace_id 一律 VARCHAR(32)，与 app.workspace.id 对齐
--   - 只加列不改语义：workspace_quota.daily_quota / used / reset_at 行为完全不变
--   - 预置价格为「初始建议值」，真实定价待业务定稿

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.subscription_plan：套餐定义（code + version 唯一）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.subscription_plan (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(32)  NOT NULL,
    version         INT          NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    -- MONTH / QUARTER / YEAR
    billing_period  VARCHAR(16)  NOT NULL DEFAULT 'MONTH',
    price           NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    -- 各计量单位包含额度，key = BillableUnit 枚举名，value = Integer
    included_units  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 超额单价（按量套餐生效）
    overage_price   NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- 功能开关：competitor_library / metrics_dashboard / open_api / webhook
    --          / team_seats / export / api_key
    feature_flags   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- draft / active / archived
    status          VARCHAR(16)  NOT NULL DEFAULT 'draft',
    description     TEXT,
    -- 管理端写接口的幂等键
    idempotency_key VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_plan_code_version UNIQUE (code, version),
    CONSTRAINT chk_subscription_plan_period CHECK (billing_period IN ('MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT chk_subscription_plan_status CHECK (status IN ('draft', 'active', 'archived')),
    CONSTRAINT chk_subscription_plan_price CHECK (price >= 0),
    CONSTRAINT chk_subscription_plan_version CHECK (version >= 1)
);

COMMENT ON TABLE app.subscription_plan IS
    '阶段4 套餐定义（多版本）。改价/改额度不 UPDATE 老版本，而是 INSERT 新版本并把老版本置 archived；存量订阅读快照不受影响';
COMMENT ON COLUMN app.subscription_plan.code IS '套餐编码：FREE / PRO / TEAM / USAGE';
COMMENT ON COLUMN app.subscription_plan.version IS '套餐版本号，同 code 下递增；与 code 组成唯一键';
COMMENT ON COLUMN app.subscription_plan.billing_period IS '计费周期：MONTH / QUARTER / YEAR';
COMMENT ON COLUMN app.subscription_plan.price IS '周期价格（NUMERIC(18,4)，单位 = currency）';
COMMENT ON COLUMN app.subscription_plan.included_units IS
    '周期内包含额度，JSONB map：key = BillableUnit 枚举名，value = Integer';
COMMENT ON COLUMN app.subscription_plan.overage_price IS
    '超额单价（NUMERIC(18,4)），USAGE 按量套餐生效；初始建议值，待业务定稿';
COMMENT ON COLUMN app.subscription_plan.feature_flags IS
    '功能开关 JSONB：competitor_library / metrics_dashboard / open_api / webhook / team_seats / export / api_key';
COMMENT ON COLUMN app.subscription_plan.status IS 'draft / active / archived';

CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_plan_idempotency_key
    ON app.subscription_plan (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscription_plan_code_status
    ON app.subscription_plan (code, status, version DESC);

-- ============================================================
-- 2. app.subscription：工作区订阅
-- ============================================================
CREATE TABLE IF NOT EXISTS app.subscription (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id          VARCHAR(32) NOT NULL,
    plan_id               UUID        NOT NULL,
    -- 订阅生效时冻结的套餐快照：code/version/name/price/included_units/feature_flags/billing_period
    plan_version_snapshot JSONB       NOT NULL DEFAULT '{}'::jsonb,
    -- trialing / active / past_due / canceled / expired / suspended
    status                VARCHAR(16) NOT NULL DEFAULT 'active',
    current_period_start  TIMESTAMPTZ,
    current_period_end    TIMESTAMPTZ,
    trial_end             TIMESTAMPTZ,
    cancel_at_period_end  BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 降级到周期末生效时记录的目标套餐
    pending_plan_id       UUID,
    auto_renew            BOOLEAN     NOT NULL DEFAULT TRUE,
    -- D9：宽限期默认 7 天
    grace_period_days     INT         NOT NULL DEFAULT 7,
    subscribed_at         TIMESTAMPTZ,
    canceled_at           TIMESTAMPTZ,
    idempotency_key       VARCHAR(128),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_status CHECK (status IN
        ('trialing', 'active', 'past_due', 'canceled', 'expired', 'suspended')),
    CONSTRAINT chk_subscription_grace_days CHECK (grace_period_days >= 0)
);

COMMENT ON TABLE app.subscription IS
    '阶段4 工作区订阅。同一 workspace 同时只允许一条 trialing/active/past_due 订阅（部分唯一索引）';
COMMENT ON COLUMN app.subscription.plan_version_snapshot IS
    '订阅生效时冻结的套餐快照；套餐改价生成新版本后存量订阅读快照不受影响';
COMMENT ON COLUMN app.subscription.status IS
    '状态机：trialing -> active -> past_due -> (active | canceled)；到期未续费 -> expired';
COMMENT ON COLUMN app.subscription.pending_plan_id IS
    '降级目标套餐（周期末生效）；升级立即生效，不使用该列';
COMMENT ON COLUMN app.subscription.grace_period_days IS
    'past_due 宽限期天数，D9 决策默认 7 天';

-- 部分唯一索引：同一 workspace 只允许一条生效中的订阅
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_workspace_active
    ON app.subscription (workspace_id)
    WHERE status IN ('trialing', 'active', 'past_due');

CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_idempotency_key
    ON app.subscription (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscription_status_period_end
    ON app.subscription (status, current_period_end);

CREATE INDEX IF NOT EXISTS idx_subscription_workspace
    ON app.subscription (workspace_id, created_at DESC);

-- 外键
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_subscription_workspace'
          AND conrelid = 'app.subscription'::regclass
    ) THEN
        ALTER TABLE app.subscription
            ADD CONSTRAINT fk_subscription_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_subscription_plan'
          AND conrelid = 'app.subscription'::regclass
    ) THEN
        ALTER TABLE app.subscription
            ADD CONSTRAINT fk_subscription_plan
            FOREIGN KEY (plan_id) REFERENCES app.subscription_plan(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_subscription_pending_plan'
          AND conrelid = 'app.subscription'::regclass
    ) THEN
        ALTER TABLE app.subscription
            ADD CONSTRAINT fk_subscription_pending_plan
            FOREIGN KEY (pending_plan_id) REFERENCES app.subscription_plan(id) NOT VALID;
    END IF;
END
$$;

-- ============================================================
-- 3. ALTER app.workspace_quota：加周期额度列
--    只加列不改语义：daily_quota / used / reset_at 行为完全不变
-- ============================================================
ALTER TABLE app.workspace_quota
    ADD COLUMN IF NOT EXISTS plan_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS period_quota   INT,
    ADD COLUMN IF NOT EXISTS period_start   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS period_end     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS overage_policy VARCHAR(16) NOT NULL DEFAULT 'reject';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_workspace_quota_overage_policy'
    ) THEN
        ALTER TABLE app.workspace_quota
            ADD CONSTRAINT chk_workspace_quota_overage_policy
            CHECK (overage_policy IN ('reject', 'charge_oversage', 'downgrade'));
    END IF;
END
$$;

COMMENT ON COLUMN app.workspace_quota.plan_id IS
    '订阅绑定的套餐（app.subscription_plan.id）；订阅生效时写入';
COMMENT ON COLUMN app.workspace_quota.period_quota IS
    '周期额度（订阅套餐包含额度合计）；与原 daily_quota 并存，原语义不变';
COMMENT ON COLUMN app.workspace_quota.period_start IS '周期额度起始时间';
COMMENT ON COLUMN app.workspace_quota.period_end IS '周期额度结束时间';
COMMENT ON COLUMN app.workspace_quota.overage_policy IS
    '超额策略：reject / charge_oversage / downgrade，默认 reject';

-- ============================================================
-- 4. ALTER app.workspace：加订阅状态与套餐编码
-- ============================================================
ALTER TABLE app.workspace
    ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(16),
    ADD COLUMN IF NOT EXISTS plan_code         VARCHAR(32);

COMMENT ON COLUMN app.workspace.subscription_status IS
    '当前订阅状态（冗余自 app.subscription），便于列表查询与鉴权';
COMMENT ON COLUMN app.workspace.plan_code IS
    '当前套餐编码（冗余自 app.subscription），便于列表查询与鉴权';

-- ============================================================
-- 5. 预置 4 个套餐（幂等：ON CONFLICT DO NOTHING）
--
--    价格与额度为「初始建议值」，真实定价待业务定稿后由管理端生成新版本。
-- ============================================================
INSERT INTO app.subscription_plan
    (code, version, name, billing_period, price, currency, included_units, overage_price, feature_flags, status, description)
VALUES
    ('FREE', 1, '免费版', 'MONTH', 0.0000, 'CNY',
     '{"ANALYSIS":10,"TITLE_GENERATION":30,"BODY_GENERATION":10,"COVER_ANALYSIS":10,"COMPLIANCE_SCAN":30,"EXPORT":0,"EMBEDDING":200,"SEARCH":10}'::jsonb,
     0.0000,
     '{"competitor_library":false,"metrics_dashboard":false,"open_api":false,"webhook":false,"team_seats":false,"export":false,"api_key":false}'::jsonb,
     'active',
     '免费版：基础能力 + 极小额度；所有增值功能关闭。到期降级目标套餐。'),

    ('PRO', 1, '专业版', 'MONTH', 199.0000, 'CNY',
     '{"ANALYSIS":200,"TITLE_GENERATION":1000,"BODY_GENERATION":300,"COVER_ANALYSIS":200,"COMPLIANCE_SCAN":1000,"EXPORT":100,"EMBEDDING":5000,"SEARCH":100}'::jsonb,
     0.5000,
     '{"competitor_library":true,"metrics_dashboard":true,"open_api":false,"webhook":false,"team_seats":false,"export":true,"api_key":true}'::jsonb,
     'active',
     '专业版：个人/小团队主力订阅，含竞品库 + 指标看板 + 导出 + API Key。'),

    ('TEAM', 1, '团队版', 'MONTH', 599.0000, 'CNY',
     '{"ANALYSIS":1000,"TITLE_GENERATION":5000,"BODY_GENERATION":1500,"COVER_ANALYSIS":1000,"COMPLIANCE_SCAN":5000,"EXPORT":500,"EMBEDDING":25000,"SEARCH":500}'::jsonb,
     0.3000,
     '{"competitor_library":true,"metrics_dashboard":true,"open_api":true,"webhook":true,"team_seats":true,"export":true,"api_key":true}'::jsonb,
     'active',
     '团队版：机构与代运营场景，含成员席位、开放 API 与出站 Webhook。'),

    ('USAGE', 1, '按量计费版', 'MONTH', 0.0000, 'CNY',
     '{"ANALYSIS":0,"TITLE_GENERATION":0,"BODY_GENERATION":0,"COVER_ANALYSIS":0,"COMPLIANCE_SCAN":0,"EXPORT":0,"EMBEDDING":0,"SEARCH":0}'::jsonb,
     0.1000,
     '{"competitor_library":false,"metrics_dashboard":false,"open_api":true,"webhook":false,"team_seats":false,"export":false,"api_key":true}'::jsonb,
     'active',
     '按量计费版：无预付额度，全部按 overage_price 结算，D6 混合计费的兜底套餐。')
ON CONFLICT (code, version) DO NOTHING;
