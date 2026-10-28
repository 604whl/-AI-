# 阶段 0：产品冻结 实施计划

**编制时间**：2026-10-17
**依据**：`COMMERCIAL_SAAS_READINESS_PLAN.md` 第 9 节
**目标**：把"全平台全品类"的边界和规则全部固化下来，形成可执行、可校验、不允许后续随意漂移的产品基线。

---

## 背景

当前项目已经完成 AI 统一、部署护栏、多租户、任务队列、原子配额、内容资产、模板、导出、审核流等阶段 1 P0 功能。
但"全平台全品类"仍是模糊概念：未明确首发哪些平台、未冻结状态机、未冻结品类规则、未冻结合规边界。
如果继续按"加 AI 页面"推进，将持续违背"内容运营操作系统"的定位。

## 目标用户

- 商业化决策者（Owner）：需要看到"我们到底卖什么、能力边界"
- 运营/法务/合规：需要看到"哪些平台、哪些品类、哪些高风险品类禁发"
- 开发/AI 工程师：需要看到"状态机、规则包、计费口径是固定约束，禁止漂移"

## 用户故事

| ID | 故事 |
|---|---|
| US-0-1 | 作为决策者，我想看到一份"平台连接等级矩阵"，明确知道每个平台我们承诺什么、不承诺什么 |
| US-0-2 | 作为运营，我想创建一篇内容时，系统强制按状态机流转，不允许直接"草稿 → 已发布" |
| US-0-3 | 作为分析师，我想看到品类规则包的版本，避免我每次问"那这条规则还是当前版本吗？" |
| US-0-4 | 作为法务/合规，我想知道哪些品类是高风险、必须人工审核、不能自动生成 |
| US-0-5 | 作为财务，我想知道每个 AI 操作对应的"计费单位"，便于做配额和账单 |

## 范围（必须完成）

1. **平台适配冻结**
   - 数据库 `platform` 表 + 平台适配器枚举
   - 平台连接等级枚举（OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT）
   - 平台 × 等级 × 能力矩阵（哪些平台首批支持、哪些是规划中、哪些禁用）

2. **内容状态机冻结**
   - 状态枚举：`DRAFT` / `IN_REVIEW` / `APPROVED` / `SCHEDULED` / `PUBLISHED` / `ARCHIVED` / `REJECTED`
   - 状态转换图（允许的转换 + 触发条件）
   - 后端 `ContentStateMachine` 校验器（强制转换不允许跳过审核）

3. **工作区模型** ✅ 已冻结（阶段 1 P0-2 完成）
   - `workspace` / `workspace_member` / 5 角色

4. **角色模型** ✅ 已冻结（阶段 1 P0-2 完成）

5. **计费口径冻结**
   - 计量单位枚举：`ANALYSIS` / `TITLE_GENERATION` / `BODY_GENERATION` / `COVER_ANALYSIS` / `EXPORT` / `EMBEDDING` / `SEARCH`
   - 计费事件枚举：`DEBIT` / `REFUND` / `WRITE_OFF` / `ADJUSTMENT`
   - 计费规则文档 `docs/BILLING-RULES.md`

6. **品类规则包冻结**
   - 数据库 `category` / `category_rule_pack` / `category_score_dimension` 表
   - 规则包 JSON Schema（目标人群、表达禁区、行业词表、CTA、指标权重、Prompt 模板引用）
   - 评分维度枚举（运营可配置）
   - 首批 10 个品类基线：教育与留学、求职与职业发展、K12 教育、医疗与健康、金融理财、美妆与医美、法律具体案件、生活方式、移民签证、其他

7. **合规边界冻结**
   - `compliance_rule` / `compliance_rule_version` 表
   - 风险等级枚举：`LOW` / `MEDIUM` / `HIGH` / `BLOCKED`
   - 高风险品类禁用清单（在品类规则包里标识）
   - 合规扫描器接口版本化

8. **数据保留策略冻结**
   - `data_retention_policy` 表
   - 默认策略：内容 365 天、媒体 180 天、报告 180 天、操作日志 365 天、删除请求 90 天
   - 可按 workspace 覆盖

9. **平台连接器授权清单**
   - 数据库 `connector_provider` / `platform_connector_authorization` 表
   - 授权清单文档 `docs/CONNECTOR-AUTHORIZATION.md`
   - 默认拒绝未授权抓取

## 非目标（本阶段不做）

- 不开发连接器实现（属于阶段 3）
- 不做实际计费扣费（阶段 4）
- 不做热点采集（阶段 3）
- 不实现浏览器辅助（阶段 2 / 3）

## 任务拆解（按派发顺序）

| 任务 | 范围 | 涉及文件 | 验收 |
|---|---|---|---|
| **A. PM 任务包** | 决策矩阵 + 文档 | `docs/STAGE0-FREEZE-PLAN.md` | 本文档完成 |
| **B. 数据库 Schema** | 9 张表 + Flyway 迁移 | `Xhs-Backend/src/main/resources/db/migration/V36__stage0_freeze.sql` | Flyway 迁移可重复执行、不破坏现有数据 |
| **C. 后端枚举与校验器** | 7 个枚举 + 1 个状态机 + 1 个规则接口 | `domain/enums/` + `domain/freeze/` | Java 编译通过 + 单测 |
| **D. 连接器授权清单** | 文档 + 枚举映射 | `docs/CONNECTOR-AUTHORIZATION.md` | 文档签字位 |
| **E. 阶段 0 冻结声明** | 文档 + 签字位 | `docs/STAGE0-FREEZE-DECLARATION.md` | 决策者/运营/法务签字位 |

## 验收标准（出口条件）

- [ ] 平台适配矩阵 `PLATFORM_MATRIX` 有最终值，首发 2 个平台（小红书 + 抖音），其余 5 个标注"规划中"
- [ ] 状态机在 `ContentStateMachine.canTransition(from, to)` 单测通过 21 条用例
- [ ] 计量单位 + 计费事件枚举定义清晰，文档说明每个单位对应一次 AI 调用
- [ ] 品类规则包 JSON Schema 通过验证器解析
- [ ] 7 张新表 Flyway 迁移成功，写入示例数据后能查询
- [ ] 高风险品类清单（医疗、金融、教育-未成年、广告法绝对化）写在规则包表
- [ ] 数据保留默认值生效
- [ ] 连接器授权清单文档签字就位（占位字段，运营/法务后续替换）
- [ ] 后端 `mvn compile` 通过；前端无新代码改动（编译无需重新跑）

## 接口 / 数据影响

### 新增枚举（后端）

```java
public enum PlatformCode { XIAOHONGSHU, DOUYIN, WECHAT, BILIBILI, WEIBO, KUAISHOU, ZHIHU, INSTAGRAM, TIKTOK }
public enum ConnectorLevel { OFFICIAL_API, AUTHORIZED_CONNECTOR, BROWSER_ASSIST, MANUAL_IMPORT }
public enum ContentStatus { DRAFT, IN_REVIEW, APPROVED, SCHEDULED, PUBLISHED, ARCHIVED, REJECTED }
public enum BillableUnit { ANALYSIS, TITLE_GENERATION, BODY_GENERATION, COVER_ANALYSIS, EXPORT, EMBEDDING, SEARCH }
public enum BillableEvent { DEBIT, REFUND, WRITE_OFF, ADJUSTMENT }
public enum RiskLevel { LOW, MEDIUM, HIGH, BLOCKED }
public enum DataClass { CONTENT, MEDIA, REPORT, AUDIT_LOG, DELETION_REQUEST }
```

### 新增表（V36 Flyway）

```sql
platform
category
category_rule_pack
category_score_dimension
compliance_rule
compliance_rule_version
data_retention_policy
connector_provider
platform_connector_authorization
```

## 风险与依赖

- **风险 1**：当前 `analysis_task` / `content_asset` 表没有 `status` 状态机字段，阶段 0 在不破坏现有数据前提下**只冻结规则**，不强制迁移。
- **风险 2**：阶段 1 已经冻结了工作区/角色，但本次需要明确"计费口径"与之关系：默认按 workspace 计费，按用户兜底。
- **依赖**：阶段 1 已完成的 workspace 表；阶段 1 已完成的 quota_usage_log（用于计费事件字段）。

## 优先级

**P0（必须）**：任务 B（数据库 schema）+ C（后端枚举）+ D（授权清单）
**P1（应该）**：任务 A 文档完善 + E 冻结声明签字位
**P2（可选）**：初始化数据 seed（基线 6 个品类 + 默认保留策略）

---

## 状态转换图

```
                ┌─────────┐
                │  DRAFT  │
                └────┬────┘
                     │ submit
                     ▼
              ┌─────────────┐
       ┌──────│  IN_REVIEW  │──────┐
       │      └─────────────┘      │
   approve│                     reject
       │      │      │      │      │
       │    reject  request_changes
       ▼      ▼      ▼      ▼      ▼
   ┌────────┐ ┌──────────────┐ ┌─────────────┐
   │APPROVED│ │   REJECTED   │ │ (back to    │
   └───┬────┘ └──────────────┘ │  DRAFT or   │
       │ schedule              │   ARCHIVED) │
       ▼                       └─────────────┘
   ┌──────────┐
   │SCHEDULED │
   └──┬───────┘
      │ publish（success）
      ▼
  ┌──────────┐  archive   ┌──────────┐
  │PUBLISHED │──────────▶│ARCHIVED  │
  └──────────┘            └──────────┘
```

非法转换（必须拒绝）：
- `DRAFT → PUBLISHED`（绕过审核）
- `PUBLISHED → DRAFT`（已发不可改）
- `ARCHIVED → *`（归档只能新版本）
- 任何 `→ IN_REVIEW` 无 reviewer 上下文

---

## 平台 × 连接矩阵（冻结首发版本）

| 平台 | 连接等级 | 写入能力 | 数据回流 | 状态 | 备注 |
|---|---|---|---|---|---|
| 小红书 | BROWSER_ASSIST → AUTHORIZED_CONNECTOR | 半自动 | 半自动 | **首发** | 复用浏览器侧边栏模式；待获取授权后升级 |
| 抖音 | MANUAL_IMPORT | 无 | 手动 CSV | **首发** | 不抓取，强制人工 |
| 微信公众号 | MANUAL_IMPORT | 无 | 手动 CSV | 规划中 | 后续接授权 API |
| 视频号 | MANUAL_IMPORT | 无 | 手动 CSV | 规划中 | 同上 |
| B 站 | MANUAL_IMPORT | 无 | 手动 CSV | 规划中 | |
| 微博 | MANUAL_IMPORT | 无 | 手动 CSV | 规划中 | |
| 知乎 | MANUAL_IMPORT | 无 | 手动 CSV | 规划中 | |
| 快手 | — | — | — | **禁用** | 平台政策禁止第三方工具 |
| Instagram | — | — | — | **禁用** | 跨境合规风险 |
| TikTok | — | — | — | **禁用** | 同上 |

任何未在此表中的平台，**默认拒绝接入**，必须经过 PM + 法务签字。

---

## 高风险品类禁用清单（必须人工审核）

| 品类 | 风险 | 处理 |
|---|---|---|
| 医疗（疾病、药品、保健品） | BLOCKED | 不允许自动生成，仅允许人工录入 |
| 金融（投资、收益、贷款） | BLOCKED | 同上 |
| 教育（K12 未成年人） | HIGH | 必须人工审核，禁止自动发布 |
| 法律（具体案件） | BLOCKED | 不允许自动生成 |
| 减肥/美容（医美） | HIGH | 必须人工审核 |
| 移民 | MEDIUM | 风险词提示，不阻断 |

---

## 签字位

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 产品负责人 | _____ | _____ | _____ |
| 运营负责人 | _____ | _____ | _____ |
| 法务/合规 | _____ | _____ | _____ |
| 技术负责人 | _____ | _____ | _____ |

> 签字前不得发布新连接器、新平台接入、新品类规则包。
