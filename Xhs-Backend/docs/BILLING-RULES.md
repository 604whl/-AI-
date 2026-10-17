# 计费规则

> 阶段 0：产品冻结的一部分（COMMERCIAL_SAAS_READINESS_PLAN.md 第 9 节）。
> 与 `com.shortvideoscripagent.xhsagentyunying.freeze` 包中的 `BillableUnit` / `BillableEvent` 枚举一一对应。

## 计费计量单位

每个单位对应"一次 AI 操作的可计费原子"。

| 单位 | 适用场景 | 失败重试是否扣费 | 典型成本系数 |
|---|---|---|---|
| `ANALYSIS` | 单篇内容分析 | 否 | 1.0 |
| `TITLE_GENERATION` | 标题生成（单次请求） | 否 | 0.2 |
| `BODY_GENERATION` | 正文生成（单次请求） | 否 | 0.6 |
| `COVER_ANALYSIS` | 封面分析 | 否 | 0.4 |
| `COMPLIANCE_SCAN` | 合规扫描 | 否 | 0.05 |
| `EXPORT` | 内容/报告导出 | 否 | 0.05 |
| `EMBEDDING` | 嵌入（KAG/RAG 写入） | 否 | 0.1 |
| `SEARCH` | 联网搜索 | 否 | 0.1 |

> 默认按 workspace 计费；用户级配额兜底。
> 失败重试一律退还（REFUND）后再发起新扣费（DEBIT），禁止"先重试再扣费"导致重复扣款。

## 计费事件

| 事件 | 方向 | 何时写入 |
|---|---|---|
| `DEBIT` | -1 单位 | 用户成功调用一次 AI 操作（业务返回成功） |
| `REFUND` | +1 单位 | 调用失败、被超时、用户取消、合规阻断 |
| `WRITE_OFF` | 0 | 因重复请求撤销的差额（与 `DEBIT` 对冲） |
| `ADJUSTMENT` | ± | 运营/客服补偿调整，必须有 reason |

## 强制规则

1. **原子扣费**：`UPDATE ... WHERE used < quota` 必须返回影响行数；行数 = 0 视为失败。
2. **可审计**：`usage_ledger` 必须记录 user_id, workspace_id, action, units, event_type, reason, request_id, created_at。
3. **可冲正**：DEBIT 必能对冲 REFUND 或 WRITE_OFF，一周内日终核对。
4. **禁止重复扣费**：同一 request_id 不允许两条 DEBIT。
5. **失败零扣费**：AsyncTask 失败 → 自动 REFUND，不依赖人工对账。
6. **跨事件对账**：每日凌晨跑对账任务，对账不上立刻告警。

## 与产品冻结的关系

- 每个用户/Workspace 在创建时会获得初始配额（来自订阅档位）。
- 配额使用 `usage_ledger` 累计，不代表"账户余额"，避免与定价模型耦合。
- 计费事件的语义与"产品是否要付费"脱耦——可以先全部免费跑通，再启动订阅。

## 与状态机的关系

| 行为 | 是否计费 |
|---|---|
| 创建草稿 | 否 |
| AI 生成正文 | DEBIT BODY_GENERATION |
| 内容被合规 BLOCKED | REFUND（已生成也要退，因为没有可用产出） |
| 内容送审 | 否 |
| 内容被人工 REJECTED | 否 |
| 内容发布 | 否 |

## 与工作区的关系

- Workspace 配额优先级 > User 个人配额；个人配额只用于"未分配 workspace 的孤儿用户"。
- 计费额度只在 OWNER/ADMIN 调整时同步给子 member。

## 签字位

| 角色 | 姓名 | 日期 |
|---|---|---|
| 产品负责人 | _____ | _____ |
| 财务负责人 | _____ | _____ |
| 技术负责人 | _____ | _____ |
