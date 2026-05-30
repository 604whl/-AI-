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
2. 设置环境变量（推荐，勿把 Key 提交到 Git）：

```powershell
# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-你的Key"
$env:AI_MOCK_ENABLED="false"
```

```bash
# Linux / macOS
export DASHSCOPE_API_KEY=sk-你的Key
export AI_MOCK_ENABLED=false
```

3. 或复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml` 并填入 Key。  
4. 启动后端，日志应出现：`Real AI enabled: DashScope model=qwen-plus, mock disabled`  
5. 若 Key 未配置且 `AI_MOCK_ENABLED=false`，分析任务会失败并提示 `ai_not_configured`。

离线开发可设 `AI_MOCK_ENABLED=true` 使用样例报告，无需 Key。
