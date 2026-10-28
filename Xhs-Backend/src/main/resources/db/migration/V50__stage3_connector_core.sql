-- V50: 阶段 3 S3-1 连接器基础设施
--
-- 目的：
--   1. 新建 app.connector_health —— 连接器健康状态与降级路径的落库表
--   2. 补齐 app.connector_provider 与 app.platform_connector_authorization 的种子数据
--
-- 背景说明：
--   app.connector_provider 与 app.platform_connector_authorization 两张表在
--   V34__stage0_freeze.sql 中已建好（结构 + 约束 + 索引），但至今**没有任何 INSERT**，
--   也没有任何一行 Java 代码读取它们 —— 也就是"授权门禁"在运行时不生效。
--   阶段 3 明确要求：连接器调用必须在运行时经过授权门禁，未授权直接 403 拒绝。
--   本迁移按 docs/CONNECTOR-AUTHORIZATION.md 的授权矩阵补齐种子数据。
--
-- 幂等性：
--   全部使用 ON CONFLICT DO UPDATE / DO NOTHING，可重复执行。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. 连接器健康状态表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.connector_health (
    id                   BIGSERIAL PRIMARY KEY,
    connector_provider_code VARCHAR(64) NOT NULL,
    platform_code        VARCHAR(32)  NOT NULL,
    -- workspace_id 为空表示"全局健康状态"；非空表示该 workspace 维度的覆盖状态
    workspace_id         VARCHAR(32),
    status               VARCHAR(32)  NOT NULL DEFAULT 'HEALTHY',
    -- HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED
    last_success_at      TIMESTAMPTZ,
    last_checked_at      TIMESTAMPTZ,
    last_error_code      VARCHAR(32),
    last_error_message   TEXT,
    consecutive_failures INT          NOT NULL DEFAULT 0,
    -- 降级路径目标连接器 provider code
    degraded_to          VARCHAR(64),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT connector_health_status_check CHECK (status IN
        ('HEALTHY','DEGRADED','UNAVAILABLE','NOT_AUTHORIZED'))
);

-- 同一 (provider, platform, workspace) 只保留一条健康记录
CREATE UNIQUE INDEX IF NOT EXISTS uq_connector_health_scope
    ON app.connector_health(connector_provider_code, platform_code, workspace_id);

CREATE INDEX IF NOT EXISTS idx_connector_health_platform
    ON app.connector_health(platform_code, status);

CREATE INDEX IF NOT EXISTS idx_connector_health_provider
    ON app.connector_health(connector_provider_code);

COMMENT ON TABLE app.connector_health IS
    '阶段3 连接器健康状态与降级路径记录；连续失败 3 次降级 DEGRADED，10 次降级 UNAVAILABLE';
COMMENT ON COLUMN app.connector_health.workspace_id IS
    '为空=全局健康状态；非空=该 workspace 维度的覆盖状态';
COMMENT ON COLUMN app.connector_health.status IS
    'HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED';
COMMENT ON COLUMN app.connector_health.degraded_to IS
    '降级目标连接器 provider code，降级路径 OFFICIAL_API → AUTHORIZED_CONNECTOR → BROWSER_ASSIST → MANUAL_IMPORT → EXPORT_ONLY';
COMMENT ON COLUMN app.connector_health.consecutive_failures IS
    '连续失败次数；>=3 记 DEGRADED，>=10 记 UNAVAILABLE';

-- ============================================================
-- 2. 连接器提供方种子数据
--    对应 docs/CONNECTOR-AUTHORIZATION.md 的"平台 × 连接器矩阵"
--    注意 app.connector_provider.connector_type 的 CHECK 约束只允许
--    ('BROWSER_EXTENSION','OFFICIAL_API','CSV_IMPORT','WEBHOOK_RECEIVER','MANUAL_FORM')
--    注意 app.platform.code 存的是大写枚举名（XIAOHONGSHU / DOUYIN ...），不是 slug
-- ============================================================
INSERT INTO app.connector_provider
    (code, display_name, platform_code, connector_type,
     requires_oauth, requires_user_credentials, risk_note, enabled)
VALUES
    ('XHS_BROWSER_SIDEBAR', '小红书浏览器侧边栏', 'XIAOHONGSHU', 'BROWSER_EXTENSION',
        FALSE, TRUE,
        '需显著告知用户系统正在代其操作；发布前必须由用户在页面确认', TRUE),
    ('XHS_OFFICIAL_API', '小红书官方 API', 'XIAOHONGSHU', 'OFFICIAL_API',
        TRUE, FALSE,
        '官方商业授权尚未签署，暂不启用', FALSE),
    ('XHS_AUTHORIZED_CONNECTOR', '小红书第三方授权连接器', 'XIAOHONGSHU', 'WEBHOOK_RECEIVER',
        TRUE, TRUE,
        '第三方数据服务授权尚未签署，暂不启用', FALSE),
    ('DOUYIN_CSV_IMPORT', '抖音 CSV 导入', 'DOUYIN', 'CSV_IMPORT',
        FALSE, FALSE,
        '抖音不开放数据抓取，强制要求 CSV 导入', TRUE),
    ('WECHAT_CSV_IMPORT', '微信公众号 CSV 导入', 'WECHAT_OA', 'CSV_IMPORT',
        FALSE, FALSE,
        '待开通', FALSE),
    ('WECHAT_CHAN_CSV_IMPORT', '视频号 CSV 导入', 'WECHAT_CHAN', 'CSV_IMPORT',
        FALSE, FALSE,
        '待开通', FALSE),
    ('BILIBILI_CSV_IMPORT', 'B 站 CSV 导入', 'BILIBILI', 'CSV_IMPORT',
        FALSE, FALSE,
        '待开通', FALSE),
    ('WEIBO_CSV_IMPORT', '微博 CSV 导入', 'WEIBO', 'CSV_IMPORT',
        FALSE, FALSE,
        '待开通', FALSE),
    ('ZHIHU_CSV_IMPORT', '知乎 CSV 导入', 'ZHIHU', 'CSV_IMPORT',
        FALSE, FALSE,
        '待开通', FALSE)
ON CONFLICT (code) DO UPDATE SET
    display_name                = EXCLUDED.display_name,
    platform_code               = EXCLUDED.platform_code,
    connector_type              = EXCLUDED.connector_type,
    requires_oauth              = EXCLUDED.requires_oauth,
    requires_user_credentials   = EXCLUDED.requires_user_credentials,
    risk_note                   = EXCLUDED.risk_note,
    enabled                     = EXCLUDED.enabled,
    updated_at                  = now();

-- ============================================================
-- 3. 平台 × 连接器 授权矩阵种子数据
--    authorized=TRUE  : 阶段 0 已批准，可在运行时调用
--    authorized=FALSE : 未授权 / 待申请，运行时门禁一律拒绝（403 CONNECTOR_NOT_AUTHORIZED）
--    scope 说明授权范围：读写 / 仅读
-- ============================================================
INSERT INTO app.platform_connector_authorization
    (platform_code, connector_provider_code, authorized,
     authorization_source, legal_reviewer, reviewed_at, expires_at, scope, notes)
VALUES
    ('XIAOHONGSHU', 'XHS_BROWSER_SIDEBAR', TRUE,
        '阶段 0 冻结清单：暂批准', NULL, now(), NULL, '读+写（写需页面确认）',
        '小红书唯一当前可用连接器；发布动作必须由用户在小红书页面确认'),
    ('XIAOHONGSHU', 'XHS_OFFICIAL_API', FALSE,
        '待申请官方商业授权', NULL, now(), NULL, '读+写',
        '官方授权函尚未签署，默认拒绝'),
    ('XIAOHONGSHU', 'XHS_AUTHORIZED_CONNECTOR', FALSE,
        '待签署第三方数据服务授权', NULL, now(), NULL, '读+写',
        '第三方授权尚未签署，默认拒绝'),
    ('DOUYIN', 'DOUYIN_CSV_IMPORT', TRUE,
        '阶段 0 冻结清单：已批准（默认）', NULL, now(), NULL, '仅读（手动导入）',
        '抖音强制 CSV 导入，不允许自动写'),
    ('WECHAT_OA', 'WECHAT_CSV_IMPORT', FALSE,
        '待开通', NULL, now(), NULL, '仅读（手动导入）', '待开通，默认拒绝'),
    ('WECHAT_CHAN', 'WECHAT_CHAN_CSV_IMPORT', FALSE,
        '待开通', NULL, now(), NULL, '仅读（手动导入）', '待开通，默认拒绝'),
    ('BILIBILI', 'BILIBILI_CSV_IMPORT', FALSE,
        '待开通', NULL, now(), NULL, '仅读（手动导入）', '待开通，默认拒绝'),
    ('WEIBO', 'WEIBO_CSV_IMPORT', FALSE,
        '待开通', NULL, now(), NULL, '仅读（手动导入）', '待开通，默认拒绝'),
    ('ZHIHU', 'ZHIHU_CSV_IMPORT', FALSE,
        '待开通', NULL, now(), NULL, '仅读（手动导入）', '待开通，默认拒绝')
ON CONFLICT (platform_code, connector_provider_code) DO UPDATE SET
    authorized           = EXCLUDED.authorized,
    authorization_source = EXCLUDED.authorization_source,
    reviewed_at          = EXCLUDED.reviewed_at,
    expires_at           = EXCLUDED.expires_at,
    scope                = EXCLUDED.scope,
    notes                = EXCLUDED.notes;
