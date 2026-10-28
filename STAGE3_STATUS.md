# 阶段 3 状态 Checkpoint

> 用途：后续会话只读本文件即可续接，**不必**整篇读 `STAGE3_ACCEPTANCE.md`（74KB）。
> 详细验收标准见 `STAGE3_ACCEPTANCE.md` 对应章节。

## 总体状态

| 项 | 值 |
|---|---|
| 阶段 | 阶段 3（连接器 / 指标 / 复盘 / 发布 / 竞品 / 知识库管理面） |
| 完成度 | **13 / 13 子任务（S3-1 ~ S3-13）已落地** |
| 出口条件 | E1 / E2 / E3 **全部 PASS** |
| 后端验证 | `mvn clean package` EXIT=0；E2E 脚本 48 通过 / 0 失败（连跑两次） |
| 前端验证 | `vue-tsc -b` EXIT=0；**7 个新页面未做浏览器端联调** |
| 遗留 | 真实平台连接需人工签署授权（`XHS_AUTHORIZED_CONNECTOR` / `XHS_OFFICIAL_API` 保持 disabled） |

## 子任务完成明细

| ID | 名称 | 状态 | 关键产出 |
|----|------|------|---------|
| S3-1 | 连接器抽象 + 授权门禁 | 完成 | `connector/` 包 + `ConnectorController`，运行时 403 生效 |
| S3-2 | 平台账号 + 状态机 | 完成 | `platform_account` + `platform_connector_authorization`（V51） |
| S3-3 | 发布排期 + 冲突检测 | 完成 | `PublishSchedule` / `PublishTask`（V56），幂等键 + 409 冲突 |
| S3-4 | 发布回执回收 | 完成 | `PublishReceipt`，回填后资产推进到 `published` |
| S3-5 | 指标快照 + 修订链 | 完成 | `metric_snapshot` + 修订链 + CSV（V52） |
| S3-6 | 趋势预测 | 完成 | `ForecastService` + `ForecastController` |
| S3-7 | 竞品对比 | 完成 | `ComparisonService` + `ComparisonController` |
| S3-8 | 复盘报告 | 完成 | `RetrospectiveService` + `RetrospectiveController` |
| S3-9 | 热点产品化 | 完成 | 来源可信度 + `hot_topic_cache`（V53） |
| S3-10 | 竞品库 | 完成 | `CompetitorAccount` / `CompetitorNote`（V58） |
| S3-11 | 知识库管理面 | 完成 | `KbDocument` + `KbDocumentAdminController`（V54） |
| S3-12 | 阶段 3 前端 7 页 | 完成 | `views/publish/` `metrics/` `retrospective/` `hotTopic/` `competitor/` `kb/` `connector/` |
| S3-13 | E2E 验证脚本 | 完成 | `scripts/stage3-e2e.ps1`（48 断言）+ `scripts/stage3-e2e-browser.ps1`（16 断言） |

## 下一步（阶段 4 候选）

1. **前端浏览器端联调** —— 7 个新页面只做了 `vue-tsc` 类型检查，需真实跑通。
2. **真实平台授权** —— 需人工签署，`XHS_AUTHORIZED_CONNECTOR` / `XHS_OFFICIAL_API` 开关打开。
3. **清理一次性脚本** —— `scripts/_*.py` 已于 22:05 清理，保留 `_audit_*.py`。

## 冻结决策

| ID | 决策 | 值 |
|----|------|---|
| D1 | 连接器授权方式 | **C —— 仅人工导入** |
| D2 | 支持平台 | **B —— 小红书 + 抖音** |
| D3 | 角色权限 | **B —— OWNER + ADMIN** |
| D4 | 指标采集频率 | 默认值 |
| D5 | 复盘建议条数 | 默认值（≥3 条且带 evidenceRefs） |

## 复盘：本阶段踩过的坑（后续阶段避免）

| 坑 | 影响面 | 规则 |
|----|--------|------|
| jsonb 列缺 TypeHandler | 5 处 500 | 实体挂 handler + `autoResultMap` |
| `? IS NULL OR` PG 无法推断类型 | 9 Mapper / 36 处 | 按 `information_schema` 真实类型加 CAST |
| `workspace_id` 类型不统一 | 17 文件连锁 | 建表时定死 VARCHAR(32) |
| HTTP 状态码只映射 40301/40401 | 全部落 400 | 403xx→403，404xx→404 |
| Redis Stream `Consumer.from` 参数错位 | 任务永不消费 | `Consumer.from(group, consumer)` |
| 批量正则脚本改源文件 | 编码损坏 3 文件 | 单文件 StrReplace，禁批量正则 |

## 已落地迁移

`V49` `V50` `V51` `V52` `V53` `V54` `V55` `V56` `V57` `V58` —— 全部已在本地库实测通过。
