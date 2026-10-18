# 阶段2 PRD：运营创作闭环

> 项目：XhsAgent 全平台 AI 内容运营 SaaS
> 分支：`xhs_agent`（领先 `origin/xhs_agent` 21 个提交）
> 上游：阶段0（产品冻结）✅、阶段1（商业 SaaS 基础）✅
> 文档状态：v1.0 待全栈开发评审与用户确认

---

## 0. 文档说明

| 项 | 内容 |
|---|---|
| 目标 | 运营用户可独立完成"选题→创作→审核→发布→导出"全链路，不依赖开发 |
| 上游交付 | 阶段0 决策表 + 阶段1 工作区/资产/模板/审核/导出/额度/任务队列 |
| 非目标 | 阶段3 的真实平台指标回写、阶段4 的订阅与计费后台 |
| 关键决策（已与用户确认） | M0 全部 6 项衔接修补 + 均衡推进 M1→M2→M4→M3→M5 + 浏览器辅助完整集成 cursor-ide-browser MCP |

---

## 1. 出口条件（来自 `COMMERCIAL_SAAS_READINESS_PLAN.md` §9 阶段2）

> 一个运营人员不借助开发人员，可以完成创建、生成、审核、导出和版本恢复。

可验证场景：

1. 运营进入工作区 → 选题池选/录入选题 → 创建内容资产草稿
2. 调用标题/正文/评论 Agent 工具生成多版本 → 写入资产版本
3. 提交审核 → Reviewer 看到队列 → 高风险自动 require review → 批准/驳回带意见
4. 编辑完善 → 调用平台预览接口检查平台规格 → 提交发布（首期 MANUAL_IMPORT 平台走导出；小红书 BROWSER_ASSIST 走浏览器辅助）
5. 导出 MD/DOCX/ZIP/CSV → 任意时刻版本回滚/对比

---

## 2. 角色与责任

| 角色 | Agent ID | 阶段2 主要责任 |
|---|---|---|
| 项目经理 | `project-manager` | 拆解 PRD、验收口径、协调里程碑 |
| 全栈开发工程师 | `fullstack-developer` | M0 衔接修补 + M1–M5 全链路实现（DB + 后端 + 前端 + AI） |
| 代码审核员 | `code-reviewer` | 每个里程碑结尾审一次；M6 集成回归 |

阶段1 已落实的角色权限（OWNER/ADMIN/EDITOR/REVIEWER/ANALYST/VIEWER）继续沿用，本阶段不再新增角色。

---

## 3. M0：阶段1 衔接修补（必须先做）

| # | 问题 | 现状 | 修补方案 | 验收 |
|---|---|---|---|---|
| M0-1 | `ContentAssetService.update()` 无乐观锁 | 主表无 version 字段 | 新增 `content_asset.version INT`（数据库迁移 + JPA @Version） | 并发更新单元测试：A/B 同时编辑，后写者 409 |
| M0-2 | 病毒扫描未集成到上传路径 | `ClamAvScanner` 代码就绪但未在 `addMedia()` 中调用 | 上传链：multipart → 流式扫描 → 通过才写 MinIO；扫描失败抛 `VirusScanUnavailableException`；拒绝时对象不入桶 | 集成测试：上传含 EICAR 文件 → 拒绝且无对象残留 |
| M0-3 | `TemplateService.apply()` 不创建 content_asset | 模板应用只返回渲染 body | `apply()` 新增可选参数 `createAsset=true`：自动创建 DRAFT 资产 + 首版本 + 反向写 `topic_pool` 状态为 `used`（若传入） | 集成测试：模板 apply 创建资产且版本号=1 |
| M0-4 | 优化稿扣费口径不明 | `OptimizeDraftTool` 调用 `optimizeDraft()`，未确认走额度守卫 | 审计 `AnalysisAppService.optimizeDraft()`：必须经过 `AtomicQuotaGuard.consume(BODY)`；失败/异常必须冲正 | 单元测试：额度不足抛错且 ledger 无残留；ledger 含 REFUND |
| M0-5 | 平台连接器授权运行时未校验 | `PlatformConnectorLevel.canWrite()` 存在但调用点缺失 | 新增 `PlatformConnectorAuthorizationService.assertCanWrite(platform, workspaceId)`；所有写入路径（小红书发布、评论）必须前置调用 | 单测：DISABLED 平台写入抛 `PlatformConnectorUnavailableException`；日志含原因 |
| M0-6 | 选题池/评论生成缺合规扫描 | `ComplianceScanner` 已支持扩展 | `GenerateCommentTool`、`TopicPoolService` 入库前调用 `scanCompliance`；HIGH/BLOCKED 直接拒绝；MEDIUM 走审核 | 单测：评论含 "最佳/100%" → 拒绝；MEDIUM 走 review 流 |

### M0 出口

- 6 个迁移脚本（含 `version` 字段）
- M0-1/2/3/4/5/6 全部集成测试通过
- 阶段2 模块可以基于已修补的资产/模板/合规/上传链路开发

---

## 4. 里程碑 M1：选题池基础设施

### 4.1 范围

- `app.topic_pool` / `app.hot_topic` / `app.competitor_analysis` / `app.competitor_content` 4 张表
- 定时任务（每 4h）调用 `GetHotTopicsTool` + `WebSearchTool`，结果落 `app.hot_topic`
- `TopicPoolService`：CRUD + 状态机（active → used → archived）
- 选题→内容资产一键创建（POST `/v1/assets` from topic，参数 `topicId`）
- 前端 `TopicPoolView.vue`：列表 / 搜索 / 过滤 / 状态切换 / 卡片视图 / 一键创建资产

### 4.2 接口契约

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| GET | `/v1/topics` | workspaceId, status, keyword, page, size | `PaginatedResponse<TopicResponse>` |
| POST | `/v1/topics` | TopicCreateRequest | `TopicResponse` |
| PATCH | `/v1/topics/{id}` | status, scheduledDate | `TopicResponse` |
| POST | `/v1/topics/{id}/to-asset` | platform, category | `AssetResponse` |
| GET | `/v1/hot-topics` | platform, since | `List<HotTopicResponse>` |

### 4.3 验收

- 4 张表上线，索引覆盖 `workspace_id, status, created_at`
- 定时任务跑通，至少 3 个平台有真实数据
- 一键创建资产后 `topic_pool.status='used'` 且关联 `asset_id`
- 前端 i18n（zh-CN/en/zh-TW）三语齐备
- `docs/topic-pool.md` 用户手册

### 4.4 风险

- 热点搜索会消耗联网搜索 quota；需在定时任务里复用缓存（Redis key：`hot_topic:{platform}:{date}` TTL 4h）
- AI 抓取的选题不能完全脱敏，可能含竞品姓名 → 入库前过合规扫描

---

## 5. 里程碑 M2：媒体中心增强

### 5.1 范围

- 图片处理：压缩（ImageMagick，< 500KB）、缩略图（200×200 / 800×800）、EXIF 提取
- 视频处理：FFmpeg 转码（H.264 720p）、首帧缩略图、时长探测
- 字幕生成：Whisper HTTP 接口（异步任务队列，可降级到 NoOp）
- 媒体中心页面 `MediaCenterView.vue`：上传进度、扫描状态、缩略图预览、按资产分组

### 5.2 接口契约（增量）

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| POST | `/v1/assets/{id}/media/upload-url` | filename, mimeType, size | `MediaUploadTicketResponse` |
| POST | `/v1/assets/{id}/media/confirm` | objectKey, scanResult | `MediaResponse` |
| GET | `/v1/media/{id}/thumbnail` | size | 302 → 签名 URL |
| POST | `/v1/media/{id}/transcribe` | language | `TaskAcceptedResponse`（异步） |

### 5.3 验收

- 病毒扫描前置（继承 M0-2）
- 图片压缩/缩略图集成测试
- FFmpeg 转码集成测试（输入 mp4 → 输出 720p mp4）
- Whisper 字幕降级测试（whisper 不可用时返回明确错误，不伪装成功）
- 对象存储生命周期：临时上传对象 24h 自动清理

### 5.4 风险

- FFmpeg 镜像层体积大，需单独构建 worker 镜像
- Whisper 调用成本与延迟 → 默认关闭，按用户显式启用
- 上传大文件需要分片 → 首版只支持 ≤ 50MB 单请求

---

## 6. 里程碑 M3：浏览器辅助（MCP 完整集成）

### 6.1 范围

- `app.browser_assist_session` 表：workspaceId, userId, platformCode, sessionToken, expiresAt, status
- MCP 集成 `cursor-ide-browser` 服务（MCP 协议已在阶段1落地）：
  - `browser_navigate` / `browser_snapshot` / `browser_click` / `browser_fill`
  - `browser_lock` / `browser_unlock` 包装为可观测的操作
  - 录制小红书"创作中心 → 发布"流程为 Playbook
- `BrowserAssistService`：会话生命周期 + 操作日志 + 失败回滚
- 前端 `BrowserAssistView.vue`：会话状态、Playbook 列表、操作回放

### 6.2 接口契约

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/browser-assist/sessions` | 创建会话，返回 sessionToken + Cookie 引导页 |
| GET | `/v1/browser-assist/sessions/{id}` | 状态/最近操作 |
| DELETE | `/v1/browser-assist/sessions/{id}` | 销毁 |
| POST | `/v1/browser-assist/playbooks/{name}/run` | 启动 playbook（异步任务） |
| GET | `/v1/browser-assist/playbooks/{name}/runs/{runId}` | 状态 |

### 6.3 验收

- 真实启动 cursor-ide-browser tab → 用户登录小红书 → sessionToken 持久化
- Playbook：新建小红书笔记 → 填标题/正文/标签 → 截图存档（不自动点击发布，必须用户确认）
- 操作审计：每个 browser_* 调用写入 `audit_event`，含操作人/会话/平台
- 失败处理：tab 关闭/超时 → session 进入 `expired`，可一键重连

### 6.4 风险（最高）

- **政策风险**：小红书 BROWSER_ASSIST 可能违反平台协议，必须在 UI 显著位置提示用户自负风险
- **凭据风险**：Cookie 加密存储（AES-GCM），签名过期 ≤ 24h，刷新令牌换发
- **MCP 故障**：cursor-ide-browser 不可用时必须降级到 MANUAL_IMPORT，不能伪装成功

---

## 7. 里程碑 M4：评论生成与多版本增强

### 7.1 范围

- `GenerateCommentTool implements AgentTool`：评论生成（首问 / 追问 / 私信话术 / 转化话术）
- `PromptEngine.buildGenerateCommentUserPrompt()` + 版本常量 `COMMENT_PROMPT_VERSION=comment-1.0.0`
- `content_asset` 增加 `version` 乐观锁字段（M0-1 落地）
- `diffVersions()` 增加 `expectedVersion` 参数 → 不匹配返回 409
- 前端版本对比页 `ContentAssetDiffView.vue`：增加并发冲突提示横幅
- 评论入库走合规扫描（M0-6）

### 7.2 接口契约

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/assets/{id}/comments/generate` | 评论生成（同步 → 异步） |
| GET | `/v1/assets/{id}/comments` | 评论草稿列表 |
| POST | `/v1/assets/{id}/comments` | 保存评论草稿 |
| POST | `/v1/assets/{id}/comments/{cid}/send` | 标记为已发送（不进合规扫描；状态可人工指定） |

### 7.3 验收

- 4 种场景 Prompt 单元测试：首问/追问/私信/转化
- 乐观锁冲突 E2E 测试：A 编辑 v3 保存 → B 编辑 v2 保存后看到冲突提示
- 评论入库后状态机：DRAFT → APPROVED（需 review）→ SENT
- Agent Chat UI 暴露 `generate_comment` 工具

### 7.4 风险

- 评论生成是高频操作，必须走额度守卫（每次扣 COMMENT quota）+ Redis cache（同一输入 1h 内复用）
- 转化话术含合规风险 → MEDIUM 级必须走 review

---

## 8. 里程碑 M5：竞品分析与发布日历

### 8.1 范围

- `CompetitorService`：手动录入竞品账号/笔记（首期）
- 竞品分析报告：复用 `AnalyzeContentTool scenario=competitor`，输出对比维度（标题结构/钩子/CTA/标签）
- `PublishingCalendarService`：基于 `topic_pool.scheduled_date` + `GetIndustryCalendarTool` 数据
- 前端：
  - `CompetitorView.vue`：竞品账号/笔记列表 + 报告查看
  - `PublishingCalendarView.vue`：月/周/日视图 + 拖拽排期

### 8.2 接口契约

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/competitors` | 创建竞品 |
| GET | `/v1/competitors` | 列表 |
| POST | `/v1/competitors/{id}/contents` | 手动录入笔记（标题/正文/链接/指标） |
| POST | `/v1/competitors/{id}/analyze` | 异步生成竞品分析报告 |
| GET | `/v1/calendar` | 月视图（month=YYYY-MM） |
| PATCH | `/v1/topics/{id}/schedule` | 排期 |

### 8.3 验收

- 手动录入竞品笔记 → 触发分析 → 报告展示维度对比
- 日历视图：草稿/审核中/已批准/已排期 四种状态可视化
- 拖拽排期乐观锁：拖拽到冲突时段弹出确认
- i18n 三语齐备

### 8.4 风险

- 手动录入数据可信度低 → 每条录入必须有"数据来源/采集时间"两个字段
- 日历跨时区 → 全部时间用 UTC 存储，前端按用户时区展示

---

## 9. 阶段2 数据模型汇总（增量）

```sql
-- M0 迁移
ALTER TABLE app.content_asset ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE app.content_asset ADD CONSTRAINT ck_asset_version CHECK (version >= 1);

-- M1 迁移
CREATE TABLE app.topic_pool (...);
CREATE TABLE app.hot_topic (...);
CREATE TABLE app.competitor_analysis (...);
CREATE TABLE app.competitor_content (...);

-- M3 迁移
CREATE TABLE app.browser_assist_session (
    id UUID PRIMARY KEY,
    workspace_id VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    platform_code VARCHAR(32) NOT NULL,
    session_token_enc BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    last_op_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- M3 迁移
CREATE TABLE app.browser_playbook_run (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES app.browser_assist_session(id),
    playbook_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    steps JSONB NOT NULL DEFAULT '[]',
    artifacts JSONB,  -- 截图、HTML 快照
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);
```

所有新表必须带：
- `workspace_id` 外键 + 索引
- `created_at` / `updated_at`
- 软删除列（如需）
- 行级权限（service 层强制 workspaceId 校验）

---

## 10. 前端增量结构

```
Xhs-Frontend/src/views/
├── topic/                # M1
│   └── TopicPoolView.vue
├── hot/                  # M1
│   └── HotTopicsView.vue
├── media/                # M2
│   └── MediaCenterView.vue
├── browser-assist/       # M3
│   ├── BrowserAssistView.vue
│   └── PlaybookRunView.vue
├── competitor/           # M5
│   ├── CompetitorListView.vue
│   └── CompetitorDetailView.vue
└── calendar/             # M5
    └── PublishingCalendarView.vue
```

i18n key 前缀：`topic.*` / `hot.*` / `media.*` / `browserAssist.*` / `competitor.*` / `calendar.*`

---

## 11. 验收门禁（阶段2 整体）

### 11.1 功能

- [ ] M0：6 项衔接修补全部集成测试通过
- [ ] M1：4 张表上线，定时任务产生真实数据，一键创建资产可用
- [ ] M2：图片压缩+缩略图+FFmpeg 转码+Whisper 字幕集成测试通过
- [ ] M3：cursor-ide-browser MCP 真实跑通小红书创作中心 Playbook（不点发布）
- [ ] M4：评论生成 4 场景可用，乐观锁 E2E 通过
- [ ] M5：竞品手动录入 + 日历拖拽排期可用

### 11.2 体验

- [ ] 运营人员 30 分钟内可完成"选题→生成→审核→导出"完整流程（不再需要开发协助）
- [ ] 所有异步任务支持刷新/断网重连后恢复
- [ ] 移动端可查看/编辑/审核/复制/导出

### 11.3 可靠性

- [ ] 病毒扫描失败不污染对象存储
- [ ] 乐观锁冲突有清晰提示
- [ ] 浏览器辅助 session 异常不会阻塞编辑/导出
- [ ] 选题池定时任务有死信与重试

### 11.4 可观测

- [ ] 所有 AI 调用记录模型/Prompt 版本/输入摘要/耗时
- [ ] 所有浏览器操作写入 audit_event
- [ ] 阶段2 模块接入阶段1 的告警通道

---

## 12. 优先级与排期建议

| 里程碑 | 估算 | 前置 | 关键风险 |
|---|---|---|---|
| M0 衔接修补 | 1 周 | 无 | 病毒扫描与乐观锁的兼容性 |
| M1 选题池 | 2 周 | M0 | 热点搜索成本/合规 |
| M2 媒体中心 | 2-3 周 | M0 | FFmpeg 镜像/Whisper 降级 |
| M3 浏览器辅助 | 3 周 | M0 | 小红书政策/凭据安全 |
| M4 评论与多版本 | 2 周 | M0 | 转化话术合规 |
| M5 竞品与日历 | 2 周 | M1 | 数据可信度/时区 |

总工期估算：**12-13 周**（单全栈工程师串行；可与审核员并行）。

---

## 13. 不在阶段2 范围

| 项 | 推迟到 |
|---|---|
| 真实平台指标回写、预测对比 | 阶段3 |
| 计费后台、订阅、发票 | 阶段4 |
| 抖音/B 站/微博 BROWSER_ASSIST 升级 | 阶段3（视授权） |
| 视频自动剪辑 | 阶段3+ |
| 跨平台内容适配 | 阶段4 |

---

## 14. 风险登记册

| ID | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R-01 | 小红书 BROWSER_ASSIST 政策风险 | 高 | 高 | UI 显著提示 + 用户协议 + 不自动发布 |
| R-02 | 浏览器 Cookie 泄露 | 中 | 高 | AES-GCM 加密 + 短过期 + 审计 |
| R-03 | 热点搜索 quota 耗尽 | 中 | 中 | 4h 缓存 + 失败降级 |
| R-04 | Whisper 服务不可用 | 中 | 低 | NoOp 降级，UI 明确提示 |
| R-05 | FFmpeg 镜像体积 | 低 | 中 | 独立 worker 镜像，多阶段构建 |
| R-06 | 评论生成被滥用 | 中 | 高 | 配额 + 合规扫描 + 转化话术必审 |
| R-07 | 乐观锁改造影响阶段1 已上线数据 | 低 | 高 | 迁移脚本默认 version=1，灰度发布 |