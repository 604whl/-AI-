# 首批知识库 Seed 清单（6 × 5 = 30 条）

> 用于 RAG 测试：`data/kb-sources/` 下 30 个 `.md` 文件，覆盖 6 种 `content_type` × 5 种 `doc_type`。
> 入库：`POST /api/v1/admin/kb/reindex`（local 环境）

## 矩阵总览

| content_type | viral_case | title_pattern | structure_template | cta_snippet | topic_card |
|--------------|------------|---------------|--------------------|-------------|------------|
| **ANXIETY** | CASE-01 | TP-ANXIETY-01 | ST-ANXIETY-01 | CS-ANXIETY-01 | TC-ANXIETY-01 |
| **OFFER** | CASE-02 | TP-OFFER-01 | ST-OFFER-01 | CS-OFFER-01 | TC-OFFER-01 |
| **INFO_GAP** | VC-INFO-GAP-01 | TP-INFO-GAP-01 | ST-INFO-GAP-01 | CS-INFO-GAP-01 | TC-INFO-GAP-01 |
| **INTERVIEW** | VC-INTERVIEW-01 | TP-INTERVIEW-01 | ST-INTERVIEW-01 | CS-INTERVIEW-01 | TC-INTERVIEW-01 |
| **TIMELINE** | VC-TIMELINE-01 | TITLE-PATTERN-01 | ST-TIMELINE-01 | CS-TIMELINE-01 | TC-TIMELINE-01 |
| **COMEBACK** | VC-COMEBACK-01 | TP-COMEBACK-01 | ST-COMEBACK-01 | CS-COMEBACK-01 | TC-COMEBACK-01 |

## 文件清单

| # | doc_id | 文件 | doc_type | content_type | 标题摘要 |
|---|--------|------|----------|--------------|----------|
| 1 | CASE-01 | CASE-01-anxiety.md | viral_case | ANXIETY | 敏感肌防晒避坑 |
| 2 | CASE-02 | CASE-02-offer.md | viral_case | OFFER | 周末探店 6 站路线 |
| 3 | VC-INFO-GAP-01 | VC-INFO-GAP-01.md | viral_case | INFO_GAP | 新手化妆 5 个踩雷点 |
| 4 | VC-INTERVIEW-01 | VC-INTERVIEW-01.md | viral_case | INTERVIEW | 大厂产品经理 Q&A |
| 5 | VC-TIMELINE-01 | VC-TIMELINE-01.md | viral_case | TIMELINE | 30 天减脂打卡 |
| 6 | VC-COMEBACK-01 | VC-COMEBACK-01.md | viral_case | COMEBACK | 裸辞 90 天重启 |
| 7 | TP-ANXIETY-01 | TP-ANXIETY-01.md | title_pattern | ANXIETY | 焦虑共鸣型标题模板 |
| 8 | TP-OFFER-01 | TP-OFFER-01.md | title_pattern | OFFER | 结果晒单型标题模板 |
| 9 | TP-INFO-GAP-01 | TP-INFO-GAP-01.md | title_pattern | INFO_GAP | 信息差干货标题模板 |
| 10 | TP-INTERVIEW-01 | TP-INTERVIEW-01.md | title_pattern | INTERVIEW | 问答清单型标题模板 |
| 11 | TITLE-PATTERN-01 | TITLE-PATTERN-01.md | title_pattern | TIMELINE | 全品类高 CTR 标题模板 |
| 12 | TP-COMEBACK-01 | TP-COMEBACK-01.md | title_pattern | COMEBACK | 逆袭叙事型标题模板 |
| 13 | ST-ANXIETY-01 | ST-ANXIETY-01.md | structure_template | ANXIETY | 痛点放大结构骨架 |
| 14 | ST-OFFER-01 | ST-OFFER-01.md | structure_template | OFFER | 结果前置结构骨架 |
| 15 | ST-INFO-GAP-01 | ST-INFO-GAP-01.md | structure_template | INFO_GAP | 清单干货结构骨架 |
| 16 | ST-INTERVIEW-01 | ST-INTERVIEW-01.md | structure_template | INTERVIEW | Q&A 拆解结构骨架 |
| 17 | ST-TIMELINE-01 | ST-TIMELINE-01.md | structure_template | TIMELINE | 时间线步骤结构骨架 |
| 18 | ST-COMEBACK-01 | ST-COMEBACK-01.md | structure_template | COMEBACK | 低谷到高光结构骨架 |
| 19 | CS-ANXIETY-01 | CS-ANXIETY-01.md | cta_snippet | ANXIETY | 焦虑型 CTA 话术 |
| 20 | CS-OFFER-01 | CS-OFFER-01.md | cta_snippet | OFFER | 晒单型 CTA 话术 |
| 21 | CS-INFO-GAP-01 | CS-INFO-GAP-01.md | cta_snippet | INFO_GAP | 干货领资料 CTA |
| 22 | CS-INTERVIEW-01 | CS-INTERVIEW-01.md | cta_snippet | INTERVIEW | 问答互动 CTA |
| 23 | CS-TIMELINE-01 | CS-TIMELINE-01.md | cta_snippet | TIMELINE | 打卡跟练 CTA |
| 24 | CS-COMEBACK-01 | CS-COMEBACK-01.md | cta_snippet | COMEBACK | 逆袭共鸣 CTA |
| 25 | TC-ANXIETY-01 | TC-ANXIETY-01.md | topic_card | ANXIETY | 护肤焦虑选题卡 |
| 26 | TC-OFFER-01 | TC-OFFER-01.md | topic_card | OFFER | 本地生活晒单选题卡 |
| 27 | TC-INFO-GAP-01 | TC-INFO-GAP-01.md | topic_card | INFO_GAP | 职场信息差选题卡 |
| 28 | TC-INTERVIEW-01 | TC-INTERVIEW-01.md | topic_card | INTERVIEW | 求职面试选题卡 |
| 29 | TC-TIMELINE-01 | TC-TIMELINE-01.md | topic_card | TIMELINE | 习惯养成选题卡 |
| 30 | TC-COMEBACK-01 | TC-COMEBACK-01.md | topic_card | COMEBACK | 人生重启选题卡 |

## 测试步骤

1. 确认 `app.kb.source-dir` 指向 `./data/kb-sources`
2. `POST /api/v1/admin/kb/reindex` → 期望 `filesProcessed: 30`（若含 README.md 则为 31，可暂时移出或加 `doc_type: _meta` 后手动跳过）
3. 开启 RAG：`app.rag.enabled=true`，按需开 `analysis-enabled` / `title-enabled`
4. 检索评测：对每种 `content_type` 提交一条测试笔记，检查 `search_kb` Top5 是否命中同类型案例

## 检索预期（示例）

| 测试输入关键词 | 期望命中 doc_type | 期望 content_type |
|----------------|-------------------|-------------------|
| 敏感肌踩雷焦虑 | viral_case / structure_template | ANXIETY |
| 探店路线人均 | viral_case | OFFER |
| 5 个避坑清单 | title_pattern / viral_case | INFO_GAP |
| 面试高频问题 | structure_template | INTERVIEW |
| 30 天打卡计划 | structure_template / topic_card | TIMELINE |
| 裸辞逆袭重启 | viral_case / cta_snippet | COMEBACK |
