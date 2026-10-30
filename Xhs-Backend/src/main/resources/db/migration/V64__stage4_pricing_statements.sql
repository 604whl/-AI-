-- V64：阶段 4 S4-3 按量计费、余额、成本与毛利
--
-- 对齐 STAGE4_PLAN.md 子能力 3（3.1 计价与价格表 / 3.2 余额与充值 / 3.3 用量→账单）
--
-- 目标：
--   1. 新建 app.price_book          价格表（单价版本化，改价不追溯历史账单）
--   2. 新建 app.workspace_balance   工作区余额（原子扣减，避免并发下负余额）
--   3. 新建 app.billing_statement   账单头（幂等键唯一）
--   4. 新建 app.billing_statement_item 账单行（可回溯 usage_ledger）
--   5. ALTER app.usage_ledger       仅加列（不改 delta 语义，历史 amount 允许 NULL）
--
-- 冻结决策：
--   D6  混合计费：订阅为主 + 按量兜底 → 价格表 + 余额 + 用量账单
--   D12 成本口径：B 调用次数为主 + 存储按量 → 每个计量单位同时有 unit_price 与 cost_per_unit
--   D9  预付 + 7 天宽限期（余额为预付形态）
--
-- 幂等性：
--   全部 IF NOT EXISTS / DO $$ 守卫 / ON CONFLICT，可重复执行
--
-- 约束：
--   - 金额一律 NUMERIC(18,4)
--   - workspace_id 一律 VARCHAR(32)，与 app.workspace.id 对齐
--   - usage_ledger 只加列不改语义：delta 仍是 +1 扣减 / -1 冲正
--
-- SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.price_book：价格表
--    唯一键：unit + region + plan_version（套餐版本）
--    同时 (unit, region, version) 唯一，version 为价格行版本，
--    ledger.price_version 引用它，便于按历史版本回算成本。
--    改价 = 插入新版本行（version+1 / plan_version+1），
--    旧版本行 effective_from 原样保留，历史账单金额不受影响。
-- ============================================================
CREATE TABLE IF NOT EXISTS app.price_book (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- BillableUnit 枚举名：ANALYSIS / TITLE_GENERATION / BODY_GENERATION
    --                / COVER_ANALYSIS / COMPLIANCE_SCAN / EXPORT / EMBEDDING / SEARCH
    unit           VARCHAR(32)  NOT NULL,
    -- 地区，默认 CN
    region         VARCHAR(8)   NOT NULL DEFAULT 'CN',
    -- 套餐版本（订阅快照里的 version），1 表示标准价
    plan_version   INT          NOT NULL DEFAULT 1,
    -- 价格行版本：改价时递增
    version        INT          NOT NULL DEFAULT 1,
    -- 收入侧单价（每次计费单元的价格）
    unit_price     NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- 成本侧单价（D12 口径：B 调用次数为主 + 存储按量）
    cost_per_unit  NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency       VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    -- 生效时间；旧版本行保留原值
    effective_from TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_price_book_unit_region_plan_version UNIQUE (unit, region, plan_version),
    CONSTRAINT uq_price_book_unit_region_version UNIQUE (unit, region, version),
    CONSTRAINT chk_price_book_unit CHECK (unit IN (
        'ANALYSIS', 'TITLE_GENERATION', 'BODY_GENERATION', 'COVER_ANALYSIS',
        'COMPLIANCE_SCAN', 'EXPORT', 'EMBEDDING', 'SEARCH')),
    CONSTRAINT chk_price_book_version CHECK (version >= 1 AND plan_version >= 1),
    CONSTRAINT chk_price_book_amount CHECK (unit_price >= 0 AND cost_per_unit >= 0)
);

COMMENT ON TABLE app.price_book IS
    '阶段4 S4-3 价格表：unit + region + plan_version 唯一；改价插入新版本行，不追溯历史账单';
COMMENT ON COLUMN app.price_book.unit IS '计量单位，取 BillableUnit 枚举名';
COMMENT ON COLUMN app.price_book.region IS '地区，默认 CN';
COMMENT ON COLUMN app.price_book.plan_version IS '套餐版本；与 app.subscription_plan.version 语义对齐';
COMMENT ON COLUMN app.price_book.version IS '价格行版本，改价递增；usage_ledger.price_version 引用它';
COMMENT ON COLUMN app.price_book.unit_price IS '收入侧单价 NUMERIC(18,4)，单位=1 个计费单元';
COMMENT ON COLUMN app.price_book.cost_per_unit IS '成本侧单价 NUMERIC(18,4)，D12 口径';

CREATE INDEX IF NOT EXISTS idx_price_book_unit_region_effective
    ON app.price_book (unit, region, effective_from DESC);

-- ============================================================
-- 2. app.workspace_balance：工作区余额（按量计费预付）
--    workspace_id 唯一；余额扣减由 BalanceService 用
--    UPDATE ... WHERE balance >= ? 的单条原子 SQL 完成
-- ============================================================
CREATE TABLE IF NOT EXISTS app.workspace_balance (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id VARCHAR(32)  NOT NULL,
    -- 可用余额
    balance      NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency     VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    -- 冻结金额（预授权 / 未支付账单占用）
    frozen       NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- active / frozen / closed
    status       VARCHAR(16)  NOT NULL DEFAULT 'active',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workspace_balance_workspace UNIQUE (workspace_id),
    CONSTRAINT chk_workspace_balance_nonneg CHECK (balance >= 0 AND frozen >= 0),
    CONSTRAINT chk_workspace_balance_status CHECK (status IN ('active', 'frozen', 'closed'))
);

COMMENT ON TABLE app.workspace_balance IS
    '阶段4 S4-3 工作区余额（USAGE 按量套餐预付形态）；余额变动全部写 app.usage_ledger 流水';
COMMENT ON COLUMN app.workspace_balance.balance IS '可用余额 NUMERIC(18,4)，扣减需保证不出现负值';
COMMENT ON COLUMN app.workspace_balance.frozen IS '冻结金额，用于未支付账单占用';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_workspace_balance_workspace'
          AND conrelid = 'app.workspace_balance'::regclass
    ) THEN
        ALTER TABLE app.workspace_balance
            ADD CONSTRAINT fk_workspace_balance_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$$;

-- ============================================================
-- 3. app.billing_statement：账单头
--    id 用 32 位十六进制（UUID 去横线），以便 usage_ledger.statement_id 用 VARCHAR(32)
--    idempotency_key 唯一 → 同工作区同周期不重复生成
-- ============================================================
CREATE TABLE IF NOT EXISTS app.billing_statement (
    id              VARCHAR(32)  PRIMARY KEY,
    workspace_id    VARCHAR(32)  NOT NULL,
    period_start    TIMESTAMPTZ  NOT NULL,
    period_end      TIMESTAMPTZ  NOT NULL,
    total_amount    NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    -- open（未支付）/ charging（扣费中）/ paid / void / failed
    status          VARCHAR(16)  NOT NULL DEFAULT 'open',
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- 幂等键：workspace_id + 周期起止
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_statement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_billing_statement_status CHECK (status IN ('open', 'charging', 'paid', 'void', 'failed')),
    CONSTRAINT chk_billing_statement_period CHECK (period_end > period_start)
);

COMMENT ON TABLE app.billing_statement IS
    '阶段4 S4-3 账单头：按订阅周期聚合 usage_ledger 生成；idempotency_key 保证同周期幂等';
COMMENT ON COLUMN app.billing_statement.id IS '32 位十六进制 ID（UUID 去横线），与 usage_ledger.statement_id 对齐';
COMMENT ON COLUMN app.billing_statement.idempotency_key IS 'workspace_id + 周期起止，重复生成时命中唯一约束';
COMMENT ON COLUMN app.billing_statement.status IS 'open 未支付（不扣费）/ charging 扣费中 / paid 已支付 / void 作废 / failed 失败';

CREATE INDEX IF NOT EXISTS idx_billing_statement_workspace
    ON app.billing_statement (workspace_id, period_start DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_billing_statement_workspace'
          AND conrelid = 'app.billing_statement'::regclass
    ) THEN
        ALTER TABLE app.billing_statement
            ADD CONSTRAINT fk_billing_statement_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$$;

-- ============================================================
-- 4. app.billing_statement_item：账单行
--    ledger_ref     = 账单行对应的首个 usage_ledger.id（UUID 文本，36 字符）
--    ledger_count   = 该行覆盖的 ledger 明细条数
--    ledger_range   = "首个 ledger id..最后一个 ledger id" 的闭区间描述
--                     （ledger_ref 单列放不下两端，因此额外提供 range 列做完整回溯）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.billing_statement_item (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(32)  NOT NULL,
    -- BillableUnit 枚举名
    unit         VARCHAR(32)  NOT NULL,
    quantity     NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit_price   NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount       NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- 回溯锚点：首个 usage_ledger.id
    ledger_ref   VARCHAR(64),
    -- 回溯区间：首个..最后一个 usage_ledger.id
    ledger_range VARCHAR(160),
    -- 该行覆盖的 ledger 明细条数
    ledger_count INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_statement_item_statement
        FOREIGN KEY (statement_id) REFERENCES app.billing_statement(id) ON DELETE CASCADE
);

COMMENT ON TABLE app.billing_statement_item IS
    '阶段4 S4-3 账单行：按计量单位聚合 usage_ledger；ledger_ref / ledger_range 回溯原始明细';
COMMENT ON COLUMN app.billing_statement_item.ledger_ref IS '首个 usage_ledger.id（回溯锚点）';
COMMENT ON COLUMN app.billing_statement_item.ledger_range IS '首个..最后一个 usage_ledger.id 闭区间';
COMMENT ON COLUMN app.billing_statement_item.ledger_count IS '该行覆盖的 ledger 明细条数';

CREATE INDEX IF NOT EXISTS idx_billing_statement_item_statement
    ON app.billing_statement_item (statement_id);

-- ============================================================
-- 5. ALTER app.usage_ledger：仅加列，不改语义
--    delta 仍是 +1 扣减 / -1 冲正
--    历史数据 amount 允许 NULL（不回填、不加 NOT NULL）
--    provider 记录供应商/模型来源，便于成本归因
-- ============================================================
ALTER TABLE app.usage_ledger
    ADD COLUMN IF NOT EXISTS unit_price   NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS amount       NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS price_version INT,
    ADD COLUMN IF NOT EXISTS statement_id  VARCHAR(32),
    ADD COLUMN IF NOT EXISTS provider      VARCHAR(32);

COMMENT ON COLUMN app.usage_ledger.unit_price IS
    '计费单价快照 NUMERIC(18,4)，取自 app.price_book；历史数据为 NULL';
COMMENT ON COLUMN app.usage_ledger.amount IS
    '计费金额快照 NUMERIC(18,4)，= unit_price * delta；历史数据允许 NULL，不回填';
COMMENT ON COLUMN app.usage_ledger.price_version IS
    '价格版本，对应 app.price_book.version，用于按历史版本回算成本';
COMMENT ON COLUMN app.usage_ledger.statement_id IS
    '已归集到的账单 ID（app.billing_statement.id）；NULL 表示未出账';
COMMENT ON COLUMN app.usage_ledger.provider IS
    '供应商/模型来源（如 dashscope / mock），用于成本归因';

CREATE INDEX IF NOT EXISTS idx_usage_ledger_statement
    ON app.usage_ledger (statement_id)
    WHERE statement_id IS NOT NULL;

-- ============================================================
-- 6. 预置价格表数据（region=CN，plan_version=1，version=1）
--
--    ⚠️ 以下单价与成本均为「初始建议值，待业务定稿」。
--
--    收入侧 unit_price 建议值（CNY / 次）：
--      ANALYSIS 0.50 / TITLE_GENERATION 0.10 / BODY_GENERATION 0.30
--      COVER_ANALYSIS 0.20 / COMPLIANCE_SCAN 0.02 / EXPORT 0.05
--      EMBEDDING 0.01 / SEARCH 0.05
--
--    成本侧 cost_per_unit 换算口径（D12）：
--      docs/BILLING-RULES.md 的「典型成本系数」是相对权重、不是金额。
--      换算方式：cost_per_unit = 成本系数 × 基准单位成本，
--      基准单位成本取 ANALYSIS = 0.1200 CNY / 次（一次完整内容分析的模型调用成本估算）。
--      于是：
--        ANALYSIS          1.00 × 0.1200 = 0.1200
--        TITLE_GENERATION  0.20 × 0.1200 = 0.0240
--        BODY_GENERATION   0.60 × 0.1200 = 0.0720
--        COVER_ANALYSIS    0.40 × 0.1200 = 0.0480
--        COMPLIANCE_SCAN   0.05 × 0.1200 = 0.0060
--        EXPORT            0.05 × 0.1200 = 0.0060
--        EMBEDDING         0.10 × 0.1200 = 0.0120
--        SEARCH            0.10 × 0.1200 = 0.0120
--      基准值 0.1200 同样是初始建议值，业务定稿后应通过插入新版本行修正，
--      不修改本批历史行（保证已出账金额可复算）。
-- ============================================================
INSERT INTO app.price_book
    (unit, region, plan_version, version, unit_price, cost_per_unit, currency, effective_from)
VALUES
    ('ANALYSIS',          'CN', 1, 1, 0.5000, 0.1200, 'CNY', NOW()),
    ('TITLE_GENERATION',  'CN', 1, 1, 0.1000, 0.0240, 'CNY', NOW()),
    ('BODY_GENERATION',   'CN', 1, 1, 0.3000, 0.0720, 'CNY', NOW()),
    ('COVER_ANALYSIS',    'CN', 1, 1, 0.2000, 0.0480, 'CNY', NOW()),
    ('COMPLIANCE_SCAN',   'CN', 1, 1, 0.0200, 0.0060, 'CNY', NOW()),
    ('EXPORT',            'CN', 1, 1, 0.0500, 0.0060, 'CNY', NOW()),
    ('EMBEDDING',         'CN', 1, 1, 0.0100, 0.0120, 'CNY', NOW()),
    ('SEARCH',            'CN', 1, 1, 0.0500, 0.0120, 'CNY', NOW())
ON CONFLICT (unit, region, plan_version) DO NOTHING;
