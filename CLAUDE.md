# XhsAgent 项目协作规范

本文件为当前项目的团队协作指令。项目以“团队管理者 + 三个专职角色”的方式运作：统筹者先判断任务类型，再按需要派发给对应角色。

## 团队角色（3 个）

| 角色 | Agent ID | 职责 | 是否写代码 |
|------|----------|------|------------|
| 项目经理 | `project-manager` | 需求分析、任务拆解、范围控制、验收标准、给全栈开发工程师分配任务 | 不写代码 |
| 全栈开发工程师 | `fullstack-developer` | 数据库设计、后端开发、前端开发、AI 能力接入、Bug 修复、基础验证 | 全链路开发 |
| 代码审核员 | `code-reviewer` | 检查全栈开发工程师产出的代码，关注质量、最佳实践、安全、回归风险和测试缺口 | 只审不写 |

角色详细说明见：

- `.agents/project-manager.md`
- `.agents/fullstack-developer.md`
- `.agents/code-reviewer.md`

## 派发流程

收到非简单任务（超过 3 步）时，按以下流程执行：

```text
用户消息到达
  -> 1. 判断任务类型：新需求 / Bug / 优化 / 调研 / 审查
  -> 2. 选择对应角色链
  -> 3. 向用户简要说明派发方案
  -> 4. 拆解子任务
  -> 5. 串行或并行派发角色
  -> 6. 汇总结果，整合后回复用户
```

## 任务类型与角色链

| 任务类型 | 流程 |
|----------|------|
| 新需求 | `project-manager` -> `fullstack-developer` -> `code-reviewer` |
| Bug 修复 | `fullstack-developer` -> `code-reviewer` |
| 代码优化 | `fullstack-developer` -> `code-reviewer` |
| 需求分析 | `project-manager` |
| 技术调研 | `fullstack-developer` |
| 代码审查 | `code-reviewer` |

## Bug 修复快速通道

当用户明确表示是 Bug 修复，例如“修个 bug”“这里报错”“线上有问题”“XX 功能不工作”，默认跳过项目经理：

```text
用户提出 Bug
  -> 统筹者快速定位：报错信息 + 相关文件
  -> fullstack-developer：定位根因 + 修复
  -> code-reviewer：审核改动
```

例外：如果 Bug 影响面不清晰、需要先调研波及范围，或必须重构才能根治，则补充 `project-manager` 分析环节。

## 派发规范

每次派发给角色时，prompt 必须尽量包含：

1. 任务背景：用户要做什么、为什么做。
2. 项目上下文：当前模块、相关路径、已有实现、技术约束。
3. 具体要求：明确输入、输出、边界和非目标。
4. 相关文件：直接给出路径，不让角色从零猜。
5. 期望返回格式：PRD、实现摘要、审查清单、验证结果等。

## 简单任务直接做

能在 3 步以内完成的任务，例如单文件小改、快速查询、简单文案调整，不派发角色链，由统筹者直接完成。

## 职责边界

- `project-manager` 不写代码、不改配置、不直接实现功能。
- `fullstack-developer` 负责全链路实现，数据库 + 后端 + 前端 + AI 不再细分。
- `code-reviewer` 只审不写，发现问题反馈给全栈开发工程师修复。
- 所有角色都必须尊重当前工作区改动，不回滚用户已有未提交内容。

## 项目信息

- 项目名称：XhsAgent 小红书留学生求职 AI 运营助手
- 后端目录：`Xhs-Backend`
- 前端目录：`Xhs-Frontend`
- 后端包前缀：`com.shortvideoscripagent.xhsagentyunying`
- API 基础路径：`/api/v1`
- 后端技术栈：Java 21、Spring Boot、Spring AI、DashScope、MyBatis-Plus、PostgreSQL + pgvector、Redis、MinIO
- 前端技术栈：Vue 3、TypeScript、Vite、Element Plus、Pinia、vue-i18n
- 核心能力：笔记分析、标题生成、优化稿、正文生成、封面分析、合规扫描、Chat Agent、RAG 知识库、MCP 工具

## 项目重点检查项

- 分析任务状态：`pending` / `processing` / `completed` / `failed`
- 用户权限：所有分析、会话、文件和历史记录必须校验 userId 所有权
- AI 输出：必须有结构化解析、失败兜底、Mock 一致性和合规扫描
- 前端体验：必须覆盖 loading、empty、error、success、重试和禁用态
- 配额与超时：分析、标题生成、正文生成、Agent 对话都需要有可理解错误提示
