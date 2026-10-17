# XhsAgent 备份与恢复

## 快速开始

### 每日备份

```bash
# 配置环境变量
export PGPASSWORD=your_db_password
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=your_access_key
export MINIO_SECRET_KEY=your_secret_key

# 执行备份
./backup.sh prod
```

### 恢复数据

```bash
# 查看可用备份
ls ./data/backups/pg_*.sql.gz

# 恢复到指定备份
./restore.sh ./data/backups/pg_prod_20261017_120000.sql.gz prod
```

## 备份内容

1. **PostgreSQL 数据库**：完整 SQL 转储（gzip 压缩），包含所有表和数据
2. **MinIO 对象存储**：xhs-agent bucket 中的所有文件

## 备份策略

- **保留周期**：默认 7 天（可通过 `BACKUP_RETENTION_DAYS` 环境变量调整）
- **存储位置**：
  - 本地：`./data/backups/`
  - MinIO：`xhs-agent-backup` bucket
- **自动清理**：超过保留期的备份自动删除

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PGHOST` | localhost | PostgreSQL 主机 |
| `PGPORT` | 5432 | PostgreSQL 端口 |
| `PGDATABASE` | xhsagent | 数据库名 |
| `PGUSER` | xhsagent | 数据库用户 |
| `PGPASSWORD` | - | 数据库密码（必填） |
| `MINIO_ENDPOINT` | http://localhost:9000 | MinIO 地址 |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO Access Key |
| `MINIO_SECRET_KEY` | minioadmin | MinIO Secret Key |
| `MINIO_BUCKET` | xhs-agent-backup | MinIO 备份 bucket |
| `BACKUP_RETENTION_DAYS` | 7 | 备份保留天数 |
| `BACKUP_DIR` | ./data/backups | 本地备份目录 |

## 定时备份（crontab）

```bash
# 每天凌晨 3 点执行备份
0 3 * * * cd /path/to/Xhs-Backend && ./infra/backup/backup.sh prod >> ./data/backups/backup.log 2>&1

# 每周日凌晨 4 点执行备份
0 4 * * 0 cd /path/to/Xhs-Backend && ./infra/backup/backup.sh prod >> ./data/backups/backup.log 2>&1
```

## 恢复演练

建议每月进行一次恢复演练，确保备份可用：

```bash
# 1. 在预发布环境恢复
./restore.sh ./data/backups/pg_prod_latest.sql.gz staging

# 2. 验证数据完整性
psql -h staging-db -U xhsagent -d xhsagent -c "SELECT count(*) FROM app.users;"

# 3. 确认无误后清理临时数据库
psql -h staging-db -U postgres -c "DROP DATABASE IF EXISTS xhsagent_restore_temp;"
```

## 故障排除

### MinIO Client 未安装

```bash
# 安装 mc
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/

# 配置 alias
mc alias set myminio http://localhost:9000 ACCESS_KEY SECRET_KEY
```

### 备份失败

检查：
1. PostgreSQL 连接是否正常
2. MinIO 服务是否可访问
3. 磁盘空间是否充足
4. 环境变量是否正确配置
