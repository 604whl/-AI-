-- V56：阶段 3 S3-3 / S3-4 —— 排期日历 + 发布任务 + 发布回执
--
-- 目标（对应 STAGE3_ACCEPTANCE.md 子能力 1「平台账号连接 / 排期日历 / 发布回执」）：
--   1. app.publish_schedule  —— 排期日历（资产 + 平台 + 账号 + 时间 + 时区 + 冲突检测）
--   2. app.publish_task      —— 发布任务（连接器 / 浏览器辅助 / 手动导入三条路径共用同一张表，用 level 区分）
--   3. app.publish_receipt   —— 发布回执（外部内容 ID / 来源链接 / 发布时间 / 核验标记）
--
-- 硬性约束：
--   * workspace_id 统一为 VARCHAR(32)（V49 归一后的类型），FK 到 app.workspace(id) ON DELETE CASCADE
--   * app.publish_task.idempotency_key 必须 UNIQUE —— 这是"发布任务重复发布率 = 0"的兜底闸门
--   * app.publish_receipt.publish_task_id UNIQUE —— 同一任务最多一条回执
--   * 严禁写入伪造的 external_content_id：回执完整性由服务层保证（两者皆空 → RECEIPT_INCOMPLETE）
--
-- 幂等：所有 DDL 均为 IF NOT EXISTS，可重复执行。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. 排期日历
-- ============================================================
CREATE TABLE IF NOT EXISTS app.publish_schedule (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    asset_id         UUID         NOT NULL REFERENCES app.content_asset(id) ON DELETE CASCADE,
    platform_code    VARCHAR(32)  NOT NULL,
    account_id       VARCHAR(64),
    -- 排期时间统一存 UTC；timezone 字段保存 IANA 时区串（如 Asia/Shanghai）用于展示与冲突判定
    scheduled_at     TIMESTAMPTZ  NOT NULL,
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    -- scheduled / executing / succeeded / failed / cancelled / expired
    status           VARCHAR(32)  NOT NULL DEFAULT 'scheduled',
    publish_task_id  UUID,
    error_code       INT,
    error_message    TEXT,
    created_by       BIGINT       REFERENCES app.users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT publish_schedule_status_check CHECK (status IN (
        'scheduled', 'executing', 'succeeded', 'failed', 'cancelled', 'expired'))
);

COMMENT ON TABLE app.publish_schedule IS 'S3-3：排期日历。排期必须建立在已审核通过（content_asset.status=approved）的资产上；同一 workspace + 平台 + 账号 + 同一时刻只允许一个非终态排期（服务层做冲突检测）。';
COMMENT ON COLUMN app.publish_schedule.workspace_id IS '所属工作区（VARCHAR(32)，与 app.workspace.id 一致）';
COMMENT ON COLUMN app.publish_schedule.scheduled_at IS '排期时刻（UTC 落库）';
COMMENT ON COLUMN app.publish_schedule.timezone IS 'IANA 时区串（如 Asia/Shanghai），用于展示与跨时区排序';
COMMENT ON COLUMN app.publish_schedule.status IS 'scheduled / executing / succeeded / failed / cancelled / expired';
COMMENT ON COLUMN app.publish_schedule.publish_task_id IS '关联的发布任务 ID（app.publish_task.id）';

CREATE INDEX IF NOT EXISTS idx_publish_schedule_ws_scheduled
    ON app.publish_schedule (workspace_id, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_publish_schedule_ws_platform_account
    ON app.publish_schedule (workspace_id, platform_code, account_id, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_publish_schedule_ws_status
    ON app.publish_schedule (workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_schedule_ws_asset
    ON app.publish_schedule (workspace_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_publish_schedule_task
    ON app.publish_schedule (publish_task_id);

-- ============================================================
-- 2. 发布任务（三条降级路径共用，level 区分）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.publish_task (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    schedule_id            UUID         REFERENCES app.publish_schedule(id) ON DELETE SET NULL,
    asset_id               UUID         REFERENCES app.content_asset(id) ON DELETE SET NULL,
    platform_code          VARCHAR(32)  NOT NULL,
    account_id             VARCHAR(64),
    connector_provider_code VARCHAR(64),
    -- OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT
    level                  VARCHAR(32)  NOT NULL,
    -- 幂等键唯一：重复发布率 = 0 的最终闸门
    idempotency_key        VARCHAR(128) NOT NULL,
    -- pending / running / succeeded / failed / manual_required / cancelled
    status                 VARCHAR(32)  NOT NULL DEFAULT 'pending',
    attempt                INT          NOT NULL DEFAULT 0,
    error_code             INT,
    error_message          TEXT,
    retryable              BOOLEAN      NOT NULL DEFAULT FALSE,
    scheduled_at           TIMESTAMPTZ,
    started_at             TIMESTAMPTZ,
    finished_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT publish_task_status_check CHECK (status IN (
        'pending', 'running', 'succeeded', 'failed', 'manual_required', 'cancelled')),
    CONSTRAINT publish_task_level_check CHECK (level IN (
        'OFFICIAL_API', 'AUTHORIZED_CONNECTOR', 'BROWSER_ASSIST', 'MANUAL_IMPORT')),
    CONSTRAINT publish_task_idempotency_key_key UNIQUE (idempotency_key)
);

COMMENT ON TABLE app.publish_task IS 'S3-4：发布任务。OFFICIAL_API / AUTHORIZED_CONNECTOR 自动发布并回写回执；BROWSER_ASSIST / MANUAL_IMPORT 置 manual_required，由用户自行发布后回填回执（严禁伪造外部内容 ID）。';
COMMENT ON COLUMN app.publish_task.idempotency_key IS '幂等键（UNIQUE）。重试必须复用原键，不产生新的外部发布';
COMMENT ON COLUMN app.publish_task.level IS '连接等级（freeze/PlatformConnectorLevel 名称），由服务端判定';
COMMENT ON COLUMN app.publish_task.status IS 'pending / running / succeeded / failed / manual_required / cancelled';
COMMENT ON COLUMN app.publish_task.retryable IS '失败是否可重试（由错误分类决定，不得为 unknown 语义）';
COMMENT ON COLUMN app.publish_task.error_code IS '失败错误码（如 RECEIPT_INCOMPLETE / PLATFORM_CHANGED 等）';
COMMENT ON COLUMN app.publish_task.error_message IS '失败原因（可读文本，不得为 unknown）';

CREATE INDEX IF NOT EXISTS idx_publish_task_ws_created
    ON app.publish_task (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_publish_task_ws_status
    ON app.publish_task (workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_publish_task_ws_asset
    ON app.publish_task (workspace_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_publish_task_ws_platform_account
    ON app.publish_task (workspace_id, platform_code, account_id);
CREATE INDEX IF NOT EXISTS idx_publish_task_schedule
    ON app.publish_task (schedule_id);

-- ============================================================
-- 3. 发布回执
-- ============================================================
CREATE TABLE IF NOT EXISTS app.publish_receipt (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    publish_task_id    UUID         NOT NULL REFERENCES app.publish_task(id) ON DELETE CASCADE,
    workspace_id       VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    asset_id           UUID         REFERENCES app.content_asset(id) ON DELETE SET NULL,
    external_content_id VARCHAR(128),
    source_url         TEXT,
    published_at       TIMESTAMPTZ,
    -- published_at 的来源：platform（平台返回）/ local（本地兜底，必须单独标注）
    published_at_source VARCHAR(16) NOT NULL DEFAULT 'platform',
    published_by       BIGINT       REFERENCES app.users(id),
    raw_response       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT publish_receipt_published_at_source_check CHECK (published_at_source IN ('platform', 'local')),
    CONSTRAINT publish_receipt_task_key UNIQUE (publish_task_id)
);

COMMENT ON TABLE app.publish_receipt IS 'S3-4：发布回执。只在真实拿到 external_content_id 或 source_url 时写入；两者皆空视为回执不完整，任务必须标 failed 且 error_code=RECEIPT_INCOMPLETE，不得假装成功。';
COMMENT ON COLUMN app.publish_receipt.external_content_id IS '平台侧内容 ID（MANUAL_IMPORT / BROWSER_ASSIST 路径由用户回填，禁止伪造）';
COMMENT ON COLUMN app.publish_receipt.source_url IS '平台侧内容链接（与 external_content_id 至少有一项非空）';
COMMENT ON COLUMN app.publish_receipt.published_at_source IS 'platform = 取自平台返回；local = 本地兜底（必须单独标注）';
COMMENT ON COLUMN app.publish_receipt.raw_response IS '平台原始回包 JSONB（不得写入凭据明文）';

CREATE INDEX IF NOT EXISTS idx_publish_receipt_ws_created
    ON app.publish_receipt (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_publish_receipt_ws_asset
    ON app.publish_receipt (workspace_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_publish_receipt_ws_external
    ON app.publish_receipt (workspace_id, external_content_id);
