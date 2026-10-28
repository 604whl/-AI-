-- V52：阶段 3 —— 指标快照与数据回流（metric_snapshot）
--
-- 目标（对应 STAGE3_ACCEPTANCE.md 子能力 2「指标快照与数据回流」）：
--   1. 建立 app.metric_snapshot 统一指标快照表：
--      - metrics JSONB 固定 10 个核心键，缺失补 0；
--      - metric_extra JSONB 承载平台差异字段（如抖音 completion_rate、B 站 danmaku）；
--      - metric_source / confidence / collected_at / is_manual 必存，杜绝「伪装实时」；
--      - revised_from_id 形成修订链，人工修订只新增快照、不覆盖原快照。
--   2. 建立 app.metric_collect_task（采集任务台账）与 app.metric_sync_state（同步健康状态），
--      让「同步延迟 / 失败率 / 最后成功时间」可查询。
--
-- 幂等：所有 DDL 均为 IF NOT EXISTS，可重复执行。
-- 兼容性：app.analysis_task.published_metrics 保留但降级为兼容读取，本次不改其表结构。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. 指标快照主表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.metric_snapshot (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    platform_code       VARCHAR(32)  NOT NULL,
    account_id          VARCHAR(64),
    asset_id            UUID         REFERENCES app.content_asset(id) ON DELETE SET NULL,
    external_content_id VARCHAR(128),
    content_type        VARCHAR(32),
    published_at        TIMESTAMPTZ,
    metrics             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metric_extra        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metric_source       VARCHAR(32)  NOT NULL,
    confidence          VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    collected_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_manual           BOOLEAN      NOT NULL DEFAULT FALSE,
    revised_from_id     UUID         REFERENCES app.metric_snapshot(id) ON DELETE SET NULL,
    revised_by          BIGINT       REFERENCES app.users(id),
    -- 采集幂等键：平台 + 账号 + 外部内容 ID + 采集窗口（手工/修订路径带独立前缀）
    collection_key      VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT metric_snapshot_source_check CHECK (metric_source IN (
        'OFFICIAL_API', 'AUTHORIZED_CONNECTOR', 'BROWSER_ASSIST',
        'MANUAL_IMPORT', 'CSV_IMPORT', 'LEGACY_JSONB')),
    CONSTRAINT metric_snapshot_confidence_check CHECK (confidence IN (
        'HIGH', 'MEDIUM', 'LOW', 'UNVERIFIED'))
);

COMMENT ON TABLE app.metric_snapshot IS
    '阶段3：指标快照。metrics 固定 10 个核心键（缺失补 0），平台差异放 metric_extra；人工修订新增快照不覆盖原快照。';
COMMENT ON COLUMN app.metric_snapshot.workspace_id IS '所属工作区（VARCHAR(32)，与 app.workspace.id 一致）';
COMMENT ON COLUMN app.metric_snapshot.metrics IS
    '统一口径指标 JSONB，固定 10 键：impressions/plays/clicks/likes/collects/comments/shares/follows/dm_inquiries/conversions';
COMMENT ON COLUMN app.metric_snapshot.metric_extra IS '平台扩展指标 JSONB（如 douyin.completion_rate、bilibili.danmaku），仅存核心键之外的字段';
COMMENT ON COLUMN app.metric_snapshot.metric_source IS
    '数据来源：OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT / LEGACY_JSONB(旧数据兼容读取)';
COMMENT ON COLUMN app.metric_snapshot.confidence IS '可信度：HIGH / MEDIUM / LOW / UNVERIFIED';
COMMENT ON COLUMN app.metric_snapshot.collected_at IS '采集时间（平台实际统计时间，手工录入可指定）';
COMMENT ON COLUMN app.metric_snapshot.is_manual IS '是否人工录入/人工修订的数据';
COMMENT ON COLUMN app.metric_snapshot.revised_from_id IS '修订链：指向被修订的原快照；原快照保留可追溯，不原地覆盖';
COMMENT ON COLUMN app.metric_snapshot.collection_key IS
    '采集幂等键：平台+账号+外部内容ID+采集窗口；同窗口重复采集不产生重复快照';

-- 采集幂等唯一约束（account_id / external_content_id 可能为 NULL，故用独立非空键列）
CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_snapshot_collection_key
    ON app.metric_snapshot (collection_key);

CREATE INDEX IF NOT EXISTS idx_metric_snapshot_ws_collected
    ON app.metric_snapshot (workspace_id, collected_at DESC);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_ws_asset
    ON app.metric_snapshot (workspace_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_ws_platform_collected
    ON app.metric_snapshot (workspace_id, platform_code, collected_at DESC);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_ws_external
    ON app.metric_snapshot (workspace_id, external_content_id);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_ws_revised_from
    ON app.metric_snapshot (workspace_id, revised_from_id);

-- ============================================================
-- 2. 采集任务台账（采集不计费，仅记录执行状态）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.metric_collect_task (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    platform_code        VARCHAR(32)  NOT NULL,
    account_id           VARCHAR(64),
    status               VARCHAR(32)  NOT NULL DEFAULT 'queued',
    -- queued / running / succeeded / failed / dead_letter
    attempt              INT          NOT NULL DEFAULT 0,
    window_start         TIMESTAMPTZ,
    window_end           TIMESTAMPTZ,
    error_code           INT,
    error_message        TEXT,
    last_success_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT metric_collect_task_status_check CHECK (status IN (
        'queued', 'running', 'succeeded', 'failed', 'dead_letter'))
);

CREATE INDEX IF NOT EXISTS idx_metric_collect_task_ws_created
    ON app.metric_collect_task (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_metric_collect_task_platform
    ON app.metric_collect_task (workspace_id, platform_code, account_id, created_at DESC);

COMMENT ON TABLE app.metric_collect_task IS
    '阶段3：指标采集任务台账。采集任务不计费，不写 usage_ledger DEBIT；失败重试超限由既有 DLQ 机制处理。';

-- ============================================================
-- 3. 同步健康状态（同步延迟 / 失败率 / 最后成功时间可见）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.metric_sync_state (
    workspace_id         VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    platform_code        VARCHAR(32)  NOT NULL,
    account_id           VARCHAR(64)  NOT NULL DEFAULT '',
    status               VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN',
    -- HEALTHY / DEGRADED / UNKNOWN（连接器健康由 S3-1 负责，此表仅记录采集侧状态）
    consecutive_failures INT          NOT NULL DEFAULT 0,
    last_success_at      TIMESTAMPTZ,
    last_collected_at    TIMESTAMPTZ,
    last_error           TEXT,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, platform_code, account_id),
    CONSTRAINT metric_sync_state_status_check CHECK (status IN ('HEALTHY', 'DEGRADED', 'UNKNOWN'))
);

CREATE INDEX IF NOT EXISTS idx_metric_sync_state_ws
    ON app.metric_sync_state (workspace_id, platform_code);

COMMENT ON TABLE app.metric_sync_state IS
    '阶段3：指标采集同步状态。连续失败 3 次标 DEGRADED；界面可展示失败原因与最后成功时间。';

-- ============================================================
-- 4. csv_import_log.import_type 放宽（若存在 CHECK 约束）
--    既有表 import_type 为 VARCHAR(32) NOT NULL 且无 CHECK；此处仅做防御性放宽，
--    保证 'METRIC' / 'COMPETITOR_NOTE' / 'HOT_TOPIC' 可写入。
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'app.csv_import_log'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%import_type%'
    ) THEN
        ALTER TABLE app.csv_import_log DROP CONSTRAINT IF EXISTS csv_import_log_import_type_check;
        ALTER TABLE app.csv_import_log ADD CONSTRAINT csv_import_log_import_type_check
            CHECK (import_type IN ('ASSET', 'TOPIC', 'METRIC', 'COMPETITOR_NOTE', 'HOT_TOPIC'));
    END IF;
END
$$;
