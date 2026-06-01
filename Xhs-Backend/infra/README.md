# 本地基础设施

## 启动

```bash
cd f:/projectAI/XhsAgent/Xhs-Backend
docker compose -f infra/docker-compose.yml up -d
```

## 连接信息

| 服务 | 地址 | 账号 |
|------|------|------|
| PostgreSQL | `localhost:5432/xhsagent` | `xhsagent` / `xhsagent_dev` |
| Redis | `localhost:6379` | 无密码 |
| MinIO API | `http://localhost:9000` | `minioadmin` / `minioadmin` |
| MinIO Console | `http://localhost:9001` | 同上 |

## 阿里云 RDS（生产）

1. 创建 PostgreSQL 实例，白名单放 ECS。  
2. 执行 `V1__init_schema.sql`（或通过 Flyway）。  
3. 确认支持 `CREATE EXTENSION vector`。  
4. 将连接串配置到环境变量 `SPRING_DATASOURCE_URL`。

## 启用真实 AI（DashScope）

1. 在 [阿里云百炼 / DashScope](https://help.aliyun.com/zh/model-studio/) 创建 API Key。  
2. **推荐（本地一次配置）**：编辑 `src/main/resources/application-local.yml`（已在 `.gitignore`），将 `spring.ai.dashscope.api-key` 改为你的 `sk-...`，并按控制台调整 `model` / `vision-model`。保存后直接 `mvn spring-boot:run`，**无需每次启动前设环境变量**。  
3. 启动后端，日志应出现：`Real AI enabled: DashScope model=qwen-turbo, mock disabled`  
4. 若 Key 未配置且 `AI_MOCK_ENABLED=false`，分析任务会失败并提示 `ai_not_configured`。  
5. **可选**：用 `scripts/local-ai-env.ps1` 通过环境变量覆盖 yml（见 `local-ai-env.ps1.example`）。

离线开发可设 `AI_MOCK_ENABLED=true` 使用样例报告，无需 Key。
