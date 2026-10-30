-- V63：阶段 4 S4-2 支付网关与订单
--
-- 依据 STAGE4_PLAN.md「子能力 2：支付网关与订单」与「数据模型总表」：
--
-- 目标：
--   1. 新建 app.subscription_order       —— 支付订单（订阅订单 + 按量充值订单）
--   2. 新建 app.payment_event            —— 渠道事件去重表（回调幂等 / 重放保护）
--   3. 新建 app.billing_reconciliation   —— 对账差异记录
--
-- 决策约束：
--   D6 混合计费 —— order_type 区分 'subscription'（订阅）与 'usage'（按量充值）
--   D7 首发渠道 = 支付宝 + 微信支付（provider 字段承载，stripe 预留）
--   D9 预付 + 7 天宽限期（宽限期逻辑在 S4-1 已实现，本表只负责下单）
--
-- 约束：
--   - 全部幂等（IF NOT EXISTS / DO $$ 守卫 / ON CONFLICT），可重复执行
--   - 金额一律 NUMERIC(18,4)
--   - workspace_id 一律 VARCHAR(32)，与 app.workspace.id 一致
--   - order_no 全局唯一（VARCHAR(32) UNIQUE）

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.subscription_order：支付订单
-- ============================================================
CREATE TABLE IF NOT EXISTS app.subscription_order (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 订单号全局唯一：SO + 时间戳 + 随机
    order_no          VARCHAR(32)   NOT NULL,
    workspace_id      VARCHAR(32)   NOT NULL,
    plan_id           UUID,
    -- 下单时冻结的套餐版本号
    plan_version      INT,
    -- subscription（订阅）/ usage（按量充值）
    order_type        VARCHAR(16)   NOT NULL DEFAULT 'subscription',
    quantity          INT           NOT NULL DEFAULT 1,
    amount            NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    -- alipay / wechat / mock / stripe
    provider          VARCHAR(16)   NOT NULL,
    -- 渠道侧订单号
    provider_order_id VARCHAR(128),
    -- pending / paid / fulfilled / refunded / closed
    status            VARCHAR(16)   NOT NULL DEFAULT 'pending',
    -- 渠道返回的支付参数（JSONB），不含任何凭据
    payment_payload   JSONB         NOT NULL DEFAULT '{}'::jsonb,
    paid_at           TIMESTAMPTZ,
    fulfilled_at      TIMESTAMPTZ,
    -- 订单过期时间（pending 超时 -> closed）
    expires_at        TIMESTAMPTZ,
    idempotency_key   VARCHAR(128),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_order_no UNIQUE (order_no),
    CONSTRAINT chk_subscription_order_status CHECK (status IN
        ('pending', 'paid', 'fulfilled', 'refunded', 'closed')),
    CONSTRAINT chk_subscription_order_type CHECK (order_type IN
        ('subscription', 'usage')),
    CONSTRAINT chk_subscription_order_amount CHECK (amount >= 0),
    CONSTRAINT chk_subscription_order_quantity CHECK (quantity >= 1)
);

COMMENT ON TABLE app.subscription_order IS
    '阶段4 S4-2 支付订单：订阅订单 + 按量充值订单。状态机 pending -> paid -> (fulfilled | refunded | closed)';
COMMENT ON COLUMN app.subscription_order.order_no IS
    '订单号，全局唯一；生成规则 SO + 时间戳 + 随机';
COMMENT ON COLUMN app.subscription_order.order_type IS
    'subscription = 订阅订单；usage = 按量充值订单（D6 混合计费）';
COMMENT ON COLUMN app.subscription_order.amount IS '订单金额 NUMERIC(18,4)，单位 = currency';
COMMENT ON COLUMN app.subscription_order.payment_payload IS
    '渠道返回的支付参数（JSONB）；严禁写入任何渠道凭据 / 签名密钥';
COMMENT ON COLUMN app.subscription_order.provider IS '支付渠道：alipay / wechat / mock / stripe';

CREATE INDEX IF NOT EXISTS idx_subscription_order_workspace
    ON app.subscription_order (workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_subscription_order_status
    ON app.subscription_order (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_subscription_order_expires
    ON app.subscription_order (expires_at)
    WHERE status = 'pending';

CREATE INDEX IF NOT EXISTS idx_subscription_order_provider
    ON app.subscription_order (provider, provider_order_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_order_idempotency_key
    ON app.subscription_order (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_subscription_order_workspace'
          AND conrelid = 'app.subscription_order'::regclass
    ) THEN
        ALTER TABLE app.subscription_order
            ADD CONSTRAINT fk_subscription_order_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_subscription_order_plan'
          AND conrelid = 'app.subscription_order'::regclass
    ) THEN
        ALTER TABLE app.subscription_order
            ADD CONSTRAINT fk_subscription_order_plan
            FOREIGN KEY (plan_id) REFERENCES app.subscription_plan(id) NOT VALID;
    END IF;
END
$$;

-- ============================================================
-- 2. app.payment_event：渠道事件去重表（回调幂等 / 重放保护）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.payment_event (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider     VARCHAR(16)  NOT NULL,
    -- 渠道事件 ID（支付宝 trade_no / 微信 transaction_id 等），同一渠道唯一
    event_id     VARCHAR(128) NOT NULL,
    -- 关联订单号（可能为空：渠道账单里出现本地不存在的订单）
    order_no     VARCHAR(32),
    event_type   VARCHAR(32)  NOT NULL DEFAULT 'payment.succeeded',
    -- 渠道上报金额 NUMERIC(18,4)
    amount       NUMERIC(18,4),
    currency     VARCHAR(8),
    -- 事件时间（渠道侧）
    occurred_at  TIMESTAMPTZ,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    -- processed / rejected / duplicate / failed
    result       VARCHAR(16)  NOT NULL DEFAULT 'processed',
    -- 失败原因（如 signature_invalid / amount_mismatch），不含凭据
    reason       VARCHAR(64),
    CONSTRAINT uq_payment_event_provider_event UNIQUE (provider, event_id),
    CONSTRAINT chk_payment_event_result CHECK (result IN
        ('processed', 'rejected', 'duplicate', 'failed'))
);

COMMENT ON TABLE app.payment_event IS
    '阶段4 S4-2 渠道支付事件去重表：同一 (provider, event_id) 只处理一次，实现回调幂等与重放保护';
COMMENT ON COLUMN app.payment_event.event_id IS '渠道事件 ID，全局唯一键的组成部分';
COMMENT ON COLUMN app.payment_event.result IS 'processed / rejected / duplicate / failed';

CREATE INDEX IF NOT EXISTS idx_payment_event_order_no
    ON app.payment_event (order_no);

CREATE INDEX IF NOT EXISTS idx_payment_event_received
    ON app.payment_event (received_at DESC);

-- ============================================================
-- 3. app.billing_reconciliation：对账差异记录
-- ============================================================
CREATE TABLE IF NOT EXISTS app.billing_reconciliation (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID,
    order_no        VARCHAR(32),
    provider        VARCHAR(16)  NOT NULL,
    local_status    VARCHAR(16),
    provider_status VARCHAR(16),
    -- local_paid_channel_unpaid / local_unpaid_channel_paid / amount_mismatch
    diff_type       VARCHAR(48)  NOT NULL,
    amount_diff     NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- open / resolved / ignored
    status          VARCHAR(16)  NOT NULL DEFAULT 'open',
    resolved_by     VARCHAR(64),
    resolved_at     TIMESTAMPTZ,
    resolution_note VARCHAR(512),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_reconciliation_diff CHECK (diff_type IN
        ('local_paid_channel_unpaid',
         'local_unpaid_channel_paid',
         'amount_mismatch')),
    CONSTRAINT chk_billing_reconciliation_status CHECK (status IN
        ('open', 'resolved', 'ignored'))
);

COMMENT ON TABLE app.billing_reconciliation IS
    '阶段4 S4-2 对账差异：本地订单 vs 渠道账单；差异可人工标记处理状态';
COMMENT ON COLUMN app.billing_reconciliation.diff_type IS
    'local_paid_channel_unpaid（本地已付渠道未付）/ local_unpaid_channel_paid（本地未付渠道已付）/ amount_mismatch（金额不一致）';
COMMENT ON COLUMN app.billing_reconciliation.amount_diff IS
    '渠道金额 - 本地金额，NUMERIC(18,4)';

CREATE INDEX IF NOT EXISTS idx_billing_reconciliation_status
    ON app.billing_reconciliation (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_reconciliation_order
    ON app.billing_reconciliation (order_no);

-- 同一订单同一差异类型不重复记录（重跑对账幂等）
CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_reconciliation_order_diff
    ON app.billing_reconciliation (COALESCE(order_no, ''), provider, diff_type);
