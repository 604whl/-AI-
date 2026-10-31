-- V67：阶段 4 S4-4 退款与发票
--
-- 依据 STAGE4_PLAN.md「子能力 4：退款与发票」：
--   4.1 退款 —— app.refund_order
--   4.2 发票 —— app.invoice
--
-- 目标：
--   1. 新建 app.refund_order  退款单（申请 → 审核 → 渠道退款 → 回调确认 → 权益冲正）
--   2. 新建 app.invoice       发票（含红冲，不物理删除原发票）
--
-- 冻结决策：
--   D10 发票类型范围：A —— 仅个人普票 + 企业普票（不含专票）
--
-- 设计要点：
--   - 退款走「申请 / 审核」两段式（S4-4 明确要求：用户申请 → 管理员审核 → 调渠道退款）
--     与 S4-2 已有的「管理端直接退款」并存：
--       * admin 退款（app.refund_order 之外）保留原语义，作为运营快捷通道；
--       * 本表的 pending → approved → refunded 是需要审核留痕的正式退款流程。
--   - 幂等：
--       * 退款单号 refund_no 全局唯一
--       * (workspace_id, idempotency_key) 唯一索引 → 同工作区同键只生成一张退款单
--       * provider_refund_id 唯一 → 同一渠道退款单只生效一次（回调幂等 / 重放保护）
--   - 发票号 invoice_no 全局唯一；红冲用 status='red_flushed' + red_flush_invoice_id 关联，
--     不物理删除原发票（留档要求）。
--
-- 约束：
--   - 全部幂等（IF NOT EXISTS / DO $$ 守卫 / ON CONFLICT），可重复执行
--   - 金额一律 NUMERIC(18,4)
--   - workspace_id 一律 VARCHAR(32)，与 app.workspace.id 一致

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.refund_order：退款单
-- ============================================================
CREATE TABLE IF NOT EXISTS app.refund_order (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 退款单号，全局唯一：RF + 时间戳 + 随机
    refund_no          VARCHAR(32)   NOT NULL,
    -- 关联订单（app.subscription_order.id）
    order_id           UUID          NOT NULL,
    order_no           VARCHAR(32)   NOT NULL,
    workspace_id       VARCHAR(32)   NOT NULL,
    -- 退款金额（不得超过订单实付金额）
    amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency           VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    -- 用户申请时填的原因
    reason             VARCHAR(512)  NOT NULL,
    -- pending（待审核）/ approved（已通过待调渠道）/ rejected（已驳回）
    -- refunded（渠道已退款且权益已冲正）/ failed（渠道退款失败）
    status             VARCHAR(16)   NOT NULL DEFAULT 'pending',
    -- 申请人（工作区 OWNER）
    requested_by       BIGINT,
    -- 审核人（平台管理员 user id）
    reviewed_by        BIGINT,
    reviewed_at        TIMESTAMPTZ,
    -- 审核意见
    review_note        VARCHAR(512),
    -- 渠道侧退款单号（支付宝 / 微信），唯一 → 回调幂等
    provider_refund_id VARCHAR(128),
    -- 退款渠道（与订单一致）
    provider           VARCHAR(16),
    -- 渠道退款完成时间
    refunded_at        TIMESTAMPTZ,
    -- 失败原因（如 provider_refund_failed），不含凭据
    failure_reason     VARCHAR(128),
    -- 幂等键：同工作区同键只生成一张退款单
    idempotency_key    VARCHAR(128),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refund_order_no UNIQUE (refund_no),
    CONSTRAINT chk_refund_order_status CHECK (status IN
        ('pending', 'approved', 'rejected', 'refunded', 'failed')),
    CONSTRAINT chk_refund_order_amount CHECK (amount > 0)
);

COMMENT ON TABLE app.refund_order IS
    '阶段4 S4-4 退款单：pending -> approved -> refunded（渠道退款成功 + 权益冲正）/ rejected / failed';
COMMENT ON COLUMN app.refund_order.refund_no IS '退款单号，全局唯一；生成规则 RF + 时间戳 + 随机';
COMMENT ON COLUMN app.refund_order.order_no IS '冗余订单号，便于运营检索与对账';
COMMENT ON COLUMN app.refund_order.amount IS '退款金额 NUMERIC(18,4)，不得超过订单实付金额';
COMMENT ON COLUMN app.refund_order.provider_refund_id IS
    '渠道侧退款单号，唯一；同一渠道退款单只生效一次（回调幂等 / 重放保护）';
COMMENT ON COLUMN app.refund_order.idempotency_key IS
    '幂等键；(workspace_id, idempotency_key) 唯一索引保证同工作区同键只生成一张退款单';

CREATE INDEX IF NOT EXISTS idx_refund_order_workspace
    ON app.refund_order (workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_refund_order_status
    ON app.refund_order (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_refund_order_order
    ON app.refund_order (order_id);

-- 同工作区同幂等键只生成一张退款单
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_order_idempotency
    ON app.refund_order (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 渠道退款单号唯一：同一渠道退款事件重复投递只生效一次
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_order_provider_refund
    ON app.refund_order (provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refund_order_order'
          AND conrelid = 'app.refund_order'::regclass
    ) THEN
        ALTER TABLE app.refund_order
            ADD CONSTRAINT fk_refund_order_order
            FOREIGN KEY (order_id) REFERENCES app.subscription_order(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refund_order_workspace'
          AND conrelid = 'app.refund_order'::regclass
    ) THEN
        ALTER TABLE app.refund_order
            ADD CONSTRAINT fk_refund_order_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$$;

-- ============================================================
-- 2. app.invoice：发票
--
-- D10：仅个人普票 + 企业普票
--   invoice_type = personal（个人普票）/ company（企业普票）
--
-- 红冲（red flush）：
--   已开票订单退款时，需对原发票开一张红字发票。
--   实现方式：原发票 status -> 'red_flushed'，并写一张 status='red' 的红冲记录，
--   两者用 red_flush_invoice_id 互指。**原发票不物理删除**（留档要求）。
-- ============================================================
CREATE TABLE IF NOT EXISTS app.invoice (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 发票号，全局唯一：INV + 年月 + 序列
    invoice_no           VARCHAR(32)   NOT NULL,
    workspace_id         VARCHAR(32)   NOT NULL,
    -- personal（个人普票）/ company（企业普票）
    invoice_type         VARCHAR(16)   NOT NULL DEFAULT 'personal',
    -- 发票抬头（企业名或个人姓名）
    title                VARCHAR(128)  NOT NULL,
    -- 税号（企业票必填；个人票可空）
    tax_no               VARCHAR(64),
    -- 开票金额 NUMERIC(18,4)
    amount               NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency             VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    -- 税率（普票默认 0.06；个人普票可为 0）
    tax_rate             NUMERIC(6,4)  NOT NULL DEFAULT 0.0600,
    -- 税额
    tax_amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    -- issued（已开票）/ red（红冲记录）/ red_flushed（已被红冲）
    status               VARCHAR(16)   NOT NULL DEFAULT 'issued',
    -- 关联订单（可选：按账单开票时为空）
    order_id             UUID,
    order_no             VARCHAR(32),
    -- 关联账单（可选）
    statement_id         VARCHAR(32),
    -- PDF 存储路径（MinIO object key 或本地相对路径）
    pdf_path             VARCHAR(512),
    -- 红冲关联：红冲记录指向原发票；原发票指向红冲记录
    red_flush_invoice_id UUID,
    -- 开票人（平台管理员 user id）
    issued_by            BIGINT,
    issued_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    idempotency_key      VARCHAR(128),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_invoice_no UNIQUE (invoice_no),
    CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('personal', 'company')),
    CONSTRAINT chk_invoice_status CHECK (status IN ('issued', 'red', 'red_flushed')),
    CONSTRAINT chk_invoice_amount CHECK (amount >= 0)
);

COMMENT ON TABLE app.invoice IS
    '阶段4 S4-4 发票：D10 仅个人普票 + 企业普票；红冲用 status 标记，原发票不物理删除';
COMMENT ON COLUMN app.invoice.invoice_no IS '发票号，全局唯一；生成规则 INV + 年月 + 序列';
COMMENT ON COLUMN app.invoice.invoice_type IS 'personal 个人普票 / company 企业普票（D10：不支持专票）';
COMMENT ON COLUMN app.invoice.title IS '发票抬头：企业名或个人姓名';
COMMENT ON COLUMN app.invoice.tax_no IS '纳税人识别号；企业票必填，个人票可空';
COMMENT ON COLUMN app.invoice.status IS 'issued 已开票 / red 红冲记录 / red_flushed 已被红冲';
COMMENT ON COLUMN app.invoice.red_flush_invoice_id IS
    '红冲关联：红冲记录指向原发票；原发票指向红冲记录（互指，便于双向追溯）';
COMMENT ON COLUMN app.invoice.pdf_path IS 'PDF 存储路径（MinIO object key），下载走签名 URL';

CREATE INDEX IF NOT EXISTS idx_invoice_workspace
    ON app.invoice (workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invoice_order
    ON app.invoice (order_id);

CREATE INDEX IF NOT EXISTS idx_invoice_statement
    ON app.invoice (statement_id);

-- 同工作区同幂等键只生成一张发票
CREATE UNIQUE INDEX IF NOT EXISTS uq_invoice_idempotency
    ON app.invoice (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 同一订单最多一张有效发票（红冲记录不受此限）
CREATE UNIQUE INDEX IF NOT EXISTS uq_invoice_active_order
    ON app.invoice (order_id)
    WHERE order_id IS NOT NULL AND status IN ('issued', 'red_flushed');

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_invoice_workspace'
          AND conrelid = 'app.invoice'::regclass
    ) THEN
        ALTER TABLE app.invoice
            ADD CONSTRAINT fk_invoice_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_invoice_order'
          AND conrelid = 'app.invoice'::regclass
    ) THEN
        ALTER TABLE app.invoice
            ADD CONSTRAINT fk_invoice_order
            FOREIGN KEY (order_id) REFERENCES app.subscription_order(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_invoice_red_flush'
          AND conrelid = 'app.invoice'::regclass
    ) THEN
        ALTER TABLE app.invoice
            ADD CONSTRAINT fk_invoice_red_flush
            FOREIGN KEY (red_flush_invoice_id) REFERENCES app.invoice(id) NOT VALID;
    END IF;
END
$$;
