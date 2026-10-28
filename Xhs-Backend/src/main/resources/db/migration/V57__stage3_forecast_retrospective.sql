-- V57：阶段 3 —— 预测存储、偏差计算、复盘报告与回写审批
--
-- 目标（对应 STAGE3_ACCEPTANCE.md 子能力 3「预测 vs 实际对比与复盘报告」）：
--   1. app.metric_forecast        —— AI 预测值（与实际值分字段存储，绝不混在同一列）
--   2. app.metric_comparison      —— 预测 vs 实际偏差（每个维度一行）
--   3. app.retrospective_report   —— 复盘报告（含建议 / 命中维度 / 失败维度）
--   4. app.retrospective_feedback —— 回写模板 / 知识库效果统计的审批流
--
-- 幂等：所有 DDL 均为 IF NOT EXISTS / DO $$ 守卫，可重复执行。
-- 兼容性：
--   - app.template_apply_log 复用（只加列 effect_stats，不新建模板统计表，符合验收文档"不新建模板统计表"）
--   - app.metric_snapshot / app.content_asset / app.workspace 只读引用，不改结构
--
-- 风险对应（R2 指标数据质量依赖手动录入）：
--   数据不足时复盘必须输出 INSUFFICIENT_DATA 而非编造建议 —— 由服务层判定，
--   表结构上通过 status CHECK 约束把该状态固化为一等公民。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.metric_forecast —— 预测值（AI 预测，与实际值分字段）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.metric_forecast (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id          VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    asset_id              UUID         REFERENCES app.content_asset(id) ON DELETE SET NULL,
    platform_code         VARCHAR(32),
    forecast_type         VARCHAR(32)  NOT NULL,
    -- TITLE_CTR / IMPRESSION / ENGAGEMENT / CONVERSION
    predicted_metrics     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 预测发布时间（预测口径的时间锚点）
    predicted_published_at TIMESTAMPTZ,
    -- 预测来源与时间（必须与实际的 metric_source / collected_at 同屏展示）
    model_name            VARCHAR(64),
    prompt_version        VARCHAR(32),
    kb_version            VARCHAR(32),
    confidence            VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    source_task_id        UUID,
    -- 幂等键（同工作区同键重复提交只产生一条预测）
    idempotency_key       VARCHAR(128),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT metric_forecast_type_check CHECK (forecast_type IN (
        'TITLE_CTR', 'IMPRESSION', 'ENGAGEMENT', 'CONVERSION')),
    CONSTRAINT metric_forecast_confidence_check CHECK (confidence IN (
        'HIGH', 'MEDIUM', 'LOW', 'UNVERIFIED'))
);

COMMENT ON TABLE app.metric_forecast IS
    '阶段3：AI 预测值。与实际值严格分表存储——预测只在本表 predicted_metrics，实际只存 app.metric_snapshot.metrics。';
COMMENT ON COLUMN app.metric_forecast.predicted_metrics IS
    '统一口径预测值 JSONB，固定 10 键（impressions/plays/clicks/likes/collects/comments/shares/follows/dm_inquiries/conversions）';
COMMENT ON COLUMN app.metric_forecast.generated_at IS '预测生成时间（与实际 collected_at 同屏展示）';
COMMENT ON COLUMN app.metric_forecast.model_name IS '预测所用模型（来源标识）';
COMMENT ON COLUMN app.metric_forecast.prompt_version IS '预测所用提示词版本（来源标识）';
COMMENT ON COLUMN app.metric_forecast.kb_version IS '预测所用知识库版本（来源标识）';

CREATE INDEX IF NOT EXISTS idx_metric_forecast_ws_asset
    ON app.metric_forecast (workspace_id, asset_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_metric_forecast_ws_platform
    ON app.metric_forecast (workspace_id, platform_code, generated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_forecast_idempotency
    ON app.metric_forecast (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ============================================================
-- 2. app.metric_comparison —— 预测 vs 实际偏差（每维度一行）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.metric_comparison (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      VARCHAR(32)   NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    forecast_id       UUID          NOT NULL REFERENCES app.metric_forecast(id) ON DELETE CASCADE,
    snapshot_id       UUID          NOT NULL REFERENCES app.metric_snapshot(id) ON DELETE CASCADE,
    asset_id          UUID          REFERENCES app.content_asset(id) ON DELETE SET NULL,
    dimension         VARCHAR(32)   NOT NULL,
    -- impressions / clicks / likes / collects / comments / shares / dm_inquiries / conversions
    predicted_value   NUMERIC(20, 4) NOT NULL DEFAULT 0,
    actual_value      NUMERIC(20, 4) NOT NULL DEFAULT 0,
    deviation_abs     NUMERIC(20, 4),
    -- 偏差率；实际值为 0 或预测值为 0 时置 NULL（不写 Infinity，不写 0 假装一致）
    deviation_pct     NUMERIC(12, 4),
    hit               VARCHAR(16)   NOT NULL,
    -- HIT / MISS / PARTIAL
    dimension_category VARCHAR(32)  NOT NULL,
    -- TITLE / COVER / TIMING / CTA / CONTENT_BODY / DISTRIBUTION
    reason            TEXT,
    computed_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    idempotency_key   VARCHAR(128),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT metric_comparison_hit_check CHECK (hit IN ('HIT', 'MISS', 'PARTIAL')),
    CONSTRAINT metric_comparison_category_check CHECK (dimension_category IN (
        'TITLE', 'COVER', 'TIMING', 'CTA', 'CONTENT_BODY', 'DISTRIBUTION'))
);

COMMENT ON TABLE app.metric_comparison IS
    '阶段3：预测 vs 实际偏差。每个维度一行；hit 判定 实际/预测 ≥0.8 → HIT，≤0.5 → MISS，中间 → PARTIAL。';
COMMENT ON COLUMN app.metric_comparison.deviation_pct IS '偏差率（%）；实际为 0 或预测为 0 时为 NULL';
COMMENT ON COLUMN app.metric_comparison.reason IS '该维度的偏差原因说明（可追溯）';

CREATE INDEX IF NOT EXISTS idx_metric_comparison_ws_asset
    ON app.metric_comparison (workspace_id, asset_id, computed_at DESC);
CREATE INDEX IF NOT EXISTS idx_metric_comparison_forecast
    ON app.metric_comparison (workspace_id, forecast_id);
CREATE INDEX IF NOT EXISTS idx_metric_comparison_snapshot
    ON app.metric_comparison (workspace_id, snapshot_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_comparison_dim
    ON app.metric_comparison (forecast_id, snapshot_id, dimension);
CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_comparison_idempotency
    ON app.metric_comparison (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ============================================================
-- 3. app.retrospective_report —— 复盘报告
-- ============================================================
CREATE TABLE IF NOT EXISTS app.retrospective_report (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      VARCHAR(32)   NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    scope_type        VARCHAR(16)   NOT NULL,
    -- ASSET / ACCOUNT / PLATFORM / CATEGORY / CAMPAIGN / PERIOD
    scope_key         VARCHAR(255)  NOT NULL,
    asset_id          UUID          REFERENCES app.content_asset(id) ON DELETE SET NULL,
    account_id        VARCHAR(64),
    platform_code     VARCHAR(32),
    category_code     VARCHAR(64),
    campaign_id       VARCHAR(64),
    period_start      TIMESTAMPTZ,
    period_end        TIMESTAMPTZ,
    summary           TEXT,
    -- 预测侧聚合（与 actuals 分字段，绝不混在同一列）
    predictions       JSONB         NOT NULL DEFAULT '{}'::jsonb,
    -- 实际侧聚合
    actuals           JSONB         NOT NULL DEFAULT '{}'::jsonb,
    hit_dimensions    JSONB         NOT NULL DEFAULT '[]'::jsonb,
    missed_dimensions JSONB         NOT NULL DEFAULT '[]'::jsonb,
    suggestions       JSONB         NOT NULL DEFAULT '[]'::jsonb,
    model_name        VARCHAR(64),
    prompt_version    VARCHAR(32),
    kb_version        VARCHAR(32),
    generated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    -- COMPLETED / DEGRADED / INSUFFICIENT_DATA
    degraded          BOOLEAN       NOT NULL DEFAULT FALSE,
    degraded_reason   TEXT,
    missing_dimensions JSONB        NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key   VARCHAR(128),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT retrospective_report_scope_check CHECK (scope_type IN (
        'ASSET', 'ACCOUNT', 'PLATFORM', 'CATEGORY', 'CAMPAIGN', 'PERIOD')),
    CONSTRAINT retrospective_report_status_check CHECK (status IN (
        'PENDING', 'COMPLETED', 'DEGRADED', 'INSUFFICIENT_DATA'))
);

COMMENT ON TABLE app.retrospective_report IS
    '阶段3：复盘报告。建议少于 3 条时状态必须为 INSUFFICIENT_DATA（禁止标记 COMPLETED）；AI 解析失败降级为 DEGRADED。';
COMMENT ON COLUMN app.retrospective_report.predictions IS '预测侧聚合（维度 → 预测值 + 来源：model/prompt/kb/生成时间）';
COMMENT ON COLUMN app.retrospective_report.actuals IS '实际侧聚合（维度 → 实际值 + 来源：metric_source/collected_at）';
COMMENT ON COLUMN app.retrospective_report.suggestions IS '建议数组，每条必须带 evidenceRefs[]（指向真实存在的 snapshot / comparison）';
COMMENT ON COLUMN app.retrospective_report.degraded IS '是否降级报告（AI 调用 / 解析失败时为 true）';
COMMENT ON COLUMN app.retrospective_report.degraded_reason IS '降级原因（AI 失败原因 / 数据不足说明）';
COMMENT ON COLUMN app.retrospective_report.missing_dimensions IS '数据不足时缺失的维度说明（缺预测 / 缺快照 / 样本量不足）';

CREATE INDEX IF NOT EXISTS idx_retrospective_report_ws_scope
    ON app.retrospective_report (workspace_id, scope_type, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_retrospective_report_ws_asset
    ON app.retrospective_report (workspace_id, asset_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_retrospective_report_ws_platform
    ON app.retrospective_report (workspace_id, platform_code, generated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_retrospective_report_idempotency
    ON app.retrospective_report (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ============================================================
-- 4. app.retrospective_feedback —— 回写审批流
-- ============================================================
CREATE TABLE IF NOT EXISTS app.retrospective_feedback (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id         UUID          NOT NULL REFERENCES app.retrospective_report(id) ON DELETE CASCADE,
    workspace_id      VARCHAR(32)   NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    target_type       VARCHAR(32)   NOT NULL,
    -- TEMPLATE / KB_DOCUMENT
    target_id         VARCHAR(128)  NOT NULL,
    proposed_content  TEXT,
    proposed_stats    JSONB         NOT NULL DEFAULT '{}'::jsonb,
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    -- PENDING / APPROVED / REJECTED / WITHDRAWN
    reviewed_by       BIGINT        REFERENCES app.users(id),
    reviewed_at       TIMESTAMPTZ,
    review_comment    TEXT,
    created_by        BIGINT        REFERENCES app.users(id),
    applied_at        TIMESTAMPTZ,
    idempotency_key   VARCHAR(128),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT retrospective_feedback_target_check CHECK (target_type IN (
        'TEMPLATE', 'KB_DOCUMENT')),
    CONSTRAINT retrospective_feedback_status_check CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

COMMENT ON TABLE app.retrospective_feedback IS
    '阶段3：复盘结果回写模板 / 知识库效果统计的审批流。PENDING 时不写入任何效果统计，只有 APPROVED 才生效。';
COMMENT ON COLUMN app.retrospective_feedback.proposed_stats IS '拟写入的效果统计样本（指标 + 标签，不含知识库正文）';
COMMENT ON COLUMN app.retrospective_feedback.applied_at IS '实际写回效果统计的时间（审批通过后写入）';

CREATE INDEX IF NOT EXISTS idx_retrospective_feedback_ws_report
    ON app.retrospective_feedback (workspace_id, report_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_retrospective_feedback_ws_target
    ON app.retrospective_feedback (workspace_id, target_type, target_id, status);
CREATE INDEX IF NOT EXISTS idx_retrospective_feedback_ws_status
    ON app.retrospective_feedback (workspace_id, status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_retrospective_feedback_idempotency
    ON app.retrospective_feedback (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ============================================================
-- 5. 模板效果统计：复用 app.template_apply_log，只加列（不新建模板统计表）
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'template_apply_log'
          AND column_name = 'effect_stats'
    ) THEN
        ALTER TABLE app.template_apply_log ADD COLUMN effect_stats JSONB;
    END IF;
END
$$;

COMMENT ON COLUMN app.template_apply_log.effect_stats IS
    '阶段3：复盘回写批准后写入的效果统计样本（指标 + 标签）。审批通过前为 NULL —— 即统计未变化。';
