# 阶段4 验收基线与开发计划（商业化和规模化）

> 编制依据：`COMMERCIAL_SAAS_READINESS_PLAN.md` §9「阶段 4」、§4.10「团队、计费和商业化」、§4.11「导出、开放接口和数据治理」、§10「优先级清单」、§11「发布门禁清单」。
> 模板对齐：`STAGE2_ACCEPTANCE.md` / `STAGE3_ACCEPTANCE.md`（功能点 + 接口契约 + 数据模型 + 出口条件 checklist）。
> 上游基线：阶段 0 产品冻结 / 阶段 1 商业 SaaS 基础 / 阶段 2 运营创作闭环 / 阶段 3 真实数据闭环（13/13 子任务已落地，E1–E3 PASS）。

---

## 阶段4 文档原文（§9 摘录）

> 目标：可收费、可扩容、可持续运营。
>
> 1. 订阅、套餐、按量计费、退款、发票、成本和毛利。
> 2. 管理后台、客服工具、内容举报、数据删除和租户销毁。
> 3. 压测、容量规划、灾备演练、多区域或多可用区部署。
> 4. 开放 API、Webhook、合作伙伴连接器和开发者文档。

**出口条件**：连续 30 天无 P0 事故，账单和额度对账一致，恢复演练成功。

---

## 阶段4 不在范围（留给阶段 5+）

- 更多官方平台连接器与跨平台统一排期（P2）
- 内容效果自动实验 / 智能预算分配（P2，阶段 4 只留数据字段与接口位）
- 行业基准库、账号 benchmarking、团队绩效、客户报表（P2）
- 合作伙伴市场（Marketplace）、Agent 工作流编排器（P2）
- 企业级 SSO/SAML、私有化部署、合规审计导出（GDPR / 个保法）→ 阶段 5
- 多品类 Prompt / 规则包 / 知识库的全量扩包（属阶段 1–3 遗留补齐，非阶段 4 主线）

---

## 现状盘点（开发前必读）

### 已具备可直接复用

| 资产 | 路径 | 阶段 4 复用点 |
|---|---|---|
| 额度原子扣减 | `quota/AtomicQuotaGuard` | 扣费主通道，直接升级为计费流水 |
| 用量账本 | `app.usage_ledger` + `UsageLedger` | **计费流水底表**，字段已含 `workspace_id / user_id / action / delta / status / reason / createdAt` |
| 额度实体 | `app.workspace_quota` + `WorkspaceQuota` | 需加 `plan_id` / 周期语义 |
| 计量单位 | `freeze/BillableUnit`（8 个）+ `freeze/BillableEvent` | 计价单位映射表直接挂到套餐 |
| 计费规则 | `Xhs-Backend/docs/BILLING-RULES.md` | DEBIT/REFUND/WRITE_OFF/ADJUSTMENT 语义冻结，不可改 |
| API Key | `app.api_key` + `ApiKey` + `WorkspaceApiKeyController` + `ApiKeyService` + `ApiKeyAuthenticationFilter` | 开放平台鉴权底座（**但前端无管理页**） |
| 平台管理后台 | `PlatformAdminController` + `PlatformAdminService`（`app.platform_admin`） | 商业化后台的鉴权底座与页面宿主 |
| 审计 | `app.audit_event` | 订阅/退款/封禁等敏感操作留痕 |
| 任务队列 | Redis Streams + DLQ + 幂等键 | 支付回调、对账、发票开具走持久化队列 |
| 数据保留/销毁 | `RetentionSweeper` / `WorkspaceDestructionService` / `AccountDeletionService` | 阶段 4 §4.11「数据删除和租户销毁」已部分覆盖 |
| 导出引擎 | MD / DOCX / ZIP / CSV | 账单 / 发票 / 对账单导出复用 |
| 限流 | `RedisRateLimiter` + `@RateLimited` | 开放 API 限流底座 |
| 监控告警 | `BusinessMetrics` + `infra/observability/prometheus-alerts.yml` | 计费/支付可观测性 |
| 备份恢复 | `infra/backup/{backup.sh,restore.sh}` + `backup-scheduled.yml` | 灾备演练脚本（**尚未真实演练**） |
| CI/CD | `.github/workflows/{ci,cd,backup-scheduled}.yml` | 阶段 4 CI 扩测点 |

### 缺失（阶段 4 必须新建）

| 领域 | 现状 | 缺口 |
|---|---|---|
| 订阅/套餐 | 仅有 `workspace_quota.daily_quota` 人工设置 | 无套餐定义、无订阅实体、无订阅状态机、无续费/到期降级 |
| 支付 | 无 | 无支付网关接入、无回调验签、无订单、无对账 |
| 计价 | `BillableUnit` 只有名称，**无单价** | 无价格表、无成本核算、无毛利 |
| 退款 | 仅 `QuotaRefundService` 内部额度冲正 | 无对外退款流程、无退款审批、无发票红冲 |
| 发票 | 无 | 无发票开具、无 PDF、无下载 |
| 后台商业化页 | `PlatformAdminController` 只有概览/用户/工作区/审计/DLQ | 无套餐管理、无订单、无成本毛利、无投诉举报、无客服工具 |
| 开放平台 | `app.api_key` 后端可用 | **无前端自助页**、无出站 Webhook、无开发者门户、无接口文档（`springdoc` 未引入） |
| 容量与高可用 | 单实例编排 `docker-compose.prod.yml` | 无压测脚本、无容量规划、多区域/多可用区、恢复演练未做 |
| 通知 | 无 | 订阅到期/续费成功/退款/发票等事件无邮件/Webhook 送达能力 |

### 阶段 1–3 遗留（阶段 4 开工前建议先清，量级小、风险低）

| 编号 | 遗留项 | 位置 | 影响 |
|---|---|---|---|
| L1 | CSV 导入资产/选题**只计数不落库** | `CsvImportService.importAssets()` / `importTopics()`（两处 `// TODO: delegate to ...`） | 手动导入路径实际不可用，直接影响「手动导入作为保底路径」承诺 |
| L2 | 媒体下载**无签名 URL** | `MediaStorageService` 直接返回 `baseUrl + objectKey` | 私有桶语义被绕过，与阶段 1「5 分钟签名 URL」验收不符 |
| L3 | 浏览器辅助**无真实浏览器驱动** | `BrowserAssistService.fillSession()` 只改状态位（无 Playwright/Selenium 依赖） | 阶段 2/3 的 BROWSER_ASSIST 降级路径不可真实执行 |
| L4 | 平台账号**无凭据加密与 OAuth 流** | `PlatformAccountService` 仅 `credential_ref` 引用校验 | 阶段 4 开放平台接入真实平台时必须补 |
| L5 | 视频转码/压缩/字幕未实现 | `MediaStorageService` 仅 ImageIO 图片缩略图 | 媒体中心能力不完整 |
| L6 | 前端缺 9 个后端已就绪的页面 | 平台账号 / 浏览器辅助 / 媒体中心 / 选题池 / 评论生成 / 合规规则 / 资产预览 / 平台后台 / API Key | 阶段 1–3 的后端能力用户触达不到 |
| L7 | 阶段 3 的 7 个新页**未做浏览器端联调** | `views/{publish,metrics,retrospective,hotTopic,competitor,kb,connector}/` | 仅 `vue-tsc` 通过，运行时行为未验证 |
| L8 | `app.api_key.workspace_id` 为 VARCHAR(64) | `V23__api_key.sql` | 与全局 `workspace_id VARCHAR(32)` 归一规则不一致，需 V61 修正 |
| L9 | 无 OpenAPI/Swagger | `pom.xml` 无 `springdoc` | 开放平台无接口文档，阶段 4 阻塞项 |
| L10 | 性能指标未压测 | 无 JMeter/K6 脚本 | §8.2 的 P95 指标无实测证据 |

---

## 子能力 1：套餐、订阅与试用

### 1.1 套餐定义

**功能**：
- 套餐实体：名称、编码、版本、周期（月/季/年）、价格、币种、包含额度、超额单价、功能开关、状态（draft/active/archived）
- 套餐与角色/功能绑定：不同套餐开放不同功能（如竞品库、指标看板、开放 API）
- 套餐快照：订阅生效时冻结一份套餐内容，防止后续改价影响存量订阅
- 套餐列表给用户看（公开只读），管理端可 CRUD

**待验收**：
- `app.subscription_plan` 表落地，`code` + `version` 唯一
- 至少预置 3 个套餐（FREE / PRO / TEAM）与 1 个按量计费套餐（USAGE）
- 套餐变更生成新版本，历史订阅仍读旧快照
- 下架套餐不影响已订阅工作区
- 套餐接口全部按 workspace 隔离
- 管理端改价需记录审计

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 幂等/限流 |
|---|---|---|---|
| GET | `/api/v1/plans` | 全部（读公开套餐） | — |
| GET | `/api/v1/plans/{code}` | 全部（读） | — |
| GET | `/api/v1/platform/admin/plans` | PLATFORM_ADMIN | 分页 |
| POST | `/api/v1/platform/admin/plans` | PLATFORM_ADMIN | 幂等键 |
| PATCH | `/api/v1/platform/admin/plans/{id}` | PLATFORM_ADMIN | 幂等键 |
| POST | `/api/v1/platform/admin/plans/{id}/archive` | PLATFORM_ADMIN | 幂等键 |

### 1.2 订阅生命周期

**功能**：
- 订阅实体：workspace 维度、套餐版本、状态（trialing/active/past_due/canceled/expired/suspended）、周期起止、续费时间、试用截止、取消时间
- 状态机：`trialing → active → past_due → (active | canceled)`；到期未续费 → `expired`
- 升降级：升级立即生效并按比例补差价；降级到周期末生效
- 试用：首次订阅某套餐可赠 N 天试用，试用期内用量受限
- 到期降级：到期未支付自动降级到 FREE 套餐，**不删除数据**，只降功能与额度
- 订阅绑定额度：订阅生效时把套餐包含额度写入 `app.workspace_quota`（周期额度而非日额度）

**待验收**：
- `app.subscription` 表落地，同一 workspace 同一时间只允许一条 `active/trialing/past_due` 订阅（部分唯一索引）
- 订阅创建/续费/取消/降级全部写 `app.audit_event`
- 到期降级由定时任务执行，降级动作幂等（重复执行不重复写额度）
- `past_due` 宽限期可配置（默认 7 天），宽限期内功能不降级
- 降级不删除工作区数据，只收窄能力
- 订阅查询按 workspace 隔离，跨 workspace 返回 404

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 幂等/限流 |
|---|---|---|---|
| GET | `/api/v1/workspaces/{workspaceId}/subscription` | OWNER/ADMIN（读全量）；全部（读当前可用能力） | — |
| POST | `/api/v1/workspaces/{workspaceId}/subscription` | OWNER | 幂等键；5 次/天/工作区 |
| POST | `/api/v1/workspaces/{workspaceId}/subscription/cancel` | OWNER | 幂等键 |
| POST | `/api/v1/workspaces/{workspaceId}/subscription/change-plan` | OWNER | 幂等键；3 次/天/工作区 |
| POST | `/api/v1/workspaces/{workspaceId}/subscription/trial` | OWNER | 幂等键；1 次/工作区 |
| GET | `/api/v1/platform/admin/subscriptions` | PLATFORM_ADMIN | 分页 + 按状态筛选 |
| PATCH | `/api/v1/platform/admin/subscriptions/{id}` | PLATFORM_ADMIN | 幂等键（补偿/延期） |

### 1.3 订阅到期定时任务

**功能**：
- `SubscriptionExpiryScheduler`：扫描 `trialing/active` 中 `current_period_end < now` 的订阅
- 按 `auto_renew` 决定发起续费还是标记到期
- 宽限期内 → `past_due`；宽限期满 → `expired` + 降级 + 通知
- 任务必须走持久化队列（不是 `@Async`），可重试、可进 DLQ

**待验收**：
- 定时任务幂等：同一订阅重复扫描不重复降级
- 降级后 `workspace_quota` 与套餐额度一致
- 降级事件写审计 + 触发通知（邮件/Webhook）

---

## 子能力 2：支付网关与订单

### 2.1 支付抽象

**功能**：
- `PaymentProvider` 统一接口：`createPayment` / `queryPayment` / `refund` / `verifyCallback`
- 实现：`StripePaymentProvider`、`AlipayPaymentProvider`、`WechatPayPaymentProvider`
- 支付配置走密钥管理系统/安全环境变量，**不落库明文**（沿用阶段 1 密钥规范）
- 渠道不可用时返回可读错误，不静默失败

**待验收**：
- 统一接口覆盖下单 / 查询 / 退款 / 回调验签四类动作
- 渠道配置缺失时启动不失败，但调用返回 `PAYMENT_PROVIDER_NOT_CONFIGURED` 可读错误
- 凭据不进日志、不进异常消息、不进 `raw_response`
- 渠道超时/失败有重试与退避策略

### 2.2 订单与支付

**功能**：
- 订单实体：订单号、工作区、套餐版本、数量、金额、币种、支付渠道、状态、支付时间、过期时间
- 订单状态机：`pending → paid → (fulfilled | refunded | closed)`；`pending` 超时 → `closed`
- 回调验签：签名校验 + 时间戳窗口 + 重放保护（nonce/事件 ID 去重）
- 幂等：同一渠道事件 ID 只处理一次
- 履约：支付成功后开订阅 + 写额度（走任务队列，失败进 DLQ 可重试）

**待验收**：
- `app.subscription_order` 表落地，订单号全局唯一
- 回调验签失败一律拒绝并记审计，**不得凭订单号直接改状态**
- 同一渠道事件重复投递 → 只入账一次（重复扣费率为 0）
- 支付成功但履约失败 → 订单仍为 `paid`，履约进队列重试，不丢单
- 金额校验：回调金额必须等于订单金额，否则拒付并告警
- 订单列表按 workspace 隔离

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 幂等/限流 |
|---|---|---|---|
| POST | `/api/v1/workspaces/{workspaceId}/orders` | OWNER | 幂等键；10 次/天/工作区 |
| GET | `/api/v1/workspaces/{workspaceId}/orders` | OWNER/ADMIN | 分页 |
| GET | `/api/v1/orders/{orderNo}` | OWNER/ADMIN（读）；支付渠道（回调，验签） | — |
| POST | `/api/v1/payments/callback/{provider}` | 渠道回调（验签 + 重放保护） | 事件 ID 去重 |
| POST | `/api/v1/platform/admin/orders/{id}/refund` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/orders` | PLATFORM_ADMIN | 分页 + 状态筛选 |

### 2.3 对账任务

**功能**：
- 每日定时对账：本地订单 vs 渠道账单
- 差异分类：本地已付渠道未付 / 本地未付渠道已付 / 金额不一致
- 差异写 `app.billing_reconciliation` 并告警，支持人工处理与重跑

**待验收**：
- 对账任务走持久化队列，失败进 DLQ
- 差异记录可人工标记处理状态
- 对账结果在管理后台可见

---

## 子能力 3：按量计费、余额、成本与毛利

### 3.1 计价与价格表

**功能**：
- 计量单位 → 单价映射：`BillableUnit`（8 个）× 套餐/地区 → 单价
- 单价版本化，改价不追溯历史账单
- 成本侧：模型调用成本（token / 调用次数）、存储成本、第三方连接器成本
- 毛利 = 收入 − 成本，按工作区 / 套餐 / 时间段聚合

**待验收**：
- `app.price_book` 表落地，`unit + region + plan_version` 唯一
- `usage_ledger` 关联价格快照，账单金额可复算
- 毛利按工作区可查，误差可解释
- 单价变更需生成新版本，历史账单金额不变

### 3.2 余额与充值

**功能**：
- 余额实体（按量计费套餐）：余额、冻结、消费记录
- 充值 → 订单 → 入账
- 余额不足时按超额策略：拒绝 / 转按量扣款 / 降级功能

**待验收**：
- 余额扣减原子（并发下不出现负余额）
- 余额变动全部写流水，可追溯
- 余额不足错误可读，提示充值

### 3.3 用量 → 账单转化

**功能**：
- 把 `usage_ledger` 按周期聚合为账单行（工作区 × 计量单位）
- 账单实体：周期、行项、数量、单价、金额、状态
- 账单生成走任务队列，幂等（同周期不重复生成）

**待验收**：
- `app.billing_statement` + `app.billing_statement_item` 落地
- 账单行可回溯到原始 `usage_ledger` 记录
- 同周期重复生成不产生重复账单
- 未支付账单不重复扣费

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 幂等/限流 |
|---|---|---|---|
| GET | `/api/v1/workspaces/{workspaceId}/billing/statements` | OWNER/ADMIN | 分页 |
| GET | `/api/v1/workspaces/{workspaceId}/billing/statements/{id}` | OWNER/ADMIN | — |
| GET | `/api/v1/workspaces/{workspaceId}/billing/cost-usage` | OWNER/ADMIN | 按时间段聚合 |
| GET | `/api/v1/platform/admin/billing/cost-margin` | PLATFORM_ADMIN | 按工作区/套餐/时间段聚合 |
| GET | `/api/v1/platform/admin/billing/reconciliation` | PLATFORM_ADMIN | 分页 |

---

## 子能力 4：退款与发票

### 4.1 退款

**功能**：
- 退款实体：退款单号、订单、金额、原因、状态、发起人、审核人、外部退款单号
- 流程：用户申请 → 管理员审核 → 调用渠道退款 → 回调确认 → 同步调整额度/余额
- 额度冲正复用 `QuotaRefundService`，不重复实现扣费逻辑

**待验收**：
- `app.refund_order` 表落地，退款单号唯一
- 退款金额不超过订单实付金额
- 退款幂等：同一渠道退款事件重复投递只生效一次
- 退款成功后额度/余额同步调整，且有对应 `usage_ledger` REFUND 记录
- 退款动作写审计

### 4.2 发票

**功能**：
- 发票实体：发票号、抬头、税号、类型（个人/企业）、金额、状态、PDF 路径、关联订单/账单
- 开票：管理员对已支付订单/账单开票，生成 PDF（可先用 HTML→PDF 或简单模板）
- 红冲：已开票订单退款需红冲

**待验收**：
- `app.invoice` 表落地，发票号唯一
- 只能对已支付（且未退款）订单开票
- PDF 可下载，下载链接签名且过期
- 红冲有对应记录，不物理删除原发票
- 开票/红冲写审计

**接口契约（新增）**：

| 方法 | 路径 | 权限 | 幂等/限流 |
|---|---|---|---|
| POST | `/api/v1/workspaces/{workspaceId}/refunds` | OWNER | 幂等键；3 次/天/工作区 |
| GET | `/api/v1/workspaces/{workspaceId}/refunds` | OWNER/ADMIN | 分页 |
| POST | `/api/v1/platform/admin/refunds/{id}/approve` | PLATFORM_ADMIN | 幂等键 |
| POST | `/api/v1/platform/admin/refunds/{id}/reject` | PLATFORM_ADMIN | 幂等键 |
| POST | `/api/v1/platform/admin/invoices` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/invoices` | PLATFORM_ADMIN | 分页 |
| GET | `/api/v1/workspaces/{workspaceId}/invoices` | OWNER/ADMIN | 分页 |
| GET | `/api/v1/workspaces/{workspaceId}/invoices/{id}/download` | OWNER/ADMIN | 签名 URL，5 分钟过期 |

---

## 子能力 5：管理后台商业化页与客服工具

### 5.1 后台页面扩展

**功能**（在 `PlatformAdminController` 基础上扩展，保留现有概览/用户/工作区/审计/DLQ）：
- 套餐管理页：CRUD + 版本 + 上下架
- 订单页：按状态/渠道/时间段筛选 + 详情 + 手动补单
- 订阅页：按状态/套餐筛选 + 延期/补偿/封禁
- 成本毛利看板：按工作区/套餐/时间段看收入、成本、毛利
- 对账页：差异列表 + 处理 + 重跑
- 退款审批页
- 发票管理页
- 投诉与内容举报处理

**待验收**：
- 所有后台接口走 `requirePlatformAdmin()`，非管理员 403
- 后台页面有分页、筛选、空态、错误态
- 敏感操作（退款、封禁、改价）二次确认 + 审计

### 5.2 投诉与内容举报

**功能**：
- 举报实体：举报人、目标类型（内容/评论/资产）、目标 ID、原因分类、描述、状态、处理人、处理结论、处理时间
- 平台侧受理入口（用户可在内容上举报）
- 处置动作：隐藏内容、警告用户、封禁工作区

**待验收**：
- `app.content_report` 表落地
- 举报可由工作区成员提交，平台侧受理
- 处置动作可回溯（谁、何时、结论）
- 封禁工作区有上限工作区的影响范围提示

### 5.3 客服工具

**功能**：
- 按用户/工作区/订单号检索
- 一键查看：该用户的订阅、订单、用量、任务失败、审计轨迹
- 手动调整额度 / 补偿（`ADJUSTMENT` 事件，必须带 reason）

**待验收**：
- 补偿走 `BillableEvent.ADJUSTMENT`，带 reason，进 `usage_ledger`
- 客服查询只读，不改业务数据
- 客服操作全部审计

### 5.4 数据删除与租户销毁（阶段 4 §4.10 明确项）

**现状**：阶段 1 已有 `WorkspaceDestructionService` / `AccountDeletionService` / `RetentionSweeper`。
**阶段 4 需补**：
- 订阅/订单/账单/发票等商业化数据的保留策略与销毁顺序
- 销毁时余额未消费、发票已开、未退款订单的处理规则
- 订阅销毁后是否自动取消续费（必须取消，避免继续扣款）

**待验收**：
- 销毁前检查未完成订单并给出提示
- 已开票订单销毁时保留发票（法务留档）
- 销毁后取消自动续费，渠道侧不再扣款
- 销毁动作写审计且不可逆

---

## 子能力 6：开放平台（开放 API / Webhook / 开发者门户）

### 6.1 API Key 自助管理

**功能**：
- 前端 API Key 管理页：创建（明文仅展示一次）、列表（脱敏）、撤销、最后使用时间
- Scope 分级：`read` / `write` / `admin`，细粒到业务域（如 `analysis:read`）
- 调用配额：按 Key 限流（复用 `RedisRateLimiter`）
- Key 泄露自检：列表页提示只在创建时展示

**待验收**：
- 前端页面可完成创建 → 复制 → 撤销全流程
- 明文 key 只在创建响应出现一次，DB 只存 SHA-256
- 撤销后立即失效
- Key 调用按 scope 校验，越权返回 403
- `app.api_key.workspace_id` 宽度修正为 VARCHAR(32)（见 L8）

### 6.2 出站 Webhook

**功能**：
- Webhook 订阅实体：workspace、事件类型集合、目标 URL、密钥、状态、失败计数、下次重试时间
- 事件类型：`subscription.created` / `subscription.expired` / `payment.succeeded` / `payment.failed` / `refund.succeeded` / `invoice.issued` / `asset.published` / `retrospective.ready`
- 投递：HMAC 签名（带时间戳）、超时控制、指数退避重试、死信
- 签名校验 + 重放保护（时间戳窗口 + 事件 ID）

**待验收**：
- `app.webhook_subscription` + `app.webhook_delivery` 表落地
- 投递必须签名，接收方可验签；签名密钥不落日志
- 失败重试有上限（默认 5 次），超限进死信并可人工重投
- 投递记录含响应码、耗时、错误原因
- 订阅 URL 需做 SSRF 校验（内网地址拒绝），沿用阶段 1 URL 安全规范
- 平台侧事件不得携带敏感内容原文

### 6.3 开发者门户与接口文档

**功能**：
- 引入 `springdoc-openapi`，生成 OpenAPI 3 文档
- 开发者门户页：快速开始、认证说明、端点参考、示例代码、配额说明
- MCP/CLI 集成说明（后端已有 `McpServerConfiguration` / `McpAuthFilter`）

**待验收**：
- `/v3/api-docs` 可访问，描述与实际路由一致
- 开发者门户页至少覆盖认证、限流、错误码、分页、幂等五节
- 关键接口有请求/响应示例
- 文档与代码同源（不手写），避免漂移

### 6.4 合作伙伴连接器

**功能**：
- 第三方连接器接入位（沿用 `connector/` 抽象）
- 合作伙伴注册与审核流程（管理端）
- 合作伙伴连接器的配额与计费归集

**待验收**：
- 合作伙伴连接器注册进 `app.connector_provider` 且走既有授权门禁
- 未审核的合作伙伴不注册
- 合作伙伴调用的用量可归集到工作区

---

## 子能力 7：容量、压测与高可用

### 7.1 压测

**功能**：
- K6 或 JMeter 脚本：登录/分析/标题/正文/资产 CRUD/导出/发布排期/指标录入
- 场景：稳态、峰值、突发、长时间耐久
- 输出：P50/P95/P99、错误率、队列积压、数据库连接池、模型耗时

**待验收**：
- 压测脚本纳入 `scripts/` 且可一条命令复跑
- 覆盖 §8.2 指标：分析 P95 < 45s、生成 P95 < 30s
- 重复发布率 = 0、重复扣费率 = 0 在压测中复验
- 结果写入 `docs/` 并附时间戳与环境说明

### 7.2 容量规划

**功能**：
- 按当前用量推算 6/12/24 个月容量：连接数、存储、模型并发、队列吞吐
- 给出扩容阈值与触发点

**待验收**：
- 产出 `docs/CAPACITY-PLAN.md`
- 明确每项资源的扩容阈值与告警线
- 与 Prometheus 告警规则对齐

### 7.3 多区域 / 多可用区部署

**功能**：
- PostgreSQL 流复制 + 只读副本 / 故障切换
- Redis 哨兵或集群
- MinIO 多节点纠删码
- 应用层多实例 + 负载均衡 + 无状态化（会话/JWT 已是无状态，需确认 SSE 与任务队列在多实例下正确）
- 跨区域数据合规（`app.workspace.region` 已预留）

**待验收**：
- 部署编排产出（`docker-compose.prod.yml` 扩展或 k8s manifest）
- 多实例下任务不重复消费（Redis Stream 消费者组验证）
- 主库故障切换演练一次并留证据

### 7.4 灾备演练

**功能**：
- `infra/backup/backup.sh` + `restore.sh` 真实执行一次
- RPO / RTO 目标定义并实测
- 恢复后数据一致性校验（余额、订阅、账本对齐）

**待验收**：
- 演练有完整记录（时间、命令、耗时、校验结果）
- 恢复后 `usage_ledger` 与账单金额一致
- 恢复演练是阶段 4 出口条件的硬指标

---

## 子能力 8：通知与事件送达

**功能**：
- 通知实体：workspace、用户、事件类型、标题、正文、已读时间、送达渠道与状态
- 事件源：订阅到期/续费成功/支付失败/退款到账/发票开具/用量超阈值/连接器降级/发布失败
- 送达渠道：站内信（先做）、邮件、Outgoing Webhook（子能力 6.2）

**待验收**：
- 站内通知在 UI 可见并可标记已读
- 邮件通道可插拔（未配置时降级为站内信，不报错）
- 通知不泄漏敏感内容
- 通知失败不阻塞主流程（订阅变更等）

---

## 数据模型总表

> 标注：**新建** = 阶段 4 新增表；**修改** = 在既有表上加列/加索引；**复用** = 不改结构。

| 表名 | 归属 | 关键字段 | 说明 | 状态 |
|---|---|---|---|---|
| `app.subscription_plan` | 套餐 | id, code, version, name, billing_period, price, currency, included_units, overage_price, feature_flags JSONB, status | 套餐定义（多版本） | 新建 |
| `app.subscription` | 订阅 | id, workspace_id, plan_id, plan_version_snapshot JSONB, status, current_period_start, current_period_end, trial_end, cancel_at_period_end, auto_renew, grace_period_days, subscribed_at, canceled_at | 工作区订阅 | 新建 |
| `app.subscription_order` | 订单 | id, order_no, workspace_id, plan_id, quantity, amount, currency, provider, provider_order_id, status, paid_at, expires_at, idempotency_key | 支付订单 | 新建 |
| `app.refund_order` | 退款 | id, refund_no, order_id, amount, reason, status, provider_refund_id, requested_by, reviewed_by, reviewed_at, review_comment | 退款单 | 新建 |
| `app.price_book` | 计价 | id, unit, region, plan_version, unit_price, cost_per_unit, effective_from, version | 计量单位单价与成本 | 新建 |
| `app.billing_statement` | 账单 | id, workspace_id, period_start, period_end, total_amount, currency, status, generated_at, idempotency_key | 周期账单 | 新建 |
| `app.billing_statement_item` | 账单行 | id, statement_id, unit, quantity, unit_price, amount, ledger_ref | 账单行（可回溯 ledger） | 新建 |
| `app.invoice` | 发票 | id, invoice_no, workspace_id, order_id, statement_id, title, tax_no, type, amount, status, pdf_object_key, issued_at, voided_at | 发票 | 新建 |
| `app.billing_reconciliation` | 对账 | id, order_id, provider, local_status, provider_status, diff_type, amount_diff, status, resolved_by, resolved_at | 对账差异 | 新建 |
| `app.webhook_subscription` | Webhook | id, workspace_id, url, secret_hash, events JSONB, status, consecutive_failures, last_success_at, last_error | 出站 Webhook 订阅 | 新建 |
| `app.webhook_delivery` | Webhook 投递 | id, subscription_id, event_type, event_id, payload JSONB, status, attempt, response_code, response_body, duration_ms, next_retry_at, created_at | 投递记录与死信 | 新建 |
| `app.content_report` | 举报 | id, workspace_id, reporter_user_id, target_type, target_id, reason_code, description, status, handled_by, handle_conclusion, handled_at | 内容举报 | 新建 |
| `app.notification` | 通知 | id, workspace_id, user_id, event_type, title, body, channel, status, read_at, created_at | 站内通知 | 新建 |
| `app.workspace_quota` | 额度 | + plan_id, period_quota, period_start, period_end, overage_policy | 周期额度与超额策略 | 修改 |
| `app.usage_ledger` | 账本 | + unit_price, amount, price_version, statement_id, provider | 从「额度流水」升级为「计费流水」 | 修改 |
| `app.workspace` | 工作区 | + subscription_status, plan_code | 便于列表查询与鉴权 | 修改 |
| `app.api_key` | API Key | workspace_id 宽度 → VARCHAR(32) | 类型归一（L8） | 修改 |
| `app.platform_admin` | 平台管理员 | — | 复用 | 复用 |
| `app.audit_event` | 审计 | — | 复用；订阅/退款/封禁/改价全留痕 | 复用 |
| `app.idempotency_key` | 幂等 | — | 复用；订单/退款/开票幂等 | 复用 |
| `app.task_dlq` | 死信 | — | 复用；支付回调/对账/发票任务 | 复用 |
| `app.connector_provider` | 连接器 | + partner_code | 合作伙伴连接器 | 修改 |

---

## 接口契约汇总表

| 方法 | 路径 | 权限角色 | 幂等 / 限流 |
|---|---|---|---|
| GET | `/api/v1/plans` | 全部 | — |
| GET | `/api/v1/plans/{code}` | 全部 | — |
| GET | `/api/v1/workspaces/{workspaceId}/subscription` | OWNER/ADMIN | — |
| POST | `/api/v1/workspaces/{workspaceId}/subscription` | OWNER | 幂等键；5 次/天 |
| POST | `/api/v1/workspaces/{workspaceId}/subscription/cancel` | OWNER | 幂等键 |
| POST | `/api/v1/workspaces/{workspaceId}/subscription/change-plan` | OWNER | 幂等键；3 次/天 |
| POST | `/api/v1/workspaces/{workspaceId}/subscription/trial` | OWNER | 幂等键；1 次/工作区 |
| GET | `/api/v1/workspaces/{workspaceId}/orders` | OWNER/ADMIN | 分页 |
| POST | `/api/v1/workspaces/{workspaceId}/orders` | OWNER | 幂等键；10 次/天 |
| GET | `/api/v1/orders/{orderNo}` | OWNER/ADMIN | — |
| POST | `/api/v1/payments/callback/{provider}` | 渠道（验签 + 重放保护） | 事件 ID 去重 |
| GET | `/api/v1/workspaces/{workspaceId}/billing/statements` | OWNER/ADMIN | 分页 |
| GET | `/api/v1/workspaces/{workspaceId}/billing/cost-usage` | OWNER/ADMIN | 时间段聚合 |
| GET | `/api/v1/workspaces/{workspaceId}/refunds` | OWNER/ADMIN | 分页 |
| POST | `/api/v1/workspaces/{workspaceId}/refunds` | OWNER | 幂等键；3 次/天 |
| GET | `/api/v1/workspaces/{workspaceId}/invoices` | OWNER/ADMIN | 分页 |
| GET | `/api/v1/workspaces/{workspaceId}/invoices/{id}/download` | OWNER/ADMIN | 签名 URL 5 分钟 |
| POST | `/api/v1/workspaces/{workspaceId}/webhooks` | OWNER/ADMIN | 幂等键 |
| GET | `/api/v1/workspaces/{workspaceId}/webhooks` | OWNER/ADMIN | 分页 |
| POST | `/api/v1/workspaces/{workspaceId}/webhooks/{id}/test` | OWNER/ADMIN | 幂等键 |
| POST | `/api/v1/workspaces/{workspaceId}/webhooks/{id}/rotate-secret` | OWNER | 幂等键 |
| DELETE | `/api/v1/workspaces/{workspaceId}/webhooks/{id}` | OWNER/ADMIN | 幂等键 |
| GET | `/api/v1/workspaces/{workspaceId}/api-keys` | OWNER/ADMIN | — |
| POST | `/api/v1/workspaces/{workspaceId}/api-keys` | OWNER/ADMIN | 幂等键；10 次/天 |
| DELETE | `/api/v1/workspaces/{workspaceId}/api-keys/{id}` | OWNER/ADMIN | 幂等键 |
| POST | `/api/v1/reports` | ANALYST/EDITOR/ADMIN/OWNER | 幂等键 |
| GET | `/api/v1/notifications` | 全部 | 分页 |
| POST | `/api/v1/notifications/{id}/read` | 全部 | — |
| GET | `/api/v1/platform/admin/plans` | PLATFORM_ADMIN | 分页 |
| POST | `/api/v1/platform/admin/plans` | PLATFORM_ADMIN | 幂等键 |
| PATCH | `/api/v1/platform/admin/plans/{id}` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/subscriptions` | PLATFORM_ADMIN | 分页 |
| PATCH | `/api/v1/platform/admin/subscriptions/{id}` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/orders` | PLATFORM_ADMIN | 分页 |
| POST | `/api/v1/platform/admin/orders/{id}/refund` | PLATFORM_ADMIN | 幂等键 |
| POST | `/api/v1/platform/admin/refunds/{id}/approve` | PLATFORM_ADMIN | 幂等键 |
| POST | `/api/v1/platform/admin/refunds/{id}/reject` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/invoices` | PLATFORM_ADMIN | 分页 |
| POST | `/api/v1/platform/admin/invoices` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/billing/cost-margin` | PLATFORM_ADMIN | 聚合 |
| GET | `/api/v1/platform/admin/billing/reconciliation` | PLATFORM_ADMIN | 分页 |
| GET | `/api/v1/platform/admin/reports` | PLATFORM_ADMIN | 分页 |
| POST | `/api/v1/platform/admin/reports/{id}/handle` | PLATFORM_ADMIN | 幂等键 |
| GET | `/api/v1/platform/admin/customers/search` | PLATFORM_ADMIN | — |
| POST | `/api/v1/platform/admin/quotas/adjust` | PLATFORM_ADMIN | 幂等键；必须带 reason |

---

## 阶段4 整体验收（出口条件）

### 必走的端到端场景

1. **注册 → 创建工作区 → 选定套餐 → 试用 → 开通订阅**（含额度写入）
2. **下单 → 支付回调（验签）→ 履约 → 订阅生效 → 额度可用**
3. **用量发生 → ledger 记 DEBIT → 周期账单生成 → 金额可复算**
4. **支付成功但履约失败 → 进 DLQ → 人工重试 → 最终履约，不丢单**
5. **用户申请退款 → 管理员审核 → 渠道退款 → 额度冲正 → 账本可对**
6. **管理员开票 → PDF 下载 → 订单退款 → 红冲**
7. **到期未续费 → 自动降级 → 功能收窄但数据保留**
8. **创建 API Key → 用 Key 调开放 API → 越权被拒 → 撤销后立即失效**
9. **订阅事件 → Webhook 投递 → 验签通过 → 失败重试 → 死信可人工重投**
10. **投诉提交 → 平台受理 → 处置 → 审计可追溯**
11. **压测跑通 → P95 达标 → 重复发布率 0 / 重复扣费率 0**
12. **备份 + 恢复演练 → 数据一致 → 订阅/余额/账本对齐**

### 性能 / 可靠性指标

- 分析任务 P95 < 45s；标题/正文生成 P95 < 30s（压测实测）
- 月度可用性 ≥ 99.5%
- 核心分析任务成功率 ≥ 98%
- **发布任务重复发布率 = 0**
- **额度扣费重复率 = 0，失败任务自动冲正**
- 数据同步延迟、失败率、最后成功时间可见
- 支付回调重复投递不重复入账
- 账单与账本对账差异 = 0（差异需有处理记录）

### 发布门禁复核（§11 中与阶段 4 相关的项）

- [ ] 生产环境不使用默认 JWT / 数据库 / Redis / MinIO / CORS 配置
- [ ] 未设置 prod profile 时启动失败
- [ ] 所有核心接口通过租户 / 用户 / 对象归属测试
- [ ] 额度扣减、退款、重复请求、并发场景有集成测试
- [ ] 上传文件、URL 抓取、导出链接、平台凭据完成安全测试
- [ ] CI 在干净环境可构建、测试、打包、发布镜像
- [ ] 数据库迁移、备份恢复、回滚在预发布环境演练成功
- [ ] 计费、额度、失败任务的扣费/冲正结果可对账
- [ ] 监控、日志、告警、客服排障手册、事故响应联系人已配置
- [ ] **连续 30 天无 P0 事故**
- [ ] **账单和额度对账一致**
- [ ] **恢复演练成功**

---

## 任务拆解与建议派发方式

### 子任务清单

| ID | 名称 | 依赖 | 建议迁移版本 |
|---|---|---|---|
| S4-0 | 遗留项清理（L1–L3、L8） | 无 | V61 |
| S4-1 | 套餐与订阅（表 + 状态机 + 定时任务 + API） | S4-0 | V62 |
| S4-2 | 支付抽象与订单（含回调验签 / 重放保护） | S4-1 | V63 |
| S4-3 | 计价、价格表、余额、账单生成 | S4-1 | V64 |
| S4-4 | 退款与发票 | S4-2, S4-3 | V65 |
| S4-5 | 对账任务 | S4-2, S4-3 | V66 |
| S4-6 | 后台商业化页 + 举报 + 客服工具 | S4-1~S4-5 | V67 |
| S4-7 | 开放平台：API Key 自助页 + 出站 Webhook | S4-6（事件源） | V68 |
| S4-8 | 开发者门户 + OpenAPI 文档 | S4-7 | — |
| S4-9 | 通知与事件送达 | S4-7 | V69 |
| S4-10 | 压测脚本 + 容量规划 | 全链路 | — |
| S4-11 | 多区域部署 + 灾备演练 | S4-10 | — |

### 依赖顺序

```text
S4-0
 └─ S4-1（套餐订阅）
     ├─ S4-2（支付订单） ──┬─ S4-4（退款发票）
     │                    └─ S4-5（对账）
     └─ S4-3（计价账单） ──┘
              └─ S4-6（后台商业化页） ── S4-7（开放平台） ── S4-8（开发者门户）
                                          └─ S4-9（通知）
     └──────────────────── S4-10（压测） ── S4-11（高可用）
```

### 建议的并行派发

- **批次 A**：`S4-0` + `S4-1`（表结构与订阅状态机，文件不重叠）
- **批次 B**：`S4-2` + `S4-3`（支付与计价，迁移版本不同）
- **批次 C**：`S4-6` + `S4-7`（后台页与开放平台，前端目录分开）
- **批次 D**：`S4-10` + `S4-8`（压测与文档，不碰业务代码）

> 子代理**不要各自 `git commit`**，合并与提交由统筹者统一执行。

### 阶段4 沿用的工程决策

- **不写大量单测**（沿用阶段 2/3 决策），但**计费/支付/退款/并发扣费必须写集成测试**（§11 门禁明确要求「额度扣减、退款、重复请求和并发场景有集成测试」）
- 每个子任务结束只跑一次全量 `mvn -o -q compile`；前端只跑一次 `vue-tsc -b`
- 迁移必须本地实测执行（Flyway 成功 + 关键查询跑通），写完 Mapper 立即跑一次实际查询
- 涉及金额的字段统一用 `NUMERIC(18,4)`，禁止 `FLOAT`/`DOUBLE`
- 所有 `workspace_id` 一律 `VARCHAR(32)`，与 `app.workspace.id` 对齐（`app.api_key` 是当前唯一例外，需 V61 修正）
- jsonb / list / map 列必须挂 `TypeHandler` + `@TableName(autoResultMap = true)`
- 支付回调 / 退款 / 开票一律幂等键 + `app.idempotency_key`
- 凭据、密钥、卡号类信息不进日志、不进异常消息、不进 `raw_response`

---

## 风险与依赖

| ID | 风险 | 影响面 | 应对 |
|---|---|---|---|
| R1 | 支付渠道资质与商户号未落地 | S4-2/S4-4 无法真实联调 | 先实现统一抽象 + Mock 渠道 + 契约测试；真实渠道待商户号到位后切换，代码零改动 |
| R2 | 计费口径从「额度」升级为「金额」会牵动 `usage_ledger` | 阶段 1 全部 AI 接口 | **只加列不改语义**：原 `delta` 保持不变，新增 `amount` / `unit_price` 冗余字段，历史数据 `amount` 可为空 |
| R3 | 订阅状态机与额度写入的并发一致性 | 重复开通、额度错扣 | 订阅创建 + 额度写入同一事务 + 幂等键；额度写入用 `UPDATE ... WHERE` 原子语义 |
| R4 | 支付回调被伪造或重放 | 直接资金损失 | 签名校验 + 时间戳窗口 + 事件 ID 去重 + 金额比对 + 全量审计；**不得凭订单号改状态** |
| R5 | 多实例下任务重复消费 | 重复扣费 / 重复发布 | Redis Stream 消费者组天然分组；上线前用并发脚本验证「同订阅只扣一次」 |
| R6 | 账单与账本口径漂移 | 对账不上，出口条件不达成 | 账单行必须可回溯到 `usage_ledger` 明细；每日对账 + 差异告警；账单生成幂等 |
| R7 | 开放 API 被刷 | 成本失控 | Key 级限流 + 套餐功能开关 + 事件白名单；写操作单独 scope |
| R8 | Webhook 目标 URL 造成 SSRF | 内网探测 | 目标地址做 DNS 解析复核 + 内网段拒绝 + 出站域名策略 |
| R9 | 多区域部署涉及数据跨境 | 合规 | `app.workspace.region` 已有字段；阶段 4 只做技术能力，业务上默认单区域 |
| R10 | 发票与退款红冲涉及财务口径 | 法务/财务不通过 | 阶段 0 的计费口径已冻结，本阶段不改；发票只做「记录 + PDF」，不接税务系统 |
| R11 | 前端缺口（L6）叠加阶段 4 后台页 | 前端工作量堆叠 | 阶段 4 前端单独排期，S4-6 与 S4-7 分批，避免与遗留页抢同一批人 |
| R12 | 性能指标无基线（L10） | 压测结果无法对比 | 先在 S4-10 前跑一次基线采样，再做优化，最后复测 |

---

## 阶段4 整体出口条件 checklist

### 子能力 1：套餐与订阅

- [ ] `app.subscription_plan` 落地，`code + version` 唯一，预置 FREE / PRO / TEAM / USAGE 四个套餐
- [ ] 套餐改价生成新版本，存量订阅读快照不受影响
- [ ] `app.subscription` 落地，同一 workspace 只允许一条生效订阅（部分唯一索引生效）
- [ ] 订阅状态机齐全：`trialing / active / past_due / canceled / expired / suspended`
- [ ] 试用可发起且只能一次
- [ ] 升级立即生效并补差价；降级周期末生效
- [ ] 到期自动降级由定时任务执行，幂等（重复扫描不重复降级）
- [ ] 降级只收窄能力与额度，**不删除数据**
- [ ] 订阅全部操作写 `app.audit_event`
- [ ] 全部订阅接口按 workspace 隔离，跨 workspace 返回 404

### 子能力 2：支付与订单

- [ ] `PaymentProvider` 统一接口覆盖下单 / 查询 / 退款 / 回调验签
- [ ] 至少实现 1 个真实渠道 + 1 个 Mock 渠道用于契约测试
- [ ] 渠道凭据走安全环境变量，不落库明文，不进日志
- [ ] `app.subscription_order` 落地，订单号唯一
- [ ] 回调必须验签 + 时间戳窗口 + 事件 ID 去重；验签失败一律拒绝并记审计
- [ ] 回调金额与订单金额不一致 → 拒付并告警
- [ ] 同一事件重复投递 → 只入账一次（重复扣费率 0）
- [ ] 支付成功但履约失败 → 订单仍 `paid`，履约进队列重试，不丢单
- [ ] 订单状态机 `pending / paid / fulfilled / refunded / closed` 齐全，过期订单自动关闭
- [ ] 订单接口按 workspace 隔离

### 子能力 3：计价 / 余额 / 账单

- [ ] `app.price_book` 落地，`unit + region + plan_version` 唯一
- [ ] 单价版本化，历史账单金额不可变
- [ ] `usage_ledger` 加 `unit_price / amount / price_version / statement_id`，**原 `delta` 语义不变**
- [ ] 账单行可回溯到原始 ledger 明细
- [ ] 同周期账单重复生成不产生重复账单（幂等键生效）
- [ ] 余额扣减原子，并发下不出现负余额
- [ ] 余额不足错误可读，提示充值
- [ ] 成本毛利可按工作区 / 套餐 / 时间段聚合，误差可解释
- [ ] 金额字段统一 `NUMERIC(18,4)`

### 子能力 4：退款与发票

- [ ] `app.refund_order` 落地，退款单号唯一
- [ ] 退款金额不超过订单实付金额
- [ ] 退款幂等：同一渠道退款事件重复投递只生效一次
- [ ] 退款成功后额度/余额同步调整，且有对应 `usage_ledger` REFUND 记录
- [ ] `app.invoice` 落地，发票号唯一
- [ ] 只能对已支付且未退款订单开票
- [ ] 发票 PDF 可下载，签名链接 5 分钟过期
- [ ] 红冲有记录，原发票不物理删除
- [ ] 退款/开票/红冲全部写审计

### 子能力 5：后台 / 举报 / 客服 / 销毁

- [ ] 后台套餐、订单、订阅、成本毛利、对账、退款、发票、举报 8 个页面齐全
- [ ] 所有后台接口走 `requirePlatformAdmin()`，非管理员 403
- [ ] 后台页面具备分页 / 筛选 / 空态 / 错误态
- [ ] 敏感操作二次确认 + 审计
- [ ] `app.content_report` 落地，举报可提交可受理可处置可追溯
- [ ] 客服补偿走 `BillableEvent.ADJUSTMENT` 且必须带 reason，进 ledger
- [ ] 租户销毁前提示未完成订单；已开票发票保留留档
- [ ] 销毁后取消自动续费，渠道侧不再扣款

### 子能力 6：开放平台

- [ ] API Key 自助页可完成创建 → 复制 → 撤销全流程
- [ ] 明文 key 只在创建时展示一次，DB 只存 SHA-256
- [ ] 撤销后立即失效；Key 级 scope 越权返回 403
- [ ] `app.api_key.workspace_id` 已修正为 VARCHAR(32)
- [ ] `app.webhook_subscription` + `app.webhook_delivery` 落地
- [ ] 投递带 HMAC 签名 + 时间戳，接收方可验签
- [ ] 失败重试上限 5 次，超限进死信并可人工重投
- [ ] 投递记录含响应码 / 耗时 / 错误原因
- [ ] Webhook URL 做 SSRF 校验，内网地址拒绝
- [ ] 开放事件不携带敏感内容原文
- [ ] OpenAPI 文档可访问且与实际路由一致
- [ ] 开发者门户覆盖认证 / 限流 / 错误码 / 分页 / 幂等

### 子能力 7：容量与高可用

- [ ] 压测脚本一条命令可复跑，输出 P50/P95/P99 与错误率
- [ ] 分析 P95 < 45s、生成 P95 < 30s 有实测证据
- [ ] 压测中复验重复发布率 0、重复扣费率 0
- [ ] `docs/CAPACITY-PLAN.md` 产出，含扩容阈值与告警线
- [ ] 多实例部署编排产出，任务不重复消费有验证记录
- [ ] 主库故障切换演练一次并留证据
- [ ] `backup.sh` + `restore.sh` 真实执行一次，恢复后账本与账单金额一致
- [ ] RPO / RTO 实测值记录在案

### 子能力 8：通知

- [ ] `app.notification` 落地，站内通知可见可标记已读
- [ ] 事件源覆盖订阅到期 / 续费成功 / 支付失败 / 退款到账 / 发票开具 / 用量超阈值
- [ ] 邮件通道可插拔，未配置时降级站内信且不报错
- [ ] 通知失败不阻塞主流程
- [ ] 通知不泄漏敏感内容

### 阶段4 整体出口条件

- [ ] E1：订阅 → 支付 → 履约 → 用量 → 账单 → 退款 → 发票全链路跑通，金额可复算
- [ ] E2：连续 30 天无 P0 事故
- [ ] E3：账单与额度对账一致（差异 = 0 或全部有处理记录）
- [ ] E4：恢复演练成功且数据一致
- [ ] E5：压测达标 + 重复发布率 0 + 重复扣费率 0
- [ ] 开放 API + Webhook + 开发者门户对外可用
- [ ] 后端 `mvn clean package` 通过，前端 `vue-tsc -b` 通过
- [ ] 新增 i18n 命名空间在 zh-CN / zh-TW / en 三语齐全，`types.ts` 同步
- [ ] 新增表 Flyway 迁移可重复执行，不破坏阶段 0-3 既有数据
- [ ] 所有新增接口通过跨 workspace 隔离验证（跨 workspace 读取返回 404）
- [ ] 计费相关接口有集成测试（原子扣减 / 并发 / 重试 / 退款 / 重复请求）

---

## 未冻结决策清单（阻塞阶段 4 的项）

> 这些决策会直接推翻表结构与接口，需在对应子任务开工前签字。

| ID | 决策 | 建议默认 | 阻塞子任务 |
|---|---|---|---|
| D6 | 首发计费方式（订阅 / 按量 / 混合） | **C —— 混合**（订阅为主 + 按量兜底） | S4-1, S4-3 |
| D7 | 首发支付渠道与优先级 | **A —— 支付宝 + 微信支付**（Stripe 作为出海可选） | S4-2 |
| D8 | 商业首发用户类型（个人 / 机构 / 代运营） | **B —— 机构 + 代运营**（团队套餐优先） | S4-1 |
| D9 | 账期（预付 / 后付 / 混合）与宽限期天数 | **A —— 预付**，宽限期 7 天 | S4-3, S4-1 |
| D10 | 发票类型范围（是否支持专票 / 税号） | **A —— 仅个人普票 + 企业普票** | S4-4 |
| D11 | 平台抽成与分成（如开放平台） | **A —— 平台抽 20%** | S4-7 |
| D12 | 成本核算口径（模型 token / 调用次数 / 存储） | **B —— 调用次数为主 + 存储按量** | S4-3 |

---

## 签字位

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 产品负责人 | | | |
| 技术负责人 | | | |
| 财务负责人 | | | |
| 法务 / 合规 | | | |
| 运营负责人 | | | |

---

## 明日开工建议（第一步）

按依赖顺序，第一批只做 **S4-0 遗留项清理**，因为它体量小、风险低、且直接解掉三个「承诺了但没实现」的问题：

1. **L1** `CsvImportService.importAssets()` / `importTopics()` 落地真实写入（复用 `ContentAssetService.createDraftInternal` / `TopicPoolService`）
2. **L2** `MediaStorageService` 改走 MinIO 预签名 URL（5 分钟 TTL），不再直出 objectKey
3. **L3** `BrowserAssistService.fillSession()` 接入 Playwright（或在无浏览器环境下显式标记 `manual_required`，不再伪装成功）
4. **L8** 新增 `V61__stage4_workspace_id_alignment.sql`，把 `app.api_key.workspace_id` 归一为 VARCHAR(32)

完成后跑一次 `mvn -o -q compile` + `mvn clean package` + `scripts/stage3-e2e.ps1` 回归，再进 S4-1。
