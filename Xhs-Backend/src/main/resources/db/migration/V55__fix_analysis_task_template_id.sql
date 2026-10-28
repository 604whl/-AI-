-- 修复：app.analysis_task.template_id 列缺失
--
-- 背景：commit c951885（feat(template): P0-4 模板库）在 AnalysisTask 实体中新增了
-- templateId 字段，并在 AnalysisAppService 中写入 / AnalysisAppService 读取回显，
-- 但当时并未提供对应的数据库迁移，导致：
--   1. MyBatis-Plus 自动生成的 SELECT 包含 template_id，报
--      "column \"template_id\" does not exist"；
--   2. Agent 工具 list_recent_analyses 每次调用都失败。
--
-- 字段语义：关联 app.template(id)（UUID）。实体侧使用 String 承载，
-- 因此这里用 VARCHAR(64) 存放 UUID 文本，避免 JDBC 层的 varchar -> uuid 隐式转换问题。
-- 不建立外键：app.template(id) 为 UUID 类型，跨类型无法建立引用完整性，
-- 模板删除语义已由 content_asset.template_id 的 ON DELETE SET NULL 覆盖。

SET search_path TO app, kb, public;

ALTER TABLE app.analysis_task
    ADD COLUMN IF NOT EXISTS template_id VARCHAR(64);

COMMENT ON COLUMN app.analysis_task.template_id IS
    '关联的模板 ID（app.template.id 的 UUID 文本形式），分析时可选的参考模板';
