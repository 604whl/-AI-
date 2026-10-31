# 阶段4 开发状态（checkpoint）

> 依据：`STAGE4_PLAN.md`（阶段4 验收基线与开发计划，857 行）
> 规则见 `CLAUDE.md`「上下文 checkpoint」：用户说「继续」时先读本文件，不要重新探索代码库。

## 已冻结决策（2026-10-29 用户拍板）

| ID | 决策 | 结论 | 影响子任务 |
|---|---|---|---|
| D6 | 首发计费方式 | **C 混合**（订阅为主 + 按量兜底） | S4-1, S4-3 |
| D7 | 首发支付渠道 | **A 支付宝 + 微信支付**（Stripe 出海可选） | S4-2 |
| D9 | 账期与宽限期 | **A 预付，宽限期 7 天** | S4-1, S4-3 |
| D8 | 首发用户类型 | B 机构 + 代运营（团队套餐优先）——按计划默认执行 | S4-1 |
| D10 | 发票类型 | A 仅个人普票 + 企业普票——按计划默认执行 | S4-4 |
| D11 | 平台抽成 | A 平台抽 20%——按计划默认执行 | S4-7 |
| D12 | 成本核算口径 | B 调用次数为主 + 存储按量——按计划默认执行 | S4-3 |

**本次开发范围**：批次 A + B = **S4-0 / S4-1 / S4-2 / S4-3**（S4-4~S4-11 留待下一轮）。

> **2026-10-31 更新**：S4-4（退款 + 发票）已完成并收口，详见本文末「S4-4 退款 + 发票 收口」。

## 环境状态

- Docker Desktop 29.5.2 已启动
- `xhsagent-postgres-dev`（localhost:5432/xhsagent，user=xhsagent，pwd=xhsagent_dev）
- `xhsagent-redis-dev`（localhost:6379）
- 迁移验证命令：`docker exec xhsagent-postgres-dev psql -U xhsagent -d xhsagent -c "..."`
- 基线编译：`mvn -o -q compile` EXIT=0

## 子任务进度

| ID | 名称 | 迁移 | 状态 | 负责 agent |
|---|---|---|---|---|
| S4-0 | 遗留项清理（L1/L2/L3/L8） | V61 | **完成** | dev-A |
| S4-1 | 套餐与订阅 | V62 | **完成** | dev-B |
| S4-2 | 支付抽象与订单 | V63 | **完成**（报告因平台错误丢失，代码已编译+启动验证） | dev-C |
| S4-3 | 计价、价格表、余额、账单 | V64 | **完成** | dev-D |
| S4-4 | 退款 + 发票 | V67 | **完成**（2026-10-31 收口，E2E 26/26） | dev-E |
| S4-5~S4-11 | 对账/后台/开放平台/门户/通知/压测/高可用 | V68+ | 本轮不做 | — |

## 文件所有权（避免并行冲突）

| 归属 | 路径 |
|---|---|
| dev-A（S4-0） | `service/CsvImportService.java`、`service/MediaStorageService.java`、`service/BrowserAssistService.java`、`V61__*.sql` |
| dev-B（S4-1） | `billing/plan/**`、`billing/subscription/**`、`billing/support/**`、`V62__*.sql`、`quota/domain/entity/WorkspaceQuota.java`、`domain/entity/Workspace.java` |
| S4-2（后续） | `billing/payment/**`、`billing/order/**`、`V63__*.sql` |
| S4-3（后续） | `billing/pricing/**`、`billing/statement/**`、`V64__*.sql`、`quota/domain/entity/UsageLedger.java` |

## 关键决策

- `app.workspace_quota` ALTER 归 S4-1（订阅生效写额度）；`app.usage_ledger` ALTER 归 S4-3（升级为计费流水）——两表互斥不重叠。
- 金额字段一律 `NUMERIC(18,4)`，禁止 FLOAT/DOUBLE。
- 所有 `workspace_id` 一律 `VARCHAR(32)`。
- jsonb/list/map 列必须挂 TypeHandler + `@TableName(autoResultMap = true)`。
- 支付回调/退款/开票一律幂等键 + `app.idempotency_key`。
- 凭据、密钥不进日志、不进异常消息、不进 `raw_response`。

## 下一步

1. S4-0 + S4-1 完成后跑 `mvn -o -q compile` + 迁移实测
2. 派发 S4-2（支付订单）+ S4-3（计价账单）并行
3. 派发 `code-reviewer` 审查批次 A+B
4. 汇总 → 更新本文件 → `scripts/push.ps1` 推送

## 批次 A+B 收口状态（2026-10-29 23:15）

### 验证结论
- `mvn -o -q clean compile` EXIT=0；`mvn -o -q -DskipTests package` EXIT=0。
- 应用启动成功（Started Application 15.63s）。
- Flyway `Successfully validated 50 migrations`；V61-V64 全部 success=t，checksum 无冲突。
- 全部新增 Java/SQL 文件 UTF-8 无 BOM（已全量扫描）。

### 统筹者复核发现并修复的 P0（4 个）
1. **MapperScan 漏注册 4 个 billing Mapper 包** —— `order`/`pricing`/`balance`/`statement` 未加入 `@MapperScan`，`PaymentEventMapper` Bean 缺失，**应用直接启动失败**。
2. **`@ConditionalOnMissingBean` 误用** —— `NoopBrowserDriver` 是普通 `@Component`，该注解仅在自动配置类生效，导致 `BrowserDriver` Bean 未注册，**应用启动失败**。
3. **支付回调过滤器 `shouldNotFilter` 判断反** —— 回调路径反而被跳过，请求落到 `AuthInterceptor` 返回 401，**渠道永远调不通**。
4. **Mock 渠道硬编码默认密钥** —— `DEFAULT_SECRET="mock-secret-dev"` 公开可猜 + 无条件注册，任何人可伪造回调白嫖订单。

### 支付回调链路实测（修复后）
| 场景 | 结果 |
|------|------|
| 无时间戳 | 40013 `timestamp_missing`，写审计 |
| 无签名 | 40012 `signature_missing`，写审计 |
| 伪造签名（未配置 secret） | 50301 `mock_secret_not_configured`，写审计 |
| 修复前 | 一律 401，渠道无法回调 |

### 待 code-reviewer 复核（agent 自报存疑项）
- 余额 `atomicDebit` 只看 `balance` 不看 `frozen`，与 `toView` 的 `balance - frozen` 口径不一致。
- `StatementService` 幂等键用 `OffsetDateTime.toString()` 拼接，时区/格式可能漂移。
- `usage_ledger` 的 `BALANCE` 行 `delta` 方向与常规相反，聚合 SQL 须排除。
- `bindIdempotencyKey` 对已绑其他 key 的订阅不生效，key-B 重放可能不被识别。
- `period_quota` 只写不扣，`overage_policy` 尚未接入 `AtomicQuotaGuard`。
### 统筹者复核追加修复（P1 2 个）

5. **余额原子扣减未排除冻结金额** —— `atomicDebit` 条件原为 `balance >= amount`，而可用余额口径是 `balance - frozen`（`toView` 的 `available` 字段、`atomicFreeze` 均用后者）。冻结资金会被二次消费。已改为 `balance - frozen >= amount`，余额不足报错信息同步改为可用余额。
   - 实测：`balance=100, frozen=80`，扣 50 → 旧逻辑 `UPDATE 1`（available 变 **-30**），新逻辑 `UPDATE 0`（正确拒绝）。
6. **账单幂等键未做时区归一** —— `buildIdempotencyKey` 用 `OffsetDateTime.toString()`，含偏移量，同一时间点用不同偏移表示会生成两个不同键，唯一索引拦不住，同周期可能出两张账单。已改为 `toInstant()` 统一到 UTC；`StatementScheduler` 的 taskId 同步归一。
   - 实测：同一周期 UTC vs +08:00 → 旧 `equal=false`，新 `equal=true`。
7. **订阅幂等键绑定与内存状态不一致** —— `bindIdempotencyKey` 忽略 UPDATE 受影响行数，行已绑定其他键时 SQL 守卫使其不生效，但内存对象仍被写入。已改为检查 `updated == 0` 并跳过内存写入 + 记 warn 日志。

### 已复核确认「无问题」（不必再改）
- **金额比较**：回调用 `BigDecimal.compareTo`（非 `equals`），scale 不同不会误判。
- **回调处理链**：验签 → 事件 ID 去重（含 `DuplicateKeyException` 并发兜底）→ 金额校验 → 条件 UPDATE → 履约入队，顺序正确。
- **`usage_ledger` delta 方向**：`BALANCE` 行方向确实与常规相反，但 `selectUnbilled`、`selectUnbilledByUnit`、`aggregateCostByUnit`、`aggregateCostByWorkspace` **四处全部带 `action <> 'BALANCE'`**，无污染。
- **支付凭据**：Alipay / WeChat 均从环境变量读取，源码中只有变量名无密钥值。
- **`period_quota` 只写不扣**：V62 明确设计为「加列不改语义」，既有 `AtomicQuotaGuard` 日额度逻辑未受影响；周期额度扣减属后续阶段范围。
- **MyBatis 参数类型**：新增查询均用显式 `CAST(... AS VARCHAR/NUMERIC/TIMESTAMPTZ)`，规避 PG 下 `? IS NULL` 类型推断失败。

## 批次 A+B 接口级 E2E 收口（2026-10-30 21:27）

### 验证结论
- `mvn -o -q clean compile` EXIT=0；`mvn -o -q package -DskipTests` EXIT=0。
- 应用启动成功；Flyway `validated 50 migrations`，V61-V64 仍 `success=t`（Docker Desktop 重启后复验）。
- **`scripts/verify-stage4.ps1` → 14/14 PASS**
- **`scripts/verify-s4-0-legacy.ps1` → 15/15 PASS**

### 本轮统筹者复核发现并修复的 P0（1 个）
8. **UUID 主键未自赋值导致订阅/订单插入 500** —— MyBatis-Plus 的 `IdType.ASSIGN_UUID` **只对 `String` 主键自动赋值**，`UUID` 主键不会自动填充（实测 `audit_event`（String id）自动填充成功，`subscription`/`subscription_order`（UUID id）传入 `null`，触发 `null value in column "id" violates not-null constraint`）。
   - 本仓库既有 26 个 UUID 主键实体**一律手动 `setId(UUID.randomUUID())`**，阶段四新增代码遗漏了该约定。
   - 已在 12 处 insert 站点补齐：`SubscriptionService`（订阅 x2）、`OrderService`、`OrderFulfillmentService`、`PaymentCallbackService`、`BalanceService`、`PlanService`（套餐 x2）、`PricingService`、`StatementService`、`ReconciliationService`（差异 x2）。

### 本轮修复的测试脚本缺陷（2 个，均为脚本问题，非服务端缺陷）
- **`verify-stage4.ps1` 回调金额写死 `1.00`** —— 服务端 `amount_mismatch` 校验本身正确（回调金额必须等于订单真实金额 `199.0000`），是脚本写死金额导致 409。改为从订单响应读取 `orderAmount` 再格式化签名金额。
- **`verify-s4-0-legacy.ps1` 在 PS7 下丢失错误响应体** —— `Invoke-Api` 的 catch 块只用了 `GetResponseStream()`（PS5.1 专有），PS7 下该调用抛错，导致 409 响应体丢失、业务码读成 `-1`。补 `ErrorDetails.Message` 回退后，服务端实际返回的 `40950 browser_manual_required` 被正确断言。

### 履约链路实测（数据库侧复核，非仅 HTTP 200）
| 验证点 | 实测结果 |
|---|---|
| 订单落库 | `order_no=SOmvv06eapE5KO` / `order_type=subscription` / `amount=199.0000` / `idempotency_key=e2e-order-...` |
| 支付回调 | `app.payment_event` → `result=processed`、`amount=199.0000`、`reason` 空 |
| 事件去重 | 同 `event_id` 重放 → `result=duplicate`（唯一索引 `uq_payment_event_provider_event` 生效） |
| 订单状态 | `pending` → **`fulfilled`** |
| 订阅落库 | `status=active`、`plan_id` 正确、`current_period_start/end` 与订单履约时刻对齐 |
| 额度生效 | `app.workspace_quota` → `daily_quota=100`、`period_quota=7900`、`plan_id` 已关联、周期区间与订阅周期一致 |
| 订阅幂等 | 同幂等键重放 → 同一 `subscription.id`（`2bfd65e2-...`） |
| 订单幂等 | 同幂等键重放 → 同一 `order_no` |

### 当前遗留
- **真实支付渠道未联通**：支付宝/微信商户号与证书未落地，渠道实现为骨架，凭据走环境变量，未配置时返回 `PAYMENT_PROVIDER_NOT_CONFIGURED`；当前仅 Mock 渠道可跑通端到端。
- **真实浏览器驱动未接入**：`NoopBrowserDriver` 恒返回不可用，需后续实现 Playwright/Selenium 或 `BrowserDriver` Bean 覆盖。
- **前端未动**：批次 A+B 全为后端，S4-4~S4-11 的前端页面与 i18n 未开始。
- **账单未生成**：本次 E2E 未产生可计费用量，`app.billing_statement` 为空属预期；账单生成链路仅验证到查询接口。

## 代码审核收口 + 系统性 P0 修复（2026-10-31 00:30）

### 审核结论
`code-reviewer` agent（`72e10015-8005-4bff-a894-62fe8f2a77b0`）交回 12 条问题（P0 x3 / P1 x4 / P2 x5），**已全部修复并逐条实测**。

### 已修复的审核问题

| 编号 | 问题 | 修复位置 |
|---|---|---|
| P0-1 | 付费套餐可直接 `subscribe` 绕过支付 | `SubscriptionService.subscribe()` → 409 `payment_required_create_order` |
| P0-2 | 用量充值订单履约后未入余额 | `OrderFulfillmentService.fulfill()` → 走 `BalanceService.creditBalance()` |
| P0-3 | `audit_event.id` 是 UUID，实体用 String → 审计事件写不进库 | `AuditEvent.id` 改 UUID + `AuditWriter` 手动 `UUID.randomUUID()` |
| P1-1 | PG 事务 aborted → 幂等重放 500 | 新增 `billing/support/IdempotencyReplay.java`（`PROPAGATION_REQUIRES_NEW`） |
| P1-2 | 付费升配可绕过支付 | `SubscriptionService.changePlan()` 按比例补差价 > 0 时 409 |
| P1-3/P1-4 | 账单缺冲正/作废接口、余额接口未暴露 | `BillingController` 新增 `chargeStatement` / `voidStatement` / `balance` |
| P2-3 | 账单行未按「计量单位 + 价格版本」分组 → 金额不可复算 | `StatementService` 分组键加 `priceVersion` |
| P2-4 | 出账冲正在独立事务中被回滚 | `rollbackChargingToOpen` 改 `PROPAGATION_REQUIRES_NEW` |
| P2-6 | 支付事件拒收状态未落库 | `markEventRejected` 改 `PROPAGATION_REQUIRES_NEW` |
| P2-7 | 履约卡住的订单静默丢失 | `BillingScheduler.requeueUnfulfilledOrders()` 定时重投 |

### 🔴 审核清单未覆盖的系统性 P0（本轮自行发现）

> **MyBatis-Plus 的 `autoResultMap = true` 只对 MP 内置方法生效。**
> 自定义 `@Select` 走 MyBatis 原生「自动映射」路径，它按 **（属性类型 + 列的 JDBC 类型）** 去全局注册表查处理器，
> 拿不到实体字段上的 `@TableField(typeHandler = ...)`。
> 而 jsonb 列在 JDBC 元数据里是 `OTHER`，项目的 jsonb 处理器只挂在 `@TableField` 上、**从未进全局注册表**
> → **jsonb 列静默变成 null**（不报错、不抛异常）。

**实测症状（修复前）**
- `GET /v1/plans` → `includedUnits` / `featureFlags` 全是 `{}`（库里明明有数据）
- `GET /v1/workspaces/{id}/subscription` → `planCode` / `price` 全是 null
- → 「变更套餐」`priceOfSnapshot()` 返回 null → **NPE 500**

**修复**
- `config/MybatisPlusConfig.java`：把 jsonb 处理器注册进 MyBatis 全局注册表 `(Map|String|List, JdbcType.OTHER)`
- `config/mybatis/PgListTypeHandler.java`（新增）：`List<String>` 有两种列形态
  - `app.topic_pool.tags` / `app.kb_document.tags` → PG `TEXT[]`（JDBC `ARRAY`）
  - `app.api_key.scopes` / `app.review_task.risk_tags` → `jsonb`（JDBC `OTHER`）
  - 只注册 jsonb 版 → `TEXT[]` 列被 MyBatis `pickSoleHandler` 回退命中 → 报 `Failed to parse jsonb array`
  - 改为**按结果集元数据的真实列类型运行时分派**

**修复后实测**
```
GET /v1/plans            → PRO.includedUnits 有 8 个单位，featureFlags 有 7 个开关
GET /v1/.../subscription  → planCode=PRO, price=199.0000, planVersion=1
```

### 验证结论（2026-10-31 00:30）

| 套件 | 结果 |
|---|---|
| `scripts/verify-stage4-fixes.ps1` | **5/5 PASS** |
| `scripts/verify-stage4.ps1` | **13/13 PASS** |
| `scripts/verify-s4-0-legacy.ps1` | **15/15 PASS** |
| `mvn -o -q test` | **Tests run 207, Failures 0, Errors 1** |

> 唯一 error：`XhsAgentYunyingApplicationTests` 的 Testcontainers `ryuk:0.12.0` 镜像拉取失败
> —— 环境网络问题，与本次改动无关（非代码缺陷）。

### 本轮附带修复
- `StatementService.java`：`UsageLedger.priceVersion` 是 `Integer`，代码按 `String` 用 → 编译不过，改 `String.valueOf`
- `PgListTypeHandler.java`：`ResultSetMetaData.getColumnIndex()` 在 PG 驱动下对 `SELECT *` 会抛错 → 改为遍历 `getColumnLabel` 自查

### 当前遗留
- **真实支付渠道未联通**（支付宝/微信商户号与证书未落地，仅 Mock 渠道可端到端）
- **真实浏览器驱动未接入**（`NoopBrowserDriver` 恒返回不可用）
- **前端未动工**（S4-4~S4-11 的前端页面与 i18n 未开始）
- **V65 历史重复扣费自愈**已执行，但只覆盖 `dedupe_key` 命中行；历史无 dedupe_key 的重复行需人工核对

### 下一步
1. `git commit` + `scripts/push.ps1` 推送本轮全部改动
2. S4-4（退款/发票）起手 —— 批次 A+B 已收口，DB 迁移 V61-V65 全部落地
3. 真实支付渠道对接（需商户号 + 证书，依赖外部资源）

## 审核跟进批次 A+B 第二轮收口（2026-10-31 01:48）

### 本轮修复的审核遗留项（P2-1 / P2-2 / P3-2 / P3-4 / P3-5）

| 编号 | 问题 | 修复位置 | 说明 |
|---|---|---|---|
| P2-1 | 下单事务内含远程 HTTP 调用 | `billing/order/OrderService.java` | 拆成 3 段：事务1 插 pending 订单并提交（`PROPAGATION_REQUIRES_NEW`）→ **事务外** 调 `provider.createPayment` → 事务2 落 payload / 失败时 `compareAndSetClosed` |
| P2-2 | 对账事务内含远程 HTTP 调用 | `billing/order/ReconciliationService.java` | `queryDailyBills()` 移到事务外，比对与落库在 `TransactionTemplate` 内 |
| P3-2 | 升级时配额未按剩余周期折算 | `billing/subscription/SubscriptionService.java` | 新增 `applyPlanQuotaProrated`：`ceil(fullTargetUnits * 剩余天数 / 周期天数)` |
| P3-4 | 写操作幂等键抢占（cancel/change_plan 抢 subscribe 的键） | `SubscriptionService.java` + 新增 V66 表 | 新建 `app.subscription_write_idempotency`（PK `workspace_id, scope, idempotency_key`），scope 分离 subscribe/trial/cancel/change_plan/admin_adjust |
| P3-5 | 管理员调整重复提交叠加延期天数 | `SubscriptionService.java` + `SubscriptionAdminController.java` | `adminAdjust` 接 `Idempotency-Key` 头，重放返回既有状态不重放副作用；缺 `reason` 返 40010 |

### 新增单元测试
- `SubscriptionServiceTest`：12 例（新增 6 例覆盖 P3-4/P3-5 作用域隔离与重放语义）
- `BalanceServiceTest`：9 例
- `StatementServiceTest`：6 例

### 验证结论（2026-10-31 01:48）

| 套件 | 结果 |
|---|---|
| `mvn -o -q compile` / `package` | EXIT=0 |
| `mvn -o test` | **Tests run 234, Failures 0, Errors 1**（唯一 error = Testcontainers `ryuk:0.12.0` 拉取失败，**环境网络问题**，非代码缺陷） |
| `scripts/verify-stage4.ps1` | **14/14 PASS** |
| `scripts/verify-stage4-fixes.ps1` | **5/5 PASS** |
| `scripts/verify-s4-0-legacy.ps1` | **15/15 PASS** |
| Flyway | `validated 52 migrations`，**V66 success=t**，`app.subscription_write_idempotency` 表 + PK 已建 |

### 本轮发现的环境侧缺陷（非业务代码）

- **本地启动 profile 必须是 `local`** —— `dev` profile 在 `application.yml` 中不存在，会被 `SecurityStartupValidator` 当作生产环境拦截（`JWT_SECRET` / `AI_MOCK_ENABLED` / 真实 API Key 三项校验失败）。
- **`cmd set X=val &&` 会把分隔空格并入变量值** —— 导致 `PAYMENT_MOCK_SECRET` 服务端与客户端不一致，Mock 回调验签必然 40012 `signature_invalid`。必须写成 `set "X=val"`。
- **`.cmd` 启动器必须是 ANSI/GBK 编码** —— UTF-8/UTF-16 的 `.cmd` 会被 `cmd.exe` 解析成乱码而静默不执行。
- **E2E 重复跑会撞限流** —— `ratelimit:*` Redis 键未自动过期，重跑前需清理，否则 step 6 返回 429 `rate_limit_exceeded`。

### 当前遗留（未变）
- **真实支付渠道未联通**（支付宝/微信商户号与证书未落地，仅 Mock 渠道可端到端）
- **真实浏览器驱动未接入**（`NoopBrowserDriver` 恒返回不可用）
- **前端未动工**（S4-4~S4-11 的前端页面与 i18n 未开始）
- **V65 历史重复扣费自愈**只覆盖 `dedupe_key` 命中行；历史无 dedupe_key 的重复行需人工核对

### 下一步（明天）
1. S4-4 起手：**退款 + 发票**（D10：仅个人普票 + 企业普票）
2. S4-5~S4-11：后台运营 / 开放平台 / 用户门户 / 通知 / 压测 / 高可用
3. 前端计费页面 + i18n 补齐（阶段四目前全为后端）
4. 真实支付渠道对接（需商户号 + 证书，依赖外部资源，需用户协调）

## S4-4 退款 + 发票 收口（2026-10-31 21:40）

### 交付范围
- **退款**（`app.refund_order`）：用户申请（OWNER / 3 次每天每工作区 / 幂等键）→ 平台管理员审核 → **事务外**调 `provider.refund()` → 渠道确认 → 额度 / 余额冲正 →（已开票）发票红冲
- **发票**（`app.invoice`）：D10 仅个人普票 + 企业普票；开票 → PDF 生成 → HMAC 签名下载（5 分钟过期）；红冲用 `status='red_flushed'` + `status='red'` 双记录，**原票不物理删除**
- **P2-1 事务边界**：审核通过拆三段 —— 事务1 CAS `pending→approved` → **事务外** HTTP 调渠道 → 事务2 CAS `approved→refunded` + 权益冲正

### 接口
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/workspaces/{ws}/refunds` | 申请退款（OWNER，幂等） |
| GET | `/v1/workspaces/{ws}/refunds` | 退款列表（OWNER/ADMIN） |
| GET | `/v1/workspaces/{ws}/refunds/{refundNo}` | 退款详情（跨工作区 404） |
| GET | `/v1/workspaces/{ws}/invoices` | 发票列表 |
| GET | `/v1/workspaces/{ws}/invoices/{id}/download-ticket` | 签名下载票据（TTL 300s） |
| GET | `/v1/workspaces/{ws}/invoices/{id}/pdf` | PDF 下载（校验 HMAC 签名 + 过期时间） |
| GET | `/v1/platform/admin/refunds` | 退款列表（平台管理员） |
| POST | `/v1/platform/admin/refunds/{refundNo}/approve` | 审核通过 → 渠道退款 → 权益冲正 |
| POST | `/v1/platform/admin/refunds/{refundNo}/reject` | 审核驳回 |
| POST | `/v1/platform/admin/invoices` | 开具发票（幂等键） |
| GET | `/v1/platform/admin/invoices` | 发票列表（平台管理员） |
| POST | `/v1/platform/admin/invoices/{id}/red-flush` | 手动红冲 |

### 本轮修复的真实缺陷（E2E 实测暴露，非推测）

| 编号 | 缺陷 | 影响 | 修复 |
|---|---|---|---|
| **S4-4-P0-1** | `InvoicePdfGenerator.resolveCjkFont()` 的兜底分支在 `catch` 里直接调 `BaseFont.createFont(...)`（抛 `IOException`）而未处理 | CJK 字体不可用时抛 `Unresolved compilation problem: Unhandled exception type IOException`，**开票接口 500** | 兜底分支显式 catch `IOException \| DocumentException`，失败抛 `invoice_pdf_font_unavailable` |
| **S4-4-P0-2** | 红冲后用 `status='issued'` 的**旧快照** `updateById(origin)` 回写，把 DB 里已改的 `red_flushed` 覆盖回 `issued` | 红冲后原票状态回滚，**看起来没被红冲** | 删除该 `updateById`，`compareAndSetRedFlushed` 已同时落 `status` 与 `red_flush_invoice_id`；内存对象同步即可 |
| **S4-4-P1-1** | 管理端手动红冲走 `redFlushByOrder(origin.getOrderId(), ...)` | **按账单开票的发票 `orderId` 为 null → 永远红冲不了** | 抽出 `doRedFlush(origin, ...)` 核心，两条路径共用 |
| S4-4-P1-2 | `application-prod.yml` 内含 `spring.profiles.active` | 未设 `SPRING_PROFILES_ACTIVE` 时默认 `prod` → 启动即 `InvalidConfigDataPropertyException` | 本地验证统一 `SPRING_PROFILES_ACTIVE=local`（属既有设计，不改代码） |

> **S4-4-P0-1 的隐蔽性**：打包产物是用 Eclipse JDT 编译的（javac 只报 warning），
> 编译错误被**烘焙进字节码**，运行时才炸成 `java.lang.Error: Unresolved compilation problem`。
> 已补 `InvoicePdfGeneratorTest`（3 例）锁死 PDF 头 `%PDF-` / 尾 `%%EOF`。

### 验证结论（2026-10-31 21:40）

| 套件 | 结果 |
|---|---|
| `scripts/verify-s4-4-refund-invoice.ps1` | **26/26 PASS** |
| `RefundServiceTest` | 15/15 PASS |
| `InvoiceServiceTest` | 15/15 PASS（含 2 例新回归） |
| `InvoicePdfGeneratorTest` | 3/3 PASS（新增） |
| S4-4 单测合计 | **33/33 PASS** |
| `mvn -o test` 全量 | **Tests run 267, Failures 0, Errors 1** |
| Flyway | `validated 53 migrations`，**V67 success=t** |
| PDF 实产物 | `%PDF-1.5` 头 + `%%EOF` 尾，内嵌 `STSong-Light` / `UniGB-UCS2-H` CJK 字体 |

> 全量唯一 error 仍是 `XhsAgentYunyingApplicationTests` 的 Testcontainers `ryuk:0.12.0`
> 镜像拉取失败 —— **环境网络问题，与本次改动无关**（与批次 A+B 收口时同一现象）。

### 退款 / 发票链路实测（数据库侧复核）

| 验证点 | 实测结果 |
|---|---|
| 退款单落库 | `refund_no=RFmvwg04i5G3H5` / `amount=199.0000` / `status=refunded` / `provider_refund_id=MOCK-...-RF` |
| 权益冲正 | 审核通过后 `entitlementReversed=true`（订阅取消 + 降级额度） |
| 渠道幂等 | 同 `event_id` 重放 → `result=duplicate` |
| 退款幂等 | 同幂等键重放 → 同一 `refund_no` |
| 超额退款 | `40016 refund_amount_exceeds_order` |
| 租户隔离 | 跨工作区查退款单 → **404** |
| 发票落库 | `invoice_no=INV20261000000562L` / `amount=199.0000` / `tax_amount=11.2642` / `tax_rate=0.0600` |
| 税额公式 | 价税合计 × 税率 ÷ (1+税率) = 199 × 0.06 ÷ 1.06 = 11.2642 ✓ |
| 企业票缺税号 | `40017 tax_no_required_for_company_invoice` |
| 重复开票 | `40917 order_already_invoiced` |
| 签名下载 | 票据 TTL **300s**；正确签名 200 / 篡改签名 **403** / 过期签名 **403** |
| 红冲 | 原票 `status=red_flushed` + `red_flush_invoice_id` 指向红票；红票 `status=red` |

### 当前遗留

- **前端未动工**：S4-4 退款 / 发票只有后端，前端页面与 i18n 仍待补
- **真实支付渠道未联通**：支付宝 / 微信商户号与证书未落地，仅 Mock 渠道可端到端
- **部分退款不自动红冲**：退款金额 ≠ 原票金额时跳过自动红冲，交财务人工处理（避免冲减金额错误），已在审计日志留 `INVOICE_RED_FLUSH_SKIPPED`
- **真实浏览器驱动未接入**：`NoopBrowserDriver` 恒返回不可用
- **Testcontainers ryuk 镜像拉取失败**：环境网络问题，致 `XhsAgentYunyingApplicationTests` 1 例 error

### 下一步
1. S4-5~S4-11：后台运营 / 开放平台 / 用户门户 / 通知 / 压测 / 高可用
2. 前端计费页面 + i18n 补齐（阶段四目前全为后端）
3. 真实支付渠道对接（需商户号 + 证书，依赖外部资源，需用户协调）