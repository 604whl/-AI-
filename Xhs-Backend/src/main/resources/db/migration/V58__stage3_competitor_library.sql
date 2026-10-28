-- V58：阶段 3 S3-10 竞品研究资产库
--
-- 目标（对应 STAGE3_ACCEPTANCE.md §4.2 "竞品研究资产库"）：
--   1. 新建 app.competitor_account —— 竞品账号库（工作区维度）
--   2. 新建 app.competitor_note   —— 竞品笔记 + 样本证据
--
-- 硬性约束：
--   - evidence_text 是"真实片段引用"，没有原文片段不得入库 → NOT NULL + CHECK 非空
--   - workspace_id 为 VARCHAR(32) + REFERENCES app.workspace(id) ON DELETE CASCADE
--   - 采集来源必须标注 source_type + captured_at
--   - 未授权平台的采集由 app.platform_connector_authorization 门禁在运行时拒绝（403）
--
-- 幂等：全部使用 IF NOT EXISTS / DO $$ 形式，可重复执行。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.competitor_account
-- ============================================================
CREATE TABLE IF NOT EXISTS app.competitor_account (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        VARCHAR(32) NOT NULL,
    platform_code       VARCHAR(32) NOT NULL,
    account_name        VARCHAR(128) NOT NULL,
    account_url         TEXT,
    external_account_id VARCHAR(128),
    follower_count      BIGINT,
    category            VARCHAR(64),
    tags                TEXT[],
    -- active / archived
    status              VARCHAR(16) NOT NULL DEFAULT 'active',
    added_by            BIGINT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_competitor_account_status CHECK (status IN ('active', 'archived'))
);

COMMENT ON TABLE app.competitor_account IS
    '阶段3 竞品账号库：工作区维度的竞品账号，用于竞品研究与账号维度互动率对比';
COMMENT ON COLUMN app.competitor_account.workspace_id IS
    '所属工作区（VARCHAR(32)，与 app.workspace.id 一致）';
COMMENT ON COLUMN app.competitor_account.platform_code IS
    '平台代码（大写枚举名，与 app.platform.code 一致）';
COMMENT ON COLUMN app.competitor_account.account_name IS '竞品账号名称';
COMMENT ON COLUMN app.competitor_account.account_url IS '竞品账号主页链接';
COMMENT ON COLUMN app.competitor_account.external_account_id IS '平台侧账号 ID（可空）';
COMMENT ON COLUMN app.competitor_account.follower_count IS '粉丝数（可空）';
COMMENT ON COLUMN app.competitor_account.category IS '品类代码（用于同品类多账号对比）';
COMMENT ON COLUMN app.competitor_account.tags IS '标签集合';
COMMENT ON COLUMN app.competitor_account.status IS 'active / archived';
COMMENT ON COLUMN app.competitor_account.added_by IS '录入人 user_id';
COMMENT ON COLUMN app.competitor_account.created_at IS '创建时间';
COMMENT ON COLUMN app.competitor_account.updated_at IS '最后更新时间（接口必须暴露）';

-- ============================================================
-- 2. app.competitor_note
-- ============================================================
CREATE TABLE IF NOT EXISTS app.competitor_note (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    competitor_account_id UUID        NOT NULL,
    workspace_id          VARCHAR(32) NOT NULL,
    platform_code         VARCHAR(32) NOT NULL,
    note_title            VARCHAR(256) NOT NULL,
    note_url              TEXT,
    published_at          TIMESTAMPTZ,
    -- 段落结构 / 钩子类型 / CTA 位置 / 标签集合
    content_structure     JSONB,
    -- likes / collects / comments / shares / impressions
    engagement            JSONB,
    captured_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- 真实原文片段引用：没有原文片段不得入库
    evidence_text         TEXT        NOT NULL,
    -- BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT / AUTHORIZED_CONNECTOR / OFFICIAL_API
    source_type           VARCHAR(32) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_competitor_note_source_type CHECK (source_type IN (
        'BROWSER_ASSIST', 'MANUAL_IMPORT', 'CSV_IMPORT',
        'AUTHORIZED_CONNECTOR', 'OFFICIAL_API'
    )),
    CONSTRAINT chk_competitor_note_evidence_not_empty CHECK (length(trim(evidence_text)) > 0)
);

COMMENT ON TABLE app.competitor_note IS
    '阶段3 竞品笔记与样本证据：evidence_text 为真实原文片段引用，缺失不允许入库';
COMMENT ON COLUMN app.competitor_note.competitor_account_id IS '所属竞品账号';
COMMENT ON COLUMN app.competitor_note.workspace_id IS '所属工作区（VARCHAR(32)）';
COMMENT ON COLUMN app.competitor_note.note_title IS '竞品笔记标题';
COMMENT ON COLUMN app.competitor_note.note_url IS '竞品笔记链接';
COMMENT ON COLUMN app.competitor_note.published_at IS '笔记发布时间';
COMMENT ON COLUMN app.competitor_note.content_structure IS
    '内容结构拆解 JSONB：段落结构 / 钩子类型 / CTA 位置 / 标签集合';
COMMENT ON COLUMN app.competitor_note.engagement IS
    '互动数据 JSONB：likes / collects / comments / shares / impressions（统一口径，缺失补 0）';
COMMENT ON COLUMN app.competitor_note.captured_at IS '采集时间（接口与 UI 必须展示）';
COMMENT ON COLUMN app.competitor_note.evidence_text IS
    '竞品笔记的真实原文片段引用（可高亮）；为空或纯空白一律拒绝入库';
COMMENT ON COLUMN app.competitor_note.source_type IS
    '采集方式：BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT / AUTHORIZED_CONNECTOR / OFFICIAL_API';

-- ============================================================
-- 3. 外键与索引
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_competitor_account_workspace'
          AND conrelid = 'app.competitor_account'::regclass
    ) THEN
        ALTER TABLE app.competitor_account
            ADD CONSTRAINT fk_competitor_account_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_competitor_note_account'
          AND conrelid = 'app.competitor_note'::regclass
    ) THEN
        ALTER TABLE app.competitor_note
            ADD CONSTRAINT fk_competitor_note_account
            FOREIGN KEY (competitor_account_id) REFERENCES app.competitor_account(id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_competitor_note_workspace'
          AND conrelid = 'app.competitor_note'::regclass
    ) THEN
        ALTER TABLE app.competitor_note
            ADD CONSTRAINT fk_competitor_note_workspace
            FOREIGN KEY (workspace_id) REFERENCES app.workspace(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_competitor_account_workspace
    ON app.competitor_account(workspace_id, platform_code, category);

CREATE INDEX IF NOT EXISTS idx_competitor_account_status
    ON app.competitor_account(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_competitor_note_account
    ON app.competitor_note(competitor_account_id, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_competitor_note_workspace
    ON app.competitor_note(workspace_id, platform_code);

CREATE INDEX IF NOT EXISTS idx_competitor_note_captured_at
    ON app.competitor_note(captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_competitor_note_source_type
    ON app.competitor_note(source_type);
