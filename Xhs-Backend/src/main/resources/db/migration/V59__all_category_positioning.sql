-- V59：产品定位迁移到「全平台全品类 AI 内容运营助手」
--
-- 背景：
--   产品定位从「小红书留学生求职垂类」升级为「全平台全品类 AI 内容运营助手」。
--   阶段 0 冻结时 app.category 只冻结了 10 个早期品类，官方模板种子（app.template）
--   仍锁在旧的求职垂类品类编码（career_fair / interview / visa / job_hunt /
--   career_development / salary / networking），与前端模板品类下拉框
--   （Xhs-Frontend/src/constants/templateCategories.ts）完全对不上。
--
-- 目标：
--   1. app.category 补齐前端展示所需的品类（FASHION / FOOD / TRAVEL / KNOWLEDGE / MOM_BABY），
--      并把既有品类的展示名对齐到前端 locale 文案。
--   2. app.template 增加 code 列，作为官方种子的幂等键（不再依赖「有数据就跳过」这种脆判定）。
--   3. 归档迁移前遗留的旧求职垂类官方模板 —— 新模板由 TemplateSeedRunner 按 code 幂等写入。
--   4. 归一存量 WORKSPACE / PERSONAL 模板的旧品类编码，避免它们在新品类筛选器里成为孤儿。
--
-- 约束：
--   - 幂等：全部用 DO $$ / IF NOT EXISTS / ON CONFLICT 守卫，可重复执行。
--   - 不删除任何模板行（app.template_apply_log / app.content_asset 有外键指向它），
--     旧模板只做 status='archived' 软归档，历史版本快照与引用记录完整保留。
--   - 不改 WORKSPACE / PERSONAL 模板的正文内容（那是用户自己的资产），
--     只改 category 让它们在新筛选器里可见。
--   - 不动 BLOCKED 风险品类（MEDICAL / FINANCE / LAW_SPECIFIC），它们是合规护栏，与产品定位无关。

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.category —— 补齐全平台全品类基线
-- ============================================================
INSERT INTO app.category (code, display_name, risk_level, auto_generate, auto_publish, description, sort_order) VALUES
    ('FASHION',   '穿搭时尚', 'LOW',  TRUE,  FALSE, '服饰搭配、鞋包、配饰',                      120),
    ('FOOD',      '美食探店', 'LOW',  TRUE,  FALSE, '探店、菜谱、零食、饮品',                      130),
    ('TRAVEL',    '旅行出行', 'LOW',  TRUE,  FALSE, '目的地攻略、路线、行前准备',                  140),
    ('KNOWLEDGE', '知识干货', 'LOW',  TRUE,  FALSE, '学习方法、职场通用技能、科普',                160),
    ('MOM_BABY',  '母婴亲子', 'HIGH', TRUE,  FALSE, '育儿内容 —— 未成年人相关，必须人工审核',     170)
ON CONFLICT (code) DO NOTHING;

-- 既有品类：展示名 / 描述对齐前端 locale 文案（V34 冻结时的旧名偏求职垂类）
UPDATE app.category SET display_name = '美妆护肤', description = '护肤、彩妆、香水 —— 医美相关表述必须人工审核'
WHERE code = 'BEAUTY';

UPDATE app.category SET display_name = '家居生活', description = '收纳、家居好物、日常习惯'
WHERE code = 'LIFESTYLE';

UPDATE app.category SET display_name = '职场成长', description = '简历、面试、晋升、副业与行业认知'
WHERE code = 'CAREER';

UPDATE app.category SET display_name = '教育留学', description = '留学申请、文书、选校与学习规划'
WHERE code = 'STUDY_ABROAD';

-- ============================================================
-- 2. app.template —— 增加 code 幂等键
-- ============================================================
ALTER TABLE app.template ADD COLUMN IF NOT EXISTS code VARCHAR(64);

COMMENT ON COLUMN app.template.code IS '官方种子幂等键（scope=OFFICIAL 时全局唯一）；用户自建模板为 NULL';

-- 唯一约束只作用于 OFFICIAL，避免 WORKSPACE 复制品互相冲突
CREATE UNIQUE INDEX IF NOT EXISTS uk_template_official_code
    ON app.template (code)
    WHERE scope = 'OFFICIAL' AND code IS NOT NULL;

-- ============================================================
-- 3. 归档迁移前遗留的旧官方模板
--    code IS NULL 即「本次迁移之前由旧种子写入」的模板（仅 OFFICIAL 范围）
-- ============================================================
UPDATE app.template
SET status = 'archived', updated_at = now()
WHERE scope = 'OFFICIAL'
  AND status = 'active'
  AND code IS NULL;

-- ============================================================
-- 4. 存量 WORKSPACE / PERSONAL 模板的旧品类编码归一
--    正文不改（用户资产），只改 category 让它在新筛选器里可被检索
-- ============================================================
UPDATE app.template SET category = 'KNOWLEDGE', updated_at = now()
WHERE scope IN ('WORKSPACE', 'PERSONAL')
  AND category IN ('career_fair', 'interview', 'visa', 'job_hunt');

UPDATE app.template SET category = 'CAREER', updated_at = now()
WHERE scope IN ('WORKSPACE', 'PERSONAL')
  AND category IN ('career_development', 'salary', 'networking');

-- 兜底：任何仍残留旧编码的模板（含漏网的 OFFICIAL）统一归入 OTHER
UPDATE app.template SET category = 'OTHER', updated_at = now()
WHERE category IN ('career_fair', 'interview', 'visa', 'job_hunt',
                   'career_development', 'salary', 'networking');

COMMENT ON TABLE app.template IS
    '模板库：OFFICIAL / WORKSPACE / PERSONAL。category 使用 app.category 品类编码（全平台全品类）；code 为官方种子幂等键。';
