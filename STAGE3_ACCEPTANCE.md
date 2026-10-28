# 阶段3 验收清单（基于 `COMMERCIAL_SAAS_READINESS_PLAN.md` §9）

> 本清单**严格依据** `COMMERCIAL_SAAS_READINESS_PLAN.md` 第 9 章"阶段 3：真实数据闭环"，并合并 §4.3（选题、热点与研究）、§4.4（AI 分析与评分）、§4.5（标题正文评论生成）、§4.6（模板库与知识库）、§4.8（发布、排期和平台连接器）、§4.9（发后数据与复盘闭环）的必做功能与验收标准。
> 文档原始范围 4 项，本清单逐项拆解为 5 个子能力，每个子能力拆到可验收的功能点 + 接口契约 + 数据模型 + 验收用例。
>
> **约束来源（阶段 0/1/2 已冻结，阶段 3 不得违反）**：
> - `Xhs-Backend/docs/CONNECTOR-AUTHORIZATION.md` —— 平台 × 连接器授权清单
> - `Xhs-Backend/docs/STAGE0-FREEZE-DECLARATION.md` —— 内容状态机 / 工作区 / 角色 / 计费口径 / 品类规则包 / 合规边界 / 数据保留
> - `Xhs-Backend/docs/BILLING-RULES.md` —— 计量单位与 DEBIT / REFUND / WRITE_OFF 语义
> - `STAGE2_ACCEPTANCE.md` —— 阶段 2 验收清单（本文件格式与文风模板）
> - `Xhs-Backend/docs/STAGE0-FREEZE-PLAN.md` —— 明确"不开发连接器实现（阶段 3）""不做热点采集（阶段 3）"，二者在阶段 0 正式移交阶段 3

---

## 阶段3 文档原文（§9 摘录）

> 目标：系统可以指导下一轮运营，而不是只生成内容。
>
> 1. 热点、竞品、账号、平台指标连接器和手动导入。
> 2. 发布回执、指标快照、预测对比、复盘报告和下一步建议。
> 3. 数据源可信度、更新时间、连接器诊断和降级。
> 4. 复盘结果经过人工确认后进入模板和知识库效果统计。
>
> 出口条件：至少一个平台完成真实的发布或手动发布回收闭环，复盘报告可以生成三条可执行建议。

## 阶段3 不在范围

- 计费后台、订阅、套餐、退款、发票、成本毛利 → 阶段4
- 管理后台（用户 / 工作区 / 套餐 / 用量 / 失败任务 / 投诉 / 内容举报）、客服工具、租户销毁 → 阶段4
- 开放 API、Webhook、合作伙伴连接器市场、开发者文档 → 阶段4
- 压测、容量规划、灾备演练、多区域部署 → 阶段4
- 跨平台内容真实适配（小红书 / 抖音以外的平台规则、真实发布通道）→ 阶段3+，**另立文档**，本阶段只保留接入位与降级路径
- 内容效果预测模型训练、自动实验、智能预算分配 → P2，不在阶段 3 出口条件内（阶段 3 只做"预测 vs 实际对比"的结构化存储与展示）
- 计费改造：阶段 3 新增的 AI 调用（复盘报告生成）**沿用** `BillableUnit.ANALYSIS` 计量，发布、指标录入本身不计费

---

## 阶段3 现状盘点（开发前必读）

| 区域 | 已有 | 缺（阶段 3 必须补） |
|---|---|---|
| 发布/排期 | `freeze/ContentStatus` 含 SCHEDULED/PUBLISHED；`content_asset` 含 platform/accountId/status；`freeze/PlatformConnectorLevel`；`app.platform`；`Stage0Service.canPlatformWrite/canPlatformRead`；`BrowserAssistSession` + `/v1/browser-assist` | `platform_account` / `publish_task` / `publish_receipt` / `publish_schedule` 表与实体；发布服务与 Controller；连接器抽象接口与适配器；排期日历 API；发布回执与外部内容 ID |
| 指标回流 | `analysis_task.published_metrics` JSONB；`PublishedMetricsDto`；`PromptEngine.appendPublishedMetrics` | `metric_snapshot` 表/实体/服务/Controller；采集定时任务；聚合与趋势 API；可信度/来源/采集时间/人工修订字段；预测值与实际值分离存储 |
| 热点 | `hot_topic` 表 + 实体 + `HotTopicService`（refresh/promoteToTopicPool/list/computeTrend）+ `/api/v1/hot-topics` + `TopicPoolScheduler`（4h） | 来源标记与可信度分级；`fetchedAt` 在 UI/接口暴露；连接器诊断与降级说明；失败原因与重试 |
| 选题池 | `topic_pool` + 实体 + CRUD + 去重 + bulk-import + to-asset + `/api/v1/topics` | 热点→选题的来源可信度透传；选题→排期的衔接 |
| 竞品研究 | `analysis_task.competitor_context` JSONB；`CompetitorContextDto`；`PromptEngine.appendCompetitorContext`；`content_asset.scenario='competitor'` | 竞品账号库、竞品笔记采集、样本证据、更新时间、账号维度对比 |
| 知识库 | `AdminKbController`（**`@Profile("local")`**，仅 POST /reindex + GET /preview）；`KnowledgeIngestService`；`KbSourceLoader`/`KbDocumentSplitter`/`KbEmbeddingService`/`KbDocumentStore`（裸 SQL 写 `kb.kb_document`）；`PgVectorRagRetriever`；`SearchKbTool` | 文档列表/检索/删除 API；`kb_document` 实体与 Mapper；逐文档索引状态；审核流；非 local profile 可用；**复盘结果回写审批入口** |
| 已发复盘 | `PromptEngine.scenarioLabel` 把 `scenario="published"` 映射为"已发复盘"；`PublishedMetricsDto` 手动填入后注入 prompt | 预测 vs 实际的分字段存储与偏差计算；复盘报告实体/服务/Controller；结构化"下一步建议"；回写模板与知识库效果统计的审批流 |

**前端缺口（`Xhs-Frontend`）**：完全没有发布/排期页、指标看板/报表页、复盘页、选题池页、热点榜页、竞品库页、知识库管理页；`src/api/` 下没有 publish / schedule / metrics / retrospective / topic / hotTopic / competitor / kb 模块。
可复用地基：`types/api.ts` 已有 `PublishedMetrics` / `CompetitorContext` 类型；`components/analysis/AnalysisContentFields.vue` 在 `scenario==='published'` 时显示手动指标录入表单；`types/api.ts` 的 `AssetStatus` 已含 `'scheduled' | 'published'`；`views/asset/ContentAssetListView.vue` 状态筛选已含 scheduled/published；`components/report/ReportCompetitorPanel.vue` 的"可借鉴点 / 勿照搬点"渲染可复用为竞品证据展示；`components/workbench/HotTopicsPanel.vue` 与 `components/chat/ChatHotTopicsCard.vue` 是 AI 工具驱动的热点卡片（无持久化、无来源标记，阶段 3 需替换为数据产品）。
**i18n 现状**：zh-CN 缺 `retentionPolicy`（zh-TW/en 有）；`locales/types.ts` 缺 quota / asset / export / review / admin / retention 等命名空间；zh-TW 缺 `admin`。阶段 3 新增命名空间（`publish` / `metrics` / `retrospective` / `hotTopic` / `competitor` / `kb` / `connector`）必须在 zh-CN / zh-TW / en 三语同步，并同步补齐 `types.ts` 类型声明。

---

## 子能力 1：平台账号连接 / 排期日历 / 发布回执

### 1.1 平台账号连接与连接状态

**功能**：
- 平台账号绑定（工作区维度）、账号标签、账号状态（未连接 / 已连接 / 授权过期 / 失效）
- 连接状态、授权过期时间、重新授权入口、最小权限声明（读 / 写分离）
- 连接器能力标记：平台当前连接等级来自 `app.platform.connector_level` + `app.platform_connector_authorization`，**不允许前端传入等级**

**待验收**：
- `app.platform_account` 表：id / workspace_id / platform_code / account_label / external_account_id / connector_provider_code / level / status / credential_ref / authorized_at / expires_at / last_success_at / last_error / created_by / created_at
- 连接状态机：
  ```
  unconnected → connected → expired → reauthorizing → connected
                    ↘ failed（连接器报错，保留 last_error）
  ```
- **未列入 `app.platform_connector_authorization`（authorized=TRUE）的平台 × 连接器组合，运行时一律拒绝**：返回 `403 CONNECTOR_NOT_AUTHORIZED`，不落库
- `app.platform.rollout_status='disabled'` 的平台（KUAISHOU / INSTAGRAM / TIKTOK）一律拒绝，连 `platform_account` 都不允许创建
- 授权到期前 7 天 / 到期当天 / 已过期三个时间点的 UI 提示与接口提示不同（未到期预警 / 到期提醒 / 需重新授权）
- 最小权限：`write_enabled=false` 的平台（如 DOUYIN）只能建 MANUAL_IMPORT 类型的任务，不能建自动发布任务
- 凭据不落明文：Cookie / Token 走 `credential_ref` 指向 AES-GCM 加密存储，日志与接口响应**不得**出现凭据明文（复用 `BrowserAssistService` 的 AES-GCM 约定）
- 连接/重授权/解绑全部写 `app.audit_event`
- 权限：连接与解绑仅 OWNER / ADMIN；EDITOR / REVIEWER / ANALYST / VIEWER 只读

### 1.2 排期日历

**功能**：
- 排期创建（资产 + 平台 + 账号 + 时间）、时区、冲突检测、取消、重试、失败原因
- 日历视图（按天/周/月的时段占用）
- 排期与内容状态机联动（APPROVED → SCHEDULED → PUBLISHED）

**待验收**：
- `app.publish_schedule` 表：id / workspace_id / asset_id / platform_code / account_id / scheduled_at / timezone / status / publish_task_id / created_by / created_at / updated_at
  - status：`scheduled / executing / succeeded / failed / cancelled / expired`
- 排期必须建立在 **已审核通过**（`content_asset.status='approved'`）的资产上；`draft` / `in_review` 资产排期返回 `409 ASSET_NOT_APPROVED`
- 走 `freeze/ContentStatus.canTransition` 校验：仅 `APPROVED → SCHEDULED` 合法；`DRAFT → SCHEDULED` 拒绝
- 时区：排期时间以 **工作区时区** 存储（UTC 落库 + timezone 字段），跨时区展示按工作区时区；同一时刻不同时区表示的排期必须能正确排序
- 冲突检测：同一工作区 + 同一平台 + 同一账号 + 同一时刻（精确到分钟）已有非终态排期时，创建返回 `409 SCHEDULE_CONFLICT` 并返回冲突排期 id；不同账号同一时刻允许
- 取消：`scheduled` 可取消 → `cancelled`，同时资产状态回退 `SCHEDULED → APPROVED`（走状态机允许的转换）
- 重试：`failed` 可重试，**复用原 `publish_task` 的幂等键**，不产生新的外部发布
- 过期：排期时间已过但未被 worker 执行 → 标 `expired`，不静默丢弃
- 失败原因：`publish_task.error_code` + `error_message` + `retryable` 必须有值且可读（不是 "unknown"）
- 排期列表 / 日历按 workspace 隔离；跨 workspace 查询返回 404（不返回 403 泄露存在性）

### 1.3 发布任务与发布回执

**功能**：
- 发布任务执行（连接器 / 浏览器辅助 / 手动导入三条路径）
- 发布回执、外部内容 ID、来源链接
- 幂等保证不重复发布

**待验收**：
- `app.publish_task` 表：id / workspace_id / schedule_id / asset_id / platform_code / account_id / connector_provider_code / level / idempotency_key(UNIQUE) / status / attempt / error_code / error_message / retryable / started_at / finished_at / created_at
- `app.publish_receipt` 表：id / publish_task_id / workspace_id / external_content_id / source_url / published_at / published_by / raw_response JSONB / verified / created_at
- **幂等**：`idempotency_key` 唯一索引 + 任务入队前检查
  - 同一 key 重复投递 → 只执行一次，第二次返回已有任务
  - **发布任务重复发布率必须为 0**（§8.2）
  - 重试路径必须携带原 key
- 发布状态机：`queued → running → succeeded / failed / dead_letter / cancelled`
- 回执完整性：成功任务必须有 `external_content_id` **或** `source_url`（至少一项）；两者皆空视为回执不完整，任务标 `failed` 且 `error_code=RECEIPT_INCOMPLETE`，不得假装成功
- 回执时间 `published_at` 来自平台返回，不使用本地时间兜底（本地兜底必须另标 `published_at_source='local'`）
- 三条降级路径（**同一套 `publish_task` 表，用 `level` 区分**）：
  - `OFFICIAL_API` / `AUTHORIZED_CONNECTOR`：系统自动发布，回写回执
  - `BROWSER_ASSIST`：**系统不点发布**，用户确认后由运营自行发布 → 用户回填 `external_content_id` / `source_url` → 生成回执；UI 必须显著提示"用户自负发布风险"
  - `MANUAL_IMPORT`：生成待发布清单 + 导出，运营自行发布后回填 → 生成回执
- 连接器异常**不阻塞**内容编辑、审核、手动导出（§4.8 验收标准）：连接器不可用时 `POST /publish-tasks` 返回 `503 CONNECTOR_UNAVAILABLE` 并附 `degradedTo` 建议（`BROWSER_ASSIST` / `MANUAL_IMPORT` / `EXPORT_ONLY`），资产仍可编辑、审核、导出
- 回填回执需权限：OWNER / ADMIN / EDITOR；ANALYST 只读
- 每次发布动作写 `app.audit_event`（操作者、平台、账号、资产、外部内容 ID）

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 请求要点 | 响应要点 | 错误码 |
|---|---|---|---|---|---|
| POST | `/api/v1/platform-accounts` | OWNER/ADMIN | `{platformCode, accountLabel, connectorProviderCode, credentialRef?}` | `{id, status, level, expiresAt}` | 409 `ACCOUNT_EXISTS` / 403 `CONNECTOR_NOT_AUTHORIZED` / 403 `PLATFORM_DISABLED` |
| GET | `/api/v1/platform-accounts?platform=&status=` | 全部角色（读） | - | `[{id, platformCode, accountLabel, level, status, expiresAt, lastSuccessAt, lastError}]` | - |
| POST | `/api/v1/platform-accounts/{id}/reauthorize` | OWNER/ADMIN | `{credentialRef}` | `{id, status, expiresAt}` | 403 `CONNECTOR_NOT_AUTHORIZED` / 409 `ACCOUNT_NOT_EXPIRED` |
| DELETE | `/api/v1/platform-accounts/{id}` | OWNER/ADMIN | - | 204 | 404 `ACCOUNT_NOT_FOUND` |
| POST | `/api/v1/publish-schedules` | EDITOR+ | `{assetId, platformCode, accountId, scheduledAt, timezone}` | `{id, status, assetStatus}` | 409 `ASSET_NOT_APPROVED` / 409 `SCHEDULE_CONFLICT` / 404 `ACCOUNT_NOT_FOUND` |
| GET | `/api/v1/publish-schedules?from=&to=&platform=&status=` | 全部角色（读） | - | `[{id, assetId, title, platformCode, accountId, scheduledAt, timezone, status}]` | - |
| POST | `/api/v1/publish-schedules/{id}/cancel` | EDITOR+ | - | `{id, status}` | 409 `SCHEDULE_NOT_CANCELLABLE` |
| POST | `/api/v1/publish-schedules/{id}/retry` | EDITOR+ | - | `{id, publishTaskId, status}` | 409 `SCHEDULE_NOT_FAILED` |
| GET | `/api/v1/publish-schedules/calendar?from=&to=` | 全部角色（读） | - | `{days: [{date, slots: [{scheduleId, platformCode, status}]}]}` | - |
| POST | `/api/v1/publish-tasks` | EDITOR+ | `{assetId, platformCode, accountId, scheduledAt?}` + `Idempotency-Key` Header | `{taskId, status, idempotencyKey}` | 409 `DUPLICATE_PUBLISH` / 503 `CONNECTOR_UNAVAILABLE` |
| GET | `/api/v1/publish-tasks/{id}` | 全部角色（读） | - | `{id, status, attempt, errorCode, errorMessage, retryable, receipt?}` | 404 `TASK_NOT_FOUND` |
| POST | `/api/v1/publish-tasks/{id}/cancel` | EDITOR+ | - | `{id, status}` | 409 `TASK_NOT_CANCELLABLE` |
| POST | `/api/v1/publish-receipts` | EDITOR+ | `{publishTaskId, externalContentId?, sourceUrl?, publishedAt?}` | `{id, verified}` | 400 `RECEIPT_INCOMPLETE` / 409 `TASK_NOT_AWAITING_RECEIPT` |
| GET | `/api/v1/assets/{id}/receipts` | 全部角色（读） | - | `[{id, externalContentId, sourceUrl, publishedAt, publishedBy}]` | - |

> 限流：发布任务创建 60 次/小时/工作区；回执回填 120 次/小时/工作区；排期创建 200 次/天/工作区。
> 所有写操作接受 `Idempotency-Key` Header（沿用阶段 1 幂等键服务）。

---

## 子能力 2：指标快照与数据回流

### 2.1 统一指标模型

**功能**：
- 平台适配器统一输出：平台、账号、内容、发布时间、指标、数据来源、采集时间、可信度
- **不允许各平台把字段写死在页面组件中**（§2.1 硬约束）

**待验收**：
- `app.metric_snapshot` 表：id / workspace_id / platform_code / account_id / asset_id / external_content_id / content_type / published_at / metrics JSONB / metric_source / confidence / collected_at / is_manual / revised_from_id / revised_by / created_at
  - `metric_source`：`OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT`
  - `confidence`：`HIGH / MEDIUM / LOW / UNVERIFIED`
  - `is_manual`：是否人工录入
- 统一指标字段口径（`metrics` JSONB 的**固定键集**，缺哪个写 0 而不是省略）：
  - 曝光 `impressions`、播放 `plays`、点击 `clicks`、点赞 `likes`、收藏 `collects`、评论 `comments`、分享 `shares`、关注 `follows`、私信 `dm_inquiries`、成交 `conversions`
  - 平台差异通过 `metric_extra` JSONB 扩展（如抖音 `completion_rate`、B 站 `danmaku`），**核心 11 个字段所有平台都必须存在**
- 平台 → 指标字段映射表由 `PlatformConnectorLevel` 对应的适配器提供，**不得在前端硬编码**
- `analysis_task.published_metrics`（既有 JSONB）**保留但降级为兼容读取**：新写入一律走 `metric_snapshot`；旧记录读取时映射为虚拟快照，`metric_source='LEGACY_JSONB'`、`confidence='LOW'`、`collected_at` 取 `publishedAt`
- 快照可修订：人工修订产生**新快照**（`revised_from_id` 指向原快照），**不原地覆盖**；原快照保留可追溯
- 指标归属校验：创建快照必须校验 `asset_id` 属于当前 workspace，且 `platform_code` + `account_id` 与资产一致；不一致返回 `409 ACCOUNT_MISMATCH`

### 2.2 采集与录入

**功能**：
- 自动同步（连接器 / 浏览器辅助回收）
- 手动录入（表单 / CSV 批量导入）

**待验收**：
- 采集定时任务：按工作区 × 平台 × 账号维度，每 **6h** 执行一次（沿用阶段 1 持久化任务队列 + 幂等 + DLQ，不引入 `@Async`）
- 采集任务幂等键 = `平台 + 账号 + 内容外部ID + 采集窗口`，同窗口重复采集不产生重复快照
- 手动录入：`POST /metric-snapshots` 直接写快照，`metric_source='MANUAL_IMPORT'`、`confidence` 由用户选择（默认 `LOW`）、`is_manual=TRUE`、`collected_at` 默认 `now()` 但用户可填平台实际统计时间
- CSV 导入：复用既有 `app.csv_import_log`（`import_type='METRIC'`），每行一条指标快照；导入前做**表头白名单校验**（只接受标准字段 + 平台扩展字段），未知列拒绝并写入 `error_detail`
- 导入失败行不影响成功行；`csv_import_log.error_detail` 必须含行号 + 原因
- 同步失败：连续失败 3 次任务进 DLQ，连接器健康状态标 `DEGRADED`，界面展示失败原因与最后成功时间
- **数据同步延迟、失败率和最后成功时间必须可见**（§8.2）
- 采集任务失败**不计费**（无 `BillableUnit` 适用于指标采集），不写 `usage_ledger` DEBIT

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 请求要点 | 响应要点 | 错误码 |
|---|---|---|---|---|---|
| POST | `/api/v1/metric-snapshots` | ANALYST/EDITOR/ADMIN/OWNER | `{assetId?, platformCode, accountId?, externalContentId?, publishedAt?, metrics:{impressions,plays,...}, metricSource, confidence, collectedAt?}` | `{id, metricSource, confidence, collectedAt, isManual}` | 400 `METRIC_FIELDS_MISSING` / 409 `ACCOUNT_MISMATCH` / 404 `ASSET_NOT_FOUND` |
| GET | `/api/v1/metric-snapshots?assetId=&platform=&from=&to=&source=` | 全部角色（读） | - | `[{id, assetId, platformCode, metrics, metricSource, confidence, collectedAt, isManual, revisedFromId}]` | - |
| GET | `/api/v1/metric-snapshots/{id}` | 全部角色（读） | - | 完整快照 + 修订链 | 404 `SNAPSHOT_NOT_FOUND` |
| POST | `/api/v1/metric-snapshots/{id}/revise` | ANALYST/EDITOR/ADMIN/OWNER | `{metrics, confidence, reason}` | `{newSnapshotId, revisedFromId}` | 404 `SNAPSHOT_NOT_FOUND` |
| POST | `/api/v1/metric-snapshots/import` | ANALYST+ | multipart csv + `{platformCode, accountId?}` | `{importLogId, totalRows, importedRows, skippedRows, errorRows}` | 400 `CSV_SCHEMA_INVALID` |
| GET | `/api/v1/metric-snapshots/trend?assetId=&platform=&granularity=day\|week` | 全部角色（读） | - | `[{bucket, impressions, likes, collects, comments, dmInquiries}]` | - |
| POST | `/api/v1/metric-snapshots/collect` | ADMIN/OWNER | `{platformCode, accountId?}` | `{taskId, status}` | 403 `CONNECTOR_NOT_AUTHORIZED` |

---

## 子能力 3：预测 vs 实际对比与复盘报告

### 3.1 预测与实际的分字段存储

**功能**：
- AI 预测值与实际值**必须分字段**，展示来源和时间
- 偏差、命中维度、失败维度、可能原因

**待验收**：
- `app.metric_forecast` 表：id / workspace_id / asset_id / platform_code / forecast_type / predicted_metrics JSONB / predicted_published_at / model_name / prompt_version / kb_version / confidence / generated_at / source_task_id
  - `forecast_type`：`TITLE_CTR / IMPRESSION / ENGAGEMENT / CONVERSION`
- **预测值与实际值不得混在同一字段**：预测只存 `metric_forecast.predicted_metrics`，实际只存 `metric_snapshot.metrics`；对外 API 响应中两者**必须分字段**返回（`predicted` / `actual`），不允许出现 `metrics` 单一混合字段
- 偏差计算：`app.metric_comparison` 表：id / forecast_id / snapshot_id / asset_id / dimension / predicted_value / actual_value / deviation_abs / deviation_pct / hit / dimension_category / reason / computed_at
  - `dimension`：`impressions / clicks / likes / collects / comments / shares / dm_inquiries / conversions`
  - `hit` 判定：实际 / 预测 ≥ 0.8 记 `HIT`；≤ 0.5 记 `MISS`；中间记 `PARTIAL`
  - `dimension_category`：`TITLE / COVER / TIMING / CTA / CONTENT_BODY / DISTRIBUTION`
  - 偏差率在实际为 0 时置 `null`（不写 Infinity / 不写 0 假装一致）
- 预测值必须带**来源和时间**：`model_name` / `prompt_version` / `kb_version` / `generated_at` 与实际值的 `metric_source` / `collected_at` 同屏展示
- 预测缺失时：不生成对比行，复盘报告显示"该资产无预测值"并明确说明**不构成预测失败**（避免把"没预测"显示成"预测偏差大"）
- **每条复盘建议必须可追溯到输入证据**（§8.3 / §4.4）：建议必须引用具体的 `assetId` / `snapshotId` / `comparisonId` / 指标名 + 数值，不允许泛化表述

### 3.2 复盘报告

**功能**：
- 单篇、账号、平台、品类、活动和时间段报表
- 下一轮选题、标题、正文和发布时间建议

**待验收**：
- `app.retrospective_report` 表：id / workspace_id / scope_type / scope_key / asset_id? / account_id? / platform_code? / category_code? / campaign_id? / period_start / period_end / summary / predictions JSONB / actuals JSONB / hit_dimensions JSONB / missed_dimensions JSONB / suggestions JSONB / model_name / prompt_version / kb_version / generated_at / status
  - `scope_type`：`ASSET / ACCOUNT / PLATFORM / CATEGORY / CAMPAIGN / PERIOD`
- 报告生成走持久化任务队列（异步），SSE / 轮询均可恢复
- **AI 结构化输出**：
  - 出参 Schema 固定并版本化：`{summary, predictions[], actuals[], hitDimensions[{dimension,evidence}], missedDimensions[{dimension,evidence,possibleReasons[]}], suggestions[{type,action,expectedImpact,evidenceRefs[],confidence}]}`
  - `suggestions[].type` ∈ `TOPIC / TITLE / BODY / POST_TIME`
  - **结构化输出解析成功率 ≥ 99%**（§8.3）
  - 解析失败：重试 1 次（换 Prompt 版本）→ 仍失败则生成**规则降级报告**（用本地偏差统计拼装建议，`status='DEGRADED'`），并在报告头部显示"AI 解析失败，本次为规则降级结果"，**禁止生成伪成功报告**（§4.4）
- **至少三条基于真实数据的下一步建议**（§4.9 硬性验收）
  - 少于 3 条时**不允许标记报告为 `COMPLETED`**：状态置 `INSUFFICIENT_DATA`，并说明缺少哪些维度（缺少实际快照 / 缺少预测 / 样本量不足）
  - 每条建议必须引用真实数据（`evidenceRefs` 非空且指向存在的 snapshot / comparison）
  - 建议不得与数据矛盾（例：实际曝光低于预测 60% 时不得建议"加大投放"）
- 报表维度：单篇 / 账号 / 平台 / 品类 / 活动 / 时间段，全部基于 `metric_snapshot` 聚合，**同一指标口径统一**（跨平台对比时展示口径说明）
- **敏感词与硬性规则测试集通过率 100%**（§8.3）：复盘建议文本同样进入合规扫描；命中 `HIGH`/`BLOCKED` 的建议被拒绝并替换为规则降级建议
- 复盘生成消耗额度：`BillableUnit.ANALYSIS` DEBIT；失败 → `REFUND`（遵循 `BILLING-RULES.md`：先 REFUND 再重新发起，**不得**先重试再扣费）
- 跨 workspace 隔离：报告、对比、快照全部按 workspace 过滤

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 请求要点 | 响应要点 | 错误码 |
|---|---|---|---|---|---|
| POST | `/api/v1/forecasts` | ANALYST+ | `{assetId, platformCode, forecastType, predictedMetrics, predictedPublishedAt}` | `{id, modelName, promptVersion, kbVersion, generatedAt}` | 404 `ASSET_NOT_FOUND` |
| GET | `/api/v1/forecasts?assetId=&platform=&forecastType=` | 全部角色（读） | - | `[{id, predictedMetrics, predictedPublishedAt, source:{modelName, promptVersion, kbVersion, generatedAt}}]` | - |
| POST | `/api/v1/comparisons` | ANALYST+ | `{forecastId, snapshotId}` | `{rows:[{dimension, predictedValue, actualValue, deviationPct, hit, dimensionCategory, reason}]}` | 404 `FORECAST_OR_SNAPSHOT_NOT_FOUND` |
| GET | `/api/v1/comparisons?assetId=&dimension=&hit=` | 全部角色（读） | - | `[comparison]` | - |
| POST | `/api/v1/retrospectives` | ANALYST+ | `{scopeType, scopeKey, assetId?, accountId?, platformCode?, categoryCode?, campaignId?, periodStart?, periodEnd?}` | `{reportId, taskId, status}` | 400 `INVALID_SCOPE` / 409 `INSUFFICIENT_DATA` |
| GET | `/api/v1/retrospectives/{id}` | 全部角色（读） | - | `{id, scopeType, scopeKey, summary, predictions[], actuals[], hitDimensions[], missedDimensions[], suggestions[], status, degraded, generatedAt}` | 404 `REPORT_NOT_FOUND` |
| GET | `/api/v1/retrospectives?scopeType=&platform=&from=&to=` | 全部角色（读） | - | `[{id, scopeType, scopeKey, suggestionCount, status, generatedAt}]` | - |
| GET | `/api/v1/retrospectives/{id}/export` | ANALYST+ | - | 导出任务 id（走既有导出通道，计 `EXPORT` 额度） | 403 `EXPORT_FORBIDDEN` |

### 3.3 复盘结果回写模板与知识库效果统计

**功能**：
- 复盘结果经**人工确认后**进入模板效果统计与知识库效果统计

**待验收**：
- `app.retrospective_feedback` 表：id / report_id / workspace_id / target_type(`TEMPLATE / KB_DOCUMENT`) / target_id / proposed_content / proposed_stats JSONB / status(`PENDING / APPROVED / REJECTED`) / reviewed_by / reviewed_at / review_comment / created_at
- 回写**必须经过管理员确认**：状态 `PENDING` 时**不写入**模板效果统计与知识库效果统计；只有 `APPROVED` 才生效
- 审批人角色：**OWNER / ADMIN**（决策项 D3，见"未冻结决策清单"）
- 拒绝必须填写 `review_comment`
- 审批动作写 `app.audit_event`
- **污染风险控制**：
  - 单条复盘结果不得直接成为知识库文档正文，只能作为**效果统计样本**（指标 + 标签）
  - 知识库回写内容若含正文，必须经 `KbSourceLoader` 重新分块 + 向量化，不允许裸写 `kb.kb_document`
  - 回写记录保留来源 `report_id`，可一键撤回（软删除 + 统计扣减）
- 模板效果统计复用既有 `app.template_apply_log` 维度，扩展"效果"字段（采用率 → 实际表现），**不新建模板统计表**

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 请求要点 | 响应要点 | 错误码 |
|---|---|---|---|---|---|
| GET | `/api/v1/retrospectives/{id}/feedback` | ADMIN/OWNER | - | `[{id, targetType, targetId, status, proposedContent, reviewedBy, reviewedAt}]` | 404 `REPORT_NOT_FOUND` |
| POST | `/api/v1/retrospectives/{id}/feedback` | ANALYST+ | `{targetType, targetId, proposedContent, proposedStats}` | `{id, status:'PENDING'}` | 400 `INVALID_TARGET` |
| POST | `/api/v1/retrospectives/feedback/{id}/approve` | ADMIN/OWNER | `{comment?}` | `{id, status:'APPROVED'}` | 409 `ALREADY_REVIEWED` |
| POST | `/api/v1/retrospectives/feedback/{id}/reject` | ADMIN/OWNER | `{comment}` | `{id, status:'REJECTED'}` | 400 `COMMENT_REQUIRED` |
| GET | `/api/v1/templates/{id}/effect-stats` | 全部角色（读） | - | `{applyCount, adoptionRate, avgImpressions, avgCollectRate, avgDmInquiries, sampleSize}` | 404 `TEMPLATE_NOT_FOUND` |

---

## 子能力 4：热点与竞品研究数据产品化

### 4.1 热点数据产品化

**功能**：
- 热点多源、缓存、更新时间、来源可信度、去重、趋势变化、可做方向
- 连接器失效自动降级为人工导入并展示不可用原因

**待验收**：
- `app.hot_topic` 复用既有表，**新增字段**：`source_name VARCHAR(64)`、`source_type VARCHAR(32)`（`SEARCH_TOOL / OFFICIAL_API / AUTHORIZED_CONNECTOR / MANUAL_IMPORT`）、`confidence VARCHAR(16)`、`is_realtime BOOLEAN`、`source_url TEXT`、`degraded BOOLEAN`、`degraded_reason TEXT`
  - **禁止把静态种子数据伪装成实时热点**（§4.3 硬性验收）：种子 / 静态数据 `is_realtime=FALSE` 且 `source_type='SEED'`，UI 必须显式标注"非实时"
- `fetched_at` 必须在接口与 UI 暴露（既有列已存在，当前未对外暴露 —— 阶段 3 补齐）
- 缓存：`app.hot_topic_cache`（新建）—— platform / category / payload / fetched_at / ttl_seconds；同窗口命中缓存不重复调用外部源
- 去重：同平台 + 归一化标题（既有 V41 归一化索引）只保留**最新一条**为 active，其余标 `superseded`；去重必须保留历史（供趋势对比）
- 趋势变化：复用既有 `computeTrend`（上升 / 下降 / 持平），但趋势输入必须是**同平台 + 同归一化标题**的多条快照；单条记录不得输出"上升"
- 可做方向：每个热点输出 `suggestedAngles`（角度建议），每条建议必须引用该热点的真实标题 + 来源 + 采集时间，**不得泛化**
- 连接器失效降级：`source_type='SEARCH_TOOL'` 连续失败 → 热点接口返回 `degraded=true` + `degradedReason`，同时返回 `fallbackAvailable: 'MANUAL_IMPORT'`；界面必须展示不可用原因（**不允许静默展示陈旧数据**）
- 手动导入热点：`POST /hot-topics/import`（CSV / 表单），`source_type='MANUAL_IMPORT'`、`confidence='LOW'`
- 热点 → 选题池：复用既有 `promoteToTopicPool`，透传 `source_name` / `confidence` / `fetched_at` 到 `topic_pool`（`topic_pool` 需新增 `source_name` / `confidence` / `source_fetched_at` 三列）
- 热点采集任务计费：`BillableUnit.SEARCH`（联网搜索），失败 REFUND

### 4.2 竞品研究资产库

**功能**：
- 竞品账号、竞品笔记、内容结构、发布时间、互动数据、样本证据
- 竞品建议必须引用真实输入内容

**待验收**：
- `app.competitor_account` 表（新建）：id / workspace_id / platform_code / account_name / account_url / external_account_id / follower_count / category / tags / status / added_by / created_at / updated_at
  - status：`active / archived`
- `app.competitor_note` 表（新建）：id / competitor_account_id / workspace_id / platform_code / note_title / note_url / published_at / content_structure JSONB（段落结构 / 钩子类型 / CTA 位置 / 标签集合） / engagement JSONB（likes / collects / comments / shares） / captured_at / evidence_text TEXT / source_type / created_at
  - `evidence_text`：竞品笔记的**真实片段引用**（可高亮的原文片段），没有原文片段不得入库
- 竞品采集：接入级别由 `app.platform_connector_authorization` 决定；**未授权平台一律拒绝采集**（403 `CONNECTOR_NOT_AUTHORIZED`）
- 采集方式：`BROWSER_ASSIST` 回收（用户在平台页确认）+ `MANUAL_IMPORT`（CSV / 链接 / 截图 OCR）；来源必须标注 `source_type` + `captured_at`
- 竞品分析（`ContentAsset.scenario='competitor'`）的 AI 输出**必须引用真实输入内容**（§4.3 硬性验收）：
  - 每条"可借鉴点"必须带 `evidenceRefs[]`（指向 `competitor_note.id` + `evidence_text` 片段）
  - 无 `evidenceRefs` 的建议在落库时被丢弃，接口返回 `400 COMPETITOR_EVIDENCE_MISSING`
  - 输出 Schema：`{borrowPoints[{point, evidenceRefs[], applySuggestion}], avoidPoints[{point, reason, evidenceRefs[]}], structureCompare[]}`
  - 沿用 `ReportCompetitorPanel.vue` 的"可借鉴点 / 勿照搬点"渲染结构，但增加证据引用区
- 竞品账号维度对比：同一品类下多账号的互动率对比（点赞/曝光、收藏/曝光、评论/曝光），统一口径
- 竞品数据**更新时间**必须暴露：`captured_at` / `updated_at` 在接口与 UI 展示
- 竞品建议文本进入合规扫描

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 请求要点 | 响应要点 | 错误码 |
|---|---|---|---|---|---|
| GET | `/api/v1/hot-topics?platform=&category=&from=&to=&sourceType=` | 全部角色（读） | - | `[{id, title, heatScore, trend, sourceName, sourceType, confidence, isRealtime, fetchedAt, suggestedAngles[]}]` + `{degraded, degradedReason, fallbackAvailable}` | - |
| POST | `/api/v1/hot-topics/refresh` | ADMIN/OWNER | `{platform?, category?}` | `{taskId, status}` | 403 `CONNECTOR_NOT_AUTHORIZED` |
| POST | `/api/v1/hot-topics/import` | ANALYST+ | multipart csv 或 `{items:[{title, platform, heatScore, sourceName, sourceUrl}]}` | `{imported, skipped, degraded}` | 400 `CSV_SCHEMA_INVALID` |
| GET | `/api/v1/hot-topics/{id}/samples` | 全部角色（读） | - | `[{id, title, sourceUrl, fetchedAt}]` | 404 `HOT_TOPIC_NOT_FOUND` |
| POST | `/api/v1/hot-topics/{id}/promote` | EDITOR+ | `{category?, tags?}` | `{topicPoolId}` | 404 `HOT_TOPIC_NOT_FOUND` |
| GET | `/api/v1/competitors?platform=&category=&status=` | 全部角色（读） | - | `[{id, platformCode, accountName, accountUrl, followerCount, tags, noteCount, updatedAt}]` | - |
| POST | `/api/v1/competitors` | ANALYST+ | `{platformCode, accountName, accountUrl?, category?, tags?}` | `{id, status}` | 403 `CONNECTOR_NOT_AUTHORIZED` |
| GET | `/api/v1/competitors/{id}/notes` | 全部角色（读） | - | `[{id, noteTitle, noteUrl, publishedAt, contentStructure, engagement, capturedAt, sourceType}]` | 404 `COMPETITOR_NOT_FOUND` |
| POST | `/api/v1/competitors/{id}/notes` | ANALYST+ | `{noteTitle, noteUrl, publishedAt?, contentStructure?, engagement?, evidenceText, sourceType}` | `{id, capturedAt}` | 400 `EVIDENCE_TEXT_REQUIRED` |
| POST | `/api/v1/competitors/{id}/notes/import` | ANALYST+ | multipart csv | `{importLogId, imported, skipped, errorRows}` | 400 `CSV_SCHEMA_INVALID` |
| POST | `/api/v1/competitors/analyze` | EDITOR+ | `{competitorAccountIds[], assetId?, platformCode, category?}` | `{taskId, status}` | 400 `NO_EVIDENCE_INPUT` |

---

## 子能力 5：连接器抽象与诊断

### 5.1 统一连接器抽象

**功能**：
- 统一 `PlatformConnector` 接口 + 4 种实现级别
- 连接器健康状态、最近成功时间、失败原因、重新授权入口、诊断 API

**待验收**：
- 统一接口必须覆盖 4 个能力维度，**所有实现共用同一签名**：
  - `publish(asset, account, scheduleContext) → PublishResult{externalContentId, sourceUrl, publishedAt}`
  - `fetchMetrics(account, externalContentId, window) → List<MetricSample>`
  - `fetchHotTopics(platform, category) → List<HotTopicSample>`
  - `fetchCompetitorNotes(account, window) → List<CompetitorNoteSample>`
- 4 种实现级别与既有冻结枚举一一对应，**不新增等级**：
  - `OFFICIAL_API` → `OfficialApiConnector`
  - `AUTHORIZED_CONNECTOR` → `AuthorizedConnectorAdapter`
  - `BROWSER_ASSIST` → `BrowserAssistConnector`（复用既有 `BrowserAssistService`，**不自动点发布**）
  - `MANUAL_IMPORT` → `ManualImportConnector`（产出待办清单，不声称已发布）
- `MANUAL_IMPORT` 级别的 `publish()` **必须返回"需人工完成"语义**（`PublishResult.manual=true`），调用方据此走回执回填流程；**禁止**返回伪造的 `externalContentId`
- 运行时门禁：连接器加载前校验 `app.platform_connector_authorization.authorized = TRUE` 且未过期；否则连接器**不注册到 Spring 容器**，调用返回 `403 CONNECTOR_NOT_AUTHORIZED`
- `app.platform.connector_level` 与授权清单**冲突时以授权清单为准**（授权清单是法务基线）
- 凭据处理：`credential_ref` 指向加密存储；连接器实现**不得**把凭据写进日志、异常消息或 `raw_response`
- 每个连接器实现必须实现 `healthCheck()`，返回 `{status, lastSuccessAt, lastError, latencyMs}`

### 5.2 诊断与降级

**待验收**：
- `app.connector_health` 表（新建）：connector_provider_code / platform_code / workspace_id(nullable=全局) / status(`HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED`) / last_success_at / last_checked_at / last_error_code / last_error_message / consecutive_failures / degraded_to / updated_at
- 诊断 API 返回**当前生效的降级路径**：`OFFICIAL_API 不可用 → BROWSER_ASSIST → MANUAL_IMPORT → EXPORT_ONLY`
- 失败分类：`AUTH_EXPIRED`（需重新授权）/ `RATE_LIMITED`（退避重试）/ `PLATFORM_CHANGED`（页面结构变化，需升级适配器）/ `NETWORK_ERROR`（重试）/ `NOT_AUTHORIZED`（拒绝，不重试）/ `UNKNOWN`
- 连续失败 3 次 → `DEGRADED`；连续失败 10 次 → `UNAVAILABLE`，自动切降级路径并写 `audit_event`
- **连接器异常不阻塞内容编辑、审核、手动导出**（§4.8 硬性验收）
- 降级切换必须**可见**：界面在连接器异常时明确显示"当前不可用原因 + 已降级为 X"，并保留重新授权入口
- 诊断只读、无副作用；不得触发真实发布

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 请求要点 | 响应要点 | 错误码 |
|---|---|---|---|---|---|
| GET | `/api/v1/connectors` | 全部角色（读） | - | `[{platformCode, platformName, connectorProviderCode, level, authorized, rolloutStatus, writeEnabled, readEnabled, health:{status, lastSuccessAt, lastError}}]` | - |
| GET | `/api/v1/connectors/{providerCode}/health` | 全部角色（读） | - | `{status, lastSuccessAt, lastCheckedAt, lastErrorCode, lastErrorMessage, consecutiveFailures, degradedTo}` | 404 `CONNECTOR_NOT_FOUND` |
| POST | `/api/v1/connectors/{providerCode}/diagnose` | ADMIN/OWNER | `{workspaceId?}` | `{status, checks:[{name, passed, detail}], degradedTo, checkedAt}` | 404 `CONNECTOR_NOT_FOUND` / 403 `CONNECTOR_NOT_AUTHORIZED` |
| GET | `/api/v1/connectors/degradation?platform=` | 全部角色（读） | - | `[{platformCode, currentLevel, degradedTo, reason, since}]` | - |

---

## 数据模型总表

> 标注：**新建** = 阶段 3 新增表；**修改** = 在阶段 0-2 既有表上加列/加索引；**复用** = 不改结构直接复用。

| 表名 | 归属 | 关键字段 | 说明 | 状态 |
|---|---|---|---|---|
| `app.platform_account` | 连接 | id, workspace_id, platform_code, account_label, external_account_id, connector_provider_code, level, status, credential_ref, authorized_at, expires_at, last_success_at, last_error | 平台账号绑定与连接状态 | 新建 |
| `app.publish_schedule` | 排期 | id, workspace_id, asset_id, platform_code, account_id, scheduled_at, timezone, status, publish_task_id, created_by | 排期日历 | 新建 |
| `app.publish_task` | 发布 | id, workspace_id, schedule_id, asset_id, platform_code, account_id, connector_provider_code, level, idempotency_key(UNIQUE), status, attempt, error_code, error_message, retryable | 发布任务 | 新建 |
| `app.publish_receipt` | 回执 | id, publish_task_id, external_content_id, source_url, published_at, published_by, raw_response, verified | 发布回执与外部内容 ID | 新建 |
| `app.metric_snapshot` | 指标 | id, workspace_id, platform_code, account_id, asset_id, external_content_id, published_at, metrics JSONB, metric_extra JSONB, metric_source, confidence, collected_at, is_manual, revised_from_id | 指标快照 | 新建 |
| `app.metric_forecast` | 预测 | id, workspace_id, asset_id, platform_code, forecast_type, predicted_metrics JSONB, predicted_published_at, model_name, prompt_version, kb_version, confidence, generated_at | AI 预测值（与实际分字段） | 新建 |
| `app.metric_comparison` | 对比 | id, forecast_id, snapshot_id, asset_id, dimension, predicted_value, actual_value, deviation_abs, deviation_pct, hit, dimension_category, reason | 预测 vs 实际偏差 | 新建 |
| `app.retrospective_report` | 复盘 | id, workspace_id, scope_type, scope_key, asset_id, account_id, platform_code, category_code, campaign_id, period_start, period_end, summary, predictions, actuals, hit_dimensions, missed_dimensions, suggestions, model_name, prompt_version, kb_version, status, degraded | 复盘报告 | 新建 |
| `app.retrospective_feedback` | 回写 | id, report_id, target_type, target_id, proposed_content, proposed_stats, status, reviewed_by, reviewed_at, review_comment | 回写模板/知识库效果统计的审批流 | 新建 |
| `app.competitor_account` | 竞品 | id, workspace_id, platform_code, account_name, account_url, external_account_id, follower_count, category, tags, status | 竞品账号库 | 新建 |
| `app.competitor_note` | 竞品 | id, competitor_account_id, platform_code, note_title, note_url, published_at, content_structure JSONB, engagement JSONB, captured_at, evidence_text, source_type | 竞品笔记与样本证据 | 新建 |
| `app.hot_topic_cache` | 热点 | platform, category, payload, fetched_at, ttl_seconds | 热点缓存 | 新建 |
| `app.connector_health` | 诊断 | connector_provider_code, platform_code, workspace_id, status, last_success_at, last_checked_at, last_error_code, last_error_message, consecutive_failures, degraded_to | 连接器健康与降级 | 新建 |
| `app.content_asset` | 资产 | status / platform / account_id | 复用；排期与发布驱动 SCHEDULED / PUBLISHED | 复用（不改结构） |
| `app.browser_assist_session` | 浏览器辅助 | status, cookie_encrypted, cookie_expires_at, asset_id | 复用；发布回执与回收来源 | 复用（不改结构） |
| `app.hot_topic` | 热点 | + source_name, source_type, confidence, is_realtime, source_url, degraded, degraded_reason | 加列：来源与可信度 | 修改 |
| `app.topic_pool` | 选题 | + source_name, confidence, source_fetched_at | 加列：热点来源透传 | 修改 |
| `app.analysis_task` | 分析 | published_metrics JSONB | 降级为兼容读取，新写入走 metric_snapshot | 复用（不改结构） |
| `app.csv_import_log` | 导入 | import_type | 复用；新增 import_type='METRIC' / 'COMPETITOR_NOTE' / 'HOT_TOPIC' | 复用（扩枚举） |
| `app.platform` | 平台 | connector_level, write_enabled, read_enabled, rollout_status | 复用；不新增等级 | 复用（不改结构） |
| `app.platform_connector_authorization` | 授权 | authorized, expires_at, scope | 复用；运行时唯一门禁 | 复用（不改结构） |
| `app.connector_provider` | 连接器 | code, connector_type, enabled | 复用 | 复用（不改结构） |
| `app.usage_ledger` | 计费 | workspace_id, action, units, event_type, reason, request_id | 复用；复盘走 ANALYSIS，热点走 SEARCH | 复用（不改结构） |
| `app.audit_event` | 审计 | - | 复用；发布/授权/审批动作全部写审计 | 复用（不改结构） |
| `app.template_apply_log` | 模板统计 | - | 复用；扩展效果统计维度 | 复用（扩字段） |
| `kb.kb_document` | 知识库 | - | 复用；阶段 3 需补实体与 Mapper（当前仅裸 SQL） | 复用（补代码资产） |

---

## 接口契约汇总表

| 方法 | 路径 | 权限角色 | 幂等 / 限流 |
|---|---|---|---|
| POST | `/api/v1/platform-accounts` | OWNER/ADMIN | 幂等键；10 次/小时/工作区 |
| GET | `/api/v1/platform-accounts` | 全部（读） | — |
| POST | `/api/v1/platform-accounts/{id}/reauthorize` | OWNER/ADMIN | 幂等键；5 次/小时/账号 |
| DELETE | `/api/v1/platform-accounts/{id}` | OWNER/ADMIN | 幂等键 |
| POST | `/api/v1/publish-schedules` | EDITOR/ADMIN/OWNER | 幂等键；200 次/天/工作区 |
| GET | `/api/v1/publish-schedules` | 全部（读） | — |
| GET | `/api/v1/publish-schedules/calendar` | 全部（读） | — |
| POST | `/api/v1/publish-schedules/{id}/cancel` | EDITOR/ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/publish-schedules/{id}/retry` | EDITOR/ADMIN/OWNER | 复用原幂等键 |
| POST | `/api/v1/publish-tasks` | EDITOR/ADMIN/OWNER | `Idempotency-Key` 必填；60 次/小时/工作区 |
| GET | `/api/v1/publish-tasks/{id}` | 全部（读） | — |
| POST | `/api/v1/publish-tasks/{id}/cancel` | EDITOR/ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/publish-receipts` | EDITOR/ADMIN/OWNER | 幂等键；120 次/小时/工作区 |
| GET | `/api/v1/assets/{id}/receipts` | 全部（读） | — |
| POST | `/api/v1/metric-snapshots` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键；600 条/天/工作区 |
| GET | `/api/v1/metric-snapshots` | 全部（读） | 分页 |
| GET | `/api/v1/metric-snapshots/{id}` | 全部（读） | — |
| POST | `/api/v1/metric-snapshots/{id}/revise` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/metric-snapshots/import` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键；20 次/天/工作区 |
| GET | `/api/v1/metric-snapshots/trend` | 全部（读） | — |
| POST | `/api/v1/metric-snapshots/collect` | ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/forecasts` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/forecasts` | 全部（读） | — |
| POST | `/api/v1/comparisons` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/comparisons` | 全部（读） | — |
| POST | `/api/v1/retrospectives` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键；20 次/天/工作区 |
| GET | `/api/v1/retrospectives` | 全部（读） | 分页 |
| GET | `/api/v1/retrospectives/{id}` | 全部（读） | — |
| GET | `/api/v1/retrospectives/{id}/export` | ANALYST/EDITOR/ADMIN/OWNER | 计 `EXPORT` 额度；50 次/天/工作区 |
| GET | `/api/v1/retrospectives/{id}/feedback` | ADMIN/OWNER | — |
| POST | `/api/v1/retrospectives/{id}/feedback` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/retrospectives/feedback/{id}/approve` | ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/retrospectives/feedback/{id}/reject` | ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/templates/{id}/effect-stats` | 全部（读） | — |
| GET | `/api/v1/hot-topics` | 全部（读） | 分页 |
| POST | `/api/v1/hot-topics/refresh` | ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/hot-topics/import` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/hot-topics/{id}/samples` | 全部（读） | — |
| POST | `/api/v1/hot-topics/{id}/promote` | EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/competitors` | 全部（读） | 分页 |
| POST | `/api/v1/competitors` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/competitors/{id}/notes` | 全部（读） | 分页 |
| POST | `/api/v1/competitors/{id}/notes` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/competitors/{id}/notes/import` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| POST | `/api/v1/competitors/analyze` | EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/connectors` | 全部（读） | — |
| GET | `/api/v1/connectors/{providerCode}/health` | 全部（读） | — |
| POST | `/api/v1/connectors/{providerCode}/diagnose` | ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/connectors/degradation` | 全部（读） | — |

> 角色模型沿用阶段 0 冻结的 5 级：`OWNER / ADMIN / EDITOR / REVIEWER / ANALYST / VIEWER`（`ANALYST` 只读 + 可录入数据，不修改内容）。`REVIEWER` 与 `VIEWER` 不出现在写操作白名单中。

---

## 阶段3 整体验收（出口条件）

> 至少一个平台完成真实的发布或手动发布回收闭环，复盘报告可以生成三条可执行建议

### 出口条件逐条对照

| # | §9 出口条件 | 判定方式 |
|---|---|---|
| E1 | 至少一个平台完成真实的发布闭环 | 小红书（`XHS_BROWSER_SIDEBAR`，BROWSER_ASSIST）完成一次"填充 → 用户确认发布 → 回填外部内容 ID/来源链接 → 生成发布回执 → 资产状态 `PUBLISHED`" |
| E2 | 或完成手动发布回收闭环 | 抖音（`DOUYIN_CSV_IMPORT`，MANUAL_IMPORT）完成一次"导出待发布清单 → 运营手动发布 → CSV 回填指标 → 生成指标快照" |
| E3 | 复盘报告可生成三条可执行建议 | 对同一资产生成复盘报告，`suggestions.length >= 3` 且每条 `evidenceRefs` 非空且指向存在的 `metric_snapshot` / `metric_comparison` |

### 必走的端到端场景

1. **连接器门禁**：未授权平台（快手 / Instagram / TikTok 或任意未列入授权清单的组合）请求创建账号 / 发布任务 → `403 CONNECTOR_NOT_AUTHORIZED`，无任何数据落库；授权清单内的小红书 / 抖音正常可用。
2. **发布闭环（浏览器辅助）**：平台账号连接 → 绑定小红书账号 → 资产审核通过 → 排期（指定时区）→ 到点执行填充 → 用户确认并自行发布 → 回填外部内容 ID + 来源链接 → 生成回执 → 资产状态变为 `PUBLISHED` → `content_asset.status` 与 `publish_receipt` 一致。
3. **手动发布回收闭环（抖音）**：资产审核通过 → 生成待发布清单（MANUAL_IMPORT）→ 导出 → 运营在小红书/抖音实际发布 → CSV 回填曝光/点赞/收藏/评论/私信 → 生成指标快照 → 界面显示来源 `MANUAL_IMPORT` + 采集时间 + 可信度。
4. **幂等与重试**：同一资产同一幂等键重复提交发布任务 5 次 → 只产生 1 次真实发布、1 条回执；失败任务重试复用原幂等键；**重复发布率为 0**。
5. **降级路径**：断开/模拟连接器不可用 → 连接器健康转 `DEGRADED` / `UNAVAILABLE` → 界面显示不可用原因与降级路径 → 改走 `BROWSER_ASSIST` / `MANUAL_IMPORT`；同时内容编辑、审核、手动导出**不阻塞**。
6. **预测 vs 实际复盘**：资产生成时留下 AI 预测值（`metric_forecast`）→ 发布 → 回填实际指标（`metric_snapshot`）→ 计算对比（`metric_comparison`）→ 生成复盘报告 → 报告显示预测值与实际值**分字段**且各自标注来源与时间 → 输出 ≥3 条带证据引用的建议。
7. **复盘回写审批**：复盘报告 → 提交回写请求（模板 / 知识库效果统计）→ 状态 `PENDING` 且**效果统计未变化** → ADMIN 批准后统计才更新 → 拒绝必须填意见 → 审计留痕。
8. **热点与竞品数据产品化**：热点接口返回来源、采集时间、可信度、是否实时；静态种子被显式标注非实时；热点 → 选题池 → 资产链路完整；竞品账号/笔记入库带真实证据片段；竞品分析输出的每条"可借鉴点"都有 `evidenceRefs`；连接器失效时热点降级并展示原因。

### 性能 / 可靠性指标

| 指标 | 目标 | 来源 |
|---|---|---|
| 分析任务 P95 | < 45 秒 | §8.1 |
| 标题 / 正文生成 P95 | < 30 秒 | §8.1 |
| 工作台首页 P95 加载 | < 2 秒；报告首屏 < 3 秒 | §8.1 |
| 结构化输出解析成功率 | ≥ 99%（复盘报告输出同样适用） | §8.3 |
| 敏感词与硬性规则测试集通过率 | 100%（复盘建议、竞品建议同样适用） | §8.3 |
| 重要建议证据 | 必须有输入证据 / 规则依据 / 知识库引用 | §8.3 |
| 发布任务重复发布率 | 0 | §8.2 |
| 额度扣费重复率 | 0，失败任务自动冲正 | §8.2 |
| 数据同步 | 延迟、失败率、最后成功时间**可见** | §8.2 |
| 核心分析任务成功率 | ≥ 98% | §8.2 |
| 月度可用性 | ≥ 99.5% | §8.2 |
| 异步任务恢复 | 刷新 / 断网重连 / 浏览器关闭后可恢复查看 | §8.1 |
| 移动端 | 至少可查看指标、复盘与连接器状态 | §8.1 |
| 指标采集周期 | 6h 一轮；失败 3 次进 DLQ 并标 `DEGRADED` | 阶段 3 新增 |
| 复盘报告生成 | 异步任务，P95 < 45 秒（对齐分析任务口径） | 阶段 3 新增 |

---

## 未冻结决策清单（阻塞阶段 3 的项）

> 来源：`COMMERCIAL_SAAS_READINESS_PLAN.md` §12。以下 5 项尚未由产品 + 运营 + 法务签字，阶段 3 无法完全开工。

### D1. 平台账号凭据托管方式（用户托管 / 平台 OAuth / 仅人工导入）

| 项 | 内容 |
|---|---|
| 决策选项 | A. 用户托管（用户自行提供账号密码 / Cookie，系统加密存储）<br>B. 平台 OAuth（平台提供授权码流程，第三方授权）<br>C. 仅人工导入（系统完全不持有平台凭据） |
| 对阶段 3 的影响 | 决定 `platform_account.credential_ref` 的存储模型、连接器接口的 `publish()` / `fetchMetrics()` 能否自动执行、`browser_assist_session.cookie_encrypted` 的生命周期，以及"真实发布闭环"（E1）走 A 还是走 C。选 A 需要重写凭据托管与安全审计（SSO/凭据轮换/越权风险），选 B 需要平台授权申请（周期不可控）。 |
| 建议默认方案 | **C（仅人工导入）为阶段 3 基线**，A 仅保留小红书 BROWSER_ASSIST 半自动（沿用既有 24h AES-GCM Cookie 机制，用户确认后自行发布）；B 列为阶段 4 待授权项 |
| 阻塞等级 | **阻塞-P0**（影响子能力 1/2/5 的接口与表结构，必须先签字） |

### D2. 首发真实连接平台名单的最终签字

| 项 | 内容 |
|---|---|
| 决策选项 | A. 仅小红书（`XHS_BROWSER_SIDEBAR`，暂批准）<br>B. 小红书 + 抖音（抖音 `DOUYIN_CSV_IMPORT` 已批准默认）<br>C. 再加入微信公众号 / 视频号 / B 站 / 微博 / 知乎的 CSV 导入 |
| 对阶段 3 的影响 | 决定阶段 3 要实现的连接器数量、出口条件 E1 走哪个平台、以及是否需要为"待开通"平台（`WECHAT_CSV_IMPORT` 等）预留实现。授权清单目前只有 2 条已批准、5 条"待开通"。 |
| 建议默认方案 | **B（小红书 + 抖音）**；C 中的平台保留 `app.platform_connector_authorization` 记录但 `authorized=FALSE`，运行时拒绝，界面标注"未开通" |
| 阻塞等级 | **阻塞-P0**（决定工作量与出口条件达成路径） |

### D3. 复盘结果回写知识库 / 模板效果统计的审批人角色

| 项 | 内容 |
|---|---|
| 决策选项 | A. 仅 OWNER<br>B. OWNER + ADMIN<br>C. OWNER + ADMIN + REVIEWER（内容审核人） |
| 对阶段 3 的影响 | 决定 `retrospective_feedback` 的审批权限矩阵、`/v1/retrospectives/feedback/{id}/approve` 的角色校验、前端按钮可见性。REVIEWER 参与会引入"内容审核 vs 数据审核"职责混合争议。 |
| 建议默认方案 | **B（OWNER + ADMIN）**；REVIEWER 只读可见 |
| 阻塞等级 | **不阻塞-可先行**（可先按 B 实现，签字后再调整白名单，改动局限于角色校验与前端按钮） |

### D4. 指标数据保存时长与跨境存储

| 项 | 内容 |
|---|---|
| 决策选项 | A. 沿用既有默认保留策略（内容 365 天 / 报告 180 天）<br>B. 指标快照单独设短周期（如 180 天）<br>C. 跨境存储（境外区域 / 境外模型供应商） |
| 对阶段 3 的影响 | 决定 `metric_snapshot` / `metric_forecast` / `metric_comparison` 的保留期与清理任务（阶段 1 `RetentionSweeper` 的覆盖范围），以及是否需要数据出境评估。当前 Instagram / TikTok 已因跨境合规被 `DISABLED`，若未来放开会影响指标模型。 |
| 建议默认方案 | **A + 指标快照沿用报告 180 天**，同时在 `app.data_retention_policy` 增加 `metric_snapshot_retention_days` 字段可按 workspace 覆盖；跨境存储阶段 4 再评估 |
| 阻塞等级 | **不阻塞-可先行**（可先按 180 天实现，保留字段与清理钩子） |

### D5. 商业首发的用户类型（个人 / 机构 / 代运营 / 全部）

| 项 | 内容 |
|---|---|
| 决策选项 | A. 仅机构 / 代运营团队<br>B. 全部支持 |
| 对阶段 3 的影响 | 影响连接器凭据的归属（个人账号 vs 团队共用）、排期日历是否需要"共享账号"概念、复盘报表是否需要跨 workspace 汇总。阶段 3 默认按**工作区**隔离实现，与用户类型无关。 |
| 建议默认方案 | **A（仅机构 / 代运营团队）**，阶段 3 不做个人版差异 |
| 阻塞等级 | **不阻塞-可先行**（阶段 3 的所有接口都已按 workspace 隔离，与该决策解耦） |

---

## 任务拆解与派发方案

### 子任务清单

| 编号 | 任务 | 范围 | 依赖 | 工作量（人·天） |
|---|---|---|---|---|
| **S3-1** | 连接器抽象与诊断 | `PlatformConnector` 接口 + 4 种实现级别 + 门禁 + `connector_health` + 诊断 API | 无（阶段 0 授权清单已冻结） | 6-8 |
| **S3-2** | 平台账号连接 | `platform_account` 表 + 状态机 + 授权过期 + 重授权 + 最小权限 + 审计 | S3-1 | 5-7 |
| **S3-3** | 排期日历 | `publish_schedule` 表 + 时区 + 冲突检测 + 取消 / 重试 / 过期 + 日历 API | S3-2 | 5-7 |
| **S3-4** | 发布任务与回执 | `publish_task` + `publish_receipt` + 幂等 + 错误分类 + 三条降级路径 + 浏览器辅助复用 | S3-1, S3-2, S3-3 | 6-8 |
| **S3-5** | 指标快照与回流 | `metric_snapshot` 表 + 统一口径 + 自动同步任务 + 手动录入 + CSV 导入 + 修订链 + 趋势 API | S3-1, S3-2 | 6-8 |
| **S3-6** | 预测存储与偏差计算 | `metric_forecast` + `metric_comparison` + 偏差 / 命中维度 / 失败维度 + 预测来源与时间 | S3-5 | 4-5 |
| **S3-7** | 复盘报告与建议 | `retrospective_report` + 结构化输出 + 解析失败降级 + ≥3 条带证据建议 + 6 种报表维度 + 合规扫描 | S3-6 | 7-9 |
| **S3-8** | 复盘回写审批 | `retrospective_feedback` + 管理员确认 + 模板效果统计扩展 + 知识库回写 + 撤回 | S3-7 | 4-5 |
| **S3-9** | 热点数据产品化 | `hot_topic` 加列 + 缓存 + 可信度 + 降级说明 + 手动导入 + 趋势修正 + 选题池来源透传 | S3-1 | 4-6 |
| **S3-10** | 竞品研究资产库 | `competitor_account` + `competitor_note` + 采集 + 证据片段 + 证据强制校验 + 账号维度对比 | S3-1, S3-9 | 6-8 |
| **S3-11** | 知识库管理面补齐 | `kb_document` 实体与 Mapper + 文档列表 / 检索 / 删除 API + 逐文档索引状态 + 非 local profile 可用 | 无 | 4-6 |
| **S3-12** | 前端页面与 i18n | 发布排期页 / 指标看板 / 复盘页 / 热点榜 / 竞品库 / 知识库管理 + 8 个 api 模块 + 3 语 i18n + 补齐既有 i18n 缺口 | S3-3, S3-4, S3-5, S3-7, S3-9, S3-10 | 10-13 |
| **S3-13** | 端到端串联与出口条件验证 | E1-E3 出口条件 + 8 条端到端场景 + 幂等 / 降级 / 权限回归 | 全部 | 4-5 |

**合计：约 71-94 人·天**（2 人并行约 36-47 工作日；3 人并行含专职前端约 25-32 工作日）

### 依赖顺序

```
S3-1 (连接器抽象)  ──┬──> S3-2 (账号连接) ──> S3-3 (排期) ──> S3-4 (发布/回执) ──┐
                     │                                                          │
                     ├──> S3-5 (指标快照) ──> S3-6 (预测/对比) ──> S3-7 (复盘) ──> S3-8 (回写) ──┤
                     │                                                          │                │
                     ├──> S3-9 (热点) ──> S3-10 (竞品) ────────────────────────────────────────────┤
                     │                                                                           │
                     └──> S3-11 (知识库管理面) ───────────────────────────────────────────────┤
                                                                                          v
                                                          S3-12 (前端) ──> S3-13 (出口条件验证)
```

**可并行**：
- 批次 A（可 4 个 subagent 并行）：S3-1、S3-2、S3-5、S3-9、S3-11 —— 均只依赖阶段 0-2 既有资产
- 批次 B（可 2-3 个并行）：S3-3、S3-6、S3-10
- 批次 C（串行或 2 并行）：S3-4、S3-7
- 批次 D：S3-8
- 批次 E：S3-12（前端，可按页面切成 3 个 subagent 并行）
- 批次 F：S3-13（串行收口）

### 建议的并行 subagent 派发方式

- **沿用阶段 2 派发策略（用户决策）**：4 个子能力 → 4 个 subagent 并行 → 每个 subagent 回报 → 统筹者按 `summary_first` 格式 review。
- **阶段 3 调整为 5 个 subagent 并行**（对应 5 个子能力），每个 subagent 领一个子能力：
  1. `publish-schedule`：S3-2 + S3-3 + S3-4
  2. `metric-pipeline`：S3-5 + S3-6
  3. `retrospective`：S3-7 + S3-8
  4. `hotspot-competitor`：S3-9 + S3-10
  5. `connector-core`：S3-1 + S3-11
- **串行收口**：S3-12（前端）→ S3-13（出口条件验证）
- **不裁剪范围**：严格按本文档 §9 阶段 3 四项 + 出口条件 E1-E3 + 8 条端到端场景执行

### 阶段3 不写测试（沿用阶段 2 决策）

- 不写 JUnit / Mockito / SpringBootTest
- **验收方式 = `mvn compile` 通过 + 本文档"必走的端到端场景"8 条全部跑通 + 出口条件 E1-E3 达成**
- 前端验收 = `vue-tsc` 类型检查通过 + 前端四态（loading / empty / error / success）逐页走查
- 集成 / 回归由运营同事在使用中验证
- 幂等、租户隔离、降级路径等**必须通过手工并发验证**（例如同幂等键并发 5 次请求），不得因为"不写测试"而跳过验证步骤

---

## 风险与依赖

| # | 风险 | 影响 | 缓解措施 |
|---|---|---|---|
| R1 | **小红书无官方 API 商业授权**（`XHS_OFFICIAL_API` 待申请，`XHS_BROWSER_SIDEBAR` 仅"暂批准"） | 出口条件 E1 只能走半自动（用户确认后自行发布），无法做到系统一键发布；页面改版即失效 | 明确承诺"半自动"而非"自动"；连接器 `PLATFORM_CHANGED` 错误分类 + 降级到 `MANUAL_IMPORT`；法务签字位在阶段 3 内补齐 |
| R2 | **指标数据质量依赖用户手动录入**（抖音等 `MANUAL_IMPORT`） | 预测 vs 实际对比失真，复盘建议不可信；冷启动阶段没有真实数据 | 快照强制记录 `metric_source` + `confidence`；`LEGACY_JSONB` 标 `LOW`；复盘报告在数据不足时输出 `INSUFFICIENT_DATA` 而非编造建议；E2 场景专门验证手动回收链路 |
| R3 | **预测模型缺乏历史数据冷启动** | `metric_forecast` 无历史样本，偏差分析意义有限 | 阶段 3 只做"预测 vs 实际"的结构化存储与展示，不训练模型；预测可由用户手工填写或来自已有生成任务；无预测时对比行不生成并显式说明 |
| R4 | **爬取类连接器的法务与平台政策风险** | 账号封禁、平台条款违约、跨境合规风险 | 严格按 `CONNECTOR-AUTHORIZATION.md` 默认拒绝；`app.platform_connector_authorization.authorized=FALSE` 的组合运行时拒绝；不实现未授权抓取；新增连接器必须走"产品 + 法务双签字"变更流程 |
| R5 | **跨平台指标口径不统一**（播放 vs 曝光 vs 阅读；私信 vs 咨询） | 平台 / 品类维度报表横向对比失真 | 核心 11 指标字段统一键集，平台差异进 `metric_extra`；跨平台报表必须展示口径说明；统一"单位"（次 / 率 / 元）并在响应中标注 |
| R6 | **复盘回写知识库 / 模板造成知识污染** | 错误结论进入知识库后被持续检索放大，影响后续所有生成 | 回写必须经 ADMIN 确认；单条复盘只进"效果统计"不直接进知识库正文；进正文必须重新分块向量化；保留 `report_id` 来源并支持一键撤回；审批与撤回写审计 |
| R7 | **前端缺口大**（7 个页面 + 8 个 api 模块 + 3 语 i18n 全新建） | 阶段 3 交付周期被前端拖长 | 前端单列 S3-12，可按页面切 3 个 subagent 并行；i18n 命名空间统一登记（`publish` / `metrics` / `retrospective` / `hotTopic` / `competitor` / `kb` / `connector`），zh-CN / zh-TW / en 三语同步 + `types.ts` 同步；顺带补齐既有 i18n 缺口（zh-CN 缺 `retentionPolicy`、zh-TW 缺 `admin`、`types.ts` 缺多命名空间） |
| R8 | **知识库管理能力仍受 `@Profile("local")` 限制** | 非 local profile 无法管理文档；回写审批流无法在生产落地 | S3-11 显式解除 profile 限制并补 `kb_document` 实体与 Mapper（当前只有裸 SQL）；补文档删除 API 与对象存储清理 |
| R9 | **发布幂等依赖任务队列与数据库唯一约束的配合** | 并发重试可能重复发布（§8.2 要求重复率为 0） | `idempotency_key` 唯一索引 + 入队前查重 + 重试复用原 key 三重保证；`publish_receipt` 按 `publish_task_id` 唯一；E4 场景强制并发 5 次验证 |
| R10 | **D1 / D2 未签字** | 凭据托管模型与平台名单变动会推翻 `platform_account` / `publish_task` 表结构与连接器实现 | 阶段 3 开工前必须签字；建议按默认方案（C + 小红书/抖音）先行实现并把可替换点隔离在连接器实现层 |

---

## 出口条件 checklist

### 子能力 1：发布与排期

- [ ] `app.platform_account` 表 + 连接状态机（unconnected / connected / expired / reauthorizing / failed）落地
- [ ] 未授权平台 × 连接器组合运行时拒绝（403 `CONNECTOR_NOT_AUTHORIZED`），无数据落库
- [ ] `rollout_status='disabled'` 平台（快手 / Instagram / TikTok）连账号都不允许创建
- [ ] 授权到期前 7 天 / 到期 / 已过期三种提示可区分
- [ ] `write_enabled=false` 的平台不能创建自动发布任务
- [ ] 凭据 AES-GCM 加密存储，接口响应与日志均无凭据明文
- [ ] 连接 / 重授权 / 解绑写 `app.audit_event`
- [ ] `app.publish_schedule` 表 + `APPROVED → SCHEDULED` 状态机校验生效
- [ ] 未审核通过的资产排期返回 409 `ASSET_NOT_APPROVED`
- [ ] 时区按工作区时区存储与展示，跨时区排期排序正确
- [ ] 同工作区 + 同平台 + 同账号 + 同时刻冲突检测生效（409 `SCHEDULE_CONFLICT`）
- [ ] 取消 / 重试 / 过期（`scheduled / failed / cancelled / expired`）状态齐全，失败原因非 `unknown`
- [ ] `app.publish_task` + `app.publish_receipt` 落地，幂等键唯一索引生效
- [ ] 同一幂等键并发 5 次 → 只 1 次真实发布、1 条回执（重复发布率 0）
- [ ] 重试复用原幂等键，不产生新的外部发布
- [ ] 回执必须含 `external_content_id` 或 `source_url` 之一，否则任务失败（`RECEIPT_INCOMPLETE`）
- [ ] `published_at` 优先取平台返回值，本地兜底单独标注 `published_at_source='local'`
- [ ] 三条降级路径（自动 / BROWSER_ASSIST / MANUAL_IMPORT）共用同一任务表并用 `level` 区分
- [ ] BROWSER_ASSIST 路径系统不点发布，UI 显著提示"用户自负发布风险"
- [ ] MANUAL_IMPORT 路径不返回伪造 `externalContentId`
- [ ] 连接器异常时内容编辑 / 审核 / 手动导出不被阻塞
- [ ] 发布动作写审计（含操作者、平台、账号、资产、外部内容 ID）

### 子能力 2：指标快照与数据回流

- [ ] `app.metric_snapshot` 表落地，含 `metric_source` / `confidence` / `collected_at` / `is_manual` / `revised_from_id`
- [ ] 核心 11 个指标字段所有平台统一键集，缺失写 0 不省略
- [ ] 平台差异只进 `metric_extra`，前端不硬编码平台字段
- [ ] `analysis_task.published_metrics` 降级为兼容读取，标记 `LEGACY_JSONB` + `LOW` 可信度
- [ ] 人工修订产生新快照，不原地覆盖，原快照可追溯
- [ ] 快照归属校验（workspace / platform / account 与资产一致），不一致返回 409 `ACCOUNT_MISMATCH`
- [ ] 采集定时任务 6h 一轮，走持久化队列 + 幂等 + DLQ，未使用 `@Async`
- [ ] 采集幂等键 = 平台 + 账号 + 外部内容 ID + 采集窗口，同窗口不产生重复快照
- [ ] 手动录入标 `is_manual=TRUE`、`metric_source='MANUAL_IMPORT'`
- [ ] CSV 导入复用 `app.csv_import_log`（`import_type='METRIC'`），表头白名单校验，未知列拒绝并写行号 + 原因
- [ ] 导入失败行不影响成功行
- [ ] 采集连续失败 3 次进 DLQ，连接器转 `DEGRADED`，界面展示失败原因与最后成功时间
- [ ] 数据同步延迟、失败率、最后成功时间在界面可见
- [ ] 指标采集不计费，不写 `usage_ledger` DEBIT

### 子能力 3：预测 vs 实际与复盘报告

- [ ] `app.metric_forecast` / `app.metric_comparison` / `app.retrospective_report` 三表落地
- [ ] 预测值与实际值分表存储，接口响应中 `predicted` / `actual` 分字段返回，无混合 `metrics` 字段
- [ ] 预测值携带 `model_name` / `prompt_version` / `kb_version` / `generated_at`，与实际的 `metric_source` / `collected_at` 同屏展示
- [ ] 偏差率在实际为 0 时为 `null`，不写 Infinity 或伪 0
- [ ] 命中维度按 ≥0.8 HIT / ≤0.5 MISS / 中间 PARTIAL 判定
- [ ] 无预测时不生成对比行，且报告显示"无预测值"，不显示为偏差大
- [ ] 每条建议带 `evidenceRefs` 指向真实 `snapshot` / `comparison`，无证据的建议被丢弃
- [ ] 复盘报告出参 Schema 版本化且固定，含 `hitDimensions` / `missedDimensions` / `suggestions`
- [ ] `suggestions[].type` ∈ TOPIC / TITLE / BODY / POST_TIME
- [ ] 结构化输出解析成功率 ≥ 99%
- [ ] 解析失败 → 重试 1 次 → 规则降级报告 `status='DEGRADED'` + 明确提示，**不生成伪成功报告**
- [ ] 建议少于 3 条时报告状态为 `INSUFFICIENT_DATA` 并说明缺失维度
- [ ] 复盘建议进入合规扫描，高风险建议被替换
- [ ] 六种报表维度（单篇 / 账号 / 平台 / 品类 / 活动 / 时间段）可查且口径统一
- [ ] 复盘生成扣 `BillableUnit.ANALYSIS`，失败按 `BILLING-RULES.md` 先 REFUND 再重发，不重复扣费
- [ ] 复盘、对比、快照全部按 workspace 隔离

### 子能力 4：热点与竞品研究数据产品化

- [ ] `app.hot_topic` 加列（`source_name` / `source_type` / `confidence` / `is_realtime` / `source_url` / `degraded` / `degraded_reason`）
- [ ] 静态种子数据标 `is_realtime=FALSE` + `source_type='SEED'`，界面显式标注"非实时"
- [ ] `fetched_at` 在接口与 UI 暴露
- [ ] `app.hot_topic_cache` 生效，同窗口命中缓存不重复调用外部源
- [ ] 去重保留历史（`superseded`），趋势基于同平台 + 同归一化标题的多条快照
- [ ] 单条热点不得输出"上升"趋势
- [ ] 热点可做方向每条引用真实标题 + 来源 + 采集时间
- [ ] 连接器失效 → `degraded=true` + `degradedReason` + `fallbackAvailable='MANUAL_IMPORT'`，不静默展示陈旧数据
- [ ] 热点手动导入入口可用（CSV / 表单）
- [ ] 热点 → 选题池透传来源与可信度（`topic_pool` 加三列）
- [ ] `app.competitor_account` / `app.competitor_note` 落地，`evidence_text` 缺失不允许入库
- [ ] 竞品采集受授权清单门禁约束，未授权平台 403
- [ ] 竞品分析输出每条"可借鉴点"带 `evidenceRefs`，无证据返回 400 `COMPETITOR_EVIDENCE_MISSING`
- [ ] 竞品数据更新时间（`captured_at` / `updated_at`）在接口与 UI 展示
- [ ] 竞品账号维度互动率对比口径统一
- [ ] 竞品建议文本进入合规扫描

### 子能力 5：连接器抽象与诊断

- [ ] `PlatformConnector` 统一接口覆盖 publish / fetchMetrics / fetchHotTopics / fetchCompetitorNotes
- [ ] 4 种实现级别与既有冻结枚举一一对应，未新增等级
- [ ] `MANUAL_IMPORT` 实现的 `publish()` 返回 `manual=true` 语义，不伪造外部 ID
- [ ] 未授权连接器不注册到容器，调用 403
- [ ] 授权清单与 `app.platform.connector_level` 冲突时以授权清单为准
- [ ] 连接器凭据不进日志、不进异常消息、不进 `raw_response`
- [ ] 每个连接器实现 `healthCheck()` 返回状态 / 最后成功时间 / 失败原因 / 延迟
- [ ] `app.connector_health` 落地，状态含 `HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED`
- [ ] 失败分类覆盖 `AUTH_EXPIRED / RATE_LIMITED / PLATFORM_CHANGED / NETWORK_ERROR / NOT_AUTHORIZED`
- [ ] 连续失败 3 次 → `DEGRADED`；10 次 → `UNAVAILABLE` 并自动降级 + 审计
- [ ] 降级路径链 `OFFICIAL_API → BROWSER_ASSIST → MANUAL_IMPORT → EXPORT_ONLY` 可查询
- [ ] 降级切换在界面可见，含不可用原因与重新授权入口
- [ ] 诊断 API 只读、无副作用，不触发真实发布
- [ ] 重新授权入口在账号页与连接器诊断页均可点击

### 阶段3 整体出口条件

- [ ] E1：小红书完成一次真实发布回收闭环（填充 → 用户确认 → 回填外部内容 ID / 来源链接 → 回执 → 资产 `PUBLISHED`）
- [ ] E2：抖音完成一次手动发布回收闭环（清单导出 → 手动发布 → CSV 回填 → 指标快照）
- [ ] E3：复盘报告输出 ≥3 条带真实数据证据的可执行建议
- [ ] 8 条端到端场景全部跑通
- [ ] 性能与可靠性指标全部达标（分析 P95 < 45s、生成 P95 < 30s、结构化解析 ≥99%、敏感词 100%、重复发布率 0、重复扣费率 0）
- [ ] 后端 `mvn compile` 通过，前端 `vue-tsc` 通过
- [ ] 新增 i18n 命名空间在 zh-CN / zh-TW / en 三语齐全，`types.ts` 同步，既有缺口（zh-CN 缺 `retentionPolicy`、zh-TW 缺 `admin`）已补齐
- [ ] D1 / D2 未冻结决策已由产品 + 运营 + 法务签字
- [ ] 阶段 3 新增表的 Flyway 迁移可重复执行、不破坏阶段 0-2 既有数据
- [ ] 所有新增接口通过跨 workspace 隔离验证（跨 workspace 读取返回 404）

---

## 签字位

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 产品负责人 | _____ | _____ | |
| 运营负责人 | _____ | _____ | |
| 法务/合规 | _____ | _____ | |
| 技术负责人 | _____ | _____ | |

> 签字代表确认"阶段 3 范围、出口条件与未冻结决策的默认方案可作为本阶段开发基线"。
