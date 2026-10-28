-- V51：S3-2 平台账号连接 —— app.platform_account
--
-- 背景：
--   阶段 3 的"真实数据闭环"需要一个工作区维度的平台账号绑定表：
--   记录某个平台（小红书 / 抖音）下用户自己的账号，用哪条连接器（connector_provider）
--   接入、当前连接状态、授权到期时间、以及凭据的**引用**。
--
-- 凭据托管决策（D1 = C 仅人工导入）：
--   系统**不持有**平台账号密码 / Cookie / Token 明文。
--   credential_ref 只允许存 browser_assist_session.id（UUID 字符串）或 NULL，
--   真正的加密 Cookie 存在 app.browser_assist_session.cookie_encrypted（AES-GCM，24h 过期）。
--   因此本表**没有**任何明文凭据列。
--
-- 幂等：所有 DDL 均为 IF NOT EXISTS，可重复执行。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. platform_account 平台账号绑定表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.platform_account (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            VARCHAR(32)  NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    platform_code           VARCHAR(32)  NOT NULL REFERENCES app.platform(code) ON DELETE CASCADE,
    account_label           VARCHAR(128) NOT NULL,
    external_account_id     VARCHAR(128),
    connector_provider_code VARCHAR(64)  NOT NULL,
    level                   VARCHAR(32)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'unconnected',
    credential_ref          VARCHAR(128),
    authorized_at           TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    last_success_at         TIMESTAMPTZ,
    last_error              TEXT,
    created_by              BIGINT       REFERENCES app.users(id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT platform_account_status_check CHECK (status IN
        ('unconnected','connected','expired','reauthorizing','failed')),
    CONSTRAINT platform_account_level_check CHECK (level IN
        ('OFFICIAL_API','AUTHORIZED_CONNECTOR','BROWSER_ASSIST','MANUAL_IMPORT','DISABLED')),
    CONSTRAINT platform_account_workspace_platform_label_key
        UNIQUE (workspace_id, platform_code, account_label)
);

COMMENT ON TABLE app.platform_account IS
    'S3-2：平台账号绑定表（工作区维度）。记录用户自有平台账号的连接状态、授权到期与凭据引用。不存明文凭据。';
COMMENT ON COLUMN app.platform_account.id IS '账号主键（UUID）';
COMMENT ON COLUMN app.platform_account.workspace_id IS '所属工作区（VARCHAR(32)，与 app.workspace.id 一致）';
COMMENT ON COLUMN app.platform_account.platform_code IS '平台代码（大写枚举名，与 app.platform.code 一致，如 XIAOHONGSHU / DOUYIN）';
COMMENT ON COLUMN app.platform_account.account_label IS '用户自定义账号标签，同工作区同平台下唯一';
COMMENT ON COLUMN app.platform_account.external_account_id IS '平台侧账号 ID（可空）';
COMMENT ON COLUMN app.platform_account.connector_provider_code IS '接入所用的连接器实现（app.connector_provider.code）';
COMMENT ON COLUMN app.platform_account.level IS '连接等级（freeze/PlatformConnectorLevel 名称），由服务端判定，不接受前端传入';
COMMENT ON COLUMN app.platform_account.status IS '连接状态：unconnected / connected / expired / reauthorizing / failed';
COMMENT ON COLUMN app.platform_account.credential_ref IS '凭据**引用**（browser_assist_session.id 或 NULL）。禁止存明文 Cookie / Token / 密码';
COMMENT ON COLUMN app.platform_account.authorized_at IS '最近一次授权成功时间';
COMMENT ON COLUMN app.platform_account.expires_at IS '授权到期时间（过期需重新授权）';
COMMENT ON COLUMN app.platform_account.last_success_at IS '最近一次连接器调用成功时间';
COMMENT ON COLUMN app.platform_account.last_error IS '最近一次失败原因（连接器报错时保留）';
COMMENT ON COLUMN app.platform_account.created_by IS '创建人（app.users.id）';
COMMENT ON COLUMN app.platform_account.created_at IS '创建时间';
COMMENT ON COLUMN app.platform_account.updated_at IS '最后更新时间';

-- ============================================================
-- 2. 索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_platform_account_workspace_status
    ON app.platform_account(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_platform_account_workspace_platform
    ON app.platform_account(workspace_id, platform_code);
CREATE INDEX IF NOT EXISTS idx_platform_account_workspace_id
    ON app.platform_account(workspace_id);
