# API Schema 与错误码

> 版本：`api-1.0.0`  
> 基础路径建议：`/api/v1`  
> 统一响应包装见下文「通用响应体」。

---

## 文档索引

| 文件 | 说明 |
|------|------|
| [analysis-request.schema.json](./analysis-request.schema.json) | 创建/提交分析任务 |
| [analysis-response.schema.json](./analysis-response.schema.json) | 分析任务详情（含报告） |
| [title-generate-request.schema.json](./title-generate-request.schema.json) | 标题生成请求 |
| [title-generate-response.schema.json](./title-generate-response.schema.json) | 标题生成响应 |
| [optimize-draft-request.schema.json](./optimize-draft-request.schema.json) | 生成优化稿请求 |
| [optimize-draft-response.schema.json](./optimize-draft-response.schema.json) | 优化稿响应 |
| [body-generate-request.schema.json](./body-generate-request.schema.json) | 生成正文请求 |
| [body-generate-response.schema.json](./body-generate-response.schema.json) | 生成正文响应 |
| [error-codes.md](./error-codes.md) | 业务错误码 |

---

## 通用响应体

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "requestId": "req_abc123",
  "timestamp": 1716537600000
}
```

| code | 含义 |
|------|------|
| 0 | 成功 |
| 非 0 | 失败，见 [error-codes.md](./error-codes.md) |

---

## 接口清单（P0）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/analysis` | 创建分析任务（异步） |
| GET | `/analysis/{id}` | 获取任务状态与报告 |
| POST | `/analysis/{id}/titles` | 基于分析上下文生成标题 |
| POST | `/analysis/{id}/optimize-draft` | 生成优化稿 |
| POST | `/analysis/{id}/body` | 基于分析上下文生成正文（P1） |
| POST | `/titles` | 独立标题生成（无分析 ID） |
| GET | `/analysis` | 历史列表（分页） |
| GET | `/auth/usage` | 今日分析用量（`usage_log` 统计） |
| DELETE | `/analysis/{id}` | 删除记录 |
| POST | `/files/cover` | 上传封面图（multipart `file`） |
| GET | `/files/cover/{objectKey}` | 读取已上传封面（需登录） |

### 任务状态机

```
pending → processing → completed
                    ↘ failed
```

- `POST /analysis` 返回 `id` + `status: pending`  
- 推荐：`GET /analysis/{id}/stream`（SSE）接收 `progress` / `done` 事件  
- 兼容：轮询 `GET /analysis/{id}` 直至 `completed` 或 `failed`

---

## 枚举定义

### AnalysisScenario

| 值 | 说明 |
|----|------|
| `draft` | 草稿优化 |
| `published` | 已发复盘（P0 可录入数据，对比报告 P1） |
| `competitor` | 竞品学习 |

### PersonaType

| 值 | 说明 |
|----|------|
| `agency` | 机构号 |
| `mentor` | 导师 IP |
| `senior` | 学长学姐 |

### ContentType

`ANXIETY` | `OFFER` | `INFO_GAP` | `INTERVIEW` | `TIMELINE` | `COMEBACK`

### TitleGenerateGoal

`high_ctr` | `high_collect` | `high_conversion` | `anxiety` | `offer` | `info_gap`

### BodyGenerateGoal

`high_ctr` | `high_collect` | `high_conversion` | `anxiety` | `offer` | `info_gap`

### IssueSeverity

`high` | `medium` | `low`

### IssueCategory

`ctr` | `emotion` | `collect` | `conversion` | `compliance`

### AnalysisStatus

`pending` | `processing` | `completed` | `failed`

---

## 认证（P0 占位）

```
Authorization: Bearer <access_token>
```

未登录可返回 `40101`；用量超限返回 `42901`。

---

## 版本策略

- URL 路径版本：`/api/v1`  
- 报告内 `promptVersion`（如 `rubric-1.0.0`）与 API 版本独立  
- 破坏性变更递增 major
