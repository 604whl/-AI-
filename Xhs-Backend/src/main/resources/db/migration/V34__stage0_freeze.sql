-- 阶段 0：产品冻结
-- 固化平台 / 品类 / 规则包 / 评分维度 / 合规规则 / 数据保留 / 连接器授权
-- 设计依据：COMMERCIAL_SAAS_READINESS_PLAN.md 第 9 节"阶段 0"
-- 实施依据：docs/STAGE0-FREEZE-PLAN.md

SET search_path TO app, kb, public;

-- ============================================================
-- 1. platform 平台适配冻结
-- ============================================================
CREATE TABLE IF NOT EXISTS app.platform (
    code              VARCHAR(32)  PRIMARY KEY,  -- XIAOHONGSHU / DOUYIN / WECHAT / ...
    display_name      VARCHAR(64)  NOT NULL,
    connector_level   VARCHAR(32)  NOT NULL,     -- OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT / DISABLED
    rollout_status    VARCHAR(16)  NOT NULL DEFAULT 'planned', -- launched / planned / disabled
    write_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    read_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    risk_note         TEXT,
    legal_reviewed_at TIMESTAMPTZ,
    legal_reviewer    VARCHAR(64),
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT platform_connector_level_check CHECK (connector_level IN
        ('OFFICIAL_API','AUTHORIZED_CONNECTOR','BROWSER_ASSIST','MANUAL_IMPORT','DISABLED')),
    CONSTRAINT platform_rollout_status_check CHECK (rollout_status IN
        ('launched','planned','disabled'))
);

COMMENT ON TABLE app.platform IS '阶段0：平台适配冻结表。代码清单 + 连接等级 + 法务签字位。';
COMMENT ON COLUMN app.platform.code IS '平台唯一代码，业务层 PlatformCode 枚举一一对应';
COMMENT ON COLUMN app.platform.connector_level IS '连接等级，决定能力范围';
COMMENT ON COLUMN app.platform.rollout_status IS 'launched=首发 / planned=规划中 / disabled=禁用';

-- 首次冻结：平台 × 等级 × 状态矩阵
INSERT INTO app.platform (code, display_name, connector_level, rollout_status, write_enabled, risk_note) VALUES
    ('XIAOHONGSHU', '小红书',          'BROWSER_ASSIST',     'launched', TRUE,  '首发平台，依赖侧边栏半自动；获取授权后升级为 AUTHORIZED_CONNECTOR'),
    ('DOUYIN',      '抖音',            'MANUAL_IMPORT',      'launched', FALSE, '首发平台，不抓取，强制人工导入 CSV'),
    ('WECHAT_OA',   '微信公众号',      'MANUAL_IMPORT',      'planned',  FALSE, '规划中：等待授权 API'),
    ('WECHAT_CHAN', '视频号',          'MANUAL_IMPORT',      'planned',  FALSE, '规划中：等待授权 API'),
    ('BILIBILI',    '哔哩哔哩',        'MANUAL_IMPORT',      'planned',  FALSE, '规划中'),
    ('WEIBO',       '微博',            'MANUAL_IMPORT',      'planned',  FALSE, '规划中'),
    ('ZHIHU',       '知乎',            'MANUAL_IMPORT',      'planned',  FALSE, '规划中'),
    ('KUAISHOU',    '快手',            'DISABLED',           'disabled', FALSE, '平台政策禁止第三方自动化'),
    ('INSTAGRAM',   'Instagram',       'DISABLED',           'disabled', FALSE, '跨境合规风险，禁用'),
    ('TIKTOK',      'TikTok',          'DISABLED',           'disabled', FALSE, '跨境合规风险，禁用')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 2. category 品类主表
-- ============================================================
CREATE TABLE IF NOT EXISTS app.category (
    code              VARCHAR(32)  PRIMARY KEY,
    display_name      VARCHAR(64)  NOT NULL,
    parent_code       VARCHAR(32)  REFERENCES app.category(code) ON DELETE SET NULL,
    risk_level        VARCHAR(16)  NOT NULL DEFAULT 'LOW',  -- LOW / MEDIUM / HIGH / BLOCKED
    auto_generate     BOOLEAN      NOT NULL DEFAULT TRUE,
    auto_publish      BOOLEAN      NOT NULL DEFAULT FALSE,
    description       TEXT,
    sort_order        INT          NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT category_risk_level_check CHECK (risk_level IN ('LOW','MEDIUM','HIGH','BLOCKED'))
);

COMMENT ON TABLE app.category IS '阶段0：品类主表。决定哪些品类允许自动生成 / 自动发布 / 必须人工审核';
COMMENT ON COLUMN app.category.risk_level IS 'BLOCKED=禁止自动生成；HIGH=必须人工审核；MEDIUM=需风险词提示；LOW=常规';
COMMENT ON COLUMN app.category.auto_generate IS '是否允许 AI 自动生成内容；BLOCKED 品类必须为 FALSE';
COMMENT ON COLUMN app.category.auto_publish IS '是否允许绕过审核直接发布；高风险以上必须为 FALSE';

-- 首次冻结：基线 8 个品类
INSERT INTO app.category (code, display_name, risk_level, auto_generate, auto_publish, description, sort_order) VALUES
    ('STUDY_ABROAD',    '教育与留学',    'LOW',     TRUE,  FALSE, '一般留学申请、择校、文书建议',     10),
    ('CAREER',          '求职与职业发展','LOW',     TRUE,  FALSE, '简历、面试、求职策略',             20),
    ('EDU_MINOR',       'K12 教育',      'HIGH',    TRUE,  FALSE, '未成年人相关教育，必须人工审核',   30),
    ('MEDICAL',         '医疗与健康',    'BLOCKED', FALSE, FALSE, '疾病、药品、保健品 — 禁止自动生成',40),
    ('FINANCE',         '金融理财',      'BLOCKED', FALSE, FALSE, '投资、收益、贷款 — 禁止自动生成',   50),
    ('BEAUTY',          '美妆与医美',    'HIGH',    TRUE,  FALSE, '医美项目必须人工审核',             60),
    ('LAW_SPECIFIC',    '法律具体案件',  'BLOCKED', FALSE, FALSE, '针对具体案件的法律建议 — 禁止生成',70),
    ('LIFESTYLE',       '生活方式',      'LOW',     TRUE,  FALSE, '旅游、美食、穿搭、家居',           80),
    ('IMMIGRATION',     '移民签证',      'MEDIUM',  TRUE,  FALSE, '需风险词提示，不阻断',             90),
    ('OTHER',           '其他',          'LOW',     TRUE,  FALSE, '未归类内容',                       999)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 3. category_rule_pack 品类规则包（可版本化）
-- ============================================================
CREATE TABLE IF NOT EXISTS app.category_rule_pack (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code        VARCHAR(32)  NOT NULL REFERENCES app.category(code) ON DELETE CASCADE,
    version              VARCHAR(16)  NOT NULL,  -- 语义化版本，如 2026.10.01
    target_audience      TEXT,                    -- 目标人群描述
    expression_exclusions TEXT,                   -- 表达禁区（自然语言）
    industry_vocab       JSONB,                   -- 行业词表 {"positive":[], "negative":[]}
    content_structure    JSONB,                   -- 内容结构模板（段落顺序、长度建议）
    cta_templates        JSONB,                   -- CTA 模板数组
    metric_weights       JSONB,                   -- 评分维度权重 {"hook":0.3,"clarity":0.2,...}
    prompt_template_refs JSONB,                   -- 关联的 Prompt 模板 ID 列表
    sample_case_refs     JSONB,                   -- 关联的样例案例 ID 列表
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by           BIGINT REFERENCES app.users(id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    activated_at         TIMESTAMPTZ,
    notes                TEXT,
    UNIQUE (category_code, version)
);

CREATE INDEX IF NOT EXISTS idx_category_rule_pack_category ON app.category_rule_pack(category_code);
CREATE INDEX IF NOT EXISTS idx_category_rule_pack_active ON app.category_rule_pack(active) WHERE active = TRUE;

COMMENT ON TABLE app.category_rule_pack IS '阶段0：品类规则包，必须版本化。每个 category 同时只允许一个 active 规则包。';

-- ============================================================
-- 4. category_score_dimension 评分维度定义
-- ============================================================
CREATE TABLE IF NOT EXISTS app.category_score_dimension (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code   VARCHAR(32)  NOT NULL REFERENCES app.category(code) ON DELETE CASCADE,
    dimension_key   VARCHAR(32)  NOT NULL,    -- hook / clarity / cta / emotion / originality / compliance ...
    display_name    VARCHAR(64)  NOT NULL,
    weight          NUMERIC(5,4) NOT NULL DEFAULT 0.0 CHECK (weight >= 0 AND weight <= 1),
    description     TEXT,
    sort_order      INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (category_code, dimension_key)
);

CREATE INDEX IF NOT EXISTS idx_category_score_dim_category ON app.category_score_dimension(category_code);

COMMENT ON TABLE app.category_score_dimension IS '阶段0：品类评分维度可配置。每个品类权重合 = 1.0';

-- ============================================================
-- 5. compliance_rule / compliance_rule_version 合规规则版本化
-- ============================================================
CREATE TABLE IF NOT EXISTS app.compliance_rule (
    code             VARCHAR(64)  PRIMARY KEY,    -- ABSOLUTE_CLAIM / MEDICAL_RISK / AD_LAW_RISK / ...
    display_name     VARCHAR(128) NOT NULL,
    category_code    VARCHAR(32)  REFERENCES app.category(code) ON DELETE SET NULL,
    risk_level       VARCHAR(16)  NOT NULL,       -- LOW / MEDIUM / HIGH / BLOCKED
    description      TEXT,
    detector_type    VARCHAR(32)  NOT NULL,       -- KEYWORD / REGEX / ML / HEURISTIC
    detector_config  JSONB,
    sample_inputs    JSONB,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT compliance_rule_risk_check CHECK (risk_level IN ('LOW','MEDIUM','HIGH','BLOCKED')),
    CONSTRAINT compliance_rule_detector_check CHECK (detector_type IN ('KEYWORD','REGEX','ML','HEURISTIC'))
);

COMMENT ON TABLE app.compliance_rule IS '阶段0：合规规则定义。规则实例化见 compliance_rule_version';

CREATE TABLE IF NOT EXISTS app.compliance_rule_version (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code         VARCHAR(64)  NOT NULL REFERENCES app.compliance_rule(code) ON DELETE CASCADE,
    version           VARCHAR(16)  NOT NULL,
    effective_from    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    effective_to      TIMESTAMPTZ,
    rule_payload      JSONB        NOT NULL,    -- 生效版本的具体规则（关键词列表、正则、阈值等）
    risk_level        VARCHAR(16)  NOT NULL,
    superseded        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by        BIGINT REFERENCES app.users(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    notes             TEXT,
    UNIQUE (rule_code, version)
);

CREATE INDEX IF NOT EXISTS idx_compliance_version_active
    ON app.compliance_rule_version(rule_code, effective_from)
    WHERE superseded = FALSE;

COMMENT ON TABLE app.compliance_rule_version IS '阶段0：合规规则版本表。合规扫描器读取 effective_from <= now < effective_to 的版本';

INSERT INTO app.compliance_rule (code, display_name, category_code, risk_level, description, detector_type, detector_config) VALUES
    ('ABSOLUTE_CLAIM',    '绝对化承诺',         NULL,     'HIGH',   '禁止"绝对保证""100%""稳过"等绝对化表述',         'REGEX',  '{"patterns":["100%","绝对","保证","稳过","包过"]}'::jsonb),
    ('MEDICAL_RISK',      '医疗风险词',         'MEDICAL','BLOCKED','医疗内容必须人工审核，禁止"治愈""特效"',          'KEYWORD', '{"words":["治愈","特效","根治"]}'::jsonb),
    ('AD_LAW_RISK',       '广告法风险',         NULL,     'HIGH',   '极限词、虚假宣传、对比贬低',                        'REGEX',  '{"patterns":["最[好大强]","第一","唯一","顶级"]}'::jsonb),
    ('MINOR_PROTECT',     '未成年保护',         'EDU_MINOR','HIGH', 'K12 / 未成年人相关内容必须人工审核',                'HEURISTIC','{"min_age":18}'::jsonb),
    ('FINANCE_RISK',      '金融风险',           'FINANCE','BLOCKED','禁止收益承诺、稳赚不赔',                          'KEYWORD', '{"words":["稳赚","无风险","高收益","保本"]}'::jsonb),
    ('IMMIGRATION_RISK',  '移民风险词',         'IMMIGRATION','MEDIUM','风险词提示，不阻断',                           'KEYWORD', '{"words":["包过","百分百","100%通过"]}'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 6. connector_provider / platform_connector_authorization 连接器授权清单
-- ============================================================
CREATE TABLE IF NOT EXISTS app.connector_provider (
    code             VARCHAR(64)  PRIMARY KEY,     -- XHS_BROWSER_SIDEBAR / XHS_OFFICIAL_API / DOUYIN_CSV_IMPORT ...
    display_name     VARCHAR(128) NOT NULL,
    platform_code    VARCHAR(32)  NOT NULL REFERENCES app.platform(code) ON DELETE CASCADE,
    connector_type   VARCHAR(32)  NOT NULL,        -- BROWSER_EXTENSION / OFFICIAL_API / CSV_IMPORT / WEBHOOK_RECEIVER
    requires_oauth   BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_user_credentials BOOLEAN NOT NULL DEFAULT TRUE,
    risk_note        TEXT,
    enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT connector_provider_type_check CHECK (connector_type IN
        ('BROWSER_EXTENSION','OFFICIAL_API','CSV_IMPORT','WEBHOOK_RECEIVER','MANUAL_FORM'))
);

COMMENT ON TABLE app.connector_provider IS '阶段0：连接器实现清单。每条记录代表一个具体的实现方式';

CREATE TABLE IF NOT EXISTS app.platform_connector_authorization (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_code       VARCHAR(32)  NOT NULL REFERENCES app.platform(code) ON DELETE CASCADE,
    connector_provider_code VARCHAR(64) NOT NULL REFERENCES app.connector_provider(code) ON DELETE CASCADE,
    authorized          BOOLEAN      NOT NULL DEFAULT FALSE,
    authorization_source TEXT,                       -- e.g. "小红书开放平台授权函（2026-Q4）"
    legal_reviewer      VARCHAR(64),
    reviewed_at         TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    scope               TEXT,                       -- 授权范围描述（只读 / 读+写）
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (platform_code, connector_provider_code)
);

CREATE INDEX IF NOT EXISTS idx_platform_connector_auth_authorized
    ON app.platform_connector_authorization(platform_code, connector_provider_code)
    WHERE authorized = TRUE;

COMMENT ON TABLE app.platform_connector_authorization IS '阶段0：平台 × 连接器 授权矩阵。任何未授权的接入在运行时拒绝';

-- ============================================================
-- 7. data_retention_policy 默认值（V33 已建表，补默认数据）
-- ============================================================
-- 仅补充：所有现存 workspace 默认有保留策略（继承全局默认）
INSERT INTO app.data_retention_policy (workspace_id, asset_retention_days, log_retention_days, media_retention_days, export_retention_days, chat_session_retention_days, updated_by)
SELECT '00000000-0000-0000-0000-000000000000', 365, 365, 180, 7, 180, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM app.data_retention_policy WHERE workspace_id = '00000000-0000-0000-0000-000000000000'
);
