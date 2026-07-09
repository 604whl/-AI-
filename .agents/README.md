# XhsAgent Project Roles

本目录定义当前项目的协作角色。每个角色文件都可以作为独立 Agent / 人员职责说明使用，也可以复制到任务提示词中。

## Core Subagents

按照 `CLAUDE(1).md` 的三角色协作要求，当前项目以团队管理视角运行：

| 角色 | Agent ID | 职责 | 是否写代码 |
|------|----------|------|------------|
| [项目经理](project-manager.md) | `project-manager` | 需求分析、任务拆解、范围控制、验收标准、给开发派发任务 | 不写代码 |
| [全栈开发工程师](fullstack-developer.md) | `fullstack-developer` | 数据库、后端、前端、AI 能力、Bug 修复和基础验证 | 全链路开发 |
| [代码审核员](code-reviewer.md) | `code-reviewer` | 审查全栈开发产出的代码，检查质量、风险、安全和测试缺口 | 只审不写 |

## Dispatch Flow

收到非简单任务（超过 3 步）时，按任务类型选择角色链：

| 任务类型 | 流程 |
|----------|------|
| 新需求 | `project-manager` -> `fullstack-developer` -> `code-reviewer` |
| Bug 修复 | `fullstack-developer` -> `code-reviewer` |
| 代码优化 | `fullstack-developer` -> `code-reviewer` |
| 需求分析 | `project-manager` |
| 技术调研 | `fullstack-developer` |
| 代码审查 | `code-reviewer` |

## Bug Fast Track

当用户明确表示是 Bug 修复，例如“修个 bug”“这里报错”“线上有问题”“XX 功能不工作”，默认跳过项目经理：

1. 统筹者快速定位报错信息和相关文件。
2. `fullstack-developer` 定位根因并修复。
3. `code-reviewer` 审查改动风险。

例外：如果 Bug 影响面不清晰、需要先判断产品边界或涉及大范围重构，再补充 `project-manager` 分析环节。

## Dispatch Prompt Requirements

派发给任一角色时，prompt 必须尽量包含：

- 任务背景：用户要做什么、为什么要做。
- 项目上下文：相关模块、当前约束、已有实现。
- 具体要求：明确输入、输出和不做什么。
- 相关文件：直接给路径，减少角色从零探索。
- 期望返回格式：PRD、实现摘要、审查清单或验证结果。

## Simple Task Rule

能在 3 步以内完成的任务，例如单文件小改、快速查询、简单文案调整，不派发角色链，由统筹者直接完成。

## Boundaries

- `project-manager` 不写代码。
- `fullstack-developer` 负责全链路实现，不把前后端再拆散。
- `code-reviewer` 只审不写，发现问题反馈给开发修复。
- 任何角色都不能回滚用户已有未提交改动。

## Legacy Role Notes

本目录中保留的 [产品经理](product-manager.md)、[全栈开发工程师](full-stack-engineer.md)、[运维人员](devops-engineer.md) 可作为补充职责说明。实际三角色派发优先使用上方 Core Subagents。

## Project Context

项目是小红书留学生求职 AI 运营助手，包含：

- 后端：Spring Boot / Java 21 / Spring AI / DashScope / MyBatis-Plus / PostgreSQL + pgvector / Redis / MinIO。
- 前端：Vue 3 / TypeScript / Vite / Element Plus / Pinia / vue-i18n。
- 核心能力：笔记分析、标题生成、优化稿、正文生成、封面分析、合规扫描、Agent 对话、RAG 知识库、MCP 工具暴露。
