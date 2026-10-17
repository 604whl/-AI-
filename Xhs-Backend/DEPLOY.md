# XhsAgent 部署指南（最小可用）

## 1. 部署前置

- 域名 + SSL 证书（强制 https，CORS 不接受 http 跨域）
- 一个 PostgreSQL 14+ 数据库（建议阿里云 RDS / 自建带 pgvector）
- 一个 Redis 实例
- 一个对象存储（MinIO / 阿里云 OSS / AWS S3）

## 2. 必填环境变量清单

> 缺失任意一项 → 启动失败。

| 变量 | 必填 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | 必须是 `prod` |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | ✅ | PostgreSQL 连接 |
| `REDIS_HOST/PORT` | ✅ | Redis 连接 |
| `JWT_SECRET` | ✅ | ≥ 32 字节随机字符串；`openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | ✅ | 形如 `https://app.example.com`，**不接受 localhost** |
| `DEEPSEEK_API_KEY` | ✅ | DeepSeek API Key |
| `MINIO_*` | ✅ | 对象存储 |
| `STORAGE_TYPE` | ✅ | `minio` 或 `local` |

## 3. 启动方式

### 方式 A：Docker Compose（推荐用于小团队自部署）

```bash
cd Xhs-Backend
cp .env.example .env
# 编辑 .env 填写真实值
docker compose up -d --build
docker compose logs -f app
```

健康检查：`curl https://api.example.com/api/v1/health`
- 期望：`{"status":"UP","components":{"db":...,"redis":...}}`

### 方式 B：直接运行 JAR

```bash
cd Xhs-Backend
./mvnw -B -ntp -DskipTests package
export $(grep -v '^#' .env | xargs)
java -jar target/XhsAgent-yunying-*.jar
```

## 4. 前端部署

```bash
cd Xhs-Frontend
cp .env.example .env.production
# 编辑 VITE_API_BASE_URL
npm install
npm run build
# dist/ 目录交给 nginx，配置代理 /api 到后端
```

## 5. 启动护栏（防呆）

应用启动时自动检查：
- ✅ 未设 `SPRING_PROFILES_ACTIVE` → 拒绝启动
- ✅ JWT_SECRET 是默认值 → 拒绝启动
- ✅ CORS 包含 localhost / 127.0.0.1 → 拒绝启动
- ✅ AI_MOCK_ENABLED=true → 拒绝启动
- ✅ DEEPSEEK_API_KEY 缺失 → 拒绝启动

## 6. 上线 checklist

- [ ] 修改 `application-prod.yml` 中的 `cors.allowed-origins` 为真实域名
- [ ] 生成强随机 JWT_SECRET 并放入密钥管理
- [ ] 确认 DeepSeek API Key 有效、账户余额充足
- [ ] 备份策略：PostgreSQL 每日快照、MinIO 桶跨区复制
- [ ] HTTPS 证书自动续期（Let's Encrypt / 阿里云）
- [ ] Nginx 限流（建议 100 RPS/IP）
- [ ] 监控：/api/v1/health 接入监控告警（UP 200；连续 3 次非 200 告警）
- [ ] 日志收集：Docker logs → 阿里云 SLS / 自建 ELK
