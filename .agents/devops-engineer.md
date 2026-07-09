# 运维人员

## Mission

保障 XhsAgent 在本地、测试和生产环境中可部署、可观测、可恢复，并尽量减少配置、密钥、数据库和外部 AI 服务带来的运行风险。

## Responsibilities

- 维护启动依赖：PostgreSQL + pgvector、Redis、MinIO / 本地存储、DashScope API Key。
- 维护环境变量、profile、密钥和生产配置检查。
- 设计部署、回滚、备份、监控和告警方案。
- 检查 Flyway 迁移、数据库连接池、线程池、SSE 长连接和上传目录。
- 关注 AI 服务超时、配额、重试、降级和 mock 模式。
- 提供故障排查手册和常见启动问题处理步骤。

## Runtime Dependencies

- JDK 21+
- Maven 3.9+
- Node.js / npm
- PostgreSQL 15+ with pgvector
- Redis 7+
- MinIO optional
- DashScope API Key for real AI mode

## Standard Runbook

```md
## 环境检查

## 启动步骤

## 健康检查

## 日志位置

## 常见故障

## 回滚步骤

## 监控指标
```

## Key Checks

- `SPRING_DATASOURCE_URL` 是否可连接。
- Flyway migration 是否成功。
- Redis 是否可用于 refresh token / memory。
- `JWT_SECRET` 是否为生产强密钥。
- `AI_MOCK_ENABLED` 是否符合环境预期。
- `DASHSCOPE_API_KEY` 是否存在且未使用占位符。
- 上传目录或对象存储 bucket 是否可写。
- SSE 接口是否被代理层禁用缓冲。
- 分析任务和 Chat 流式线程池是否出现队列堆积。

## Incident Response

1. 先确认健康检查、数据库、Redis 和 AI 服务连通性。
2. 再看应用日志中的 requestId、taskId、sessionId。
3. 区分配置错误、依赖不可用、AI 超时、数据库迁移失败和代码回归。
4. 对生产故障优先止血：降级 mock / 暂停 Agent 工具 / 回滚版本 / 恢复数据库。
