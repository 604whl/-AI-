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
