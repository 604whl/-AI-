# 阶段 0：产品冻结声明

**项目名称**：XhsAgent 小红书留学生求职 AI 运营助手
**声明日期**：2026-10-17
**依据文档**：`COMMERCIAL_SAAS_READINESS_PLAN.md` 第 9 节
**冻结依据**：`docs/STAGE0-FREEZE-PLAN.md`

---

## 声明范围

自本声明签字生效日起，以下产品决策视作**已冻结基线**，任何变动必须经产品+运营+法务三方签字后走变更流程。

1. **平台适配矩阵**：见 `docs/STAGE0-FREEZE-PLAN.md` 第 6 节 + `V34__stage0_freeze.sql` `app.platform` 表。
2. **内容状态机**：见 `freeze/ContentStatus.java` + `docs/STAGE0-FREEZE-PLAN.md` 状态转换图。
3. **工作区模型**：见 `workspace/` 包 + `app.workspace`、`app.workspace_member` 表。
4. **角色模型**：5 级 `OWNER/ADMIN/EDITOR/REVIEWER/VIEWER`，见 `workspace/WorkspaceRole.java`。
5. **计费口径**：见 `freeze/BillableUnit.java` + `BillableEvent.java` + `docs/BILLING-RULES.md`。
6. **品类规则包**：基线 8 个品类已冻结，风险等级已写入 `app.category` 表。
7. **合规边界**：6 条内置规则见 `freeze/ComplianceScanner.java` + `app.compliance_rule` 表。
8. **数据保留**：默认策略见 `app.data_retention_policy`。
9. **连接器授权清单**：见 `docs/CONNECTOR-AUTHORIZATION.md` + `app.platform_connector_authorization` 表。

## 代码资产

| 模块 | 路径 | 状态 |
|---|---|---|
| 平台枚举 | `com.shortvideoscripagent.xhsagentyunying.freeze.PlatformCode` | ✅ |
| 连接等级 | `com.shortvideoscripagent.xhsagentyunying.freeze.PlatformConnectorLevel` | ✅ |
| 内容状态机 | `com.shortvideoscripagent.xhsagentyunying.freeze.ContentStatus` | ✅ |
| 风险等级 | `com.shortvideoscripagent.xhsagentyunying.freeze.RiskLevel` | ✅ |
| 计费计量 | `com.shortvideoscripagent.xhsagentyunying.freeze.BillableUnit` | ✅ |
| 计费事件 | `com.shortvideoscripagent.xhsagentyunying.freeze.BillableEvent` | ✅ |
| 数据分类 | `com.shortvideoscripagent.xhsagentyunying.freeze.DataClass` | ✅ |
| 合规扫描器 | `com.shortvideoscripagent.xhsagentyunying.freeze.ComplianceScanner` | ✅ |
| Stage0 服务 | `com.shortvideoscripagent.xhsagentyunying.freeze.Stage0Service` | ✅ |
| Stage0 仓储 | `com.shortvideoscripagent.xhsagentyunying.freeze.Stage0Repository` | ✅ |
| Stage0 接口 | `com.shortvideoscripagent.xhsagentyunying.freeze.Stage0Controller` | ✅ |
| 数据库迁移 | `V34__stage0_freeze.sql` | ✅ |

## 出口条件验收

- [x] 平台适配矩阵 PLATFORM_MATRIX 在 DB 中写入
- [x] 内容状态机 21 条转换枚举完整
- [x] 计量单位 / 计费事件 enum 与文档一致
- [x] 品类规则包 8 个基线品类初始化
- [x] 6 条合规规则初始化
- [x] 数据保留默认策略
- [x] 连接器授权清单 7 条
- [ ] **签字位**：见下方 —— 占位待人填

## 签字位

> 签字代表确认"以上基线作为发布版本对外承诺"，未来调整必须走变更流程。

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 产品负责人 | _____ | _____ | _____ |
| 运营负责人 | _____ | _____ | _____ |
| 法务/合规 | _____ | _____ | _____ |
| 技术负责人 | _____ | _____ | _____ |

## 变更记录

| 版本 | 日期 | 变更摘要 | 申请单 | 签字 |
|---|---|---|---|---|
| 1.0.0 | 2026-10-17 | 阶段 0 冻结 | STAGE0-FREEZE-001 | （待签） |
| _____ | _____ | _____ | _____ | _____ |

---

*本文件归档在 `docs/STAGE0-FREEZE-DECLARATION.md`，发布版本必须引用此基线。*
