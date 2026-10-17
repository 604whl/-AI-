# SLO / RPO / RTO 目标（XhsAgent 公网运营）

本文档定义 XhsAgent 的服务等级目标（SLO）与灾难恢复指标，作为运维手册的依据。
**任何目标变更需经技术负责人 + 产品负责人 + 法务三方确认。**

---

## 服务可用性 SLO

| 服务 | SLO（月度） | 测量窗口 | 失败定义 |
|------|------------|---------|---------|
| Web API (`/api/v1/*`) | **99.5%** | 自然月 | HTTP 5xx + 业务 code >= 50000 |
| 管理后台 UI | **99.0%** | 自然月 | 首屏 TTFB > 3s 或白屏 |
| 任务处理（worker） | **99.0%** | 自然月 | 任务 5 分钟内未进入 DLQ |
| AI 分析接口 | **99.0%** | 自然月 | 60 秒内未返回结构化结果 |

> 99.5% ≈ 每月允许停机 3.6 小时。99.0% ≈ 每月允许停机 7.2 小时。

**误报预算**：每月停机时间分配到 P0 故障 60% / P1 故障 30% / 计划维护 10%。

---

## 数据保护 RPO / RTO

| 数据 | RPO（最大数据丢失） | RTO（最长恢复时间） | 备份方式 | 备份频率 |
|------|---------------------|----------------------|---------|---------|
| PostgreSQL 主库 | **15 分钟** | **1 小时** | WAL 流复制 + 每 15 分钟 base backup | 15 min |
| Redis（AOF + 快照） | **5 分钟** | **30 分钟** | AOF + 每日 RDB 快照 | 5 min |
| MinIO 对象存储 | **1 小时** | **4 小时** | 跨区域复制 + 版本控制 | 1 h |
| 审计日志 `app.audit_event` | **24 小时** | **24 小时** | 每日导出到对象存储 | 24 h |
| KB 知识库向量数据 | **24 小时** | **8 小时** | pg_dump 每日全量 | 24 h |

**关键承诺**：
- 任何对生产数据库的 DDL 变更必须先在 staging 演练备份恢复。
- WAL 流复制异常时，告警应 5 分钟内触发。
- DR 演练每季度至少一次，由 SRE 负责人签字。

---

## 备份恢复剧本

### 1. PostgreSQL（主库）

**自动备份**：使用 `infra/backup/backup.sh` 每日凌晨 2 点执行：
```bash
# cron 配置
0 2 * * * /opt/xhs-agent/infra/backup/backup.sh >> /var/log/xhs-backup.log 2>&1
```

**手动恢复**：
```bash
# 1) 停服
docker compose down backend
# 2) 选择最近一次全量备份
ls -lh /var/backups/xhs/pg/
# 3) 用 restore.sh 预演
./infra/backup/restore.sh /var/backups/xhs/pg/xhs-2026-10-17-0200.sql.gz
# 4) 二次确认后启动
docker compose up -d backend
```

### 2. Redis

```bash
# AOF 重写 + 备份
redis-cli BGREWRITEAOF
cp /var/lib/redis/appendonly.aof /var/backups/redis/$(date +%F).aof
```

### 3. MinIO

```bash
# 跨区域复制
mc mirror --remove --overwrite source/ destination/
# 版本控制回滚
mc cp --version-id <vid> source/bucket/key local/
```

---

## 监控告警

详见 [`prometheus-alerts.yml`](./prometheus-alerts.yml)。

关键规则：
- `TaskDLQSpike`：5 分钟死信 > 10 → critical
- `TaskFailureRateHigh`：5 分钟失败率 > 30% → critical
- `JvmHeapHigh`：堆使用率 > 85% 持续 5 分钟 → warning
- `QuotaDeniedSpike`：额度拒绝 5 分钟 > 50 次 → warning
- `Http5xxSpike`：HTTP 5xx > 1 req/s → critical

告警联系人：
- 业务负责人：dev@xhs.local（占位，部署时需替换）
- 7×24 on-call：通过 PagerDuty/Opsgenie 路由（部署时需配置）

---

## 事故响应

P0（核心功能不可用）事故响应流程：

1. **0 ~ 5 分钟**：on-call 收到告警，确认事故范围。
2. **5 ~ 15 分钟**：召集 3 人应急小组（dev + SRE + 产品）。
3. **15 ~ 60 分钟**：定位 + 缓解（rollback / 关停非核心功能 / 切换备用方案）。
4. **1 ~ 24 小时**：恢复 + 客户告知。
5. **24 ~ 72 小时**：事故复盘（postmortem），形成行动项。

每次 P0 事故必须在 7 天内输出 [`docs/postmortems/YYYY-MM-DD-<title>.md`]，
包含：时间线、根因、影响面、缓解措施、行动项、负责人、完成日期。
