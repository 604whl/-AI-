# 阶段5 验收基线与开发计划（运营决策闭环与功能商业化）

> 编制依据：`COMMERCIAL_SAAS_READINESS_PLAN.md` §4「商业化能力」、§10「优先级清单」、§12「未冻结决策」；用户 2026-10-31 决策（投流仅做策略助手 / 人群画像仅自反推 / 产出完整规划）。
> **2026-10-31 二次决策（商业化策略调整）**：暂不开通收费（资质申请周期长）→ 现阶段**全功能免费无限量 + 保留 BYOK**；同时**结构化预留**收费所需的全部结构（功能门控骨架 / 计量台账 / 模式开关 / 席位与价目数据结构），**阶段 5 开工时创建收费分支**，资质到位后「合并分支 + 翻开关」即可开收，业务代码零改动。
> 详细预留清单与切换手册见 **`STAGE5_MONETIZATION_RESERVE.md`**（本文 §0.1 / §9.1 / §12 为其索引与摘要）。
> 上游基线：阶段 0 冻结 / 阶段 1 SaaS 基础 / 阶段 2 创作闭环 / 阶段 3 数据闭环 / 阶段 4 商业化（套餐·订阅·支付·账单已落地）。
> 模板对齐：`STAGE4_PLAN.md`（现状盘点 → 子能力 → 接口契约 → 数据模型 → 风险矩阵 → 出口条件 → 未冻结决策）。

---

## 0. 一句话定位

> **从「内容生成工具集」升级为「运营决策闭环系统」——我们卖的是「该做什么、为什么、做完怎么复盘」的决策能力，不是 Token，也不是内容本身。**

阶段 4 之前，产品是**单点功能集**（分析 / 标题 / 正文 / 评论生成），每个功能用完即弃。
阶段 5 之后，产品是**带反馈的运营循环**：

```
选题 → 内容 → 审核 → 发布 → 分发（自然 + 付费） → 数据回流 → 复盘归因 → 下一轮选题
         ↑                                                                    │
         └────────────────────── 策略与人群匹配 ────────────────────────────┘
```

---

## 0.1 商业化策略：免费先行 · 收费预留（2026-10-31 二次决策）

> **当前阶段不开通收费**（支付资质申请周期长）。本阶段商业化目标不是「赚到钱」，而是
> **把将来收费要动的地方全部建好**，做到资质到位时「合并分支 + 翻开关」即可开收，业务代码零改动。

| 项 | 现阶段（FREE） | 后期（PAID） |
|---|---|---|
| 功能可用性 | **全功能、无限量、免费** | 按套餐档位差异化 |
| 大模型 | **用户可自带 Key（BYOK）**，平台 Key 兜底 | 同上（成本趋近 0 → 卖功能不卖 token） |
| 计量台账 | **照常写**（`amount=0`，用量真实） | 照常写（`amount` 真实） |
| 计费/账单 | 不出账 | 正常出账，金额可复算 |
| 席位 | 表结构就位，不启用校验 | 按套餐限制 |
| 价目表 | 单价 `0` | 单价真实值 |

**落地方式（4 条铁律）**：

1. **门装好，门控数据可换** —— 所有收费相关能力统一走一个「能力门」（`@RequiresFeature` + 守卫 + 切面）；
   免费期把 FREE 套餐的 `feature_flags` 设为**全开**，门自然全放行；收费期只改数据。
2. **禁止免费期特判** —— 代码里**不得**出现 `if (billingMode == FREE) return true;` 这类分支。
   免费靠「套餐数据全开」实现，不靠代码判断。
3. **台账免费期照写** —— `amount` 必须写 `0`（**不能是 NULL**，否则该行永久无法出账），
   用量真实记录，为后期定价提供量级依据。
4. **收费差异只落 4 个点** —— 套餐数据 / 计费周期起点 / 席位校验启用 / 升级引导 UI。
   这 4 点都不在业务功能代码里，因此收费分支不会产生难以合并的长期分叉。

**完整预留清单、切换手册、待确认项见** → **`STAGE5_MONETIZATION_RESERVE.md`**（§11 有 10 个待确认项 Q1–Q10）。

---
## 1. 阶段5 不在范围（留给阶段 6+）

| 项 | 原因 |
|---|---|
| 投流**执行**（调广告 API 真实下单 / 改预算） | 需平台广告 API 授权，周期不可控；且阶段 0 D1=C 已冻结「不持有平台凭据」 |
| 达人/达人库（BMS 采买） | PRD-V3.0 §5.1 已明确 P0 不做；数据复杂度高 |
| 平台真实用户画像 | 拿不到；仅做**自有数据反推**并标注可信度 |
| 私域 CRM / 企微 / 飞书打通 | 阶段 6+ |
| 多品类 Prompt / 规则包全量扩包 | 阶段 1–3 遗留补齐，非主线 |
| 企业级 SSO / SAML / 私有化部署 | 阶段 5+ |

---

## 2. 现状盘点（开发前必读）

### 2.1 已具备可直接复用

| 资产 | 路径 | 阶段 5 复用点 |
|---|---|---|
| 套餐功能开关 | `billing/support/PlanSnapshot` + `app.subscription_plan.feature_flags` | **S5-0 地基**（见 §3.1） |
| 套餐快照机制 | `plan_version_snapshot` JSONB | 功能鉴权读快照（不读实时套餐），保证改价不影响存量 |
| 统一 10 键指标口径 | `connector/MetricKeys.CORE_KEYS` | **S5-1 只加维度不加键**（见 §3.2 硬约束） |
| 指标快照 | `app.metric_snapshot` + `MetricSnapshot` | 加「流量来源」维度，支撑 ROI |
| 预测 vs 实际对比 | `app.metric_comparison` + `ComparisonService.categorize()` | 已能把指标差归到 标题/封面/正文/CTA/分发 |
| 复盘报告 | `app.retrospective_report` + `RetrospectiveService` | 已支持 ASSET/ACCOUNT/PLATFORM/CATEGORY/CAMPAIGN/PERIOD 六维度 |
| 复盘回写审批流 | `app.retrospective_feedback` | 「建议 → 模板/知识库回写」已有审批链 |
| 热点 → 选题池 | `app.hot_topic` → `app.topic_pool` | 选题不靠拍脑袋；已有来源/可信度透传 |
| 竞品库 | `app.competitor_account` + `app.competitor_note` | 人群反推的素材来源之一 |
| 内容资产状态机 | `app.content_asset`（draft→in_review→approved→scheduled→published） | 投流对象的载体，已有 `platform` / `accountId` |
| 发布排期 | `app.publish_schedule` + `app.publish_task` + `app.publish_receipt` | 投流建议的「发布时间」依据 |
| 合规规则库 | `app.compliance_rule` + `freeze/ComplianceScanner` | 投流素材的**独立高强度门禁**复用 |
| 角色模型 | `workspace/WorkspaceRole`（OWNER/ADMIN/EDITOR/REVIEWER/VIEWER） | 席位计费的角色基础 |
| 计费底座 | `app.price_book`（unit_price + cost_per_unit 双单价） | **卖功能后 cost 趋 0，unit_price 不变** |
| 用量台账 | `app.usage_ledger` | 新功能计量入口 |
| 审计 | `app.audit_event` | 人群/投流/密钥操作留痕 |

### 2.2 缺失（阶段 5 必须新建）

| 领域 | 现状 | 缺口 |
|---|---|---|
| 功能开关鉴权 | `PlanSnapshot.hasFeature()` **零调用点** | **无任何接口消费 feature_flags**（阻塞项） |
| 周期额度 | `app.workspace_quota.period_quota` 只写不扣 | 真实闸门是 `daily_quota`，套餐额度形同虚设 |
| 管理端改套餐 | `AdminAdjustRequest` 只有 extendDays/status/gracePeriodDays/reason | 无法把订阅直接指定到某套餐 |
| 流量来源维度 | `metric_snapshot.metrics` 10 键全是总量 | **无自然流/付费流区分 → ROI 算不出来** |
| 人群模型 | 无 `audience` 表；`users.default_persona` 是内容人设 | 缺人群包 / 人群规模 / 人群-内容匹配 |
| 投流策略 | 无模块 | 加投评估 / 素材变体 / 投后复盘 全缺 |
| 运营工作台 | 前端按功能菜单切分（31 个 view） | 无「今天该做什么」聚合层 |
| 席位 | `workspace_member` 有角色，**无席位数量概念** | 席位计费缺计量基础 |
| 大模型凭据 | 全局 yml 单 Key | 无租户级 BYOK（**免费期就要真做**，§3.7 / L16） |
| **商业模式开关** | 无 `billing.mode` 概念 | 无「免费→收费」的一键切换点（**免费期必需**，§0.1 / L15） |

### 2.3 阶段 4 遗留（量级小，建议先清）

| 编号 | 遗留项 | 位置 | 影响 |
|---|---|---|---|
| L10 | `feature_flags` 落地成真实鉴权 | 全项目 | **阻塞「卖功能」全部叙事**；免费期也要装好门 |
| L11 | `period_quota` 未接入扣费链路 | `quota/AtomicQuotaGuard` | 套餐额度不生效 |
| L12 | `auto_renew` 未实现续费分支 | `billing/subscription/task/SubscriptionExpiryTaskHandler` | 到期必降级，付费用户会被降 |
| L13 | `AdminAdjustRequest` 无 `planCode` | `dto/billing/SubscriptionDtos` | 内部账号/补偿无法定向套餐 |
| **L14** | 无 `INTERNAL` 全功能套餐种子 | `billing/plan/PlanService` | 「内部 0 元超级账号」无法自助开通 |
| **L15** | 无商业模式开关 `app.billing.mode` | 新增 | 免费→收费无切换点（免费期必需） |
| **L16** | 无租户级 BYOK | `ai/model/ModelProviderRegistry` 等 6 处 | 用户无法配置自己的大模型（用户明确要求） |

---

## 3. 子能力分解

### 3.1 子能力 S5-0：功能开关落地成真实鉴权（**阻塞-P0**）

> **这是整个阶段 5 的地基。** 不做这一步，「按功能收费」只是账目区分 —— 付费用户拿不到 FREE 用户拿不到的东西。

**现状事实**：`PlanSnapshot.hasFeature(sub, flag)` 已实现，但全项目**零调用点**；`feature_flags` 只在 `SubscriptionService.toView()` 里原样返回给前端。

**功能**：
- 统一功能守卫：取当前 workspace 生效订阅 → 读 `plan_version_snapshot.featureFlags` → 判断开关
- 注解 + 全局切面：`@RequiresFeature("export")`，命中即 403 `FEATURE_NOT_ENABLED`
- 未订阅 / 订阅失效（expired/canceled）→ 视为 FREE 档（**降级即收窄**，`downgradeToFree` 已实现只改额度不删数据）
- 无订阅的工作区 → 默认 FREE 档功能集

**开关清单（沿用 `V62` 已定义的 7 个，语义扩展）**：

| flag | 含义 | 建议归属 |
|---|---|---|
| `competitor_library` | 竞品库 | PRO+ |
| `metrics_dashboard` | 指标看板 | PRO+ |
| `export` | 导出 | PRO+ |
| `api_key` | API Key 管理 | PRO+ |
| `open_api` | 开放 API 调用 | TEAM+ |
| `webhook` | 出站 Webhook | TEAM+ |
| `team_seats` | 多席位 | TEAM+ |
| `audience_insight`（**新增**） | 人群洞察 | PRO+ |
| `ad_strategy`（**新增**） | 投流策略 | TEAM+ |
| `ops_workbench`（**新增**） | 运营工作台 | TEAM+ |

**数据模型**：不新增表。`feature_flags` 是 JSONB，加键无需迁移；`plan_version_snapshot` 随订阅快照冻结。

**接口契约**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/workspaces/{ws}/features` | 全部成员 | 返回当前工作区**生效**的功能开关集合（前端据此隐藏入口） |
| GET | `/api/v1/plans` | 公开 | 已在返回 `featureFlags`，无需改 |

**验收**：
- FREE 工作区调 PRO 接口 → 403 `FEATURE_NOT_ENABLED`（**收费期行为**）
- 订阅过期降级 → PRO 接口立即 403（不需重启）
- 切面**零漏挂**：`/v1/**` 全量接口逐个标注开关，评审时逐条核对
- 无订阅工作区 → 只拿到 FREE 开关集
- 单元测试：每个开关至少 1 条正例 + 1 条反例

> **⚠️ 免费期口径调整（见 §0.1）**：现阶段 FREE 套餐 `feature_flags` 全开，
> 因此**上述「403 反例」在免费期不可能通过** —— 那是收费分支才跑的测试。
> 免费期的验收改为：
> - 切面**已挂载**（启动自检输出「已挂载 / 未挂载」清单，未挂载数 = 0）
> - FREE 工作区访问**全部**业务接口无 403
> - 代码中**零**免费期特判（无 `billingMode == FREE` 之类的提前 return）
> - 「应拒绝」用例移入收费分支的 CI

**风险**：
- R-S5-1 **切面漏挂**导致付费功能对所有人开放（商业损失但不致命）→ 用「全量接口 × 开关矩阵」清单表逐条勾，测试用例反向覆盖
- R-S5-2 读实时套餐而非快照导致改价影响存量 → **必须读 `plan_version_snapshot`**

---

### 3.2 子能力 S5-1：流量来源维度（自然流 / 付费流）

> **投流 ROI 的前置。** 不做这个，投流建议是盲的。

**现状事实**：`MetricKeys.CORE_KEYS` 是「固定 10 个键，顺序不可变」（口径冻结），`price_book` / `metric_forecast` / `metric_comparison` 三处 JOIN 都依赖它。

**硬约束**：⚠️ **不得往 `CORE_KEYS` 里加键**。会破坏三处 JOIN 契约与历史数据兼容性。

**正确做法**：**新增独立的「流量来源」维度表**，与快照形成一对多。

**数据模型**：

```sql
-- app.metric_traffic_split：一次指标快照的流量来源拆分
CREATE TABLE app.metric_traffic_split (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    snapshot_id     UUID        NOT NULL REFERENCES app.metric_snapshot(id) ON DELETE CASCADE,
    traffic_type    VARCHAR(16) NOT NULL,   -- ORGANIC / PAID / TOTAL / UNKNOWN
    metrics         JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- 仍用 10 键口径
    spend_amount    NUMERIC(18,4),           -- 投放花费（用户回填，可空）
    confidence      VARCHAR(16) NOT NULL DEFAULT 'LOW',
    source          VARCHAR(32) NOT NULL,   -- MANUAL / CSV / CONNECTOR
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT metric_traffic_split_type_check
        CHECK (traffic_type IN ('ORGANIC','PAID','TOTAL','UNKNOWN'))
);
CREATE INDEX idx_metric_traffic_split_ws ON app.metric_traffic_split(workspace_id, snapshot_id);
```

**语义**：
- `TOTAL` = 该快照全量（可与 `metric_snapshot.metrics` 对齐，作为校验基线）
- `ORGANIC` + `PAID` 若同时存在，两者相加应 ≈ `TOTAL`（超差则标 `confidence=LOW`）
- `spend_amount` 是**用户回填**的投放花费（我们不连广告后台，见 §1）

**接口契约**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/metric-snapshots/{id}/traffic-splits` | ANALYST+ | 回填流量拆分（幂等键） |
| GET | `/api/v1/metric-snapshots/{id}/traffic-splits` | 全部成员 | 读拆分 |
| GET | `/api/v1/workspaces/{ws}/traffic-summary?from=&to=` | 全部成员 | 聚合：自然/付费占比、ROI、消耗 |

**验收**：
- 拆分键集与 `MetricKeys.CORE_KEYS` 严格一致（缺补 0，多余拒绝）
- `ORGANIC + PAID` vs `TOTAL` 偏差 > 20% → 标 `confidence=LOW` 并在响应中说明
- 无拆分数据的快照 → 流量占比返回 `null` + `degradedReason=traffic_split_missing`（**不猜、不填 0**）

---

### 3.3 子能力 S5-2：人群洞察（仅自有数据反推）

> **用户已拍板：只从我们自己的数据反推，明确标注可信度，不声称是平台真实画像。**

**现状事实**：现有「人」的数据全部错位 ——
- `users.default_persona` = 内容语气人设（agency/mentor/senior），不是受众
- `topic_pool.target_persona` = 选题目标人设，同上
- `kb_document.audience` (TEXT[]) = 知识库适用标签，不是画像
- `competitor_account.category` + `tags` = 竞品品类，**没有粉丝画像**

**反推方法**（只用我们已有的数据）：
1. **内容侧特征**：`content_asset.persona` / `industry` / `platform` / `tags` / 正文五段式结构
2. **结果侧表现**：`metric_snapshot.metrics` 的 10 键 + `metric_traffic_split` 拆分
3. **统计口径**：某人群特征组合下的内容，其 `collects/impressions`（收藏率）、`dm_inquiries/impressions`（咨询率）、`conversions/impressions`（转化率）**显著高于基线** → 判定为「该内容的有效受众特征组合」

**数据模型**：

```sql
-- app.audience_segment：人群包（反推产出，非平台画像）
CREATE TABLE app.audience_segment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    code              VARCHAR(64) NOT NULL,   -- workspace 内唯一
    name              VARCHAR(128) NOT NULL,
    description       TEXT,
    age_range         VARCHAR(32),
    gender            VARCHAR(16),
    city_tier         VARCHAR(32),
    interest_tags     TEXT[],
    category_code     VARCHAR(32),            -- 关联 app.category
    persona           VARCHAR(32),
    sample_size       INT NOT NULL DEFAULT 0, -- 支撑该结论的样本内容数
    -- 效果指标（相对基线的倍率，>=1 表示优于基线）
    collect_rate_mult NUMERIC(10,4),
    inquiry_rate_mult NUMERIC(10,4),
    convert_rate_mult NUMERIC(10,4),
    confidence        VARCHAR(16) NOT NULL DEFAULT 'LOW',
    -- 数据来源与边界声明
    derivation_method VARCHAR(64) NOT NULL,   -- INFERRED_FROM_OWN_METRICS
    source_note       TEXT,                   -- 人类可读的推导说明
    status            VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audience_segment_confidence_check
        CHECK (confidence IN ('HIGH','MEDIUM','LOW','UNVERIFIED'))
);
COMMENT ON TABLE app.audience_segment IS
    '人群包：仅由本工作区自有指标反推，非平台真实画像；sample_size 与 confidence 必须同时对外暴露。';

-- app.content_audience_match：内容 × 人群匹配度
CREATE TABLE app.content_audience_match (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    asset_id        UUID NOT NULL REFERENCES app.content_asset(id) ON DELETE CASCADE,
    segment_id      UUID NOT NULL REFERENCES app.audience_segment(id) ON DELETE CASCADE,
    match_score     NUMERIC(10,4) NOT NULL,
    evidence_refs   JSONB NOT NULL DEFAULT '[]'::jsonb, -- 支撑的快照 id 列表
    confidence      VARCHAR(16) NOT NULL DEFAULT 'LOW',
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (asset_id, segment_id)
);
```

**可信度分级规则（写死，不允许 AI 自行放宽）**：

| confidence | 条件 |
|---|---|
| `HIGH` | `sample_size >= 20` 且覆盖 >= 3 个自然周 |
| `MEDIUM` | `sample_size >= 8` 且覆盖 >= 2 个自然周 |
| `LOW` | `sample_size >= 3` |
| `UNVERIFIED` | `sample_size < 3` 或仅单条内容 → **不产出匹配结论** |

**合规硬约束（不可协商）**：
- UI **必须**展示「基于本账号自有数据反推，非平台官方画像」+ `sample_size` + `confidence`
- **禁止**输出「平台用户画像」「真实用户分布」等表述
- **禁止**推断**敏感属性**（年龄精确值、性别、疾病、收入、宗教、政治倾向）→ 由 `freeze/ComplianceScanner` 加一条 `SENSITIVE_AUDIENCE_INFERENCE` 规则（`RiskLevel.BLOCKED`）拦截
- 不得跨 workspace 聚合人群数据（**别人的数据不能用来推断你的受众**）

**接口契约**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/workspaces/{ws}/audience-segments` | 全部成员 | 人群列表（含 sample_size / confidence） |
| POST | `/api/v1/workspaces/{ws}/audience-segments/rebuild` | ANALYST+ | 触发反推（异步任务队列） |
| GET | `/api/v1/content-assets/{id}/audience-match` | 全部成员 | 该内容匹配到的人群包 + 证据 |
| POST | `/api/v1/content-assets/{id}/audience-match` | ANALYST+ | 人工确认/修正匹配（人工优先于 AI） |

**验收**：
- `sample_size < 3` → 不产出匹配结论，返回 `insufficient_data`
- 每条匹配结论 `evidence_refs` 非空且指向**真实存在**的快照
- UI 强制展示反推声明（前端组件测试）
- 敏感属性推断 → `ComplianceScanner` 阻断

---

### 3.4 子能力 S5-3：投流策略助手（**只做决策，不做执行**）

> **用户已拍板：strategy_only。** 能力边界必须写进产品与合同，避免「承诺投放但做不到」。

**能力边界（对外话术口径）**：

| 我们做 | 我们不做 |
|---|---|
| 判断哪条内容值得加投 | ❌ 调广告 API 真实下单 |
| 建议预算档位 / 时段 / 人群包 | ❌ 读取平台广告账户余额 |
| 生成 3 版素材变体供 A/B | ❌ 配置平台后台人群定向 |
| 投后 ROI 复盘（基于回填数据） | ❌ 声称掌握平台真实投放数据 |
| 投放资质与合规预检 | ❌ 代替用户做投放决策 |

**数据模型**：

```sql
-- app.ad_strategy：投流策略（对一条内容资产的加投建议）
CREATE TABLE app.ad_strategy (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    asset_id          UUID NOT NULL REFERENCES app.content_asset(id) ON DELETE CASCADE,
    -- 决策结论
    verdict           VARCHAR(16) NOT NULL,   -- BOOST / TEST / HOLD / SKIP
    verdict_reason    TEXT NOT NULL,
    -- 建议参数
    budget_tier       VARCHAR(32),            -- LOW / MEDIUM / HIGH
    suggest_time_slots JSONB,                -- ["20:00-22:00", ...]
    suggest_segments  JSONB,                 -- [segment_id, ...]
    -- 依据
    evidence_refs     JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence        VARCHAR(16) NOT NULL DEFAULT 'LOW',
    -- 合规预检结果
    compliance_passed BOOLEAN NOT NULL DEFAULT FALSE,
    compliance_notes  JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- 落地状态（人工执行后回填）
    execution_status  VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING / SUBMITTED / REJECTED
    user_notes        TEXT,
    created_by        BIGINT REFERENCES app.users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ad_strategy_verdict_check
        CHECK (verdict IN ('BOOST','TEST','HOLD','SKIP'))
);
COMMENT ON TABLE app.ad_strategy IS
    '投流策略建议（非执行）。系统不调广告 API，不持有广告账户凭据；execution_status 仅记录用户在平台后台的执行情况。';

-- app.ad_creative_variant：素材变体
CREATE TABLE app.ad_creative_variant (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      VARCHAR(32) NOT NULL REFERENCES app.workspace(id) ON DELETE CASCADE,
    strategy_id       UUID NOT NULL REFERENCES app.ad_strategy(id) ON DELETE CASCADE,
    variant_no        INT NOT NULL,
    title             TEXT NOT NULL,
    body              TEXT NOT NULL,
    hook_type         VARCHAR(32),
    compliance_passed BOOLEAN NOT NULL DEFAULT FALSE,
    compliance_notes  JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (strategy_id, variant_no)
);
```

**判定规则（BOOST/TEST/HOLD/SKIP）—— 必须基于真实指标，禁止拍脑袋**：

| 结论 | 条件（全部需有 `evidence_refs`） |
|---|---|
| `BOOST` | 自然流收藏率 > 基线 1.3× **且** 咨询率 > 基线 1.2× **且** 无合规阻断 |
| `TEST` | 收藏率或咨询率其一显著优于基线，但样本不足 → 建议小额测试 |
| `HOLD` | 表现接近基线，暂不加投 |
| `SKIP` | 合规预检未通过，或表现显著低于基线 |

**⚠️ 合规门禁（比自然内容更严，不是更松）**：
- 付费素材的「极限词」定性从「广告法风险(HIGH)」升级为「**广告投放违规(BLOCKED)**」
- 新增品类**投放资质**检查：医美 / 金融 / 教育培训 / 医疗 → 标注「需投放资质，投放前请确认」
- 素材**必须**附「建议标注广告合作」提示
- 复用 `freeze/ComplianceScanner`，新增 `AD_LAW_RISK` 档位升级 + `PAID_MEDIA_RULES` 规则包

**接口契约**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/content-assets/{id}/ad-strategies` | ANALYST+ | 生成投流策略（异步） |
| GET | `/api/v1/content-assets/{id}/ad-strategies` | 全部成员 | 策略列表 |
| PATCH | `/api/v1/ad-strategies/{id}` | ANALYST+ | 人工修正结论 / 回填执行状态 |
| POST | `/api/v1/ad-strategies/{id}/variants` | ANALYST+ | 生成素材变体（3 版） |
| POST | `/api/v1/ad-strategies/{id}/variants/{vid}/select` | ANALYST+ | 选定版本 |
| GET | `/api/v1/workspaces/{ws}/ad-performance?from=&to=` | 全部成员 | 投后 ROI 复盘（基于回填的 traffic_split + spend） |

**验收**：
- 每条策略 `evidence_refs` 非空 → 否则拒绝生成（**不允许无依据的结论**）
- `compliance_passed=false` 的策略不可被选定素材变体
- `BOOST` 结论在样本不足时降级为 `TEST`（防过度加投）
- UI 明示「本工具不代投，投放请在小红书后台执行」
- 素材变体全部过合规扫描，命中 `BLOCKED` 拒绝入库

---

### 3.5 子能力 S5-4：运营工作台（聚合层）

> 把「31 个功能页面」变成「运营一天怎么过」。

**核心视图**（一个页面聚合，不新建一堆页面）：

| 区块 | 内容 | 数据来源 |
|---|---|---|
| 今日待办 | 待审核稿 / 待发布排期 / 待复盘内容 / 失败的发布任务 | `content_asset` / `publish_schedule` / `publish_task` |
| 待加投 | `ad_strategy.verdict=BOOST` 且未执行 | `ad_strategy` |
| 昨日表现 | 昨日发布内容的 10 键指标 + 自然/付费拆分 | `metric_snapshot` / `metric_traffic_split` |
| 本周热点 | 可提升到选题池的热点 | `hot_topic` |
| 人群提示 | 与近期内容匹配度最高的人群包 | `audience_segment` / `content_audience_match` |
| 额度与到期 | 剩余额度 / 订阅到期日 / 席位用量 | `workspace_quota` / `subscription` |

**接口契约**：

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/workspaces/{ws}/workbench` | 全部成员 | 一次性返回聚合视图 |
| GET | `/api/v1/workspaces/{ws}/workbench/tasks?status=` | 全部成员 | 待办分页（走持久化任务队列口径） |

**验收**：
- 单次请求 ≤ 5 个下游查询（避免 N+1）
- 每个区块有 loading / empty / error / degraded 四态
- 数据缺失的区块显示 `degraded` 而非静默隐藏（沿用阶段 3 的诚实降级约定）

---

### 3.6 子能力 S5-5：席位与计价扩展

**现状事实**：`workspace_member` 有角色，**没有席位数量**；`BillableUnit` 8 个全是 AI 操作维度。

**新增计量单位**（扩展 `freeze/BillableUnit` 枚举 + `price_book` 预置单价）：

| 单位 | 含义 | 建议定价口径 |
|---|---|---|
| `SEAT` | 席位（订阅周期内） | **按周期固定，不按次** |
| `AUDIENCE_SEGMENT` | 人群包生成 | 按次 |
| `AD_STRATEGY` | 投流策略生成 | 按次 |
| `AD_CREATIVE_VARIANT` | 素材变体生成 | 按次 |
| `WORKBENCH_QUERY` | 工作台聚合查询 | 按次 |

**席位模型**：

```sql
-- app.workspace_seat：席位占用
CREATE TABLE app.workspace_seat (
    workspace_id  VARCHAR(32) PRIMARY KEY REFERENCES app.workspace(id) ON DELETE CASCADE,
    plan_id       UUID,
    seat_quota    INT NOT NULL DEFAULT 1,   -- 套餐赠送席位
    seat_used     INT NOT NULL DEFAULT 0,   -- 已占用（成员数）
    overage_rate  NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 加席位超限时：成员加入被拒 409 seat_limit_reached
```

**成本口径变化（关键）**：

| 项 | 阶段 4 口径 | 阶段 5 口径 |
|---|---|---|
| `unit_price` | 每次 AI 操作 | **不变**（我们按「一次操作/一项功能」收服务费） |
| `cost_per_unit` | DeepSeek token 成本 | **趋近 0**（BYOK 后仅算力/存储） |
| 毛利 | 收入 − 模型成本 | **≈ 100%**（卖功能模式） |
| 收入主体 | 按次 | **席位订阅为主**（更稳定） |

**验收**：
- 席位超限时成员加入被拒，且返回当前用量
- 新计量单位全部有 `price_book` 行（缺失时单价按 0 计但**不阻断业务**，沿用现有 `applyPriceSnapshot` 约定）
- 账单行的 `unit` 与新枚举一致（`CANONICAL_UNIT_SQL` 归一表达式需同步）

---

### 3.7 子能力 S5-6：BYOK（用户自带模型 Key）

> 阶段 5 后期项。**成本结构改造**，让「卖功能」的毛利成立。

**现状约束**：

| 现状 | 位置 | 挡路原因 |
|---|---|---|
| Provider 是启动期 Bean | `config/OpenAiConfig` 的 `@Bean` + `@ConditionalOnProperty` | 用户 Key 运行期才知道 |
| Registry 构造期不可变 Map | `ai/model/ModelProviderRegistry` 构造器 `Collectors.toMap` | 运行期加不进去 |
| 路由是全局单链 | `ModelProviderRegistry.chatWithFallback` | 无 workspace 维度 |
| 全局 Key 断言 | `ai/AiRuntimePolicy.assertRealAiAvailable()` | 会误拦用户自带 Key 的场景 |
| Agent 通道硬编码 3 个 provider | `ai/agent/model/AgentModelProviderRegistry` 的 `switch` | 两条路行为不一致 |
| Embedding 全局单 Bean | `ai/kb/KbEmbeddingService` + `vector(1024)` 固定维度 | **不同厂商维度不同 → 向量对不上** |

**方案要点**：

1. **凭据表**：`app.workspace_model_credential`（`workspace_id / provider_code / base_url / model / api_key_encrypted / status / last_verified_at`）
2. **加密**：AES-GCM，复用 `BrowserAssistService` 既有约定
3. **回显**：前端只显示掩码（`sk-****abcd`），后端不回显明文
4. **日志**：API Key / Authorization / Cookie 一律脱敏（复用现有 `SensitiveMaskingConverter`）
5. **Embedding 不 BYOK**：知识库是平台资产且维度固定 → **只有 chat 走用户 Key**
6. **失败不静默回退**：用户 Key 401/429 → 直接报错并引导换 Key（静默回退会烧平台预算且用户误判）
7. **销毁联动**：`WorkspaceDestructionService` 必须删除凭据

**⚠️ 与阶段 0 冻结决策的关系**：`D1 = C（仅人工导入，仅针对平台账号凭据）`。BYOK 是**大模型供应商 Key**，属不同域，但**同样受「不持有明文凭据」原则约束**。→ 需新增一条冻结决策 **D18** 明确边界（见 §6）。

**验收**：
- 多 workspace 用不同 provider/key，调用互不串味（并发测试）
- 日志/异常/审计 metadata 中 0 处出现明文 Key
- 用户 Key 失效 → 明确错误码 + 前端引导
- Embedding 仍走平台 Key，知识库跨租户检索行为不变

---

### 3.8 子能力 S5-7：管理端能力补齐（L10–L16）

| 编号 | 项 | 改动 | 理由 |
|---|---|---|---|
| L10 | `feature_flags` 鉴权落地 | 见 §3.1 | 卖功能地基（免费期也要装好门） |
| L11 | `period_quota` 接入扣费 | `AtomicQuotaGuard` 增加周期额度校验（`used < period_quota`），超额按 `overage_policy` 处理 | 让套餐额度真正生效；免费期 `overage_policy=ALLOW` 不阻断 |
| L12 | `auto_renew` 实现 | `SubscriptionExpiryTaskHandler` 在 `past_due` 宽限期耗尽前发起续费订单（走持久化队列） | 付费用户不该被莫名降级；**收费期前必须做** |
| L13 | `AdminAdjustRequest` 加 `planCode` | 管理端可直接指定订阅套餐 + 重写快照 + 写额度 | 内部账号 / 补偿 / 迁移场景 |
| L14 | 内部 0 元套餐通道 | 允许管理端创建 `price=0` 的自定义套餐（`createPlan` 已支持）+ `INTERNAL` 全功能套餐种子 | 自助全功能账号、测试环境、内部账号 |
| **L15** | **模式开关 `app.billing.mode`** | 新增 `FREE / PARTIAL / PAID` 三档，4 个作用点（门控 / 配额 / 出账 / 台账） | 免费→收费的一次性运营决策，不需跑数据迁移 |
| **L16** | **BYOK 凭据表 + 路由改造** | `app.workspace_model_credential` + Provider 解析链改为运行期可插拔 | 用户明确要求「保留用户配置自己的大模型」 |

> **L15 / L16 是免费期的必需项**（见 §0.1 与 `STAGE5_MONETIZATION_RESERVE.md` §2 的 R-2 / R-6）。

**验收**：
- `period_quota` 生效后，`daily_quota` 降级为「日上限」而非「总量上限」
- `auto_renew` 续费失败进 DLQ 且不重复扣费
- 管理端改套餐后订阅快照 / `workspace.plan_code` / 额度三者一致
- `app.billing.mode` 缺省为 `FREE`；三级读取优先级固定（环境变量 > yml > 默认）
- BYOK：多 workspace 用不同 provider/key 并发不串味；平台 Key 兜底链可用

---

## 4. 数据模型总表

| 表名 | 归属 | 关键字段 | 状态 |
|---|---|---|---|
| `app.audience_segment` | 人群 | workspace_id, code, name, age_range, gender, city_tier, interest_tags[], sample_size, *_rate_mult, confidence, derivation_method | 新建 |
| `app.content_audience_match` | 人群 | workspace_id, asset_id, segment_id, match_score, evidence_refs, confidence | 新建 |
| `app.ad_strategy` | 投流 | workspace_id, asset_id, verdict, verdict_reason, budget_tier, suggest_time_slots, suggest_segments, evidence_refs, compliance_passed, execution_status | 新建 |
| `app.ad_creative_variant` | 投流 | workspace_id, strategy_id, variant_no, title, body, compliance_passed, selected | 新建 |
| `app.metric_traffic_split` | 指标 | workspace_id, snapshot_id, traffic_type, metrics, spend_amount, confidence, source | 新建 |
| `app.workspace_seat` | 席位 | workspace_id, plan_id, seat_quota, seat_used, overage_rate | 新建 |
| `app.workspace_model_credential` | BYOK | workspace_id, provider_code, base_url, model, api_key_encrypted, status, last_verified_at | 新建（后期） |
| `app.subscription_plan` | 套餐 | 复用，`feature_flags` JSONB 加新键无需迁移 | 复用 |
| `app.subscription` | 订阅 | 复用 | 复用 |
| `app.metric_snapshot` | 指标 | 复用，**不加列**（流量拆分走独立表） | 复用 |
| `app.content_asset` | 资产 | 复用 | 复用 |
| `app.price_book` | 计价 | 复用，新增 5 个 unit 的价格行 | 复用 |

> **迁移要求**：全部 `IF NOT EXISTS`，幂等双跑通过（沿用阶段 3–4 验证约定）。

---

## 5. 接口契约总表

| 方法 | 路径 | 权限 | 开关 | 说明 |
|---|---|---|---|---|
| GET | `/api/v1/workspaces/{ws}/features` | 成员 | — | 生效功能开关集 |
| POST | `/api/v1/metric-snapshots/{id}/traffic-splits` | ANALYST+ | `metrics_dashboard` | 回填流量拆分 |
| GET | `/api/v1/metric-snapshots/{id}/traffic-splits` | 成员 | `metrics_dashboard` | 读拆分 |
| GET | `/api/v1/workspaces/{ws}/traffic-summary` | 成员 | `metrics_dashboard` | 自然/付费聚合 + ROI |
| GET | `/api/v1/workspaces/{ws}/audience-segments` | 成员 | `audience_insight` | 人群列表 |
| POST | `/api/v1/workspaces/{ws}/audience-segments/rebuild` | ANALYST+ | `audience_insight` | 触发反推 |
| GET | `/api/v1/content-assets/{id}/audience-match` | 成员 | `audience_insight` | 内容×人群匹配 |
| POST | `/api/v1/content-assets/{id}/audience-match` | ANALYST+ | `audience_insight` | 人工修正 |
| POST | `/api/v1/content-assets/{id}/ad-strategies` | ANALYST+ | `ad_strategy` | 生成投流策略 |
| GET | `/api/v1/content-assets/{id}/ad-strategies` | 成员 | `ad_strategy` | 策略列表 |
| PATCH | `/api/v1/ad-strategies/{id}` | ANALYST+ | `ad_strategy` | 修正结论 / 回填执行状态 |
| POST | `/api/v1/ad-strategies/{id}/variants` | ANALYST+ | `ad_strategy` | 生成素材变体 |
| POST | `/api/v1/ad-strategies/{id}/variants/{vid}/select` | ANALYST+ | `ad_strategy` | 选定变体 |
| GET | `/api/v1/workspaces/{ws}/ad-performance` | 成员 | `ad_strategy` | 投后 ROI 复盘 |
| GET | `/api/v1/workspaces/{ws}/workbench` | 成员 | `ops_workbench` | 运营工作台聚合 |
| GET | `/api/v1/workspaces/{ws}/workbench/tasks` | 成员 | `ops_workbench` | 待办分页 |
| POST | `/api/v1/workspaces/{ws}/model-credentials` | OWNER/ADMIN | `api_key` | 保存 BYOK 凭据 |
| GET | `/api/v1/workspaces/{ws}/model-credentials` | OWNER/ADMIN | `api_key` | 列表（掩码） |
| DELETE | `/api/v1/workspaces/{ws}/model-credentials/{id}` | OWNER/ADMIN | `api_key` | 删除凭据 |

**全局约束（沿用既有约定）**：
- 所有 `/v1/**` 接口跨 workspace 访问返回 **404**（不泄露存在性）
- 写接口必须带 `Idempotency-Key`，重放返回同一结果
- 敏感读接口限流；BYOK 凭据接口额外限流（10 次/天）
- 新增 i18n 命名空间在 zh-CN / zh-TW / en 三语齐全，`types.ts` 同步

---

## 6. 未冻结决策清单

> 这些决策会直接推翻表结构与接口，需在对应子能力开工前签字。

| ID | 决策 | 建议默认 | 阻塞子能力 |
|---|---|---|---|
| **D13** | 大模型凭据托管方式 | **C —— 混合**：默认可用（平台送少量试用额度），用户可自带 Key 覆盖 | S5-6 |
| **D14** | 混合模式下平台赠送额度 | 每工作区各 100 次试用，超出引导自带 | S5-6 |
| **D15** | BYOK 用户的定价 | **原价**（我们卖功能不卖 token）；可给「已自带 Key」更大 `includedUnits` 档位 | S5-5 |
| **D16** | Embedding 是否 BYOK | **否**（维度固定 + 知识库是平台资产） | S5-6 |
| **D17** | 用户 Key 失败时的降级 | **直接报错 + 引导换 Key**（不静默回退） | S5-6 |
| **D18** | BYOK 与阶段 0「D1=C 不持有明文凭据」的边界 | **允许持有大模型 Key（加密），仍禁止持有平台账号凭据** | S5-6 |
| **D19** | 投流对外能力边界 | **仅策略助手，不执行**（用户已拍板） | S5-3 |
| **D20** | 人群画像数据来源 | **仅自有数据反推 + 标注可信度**（用户已拍板） | S5-2 |
| **D21** | 席位是否成为主要收入项 | **是**（订阅以席位为底座） | S5-5 |
| **D22** | 投流素材是否需要独立标识「广告合作」 | **需要**（合规要求，产品只提示不代替用户标注） | S5-3 |

### 6.1 商业模式冻结项（2026-10-31 二次决策，需补入 `STAGE0-FREEZE-DECLARATION.md`）

| ID | 决策 | 已定结论 | 说明 |
|---|---|---|---|
| **D23** | 现阶段是否开通收费 | **否** —— 全功能免费无限量 | 支付资质申请周期长，先跑通产品与用户 |
| **D24** | 免费期如何实现「将来能收费」 | **结构化预留 + 模式开关**：门装好、数据可换、代码零特判 | 详见 `STAGE5_MONETIZATION_RESERVE.md` |
| **D25** | 收费功能放哪 | **阶段 5 开工时创建分支 `feature/monetization-gating`**；主线上只放数据结构与门控骨架 | 避免长期分叉 |
| **D26** | 免费期是否保留用量配额 | **不限制**（用户已定：全功能无限量） | `daily_quota` / `period_quota` 写极大值，`overage_policy = ALLOW` |
| **D27** | 免费期是否保留计量台账 | **保留，且 `amount` 必须写 `0`（不能 NULL）** | 免费期是定价依据的采集期；NULL 会导致该行永久无法出账 |
| **D28** | BYOK 在免费期是否保留 | **保留，且平台 Key 兜底**（用户 Key 优先 → 平台 Key → Mock） | 用户明确要求「保留用户配置自己的大模型」 |
| **D29** | 后期切换开收的操作方式 | **合并收费分支 + 改套餐数据 + 翻 `app.billing.mode` 开关** | 业务代码零改动 |

> **D23–D29 是「商业模式冻结项」**，与阶段 0 的技术冻结项（`D1`–`D12`）同级别，需在
> `Xhs-Backend/docs/STAGE0-FREEZE-DECLARATION.md` 增补章节。

---

## 7. 风险矩阵

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R-S5-1 | 功能开关切面漏挂 | 付费功能对所有人开放（商业损失） | 「全量接口 × 开关」矩阵表逐条勾；测试反向覆盖 |
| R-S5-2 | 读实时套餐而非快照 | 改价影响存量订阅 | 强制读 `plan_version_snapshot` |
| R-S5-3 | 擅自往 `CORE_KEYS` 加键 | 破坏 price_book / forecast / comparison 三处 JOIN | 硬约束写入迁移说明；流量拆分走独立表 |
| R-S5-4 | 人群反推被当作「平台画像」外传 | 合规 + 信任风险 | UI 强制声明 + 敏感属性 BLOCKED 规则 + 文档口径 |
| R-S5-5 | 投流建议被误当执行承诺 | 客诉 + 平台关系风险 | 产品文案 + 接口返回 `execution_status` + 合同条款 |
| R-S5-6 | 付费素材合规要求低于自然内容 | 广告主被处罚 / 封户 | 独立 `PAID_MEDIA_RULES` 规则包 + 投放资质检查 |
| R-S5-7 | BYOK Key 泄露 | 用户真实资金损失 | AES-GCM + 掩码回显 + 日志脱敏 + 销毁联动 + 安全测试 |
| R-S5-8 | 用户 Key 静默回退烧平台预算 | 成本失控 | 失败直接报错，禁止回退 |
| R-S5-9 | 席位超限未拦住 | 收入漏损 | 成员加入路径统一校验 `seat_used < seat_quota` |
| R-S5-10 | 工作台 N+1 查询 | 首页慢 | 聚合查询 ≤ 5 次下游调用，加载测试 |
| R-S5-11 | `auto_renew` 重复扣费 | 客诉 | 幂等键 + 持久化队列 + 重复消费测试 |

---

## 8. 阶段5 整体出口条件

- [ ] **E1**：功能开关真实生效 —— **免费期口径**：FREE 工作区访问全部业务接口无 403，切面挂载自检「未挂载数 = 0」，代码零免费期特判；**收费期口径**：FREE 工作区调 PRO 接口返回 403（该反例用例在收费分支 CI 中执行）
- [ ] **E2**：套餐额度生效 —— `period_quota` 参与扣费校验，套餐档位差异在真实调用中可观测
- [ ] **E3**：投流策略可复算 —— 每条策略结论有 `evidence_refs` 且指向真实快照，素材变体全部过合规
- [ ] **E4**：人群可追溯 —— 每条匹配有证据、样本量、置信度，无「平台画像」表述
- [ ] **E5**：ROI 可算 —— 有流量拆分的数据能算出自然/付费占比与 ROI，缺失时诚实降级
- [ ] **E6**：工作台可用 —— 单请求 ≤ 5 下游查询，四态齐全，无 N+1
- [ ] **E7**：席位生效 —— 超限拒绝、账单含席位项、收入主体从「按次」转向「席位」
- [ ] **E8**：BYOK 安全 —— 并发不串味、明文 0 泄漏、失效有引导、销毁联动删除凭据
- [ ] **E9**：订阅自愈 —— `auto_renew` 续费成功，失败进 DLQ 不重复扣费
- [ ] **E10**：迁移幂等 —— 全部新增迁移事务内双跑通过，Flyway 连续校验 success=t
- [ ] **E11**：回归全绿 —— 阶段 0–4 三套 E2E 脚本全部 PASS，后端 `mvn clean package`、前端 `vue-tsc -b` 通过
- [ ] **E12**：合规评审通过 —— 法务确认投流边界话术、人群反推声明、BYOK 密钥保管责任
- [ ] **E13**：免费期全功能可用 —— FREE 工作区访问**全部**业务接口无 403，用量台账完整写入（`amount=0`）
- [ ] **E14**：预留完备性 —— 「接口 × 开关」矩阵 100% 覆盖；代码中**零**免费期特判（`grep` 检查无 `billingMode == FREE` 分支）
- [ ] **E15**：切换可演练 —— 在测试环境把 `APP_BILLING_MODE` 切到 `PARTIAL`，FREE 工作区调 PRO 接口返回 403，PAID 工作区返回 200，随后可一键切回 `FREE`
- [ ] **E16**：收费分支已建 —— `feature/monetization-gating` 分支存在，含切换手册，合并无业务代码冲突

---

## 9. 落地顺序与批次建议

| 批次 | 子能力 | 理由 | 预估 |
|---|---|---|---|
| **B0** | 模式开关 + FREE 套餐种子 + 能力门骨架（S5-0）+ L13/L14 | 「预留三件套」之**门 + 数据**；免费期也要装好 | 2–3 天 |
| **B1** | 计量台账口径统一 + L11（周期额度）+ 计量单位扩展 | 「预留三件套」之**计量**；为后期定价积累用量 | 2–3 天 |
| **B2** | S5-6（BYOK 全链路） | 用户明确要求「保留用户配置自己的大模型」 | 3–5 天 |
| **B3** | S5-1（流量维度） | ROI 前置，投流依赖 | 2–3 天 |
| **B4** | S5-2（人群洞察） | 差异化核心 | 3–4 天 |
| **B5** | S5-3（投流策略） | 卖点兑现 | 4–5 天 |
| **B6** | S5-5（席位与计价） | 收入模型 | 3–4 天 |
| **B7** | S5-4（运营工作台） | 把工具集变工作流产品 | 3–4 天 |
| **B8** | 收费分支 + 切换手册 + 矩阵终审（L12） | 预留收口 | 1–2 天 |

> **B0–B2 是「预留三件套」**：门 / 数据 / 计量。**B2 之后即使立刻停工去申请资质，也已经具备开收条件。**
> B3–B5 是**卖点**；B6–B7 是**形态**；B8 是**预留收口**。
> 详见 §12.1 与 `STAGE5_MONETIZATION_RESERVE.md` §13。

---

### 9.1 收费分支与切换手册（免费先行的落地形式）

> 用户已定：**阶段 5 开工时创建收费分支**，不在免费期建（避免长期分叉）。

| 项 | 值 |
|---|---|
| 分支名 | `feature/monetization-gating`（阶段 5 开工时建） |
| 基点 | 阶段 5 完成后的主线 |
| 存放内容 | 仅收费差异化：真实价目表数据、计费周期起点字段、出账过滤、席位超限校验、升级引导 UI |
| 不存放 | 业务功能本体（人群 / 投流 / 工作台 / BYOK 都在主线） |

**切换手册（资质到位后）**：

```bash
git checkout xhs_agent
git merge feature/monetization-gating

# 填真实价目表（数据）：app.subscription_plan / app.price_book / included_units
# 翻开关（配置）：APP_BILLING_MODE=PARTIAL   或   PAID
# 跑数据迁移（若需回填计费周期起点）

# 验证：FREE 工作区调 PRO 接口 -> 403 FEATURE_NOT_ENABLED
#         PAID 工作区调 PRO 接口 -> 200
#         账单金额 = 单价 x 用量（可复算）
```

**回滚预案**：开关配错 → 改配置重启（1 分钟）；单价填错 → `price_book` 版本化插新行；出账算错 → 冲正台账行。

> 详见 `STAGE5_MONETIZATION_RESERVE.md` §9。

---
## 10. 签字位

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 产品负责人 | | | |
| 技术负责人 | | | |
| 财务负责人 | | | |
| 法务 / 合规 | | | |
| 运营负责人 | | | |

---

## 11. 与既有文档的关系

| 文档 | 关系 |
|---|---|
| `STAGE5_MONETIZATION_RESERVE.md` | **本文的配套预留设计** —— 免费先行 + 收费预留的完整方案、切换手册、待确认项 Q1–Q10 |
| `COMMERCIAL_SAAS_READINESS_PLAN.md` | 上游（阶段 4 之后的新阶段，本文为其展开） |
| `STAGE4_PLAN.md` | 上游基线；本文 §2 盘点直接引用其资产表 |
| `STAGE4_STATUS.md` | 阶段 4 checkpoint；阶段 5 开工前需新建 `STAGE5_STATUS.md` |
| `Xhs-Backend/docs/PRD-V3.0.md` | **需更新** —— 现定位为「内容生成工具」，本文 §0 是新定位 |
| `Xhs-Backend/docs/STAGE0-FREEZE-DECLARATION.md` | **需更新** —— 增加 D13–D22 技术冻结项 + **D23–D29 商业模式冻结项**（§6.1） |
| `Xhs-Backend/docs/BILLING-RULES.md` | **需更新** —— `BillableUnit` 扩展 5 个单位 + 免费期台账口径（`amount=0`） |
| `CLAUDE.md` | 项目信息段落待更新（卖点从「内容生成」改为「运营决策闭环」；商业化阶段说明） |

---

## 12. 商业化预留索引（详见 STAGE5_MONETIZATION_RESERVE.md）

> 本节是主计划与预留文档之间的索引。**预留的完整设计、切换手册、待确认项一律以 `STAGE5_MONETIZATION_RESERVE.md` 为准。**

| 预留物 | 主计划对应位置 | 预留文档章节 | 免费期状态 | 收费期动作 |
|---|---|---|---|---|
| 能力门骨架（`@RequiresFeature` + 守卫 + 切面） | §3.1 S5-0 | §3 | 装好，恒放行 | 不改代码，改数据 |
| 模式开关 `app.billing.mode`（FREE / PARTIAL / PAID） | 新增（§0.1） | §4 | `FREE` | 改配置 |
| FREE 套餐种子（全开 flags + 极大额度 + 单价 0） | §3.8 L14 | §5 | 建好 | 改数据 |
| 计量台账免费期口径（`amount=0` 照写） | §3.6 / §3.8 L11 | §6 | 照常写 | 改出账下界 |
| 席位模型 `app.workspace_seat` | §3.6 S5-5 | §7 | 建表不启用 | 启用校验 |
| BYOK 凭据全链路 | §3.7 S5-6 | §8 | **本次真实实现** | 无需改 |
| 收费分支 `feature/monetization-gating` | §9.1 | §9 | 阶段 5 开工时建 | 合并 + 翻开关 |
| 「接口 × 开关」矩阵 | §5 | §10 | 建好并逐条标注 | 评审终审 |

### 12.1 批次顺序调整（预留优先）

| 批次 | 内容 | 性质 |
|---|---|---|
| **B0** | 模式开关 + FREE 套餐种子 + 能力门骨架 + 内部全功能账号（L13/L14） | 预留核心 |
| **B1** | 计量台账口径统一 + `period_quota` 接入（L11）+ 计量单位扩展 | 预留核心 |
| **B2** | BYOK 全链路（S5-6） | 本次真实功能（用户明确要求） |
| **B3** | 流量维度（S5-1） | 业务功能 |
| **B4** | 人群洞察（S5-2） | 业务功能 |
| **B5** | 投流策略（S5-3） | 业务功能 |
| **B6** | 席位表结构 + 计价扩展（S5-5） | 半预留 |
| **B7** | 运营工作台（S5-4） | 业务功能 |
| **B8** | 收费分支 + 切换手册终审 + 矩阵终审 | 预留收口 |

> **B0–B2 是「预留三件套」**：门 / 数据 / 计量。**B2 完成即具备开收条件**，即使此刻停止开发去申请资质也不影响。

### 12.2 待确认决策（Q1–Q10）

见 `STAGE5_MONETIZATION_RESERVE.md` §11。其中**影响迁移的 1 项**：

- **Q3**：计费周期起点如何记录 —— 用于解决「免费期 0 元台账行被批量出账」问题，需在 `app.subscription` 加字段。
