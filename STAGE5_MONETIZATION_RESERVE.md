# 阶段5 商业化预留方案（免费先行 · 收费后接）

> **文档定位**：本文是 `STAGE5_PLAN.md` 的**配套预留设计**，专治「后期要收费时发现代码要重写」。
> **决策来源**：用户 2026-10-31 决策 —— 暂不开通收费（资质申请周期长）→ 现阶段**全功能免费无限量 + 保留 BYOK**；但**现在就把收费要用的结构全部建好**；**阶段 5 开工时创建收费分支**；资质到位后「合并分支 + 翻开关」即可开收。
> **配套文档**：`STAGE5_PLAN.md`（阶段 5 主计划）、`COMMERCIAL_SAAS_READINESS_PLAN.md`（上游）、`Xhs-Backend/docs/BILLING-RULES.md`（计费口径）。
> **状态**：草案，**待确认项见 §11**。本文不含任何代码实现，落地细节留待阶段 5 开工时定稿。

---

## 0. 核心原则：门装好，门控数据可换

> **业务代码永不改，只有「门」的决策依据变。**

把每个收费相关能力想象成一扇门：

```
请求 --> [ 能力门 ] --> 业务逻辑 --> 台账 --> 账单
          |
          +-- 门开/门关 由「套餐数据 + 模式开关」决定
```

**免费期要做的事**：把门**装上**（结构、注解、守卫、台账、价目表行），但把开关数据设为「全开」。
**收费期要做的事**：把开关数据设为「按套餐区分」+ 填真实单价。**不动业务逻辑，不动接口契约，不动前端功能页**。

| 层次 | 免费期（现在） | 收费期（后期） | 是否需要改代码 |
|---|---|---|---|
| 业务逻辑层（生成、分析、匹配） | 完整实现 | 完全相同 | ❌ 不改 |
| 接口契约层（路径/入参/出参） | 完整实现 | 完全相同 | ❌ 不改 |
| 能力门（守卫 + 注解 + 开关读取） | **装好**，判定结果恒为「放行」 | **同一套代码**，判定结果变成「按套餐」 | ❌ 不改 |
| 套餐数据（feature_flags） | FREE 套餐全开 | PRO/TEAM 差异化 | ❌ 只改数据 |
| 价目表（price_book） | 单价 = 0 | 单价 = 真实值 | ❌ 只改数据 |
| 模式开关（billing.mode） | FREE | PARTIAL / PAID | ❌ 只改配置 |
| 席位 / 订阅 / 支付 | 表结构就位，不启用 | 启用 | ⚠️ 仅「启用开关」 |

---

## 1. 现状事实盘点（已核对代码）

### 1.1 已经可用的（阶段 4 已落地）

| 资产 | 路径 | 免费期作用 | 收费期作用 |
|---|---|---|---|
| 套餐功能开关存储 | `app.subscription_plan.feature_flags`（JSONB） | FREE 套餐写全开 | 按档位差异化 |
| 套餐快照机制 | `app.subscription.plan_version_snapshot`（JSONB） | 冻结免费态 | 冻结付费态，改价不影响存量 |
| 功能开关读取工具 | `billing/support/PlanSnapshot.hasFeature()` | **判定放行** | 判定按套餐 |
| 价目表（双单价） | `app.price_book`（`unit_price` + `cost_per_unit`） | 单价 0 | 单价真实值 |
| 用量台账 | `app.usage_ledger`（含 `unit_price/amount/price_version`） | **照常写，金额记 0** | 照常写，金额真实 |
| 扣费守卫 | `quota/AtomicQuotaGuard`（含幂等 + 价格快照） | 照常跑 | 照常跑 |
| 周期额度写入 | `WorkspaceQuotaMapper.applySubscriptionQuota` | 写大值 | 写真值 |
| 计量单位枚举 | `freeze/BillableUnit`（8 个） | 够用 | 需扩展 5 个 |
| 归一表达式 | `UsageLedgerMapper.CANONICAL_UNIT_SQL` | 够用 | 新单位需同步 |

### 1.2 缺失 / 半成品（阶段 5 必须补）

| 编号 | 项 | 事实 | 阶段 5 处理 |
|---|---|---|---|
| **L10** | 功能开关零调用点 | `PlanSnapshot.hasFeature()` 已实现但**全项目零调用**，`feature_flags` 只在 `SubscriptionService.toView()` 原样回前端 | **必须做**（否则「预留」是假的） |
| **L11** | `period_quota` 只写不扣 | `atomicConsume` 只判 `daily_quota`，`period_quota` / `overage_policy` 从不参与判定 | **必须做** |
| **L12** | `auto_renew` 无续费分支 | `SubscriptionExpiryTaskHandler` 到期直接降级 | 收费期前做 |
| **L13** | `AdminAdjustRequest` 无 `planCode` | 无法把订阅定向到指定套餐 | **必须做**（免费期要「内部全功能账号」） |
| **L14** | 内部 0 元套餐通道 | `createPlan` 已支持 `price=0`，但无「全功能 flag」的套餐种子 | **必须做** |
| **L15** | 模式开关不存在 | 无 `billing.mode` 概念 | **必须做**（本文核心新增） |
| **L16** | BYOK 凭据表不存在 | 全局 yml 单 Key，租户级 BYOK 未实现 | **必须做**（用户明确要求「保留用户配置自己的大模型」） |

### 1.3 免费期明确不做（避免过度设计）

- 不做「试用期倒计时」「到期弹窗」「套餐升级引导页」等收费心理设计
- 不做「超额付费购买次数」的自助入口
- 不做「账单页」的复杂图表（保留最小可用的账单列表，用于自检）
- 不做席位超限拒绝逻辑的完整实现（只留结构 + 开关位）

---

## 2. 预留结构总览（8 件要做的事）

| 编号 | 预留物 | 类型 | 免费期状态 | 收费期动作 |
|---|---|---|---|---|
| **R-1** | 能力门骨架（`@RequiresFeature` + 守卫 + 切面） | 代码结构 | 装好，恒放行 | 无需改（改数据即生效） |
| **R-2** | 模式开关（`app.billing.mode`） | 配置 | `FREE` | 改配置 |
| **R-3** | 免费套餐种子（全开 flags + 全额额度 + 单价 0） | 数据 | 建好 | 改数据 |
| **R-4** | 计量单位扩展（5 个新单位 + 归一表达式 + 价目表行） | 结构 | 枚举与归一就位，单价 0 | 填真实单价 |
| **R-5** | 席位模型（`app.workspace_seat` 表结构） | 数据结构 | 建表不启用 | 启用 + 接成员加入校验 |
| **R-6** | BYOK 凭据（表 + 加密 + 掩码 + 路由） | 功能 | **本次必须真做**（用户要求） | 无需改 |
| **R-7** | 收费分支 + 切换手册 | Git | 阶段 5 开工时建 | 合并 + 翻开关 |
| **R-8** | 「接口 × 开关」矩阵清单 | 文档 | 建好并逐条标注 | 评审用 |

---

## 3. R-1 能力门骨架（预留的核心）

### 3.1 设计要点

> **免费期装门但门永远开着。** 这样收费期不需要往业务代码里插判断，只需要在「套餐数据」里做区分。

需要预留的结构（**具体类名/包路径待阶段 5 定稿**，此处只定职责边界）：

| 组件 | 职责 | 免费期行为 | 收费期行为 |
|---|---|---|---|
| 功能守卫（Guardless） | 读当前工作区生效订阅 → 读快照里的 `featureFlags` → 判定某 flag | FREE 套餐 flags 全开 → 放行 | PRO/TEAM 差异化 → 按档位 |
| 注解 `@RequiresFeature("ad_strategy")` | 声明接口所需能力 | 挂在**全部**新接口上 | 同一批注解继续有效 |
| 全局切面（AOP） | 拦注解 → 调守卫 → 不通过抛 403 `FEATURE_NOT_ENABLED` | 全部放行 | 按套餐拒绝 |
| 「读取当前工作区开关集」接口 | 前端据此隐藏入口 | 返回全开 | 返回按套餐 |

### 3.2 免费期「恒放行」怎么做到不写死

**关键：不要在代码里写 `if (mode == FREE) return true;` 这种特判。**

正确做法是「**数据即全开**」：

```
FREE 套餐的 feature_flags = {
  "competitor_library": true, "metrics_dashboard": true, "export": true,
  "api_key": true, "open_api": true, "webhook": true, "team_seats": true,
  "audience_insight": true, "ad_strategy": true, "ops_workbench": true
}
```

这样代码里**没有任何免费期特判**，收费期只需把不同套餐的 flags 改成不同值。

### 3.3 降级语义（收费期必须正确，免费期不触发）

| 场景 | 判定结果 | 说明 |
|---|---|---|
| 有生效订阅 + 快照 flags 含该 flag 且为 true | 放行 | 正常路径 |
| 有生效订阅但 flag 缺失或 false | 拒绝 403 | 正常路径 |
| 订阅 `expired` / `canceled` | 视为 FREE 档 | **降级即收窄**，`downgradeToFree` 已实现只改额度不删数据 |
| 无订阅（从未订阅过） | 视为 FREE 档 | 取 FREE 套餐的 flags |
| 订阅快照缺失（数据异常） | 视为 FREE 档 | 兜底为最严（FREE）而非最宽 |

> **兜底方向必须「严」不能「宽」**：快照异常时若放行全功能，等于门形同虚设。

### 3.4 「接口 × 开关」矩阵（R-8）

每条接口必须归入某个开关档位。**评审时逐条勾，任何一条空着都不算预留完成。**

| 开关 | 归属 | 覆盖接口（阶段 5 新增） | 阶段 5 之前已有 |
|---|---|---|---|
| `competitor_library` | PRO+ | 竞品库相关 | 已有 |
| `metrics_dashboard` | PRO+ | 流量拆分 / 流量汇总 | 指标看板已有 |
| `export` | PRO+ | 导出 | 已有 |
| `api_key` | PRO+ | BYOK 凭据增删查 | 新增 |
| `open_api` | TEAM+ | 开放 API | 已有 |
| `webhook` | TEAM+ | 出站 Webhook | 已有 |
| `team_seats` | TEAM+ | 成员邀请 / 席位管理 | 成员管理已有 |
| `audience_insight` | PRO+ | 人群包列表 / 反推 / 匹配 | 新增 |
| `ad_strategy` | TEAM+ | 投流策略 / 素材变体 / 投后复盘 | 新增 |
| `ops_workbench` | TEAM+ | 运营工作台 | 新增 |

---

## 4. R-2 模式开关（`app.billing.mode`）

### 4.1 三档语义

| 模式 | 含义 | 门控行为 | 配额行为 | 账单行为 |
|---|---|---|---|---|
| `FREE` | 全免费（**现在**） | 按 FREE 套餐 flags（实际全开） | 不阻断 | 不出账 |
| `PARTIAL` | 部分收费（过渡期，可选） | 按套餐 flags | 阻断超额 | 出账 |
| `PAID` | 全面收费（后期） | 按套餐 flags | 阻断超额 | 出账 |

### 4.2 开关放在哪

> 放在 **应用配置**（不是数据库），因为：
> - 免费 → 收费是**一次性的运营决策**，不是租户级差异；
> - 放在配置里，改一次全局生效，不需要跑数据迁移；
> - 放数据库反而增加「谁来改、怎么审计」的复杂度。

读取优先级：`环境变量 APP_BILLING_MODE` > `application.yml` 的 `app.billing.mode` > 默认 `FREE`。

### 4.3 开关的 4 个作用点（**全部预留，免费期不改变行为**）

| 作用点 | 免费期 | 收费期 |
|---|---|---|
| ① 门控判定 | FREE 套餐 flags 全开 → 全放行 | 按套餐 flags |
| ② 配额阻断 | 不阻断（`period_quota` 写大值 / 或标记为不阻断） | 超额拒绝 |
| ③ 账单出账 | 不出账（`statement_id` 不写、或出账任务按 mode 跳过） | 正常出账 |
| ④ 计量台账 | **照常写**（关键！见 §6） | 照常写 |

> **④ 是重点**：台账必须**在免费期就照常写**，否则收费期没有历史用量数据可参照、也没有「量级感」来定价。

---

## 5. R-3 免费套餐种子（数据侧预留）

### 5.1 FREE 套餐的 flags 全开

见 §3.2 的 flags 全开清单。**通过 `createPlan` 创建，`price=0`，`includedUnits` 给一个极大值**（如各动作 1_000_000）。

### 5.2 额度口径

| 字段 | 免费期取值 | 收费期取值 |
|---|---|---|
| `daily_quota` | 极大值（如 1_000_000） | 按套餐真实日上限 |
| `period_quota` | 极大值 | 按套餐周期总额 |
| `overage_policy` | `ALLOW`（放行不阻断） | `BLOCK` / `CHARGE` |

> **`overage_policy` 已有字段与 `applySubscriptionQuota` 写入通道，无需新增字段。**

### 5.3 内部 0 元全功能账号（L13 + L14）

用户明确要「预留一个超级账号」。做法：管理端 `AdminAdjustRequest` 加 `planCode` 字段（L13），
支持把任意工作区的订阅定向到指定套餐；配合一个 `INTERNAL` 套餐（`price=0`、flags 全开、额度极大）。

**不需要新表**，只需：

1. `AdminAdjustRequest` 加 `planCode` 字段（DTO 改动）；
2. `AdminAdjustService` 支持「改套餐 + 重写快照 + 写额度」；
3. 管理端一个 seed 脚本建 `INTERNAL` 套餐。

---

## 6. R-4 计量台账：免费期怎么写才不污染后期账单

### 6.1 关键事实（已核对 `UsageLedgerMapper`）

- 出账 SQL `selectUnbilled` 的条件是：`statement_id IS NULL AND amount IS NOT NULL AND status = 'CONSUMED' AND action <> 'BALANCE'`
- `amount IS NOT NULL` 是**出账的硬门槛** —— 免费期若把 `amount` 写成 `NULL`，这些行**永远不会被出账**；
- 免费期若把 `amount` 写成 `0`，这些行**会被出账**，产生一批 0 元账单行。

### 6.2 免费期的正确写法

> **推荐：`amount = 0` + `statement_id` 保持 NULL + 出账任务按 `billing.mode` 跳过。**

| 字段 | 免费期取值 | 理由 |
|---|---|---|
| `delta` | `+1`（真实用量） | 用量必须真实记录 |
| `status` | `CONSUMED` | 与收费期同口径 |
| `unit_price` | `0` | 免费期单价为 0 |
| `amount` | `0`（**不是 NULL**） | NULL 会导致该行永久无法出账，破坏「用量可追溯」 |
| `price_version` | 指向 FREE 套餐对应的价目表行 | 可复算、可追溯 |
| `provider` | 实际使用的 provider（dashscope / 用户 BYOK 的 provider_code） | **成本归因**：BYOK 用户成本≈0，平台 Key 用户成本=真实 token 成本 |
| `statement_id` | `NULL` | 由出账任务在 PAID 模式才写 |

### 6.3 免费期台账的一个副作用（必须提前知道）

> 免费期台账里会有大量 `amount=0` 的行。若后期收费期直接把这些行纳入出账，会给用户开出**一堆 0 元账单行**。

**解决办法（收费期前必须做）**：出账 SQL 增加一个「计费周期起始时间」下界，
例如只出账 `created_at >= <收费生效时间>` 的行。这需要 `app.subscription` 上有一个「首个计费周期起点」字段，
**本方案把它列入 §11 待确认项 `Q3`**。

---

## 7. R-5 席位模型（预留结构，不启用）

### 7.1 表结构

```sql
-- app.workspace_seat：席位占用（阶段 5 建表，免费期不启用校验）
CREATE TABLE IF NOT EXISTS app.workspace_seat (
    workspace_id  VARCHAR(32) PRIMARY KEY REFERENCES app.workspace(id) ON DELETE CASCADE,
    plan_id       UUID,
    seat_quota    INT NOT NULL DEFAULT 1,      -- 套餐赠送席位
    seat_used     INT NOT NULL DEFAULT 0,       -- 已占用（成员数）
    overage_rate  NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 7.2 免费期行为

- 表**建了但不做超限校验**（`seat_quota` 写极大值即可，等价于不限）；
- 成员加入路径**预留一个校验钩子**，免费期该钩子恒返回 true；
- 不做「席位不足」的前端提示。

---

## 8. R-6 BYOK（本次必须真做，不是预留）

> 用户明确要求「保留用户可以配置自己的大模型」。这一项**免费期就要真实可用**，不是留结构。

### 8.1 已核对的挡路事实

| 现状 | 位置 | 挡路原因 |
|---|---|---|
| Provider 是启动期 Bean | `config/OpenAiConfig` 的 `@Bean` + `@ConditionalOnProperty` | 用户 Key 运行期才知道 |
| Registry 构造期不可变 Map | `ai/model/ModelProviderRegistry` 构造器 `Collectors.toMap` | 运行期加不进去 |
| 路由是全局单链 | `ModelProviderRegistry.chatWithFallback` | 无 workspace 维度 |
| 全局 Key 断言 | `ai/AiRuntimePolicy.assertRealAiAvailable()` | 会误拦用户自带 Key 的场景 |
| Agent 通道硬编码 3 个 provider | `ai/agent/model/AgentModelProviderRegistry` 的 `switch` | 两条路行为不一致 |
| Embedding 全局单 Bean | `ai/kb/KbEmbeddingService` + `vector(1024)` 固定维度 | 不同厂商维度不同 → 向量对不上 |

### 8.2 方案要点（沿用 STAGE5_PLAN.md §3.7）

1. **凭据表**：`app.workspace_model_credential`（`workspace_id / provider_code / base_url / model / api_key_encrypted / status / last_verified_at`）
2. **加密**：AES-GCM，复用 `BrowserAssistService` 既有约定
3. **回显**：前端只显示掩码（`sk-****abcd`），后端不回显明文
4. **日志**：API Key / Authorization / Cookie 一律脱敏（复用现有 `SensitiveMaskingConverter`）
5. **Embedding 不 BYOK**：知识库是平台资产且维度固定 → 只有 chat 走用户 Key
6. **失败不静默回退**：用户 Key 401/429 → 直接报错并引导换 Key（静默回退会烧平台预算且用户误判）
7. **销毁联动**：`WorkspaceDestructionService` 必须删除凭据
8. **免费期仍允许平台 Key 兜底**：用户没配 Key 时走平台全局 Key（但要记录 `provider` 到台账）

### 8.3 免费期 BYOK 的一个额外考量

> 免费期让用户自带 Key 有一个**副作用**：平台成本下降，但要保证**没配 Key 的用户仍能用**。
因此免费期的 Provider 解析顺序必须是：**用户 Key 优先 → 平台 Key 兜底 → Mock**。

---

## 9. R-7 收费分支与切换手册

### 9.1 分支策略（用户已定：阶段 5 开工时创建）

| 项 | 决策 |
|---|---|
| 分支名 | `feature/monetization-gating`（建议，阶段 5 开工时建） |
| 创建时机 | **阶段 5 开工时**（不在免费期建，避免长期分叉） |
| 基点 | 阶段 5 完成后的 `xhs_agent` 主线 |
| 存放内容 | 仅「收费相关的差异化实现」：真实价目表数据、计费周期起点字段、出账过滤、席位超限校验、升级引导 UI |
| 不存放 | 业务功能本体（人群/投流/工作台/BYOK 都在主线） |

### 9.2 为什么收费内容可以独立成一个分支

因为收费差异**只落在 4 个点**（§4.3）：

1. 套餐数据（flags 差异化 + 真实单价）
2. 计费周期起点（出账下界）
3. 席位超限校验启用
4. 升级引导 / 账单 UI

**这 4 点都不在业务功能代码里。** 所以分支不会变成「难以合并的长期分叉」，
而是「一个装着数据脚本 + 少量开关启用的补丁集」。

### 9.3 切换手册（资质到位后的操作步骤）

```bash
# 1. 合并收费分支（主线功能已在，只合并收费差异化部分）
git checkout xhs_agent
git merge feature/monetization-gating

# 2. 填真实价目表（数据，不是代码）
#    - app.subscription_plan: PRO / TEAM 套餐的 feature_flags 差异化
#    - app.price_book: 各 unit 的 unit_price / cost_per_unit 真实值
#    - app.subscription_plan.included_units: 各档位包含额度

# 3. 翻开关（配置，不是代码）
#    APP_BILLING_MODE=PARTIAL   # 或 PAID

# 4. 跑数据迁移（若需回填计费周期起点）

# 5. 验证：FREE 工作区调 PRO 接口 -> 403 FEATURE_NOT_ENABLED
#         PAID 工作区调 PRO 接口 -> 200
#         账单出账金额 = 单价 x 用量（可复算）
```

### 9.4 回滚预案

| 故障场景 | 回滚动作 |
|---|---|
| 开关配错导致全站 403 | 改 `APP_BILLING_MODE=FREE` 重启（1 分钟） |
| 价目表单价填错 | `price_book` 是版本化的 → 插新版本行即可，不动历史 |
| 出账算错 | 冲正台账行 + 重开周期（`related_log_id` 已有机制） |
| 分支合并冲突 | 冲突只可能在「计费相关文件」→ 手工解，业务文件零冲突 |

---

## 10. R-8 「接口 × 开关」矩阵的强制校验

> **这条最容易在赶工期时被跳过，但它决定了「预留」是不是真的。**

### 10.1 校验方式（免费期就要有）

| 手段 | 说明 |
|---|---|
| 切面挂载自检 | 启动时扫描所有 `@RequiresFeature` 标注的接口，输出「已挂载 / 未挂载」清单 |
| 矩阵文档评审 | §3.4 的表逐条勾 |
| 反向测试 | 对每个开关写 1 条「应拒绝」+ 1 条「应放行」用例 |

> **注意**：免费期所有开关都放行，所以「应拒绝」用例在免费期**无法通过**。
因此这批测试必须放在**收费分支**里（切换后 CI 才跑）。

### 10.2 切面漏挂的自检输出格式（预留接口）

```
GET /api/v1/admin/feature-audit
{
  "mode": "FREE",
  "totalEndpoints": 128,
  "gated": 126,
  "ungated": [
    { "method": "POST", "path": "/v1/xxx", "reason": "no @RequiresFeature" }
  ]
}
```

---

## 11. 待确认决策清单（**文档未定稿，需与产品/财务对齐**）

| ID | 待确认项 | 建议默认 | 影响 |
|---|---|---|---|
| **Q1** | 免费期是否需要「用量可视化」（让用户看到自己用了多少） | 需要（只显示量，不显示金额） | 前端 + 台账查询 |
| **Q2** | 免费期是否显示「套餐/订阅」入口 | 隐藏（但保留接口） | 前端导航 |
| **Q3** | 计费周期起点如何记录（解决 §6.3 的 0 元账单行问题） | 在 `app.subscription` 加 `first_billed_at`，出账按它过滤 | **需迁移** |
| **Q4** | BYOK 免费期是否允许「平台 Key 兜底」 | 允许（否则新用户无 Key 无法体验） | Provider 解析链 |
| **Q5** | 免费期是否限制单工作区成员数 | 不限（`seat_quota` 极大值） | 席位启用时机 |
| **Q6** | 免费期台账保留多久 | 长期保留（数据资产，也是定价依据） | 清理任务 |
| **Q7** | 收费期套餐档位与定价（FREE/PRO/TEAM 的价格与额度） | 待财务输入 | 价目表数据 |
| **Q8** | 「内部 0 元全功能账号」是独立套餐还是 FREE 的别名 | 独立 `INTERNAL` 套餐（不进公开价目表） | L13/L14 |
| **Q9** | 阶段 5 收费分支名与基点 | `feature/monetization-gating` @ 阶段5完成态 | Git |
| **Q10** | `app.billing.mode` 的默认值（环境缺失时） | `FREE` | 安全默认 |

---

## 12. 风险矩阵（预留特有）

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| MR-1 | 免费期代码里写了「免费特判」分支 | 收费期要改业务代码 → 推倒重来 | **禁止特判**；免费靠套餐数据全开实现（§3.2） |
| MR-2 | 免费期台账 `amount` 写成 NULL | 用量永久不可出账，收费期无历史参照 | 强制 `amount = 0`（§6.2） |
| MR-3 | 免费期 0 元台账被批量出账 | 用户收到一堆 0 元账单 | 出账加计费周期起点下界（Q3） |
| MR-4 | 「接口 × 开关」矩阵漏填 | 收费期才发现某功能没门控 | 启动自检 + 评审逐条勾（§10） |
| MR-5 | 长期分叉导致合并冲突 | 切换成本飙升 | 收费内容只落 4 点（§9.2）；分支只装数据脚本 + 开关启用 |
| MR-6 | 模式开关读取来源不统一（环境变量 vs yml vs 数据库） | 线上行为不一致 | 三级优先级固定（§4.2），并写进运维手册 |
| MR-7 | BYOK Key 泄露 | 用户真实资金损失 | AES-GCM + 掩码 + 日志脱敏 + 销毁联动（§8.2） |
| MR-8 | 免费期用量台账膨胀 | 存储成本 | Q6 定保留策略；台账是定价依据，不建议早删 |
| MR-9 | 免费期把「凭证/凭据」类接口也放开 | 未来成为攻击面 | `api_key` / `open_api` / `webhook` 即使免费期也**保留独立鉴权与限流** |
| MR-10 | 用户预期错位（以为免费就无限用，之后收费引发流失） | 口碑损失 | 前期明确「当前免费，未来会开订阅」的对外说明 |

---

## 13. 阶段 5 落地顺序（预留视角）

| 批次 | 内容 | 是否属于预留 |
|---|---|---|
| **B0** | 模式开关 + FREE 套餐种子 + 能力门骨架 + 内部账号（L13/L14） | ✅ 预留核心 |
| **B1** | 计量台账口径统一 + `period_quota` 接入（L11）+ 计量单位扩展 | ✅ 预留核心 |
| **B2** | BYOK 全链路（R-6） | ✅ 本次真实功能 |
| **B3** | 流量维度（S5-1） | 业务功能 |
| **B4** | 人群洞察（S5-2） | 业务功能 |
| **B5** | 投流策略（S5-3） | 业务功能 |
| **B6** | 席位表结构（R-5）+ 计价扩展 | ⚠️ 半预留 |
| **B7** | 运营工作台（S5-4） | 业务功能 |
| **B8** | 收费分支 + 切换手册 + 「接口×开关」矩阵终审 | ✅ 预留收口 |

> **B0–B2 是「预留三件套」**：门 / 数据 / 计量。**B2 之后即使立刻停工去申请资质，也已经具备开收条件。**

---

## 14. 与既有文档的关系

| 文档 | 关系 |
|---|---|
| `STAGE5_PLAN.md` | 主计划；本文是其商业化预留配套 |
| `COMMERCIAL_SAAS_READINESS_PLAN.md` | 上游；本文 §1 现状盘点与其一致 |
| `Xhs-Backend/docs/BILLING-RULES.md` | **需更新** —— 新增 5 个计量单位 + 免费期台账口径 |
| `Xhs-Backend/docs/PRD-V3.0.md` | **需更新** —— 定位改为「运营决策闭环」 |
| `Xhs-Backend/docs/STAGE0-FREEZE-DECLARATION.md` | **需更新** —— 新增 D23 商业模式冻结项 |
| `CLAUDE.md` | 项目信息段落待更新（卖点 + 商业化阶段） |

---

## 15. 签字位

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 产品负责人 | | | |
| 技术负责人 | | | |
| 财务负责人 | | | |
| 法务 / 合规 | | | |
| 运营负责人 | | | |
