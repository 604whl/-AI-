# 阶段 1 剩余 P0 任务规划

> 编制日期：2026-10-17
> 编制依据：`COMMERCIAL_SAAS_READINESS_PLAN.md` 第 9-11 节
> 上一阶段提交：`06741ca`（多租户 + 成员管理 + Service 层隔离）
> 总工期：约 26.5 人·天（2 人并行 13-14 工作日）

## 实施顺序

```
A. P0-7 (CI/镜像/备份)  ║  P0-8 (安全加固)         ← 并行
   ↓
B. P0-1 (任务队列 + 幂等 + 死信)                  ← 串行
   ↓
C. P0-2 (原子额度 + 冲正)                         ← 串行
   ↓
D. P0-3 (内容资产)                                ← 串行
   ↓
E. P0-4 (模板)  ║  P0-6 (审核)  ║  P0-5 (导出)   ← 并行
   ↓
F. P0-9 (数据保留 + 租户销毁)                     ← 串行
```

---

## P0-1 持久化任务队列 + 幂等 + 死信

- **依赖前置**：无
- **目标**：用 Redis Streams 替代 `@Async` 临时方案；引入幂等键、状态机、重试退避、DLQ
- **数据库变更**：
  - `app.analysis_task` 新增 `idempotency_key` (UNIQUE)、`started_at`、`finished_at`、`retry_count`、`dead_letter_at`
  - 新建 `app.task_dlq`（id, original_task_id, payload, error, failed_at）
  - 新建 `V17__task_queue.sql`
- **新增后端**：
  - `infra/taskqueue/TaskQueueProducer` —— XADD 投递
  - `infra/taskqueue/TaskQueueConsumer` —— XREADGROUP + worker
  - `infra/taskqueue/TaskRegistry` —— 任务类型→handler 路由
  - `infra/taskqueue/DeadLetterQueue` —— 超 3 次进 DLQ
  - `infra/idempotency/IdempotencyKeyService` —— Redis SETNX
- **修改后端**：
  - `ai/orchestrator/AnalysisOrchestrator` —— 改为投递到队列
  - `service/AnalysisAppService` —— 提供 idempotencyKey 透传
- **API 端点**：
  - `POST /v1/analysis` 接受 `Idempotency-Key` Header
  - `GET /v1/admin/tasks/dlq` —— 查 DLQ（ADMIN 角色）
  - `POST /v1/admin/tasks/{id}/retry` —— 人工重试
- **单测**：幂等键重复、状态机迁移、超时 kill、退避 1/2/4s、DLQ 入队
- **集成测试**：worker 杀掉后任务恢复；事务回滚不留孤儿任务
- **验收**：
  - [ ] 5 个相同 Idempotency-Key 并发请求只执行 1 次
  - [ ] worker 异常后 30s 内任务可被新 worker 接管
  - [ ] 数据库 COMMIT 后才有任务入队
  - [ ] 超 3 次失败自动入 DLQ 并有审计
  - [ ] 前端 `GET /v1/analysis/{id}` 任何时刻返回真实状态
- **工作量**：5-7 人·天
- **风险**：事务边界；Redis Stream MAXLEN 调优；XAUTOCLAIM 处理 pending 任务

---

## P0-2 原子化额度扣减 + 冲正 + 冲正

- **依赖前置**：P0-1（任务成功后扣减）
- **目标**：单 SQL UPDATE 原子扣减；失败/超时/重复自动冲正；`usage_ledger` 全审计
- **数据库变更**：
  - 新建 `app.workspace_quota`（workspace_id PK, daily_quota, used, reset_at）
  - 新建 `app.usage_ledger`（id, workspace_id, action, ref_id, delta, status=CONSUMED/REFUNDED, related_log_id, created_at）
  - 改 `app.usage_log` 加 `refund_status` / `refunded_at` / `original_log_id`
  - 新建 `V18__quota_ledger.sql`
- **新增后端**：
  - `quota/QuotaLedgerService` —— 记账
  - `quota/QuotaRefundService` —— 冲正
  - `quota/AtomicQuotaGuard` —— 单 SQL UPDATE 守卫
- **修改后端**：
  - `service/UserQuotaService` —— 改为 ledger 模式
  - `service/AnalysisAppService` / `ChatAppService` / `TitleService` —— 任务成功才扣减，失败走冲正
- **API 端点**：
  - `GET /v1/workspaces/{id}/quota` —— 查当前剩余
  - `GET /v1/workspaces/{id}/quota/ledger` —— 查审计
- **单测**：1000 并发同一 workspace 不超额；失败冲正；幂等请求不重复扣
- **集成测试**：任务失败 1s 内冲正；重复请求返回 429 且 ledger 留痕
- **验收**：
  - [ ] 1000 并发同 workspace 分析请求最多成功 `daily_quota` 次
  - [ ] 失败任务 1s 内冲正，剩余次数回补
  - [ ] ledger 记录所有扣减/冲正
  - [ ] 切换 workspace 后 quota 独立
- **工作量**：3-5 人·天
- **风险**：UPDATE 并发性能；冲正时 ledger 与 usage_log 一致性

---

## P0-3 内容资产 ContentAsset

- **依赖前置**：P0-1、P0-2
- **目标**：内容资产模型 + 版本回滚 + 多媒体附件 + workspace 隔离
- **数据库变更**：
  - 新建 `app.content_asset`（id, workspace_id, owner_user_id, title, body, persona, scenario, industry, status, source_task_id, platform, account_id, created_at, updated_at）
  - 新建 `app.content_asset_version`（asset_id, version_no, title, body, edited_by, edit_reason, created_at）
  - 新建 `app.content_asset_media`（asset_id, media_type, object_key, role=cover/image/video）
  - 状态枚举：draft / in_review / approved / scheduled / published / archived
  - 新建 `V19__content_asset.sql`
- **新增后端**：
  - `domain/entity/ContentAsset.java` / `ContentAssetVersion.java` / `ContentAssetMedia.java`
  - `service/ContentAssetService.java` —— 草稿自动保存（30s）/ 版本比较 / 回滚
  - `controller/v1/ContentAssetController.java`
- **修改后端**：
  - `service/AnalysisAppService` —— 分析完成后 create draft asset
- **API 端点**：
  - `POST /v1/assets` / `GET /v1/assets` / `GET /v1/assets/{id}` / `PATCH /v1/assets/{id}` / `DELETE /v1/assets/{id}`
  - `GET /v1/assets/{id}/versions` / `POST /v1/assets/{id}/versions` / `POST /v1/assets/{id}/rollback/{versionNo}`
  - `POST /v1/assets/{id}/media` / `DELETE /v1/assets/{id}/media/{mediaId}`
- **新增前端**：
  - `views/asset/ContentAssetListView.vue`
  - `views/asset/ContentAssetDetailView.vue`
  - `views/asset/ContentAssetDiffView.vue`
- **单测**：状态机迁移；版本回滚数据完整性；workspace 隔离；自动保存节流
- **集成测试**：5 次 AI 生成 + 3 次人工编辑 = 8 个历史版本；跨 workspace 读取返回 404
- **验收**：
  - [ ] 1 个 asset 关联 1 封面 + 2 图片，全部能下载/预览
  - [ ] 任意版本可回滚，回滚后产生新版本号
  - [ ] 自动保存 30s 触发，可关闭
  - [ ] 切换 workspace 后看不到其他 asset
- **工作量**：10-14 人·天
- **风险**：大文本版本存储成本；MinIO 对象生命周期与数据库一致性

---

## P0-4 模板库 Template（三级范围）

- **依赖前置**：P0-3（asset 作为生成目标）
- **目标**：OFFICIAL / WORKSPACE / PERSONAL 三级模板，变量替换，官方 8 种子
- **数据库变更**：
  - 新建 `app.template`（id, scope, workspace_id, owner_user_id, category, scenario, persona, title, body_template, variables JSONB, default_goal, default_tone, version, status=active/archived, created_at）
  - 新建 `app.template_version`（template_id, version_no, snapshot, published_at, published_by）
  - 新建 `app.template_apply_log`（template_id, asset_id, workspace_id, user_id, created_at）
  - 新建 `V20__template.sql`
  - 8 个种子 SQL（`data/templates/01-08.json`）
- **新增后端**：
  - `domain/entity/Template.java` / `TemplateVersion.java` / `TemplateApplyLog.java`
  - `service/TemplateService.java` —— 变量替换、版本管理、复制、统计
  - `controller/v1/TemplateController.java`
  - `data/templates/seed.json` —— 8 个官方模板
- **修改后端**：
  - `controller/v1/AnalysisController` `body` 和 TitlesController 接受 `templateId`
- **API 端点**：
  - `GET /v1/templates?scope=official|workspace|personal`
  - `POST /v1/templates` / `PATCH /v1/templates/{id}` / `POST /v1/templates/{id}/publish` / `POST /v1/templates/{id}/archive`
  - `POST /v1/templates/{id}/duplicate` —— 复制到工作区
  - `GET /v1/templates/{id}/apply-stats`
  - `POST /v1/templates/{id}/render` —— 预览
- **新增前端**：
  - `views/template/TemplateLibraryView.vue`
  - `views/template/TemplateEditorView.vue`
- **单测**：变量替换（`{{platform}}` 缺失报错）；scope 权限；版本号自增
- **集成测试**：OFFICIAL 不可删只可复制；apply 一次产生 apply_log
- **验收**：
  - [ ] 官方模板不可删
  - [ ] 模板作者能看到自己模板被采用次数
  - [ ] 渲染结果可保存为 ContentAsset，关联可追溯
  - [ ] 8 个官方种子在初始化时插入
- **工作量**：7-10 人·天
- **风险**：模板变量缺失时的回退提示；版本回退兼容性

---

## P0-5 导出（MD / DOCX / ZIP / CSV）

- **依赖前置**：P0-3
- **目标**：多格式导出，签名 URL 24h 过期，限流 50/天/workspace
- **数据库变更**：
  - 新建 `app.export_log`（id, workspace_id, user_id, asset_id, format, status, object_key, expires_at, created_at）
  - 新建 `V21__export_log.sql`
- **新增后端**：
  - `service/ExportService.java`
  - `service/export/MarkdownRenderer.java` —— frontmatter + 图片 base64
  - `service/export/DocxGenerator.java` —— poi-ooxml
  - `service/export/ZipPacker.java` —— md + 原图 + report.json
  - `service/export/CsvExporter.java` —— 批量 asset 列表
  - `controller/v1/ExportController.java`
- **API 端点**：
  - `GET /v1/assets/{id}/export?format=md|docx|zip` —— 异步生成
  - `GET /v1/workspaces/{id}/export.csv` —— CSV
  - `GET /v1/exports/{id}` —— 查导出状态
  - `GET /v1/exports/{id}/download` —— 签名 URL 重定向
- **单测**：Markdown 包含 frontmatter；DOCX 段落 + 封面图；CSV 列对齐；签名 URL 24h 后失效
- **集成测试**：>1000 行正文能完整导出；超过 50/天 拒绝
- **验收**：
  - [ ] DOCX 用 Word 打开排版正确，封面图清晰
  - [ ] 24h 后签名 URL 失效返回 410
  - [ ] 单 workspace 每天最多 50 次
  - [ ] 大于 1000 行正文能完整导出
- **工作量**：3-5 人·天
- **风险**：poi-ooxml 中文 / emoji；MinIO 签名 URL 跨域

---

## P0-6 审核流 Review

- **依赖前置**：P0-3（asset 状态机）
- **目标**：Reviewer 流；高风险自动 require review
- **数据库变更**：
  - 新建 `app.review_task`（id, asset_id, version_no, reviewer_user_id, status=pending/approved/rejected, comment, decided_at）
  - 新建 `app.review_comment`（id, review_task_id, comment, highlighted_text, created_by, created_at）
  - 新建 `V22__review.sql`
- **新增后端**：
  - `domain/entity/ReviewTask.java` / `ReviewComment.java`
  - `service/ReviewService.java`
  - `controller/v1/ReviewController.java`
- **修改后端**：
  - `controller/v1/ComplianceController` 高风险分自动 create ReviewTask
  - `service/ContentAssetService.submitReview()` 状态机迁移
- **API 端点**：
  - `GET /v1/reviews/queue?status=pending`
  - `POST /v1/reviews/{assetId}/submit` —— Editor 提交
  - `POST /v1/reviews/{taskId}/approve` / `POST /v1/reviews/{taskId}/reject`
  - `POST /v1/reviews/{taskId}/comments`
- **新增前端**：
  - `views/review/ReviewQueueView.vue`
  - `views/review/ReviewDetailView.vue`
- **单测**：状态机迁移；高风险触发；非 Reviewer 角色拒绝
- **集成测试**：高风险不能直接发布；Editor 看到退回原因
- **验收**：
  - [ ] 高风险内容不能被 Editor 直接发布
  - [ ] 退回时附 remark，Editor 必须看到
  - [ ] 审计日志记录每一步
  - [ ] Reviewer/Admin 角色限制
- **工作量**：7-10 人·天
- **风险**：高风险判定规则的版本化；多 Reviewer 并发决定

---

## P0-7 CI / 集成测试 / 镜像 / 迁移 / 备份

- **依赖前置**：无（可与 P0-8 并行）
- **目标**：完整生产工程能力
- **新增**：
  - `.github/workflows/ci.yml` —— format + tsc + mvn test + docker build
  - `.github/workflows/cd.yml` —— 镜像推送 + 部署
  - `Dockerfile` —— 多阶段 builder→runtime
  - `docker-compose.prod.yml` —— app + worker + pg + redis + minio
  - `infra/worker/Dockerfile` —— 独立消费进程
  - `infra/backup/backup.sh` —— pg_dump + minio sync
  - `infra/backup/restore.sh` —— 恢复脚本
  - `infra/backup/README.md`
- **修改**：
  - `pom.xml` —— 加 testcontainers + rest-assured
  - `src/test/java/.../IntegrationTestBase.java` —— testcontainers 公共基类
  - `XhsAgentYunyingApplicationTests.java` —— 改为 testcontainers
- **验收**：
  - [ ] 干净环境 `docker compose -f docker-compose.prod.yml up` 启动完整服务
  - [ ] `mvn test` 不依赖本机 DB
  - [ ] `git push` 触发 CI 全绿
  - [ ] restore.sh 在预发布演练成功
- **工作量**：5-7 人·天
- **风险**：Testcontainers 在 CI 资源；多阶段镜像大小；备份恢复数据一致性

---

## P0-8 安全加固

- **依赖前置**：无（可与 P0-7 并行）
- **目标**：上传安全 / SSRF / 限流 / 脱敏 / 失败锁定 / API Key
- **新增**：
  - `infra/upload/FileValidator.java` —— 魔数 + 大小 + 分辨率
  - `infra/upload/MimeSniffer.java` —— file command 检测真实类型
  - `infra/upload/VirusScanner.java` —— ClamAV 可选
  - `infra/security/UrlPolicyGuard.java` —— IP 黑名单 + redirect 限制
  - `infra/ratelimit/RedisRateLimiter.java` —— Lua 脚本
  - `infra/logging/SensitiveMaskingConverter.java` —— logback PatternLayout
  - `auth/ApiKeyService.java` + `V23__api_key.sql`
  - `domain/entity/ApiKey.java`
- **修改**：
  - `service/CoverStorageService` —— 接入 FileValidator
  - `ai/agent/web/UrlFetchService` —— 接入 UrlPolicyGuard
  - `auth/AuthService` —— 失败锁定 5/15min
  - `controller/v1/AuthController` —— 限流 5/min/IP
  - 所有 Controller 加 401 锁账号提示
- **API 端点**：
  - `POST /v1/workspaces/{id}/api-keys` / `GET /v1/workspaces/{id}/api-keys` / `DELETE /v1/workspaces/{id}/api-keys/{id}`
  - Header `X-API-Key: xhs_sk_xxx` 替代 JWT 给 MCP/CLI
- **单测**：上传 .jpg/.exe 拒绝；SSRF 169.254 拒绝；登录 5 次锁 15min；日志脱敏
- **集成测试**：API Key 调任何接口都校验 workspace
- **验收**：
  - [ ] .jpg 实际是 .exe 被拒
  - [ ] 6 次密码错后第 6 次 423 Locked
  - [ ] 日志无 Authorization
  - [ ] xhs_sk_xxx 工作区隔离
- **工作量**：5-7 人·天
- **风险**：ClamAV 在容器内性能；Lua 脚本原子性

---

## P0-9 数据保留 + 租户销毁

- **依赖前置**：P0-3、P0-4、P0-5
- **目标**：合规销户 + 工作区销毁 + 个人数据导出
- **数据库变更**：
  - `app.user` 加 `scheduled_deletion_at` / `deletion_status`
  - 新建 `app.workspace_destruction`（workspace_id, requested_by, requested_at, confirm_phrase, status=pending/confirmed/cancelled, execute_at）
  - 新建 `app.data_retention_policy`（workspace_id, asset_retention_days, log_retention_days, media_retention_days, updated_at）
  - 新建 `V24__retention.sql`
- **新增**：
  - `service/AccountDeletionService.java` —— 30 天软删
  - `service/WorkspaceDestructionService.java` —— 7 天宽限
  - `service/PersonalDataExportService.java` —— zip 打包
  - `infra/scheduler/RetentionSweeper.java` —— 定时清理（worker 进程）
  - `controller/v1/admin/AdminRetentionController.java`
- **修改**：
  - `auth/AuthService.deleteMyAccount()` —— 30 天流程
  - `workspace/WorkspaceService.destroy()` —— 删全部数据 + minio 对象 + 搜索索引
- **API 端点**：
  - `DELETE /v1/auth/me?confirm=<phrase>` —— 30 天后删
  - `POST /v1/auth/me/cancel-deletion`
  - `POST /v1/workspaces/{id}/destroy?confirm=DELETE`
  - `POST /v1/workspaces/{id}/cancel-destroy`
  - `GET /v1/auth/me/export?format=zip`
  - `GET /v1/workspaces/{id}/retention` / `PUT /v1/workspaces/{id}/retention`
- **单测**：30 天后物理删；workspace 销毁后 404；导出 zip 完整
- **集成测试**：MinIO 对象清理；pgvector 索引清理
- **验收**：
  - [ ] 销户 30 天后物理删
  - [ ] 销毁工作区后接口 404
  - [ ] 导出 zip 含全部数据
  - [ ] 定时任务每日执行
- **工作量**：3-5 人·天
- **风险**：MinIO 物理删除不可逆；搜索索引清理时序

---

## 总体里程碑

| 阶段 | 任务 | 工期 | 累计 |
|---|---|---|---|
| A | P0-7 + P0-8 并行 | 7d | 7d |
| B | P0-1 | 6d | 13d |
| C | P0-2 | 4d | 17d |
| D | P0-3 | 12d | 29d |
| E | P0-4 + P0-5 + P0-6 并行 | 10d | 39d |
| F | P0-9 | 4d | 43d |

**串行总工期：约 43 人·天**
**2 人并行：约 22 工作日**
**3 人并行（含前端专职）：约 16 工作日**

## 出口条件（阶段 1 完成判定）

- [ ] 所有 9 个 P0 验收清单全绿
- [ ] `mvn test` 100% 通过（testcontainers）
- [ ] CI 流水线全绿
- [ ] 干净环境 `docker compose up` 一键启动
- [ ] 安全测试 + 集成测试报告归档
- [ ] 备份恢复演练一次成功
- [ ] 公网内测部署成功
