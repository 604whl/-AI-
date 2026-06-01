# 小红书留学生求职 AI 运营助手 — 需求文档包

本目录为产品与架构文档。实现代码见 `XhsAgent-yunying`（后端）与 `Xhs-Frontend`（前端）。开发前请先阅读 [PRD-V3.0.md](./PRD-V3.0.md) 与 [TECH-ARCHITECTURE.md](./TECH-ARCHITECTURE.md)。

## 文档清单

| 文档 | 状态 | 说明 |
|------|------|------|
| [PRD-V3.0.md](./PRD-V3.0.md) | 已定稿 | 产品需求：分期、用户故事、验收标准 |
| [wireframe-report.md](./wireframe-report.md) | 已定稿 | 核心报告页线框与交互 |
| [prompt-rubric.md](./prompt-rubric.md) | 已定稿 | 五维评分 + 六类内容 Rubric（`rubric-1.0.0`） |
| [seed-cases.md](./seed-cases.md) | 已定稿 | 20 篇评测种子案例说明 |
| [seed-cases.csv](./seed-cases.csv) | 已定稿 | 种子案例表格（可导入 Excel） |
| [api-schema/](./api-schema/) | 已定稿 | JSON Schema + 错误码（`api-1.0.0`） |
| [TECH-ARCHITECTURE.md](./TECH-ARCHITECTURE.md) | 已定稿 | 全栈技术架构（`arch-1.0.0`） |
| [RAG-DESIGN.md](./RAG-DESIGN.md) | 已定稿 | PostgreSQL + pgvector RAG |
| [AGENT-TOOL-CALLING.md](./AGENT-TOOL-CALLING.md) | 开发中 | Agent 工具调用、记忆、对话 API（`agent-1.0.0`） |
| [../infra/README.md](../infra/README.md) | 已定稿 | 本地 Docker 环境 |

## 推荐阅读顺序

1. PRD V3.0 → 2. TECH-ARCHITECTURE → 3. 线框图 → 4. Rubric → 5. API Schema → 6. 种子案例评测

## 版本对应

- Prompt/Rubric：`rubric-1.0.0`
- API 契约：`api-1.0.0`
