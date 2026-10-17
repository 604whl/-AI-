# 连接器授权清单（阶段 0 冻结）

> 阶段 0：产品冻结的一部分（COMMERCIAL_SAAS_READINESS_PLAN.md 第 12 节"需要冻结的产品决策"）。
> 与数据库表 `app.platform_connector_authorization`、`app.connector_provider` 一一对应。

## 授权等级

| 等级 | 描述 | 法务要求 | 写入能力 | 数据回流 |
|---|---|---|---|---|
| `OFFICIAL_API` | 平台提供的官方 API，已获商业授权 | 必须有书面授权函 | 是 | 自动 |
| `AUTHORIZED_CONNECTOR` | 经授权的第三方数据服务（如新榜、艺恩） | 第三方授权合同 + 法务签字 | 受限 | 受限 |
| `BROWSER_ASSIST` | 用户在平台页面确认，系统负责填充与回收 | 必须显著告知用户在做什么 | 是 | 半自动 |
| `MANUAL_IMPORT` | CSV / Excel / 截图 / 链接 / 媒体上传 | 最低 | 否 | 手动 |
| `DISABLED` | 明确禁止接入 | 平台政策或合规风险 | — | — |

## 平台 × 连接器 矩阵

| 平台 | 推荐连接器 | 等级 | 授权状态 | 法务签字日 | 截止日 |
|---|---|---|---|---|---|
| 小红书 | `XHS_BROWSER_SIDEBAR` | `BROWSER_ASSIST` | **暂批准** | 待签 | 待定 |
| 小红书 | `XHS_OFFICIAL_API` | `OFFICIAL_API` | 待申请 | — | — |
| 抖音 | `DOUYIN_CSV_IMPORT` | `MANUAL_IMPORT` | **已批准（默认）** | — | — |
| 微信公众号 | `WECHAT_CSV_IMPORT` | `MANUAL_IMPORT` | 待开通 | — | — |
| 视频号 | `WECHAT_CHAN_CSV_IMPORT` | `MANUAL_IMPORT` | 待开通 | — | — |
| B 站 | `BILIBILI_CSV_IMPORT` | `MANUAL_IMPORT` | 待开通 | — | — |
| 微博 | `WEIBO_CSV_IMPORT` | `MANUAL_IMPORT` | 待开通 | — | — |
| 知乎 | `ZHIHU_CSV_IMPORT` | `MANUAL_IMPORT` | 待开通 | — | — |
| 任何 | 任何 | `DISABLED` | **拒绝** | — | — |

## 默认拒绝原则

1. **未列入本表的平台**：运行时一律拒绝。法务/PM 签字后才能加入。
2. **平台政策禁止的连接器**：列入 `DISABLED`，运营禁止"寻找灰色实现"。
3. **跨境平台**（Instagram / TikTok）：默认 `DISABLED`，单独评估数据出境合规后再考虑。
4. **生效日**：本清单自签字日起生效；后续修改必须走变更流程（产品+法务双签字）。

## 看板要求

- 任何与平台、连接器、抓取、自动化发布有关的 PRD 都必须勾选对应平台 × 连接器。
- 没有勾选的能力一律视为"承诺未做的功能"。

## 签字位

| 角色 | 姓名 | 日期 |
|---|---|---|
| 产品负责人 | _____ | _____ |
| 运营负责人 | _____ | _____ |
| 法务/合规 | _____ | _____ |
| 技术负责人 | _____ | _____ |
