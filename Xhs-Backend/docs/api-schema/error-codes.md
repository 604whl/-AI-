# 业务错误码

> HTTP 状态码与业务 `code` 配合使用：4xx/5xx 表示传输层，body 内 `code` 表示业务细节。

---

## 成功

| code | HTTP | message |
|------|------|---------|
| 0 | 200 | ok |

---

## 客户端错误 4xxxx

| code | HTTP | message | 说明 |
|------|------|---------|------|
| 40001 | 400 | invalid_request | 请求体 JSON 无法解析 |
| 40002 | 400 | validation_failed | 字段校验失败，见 `data.errors[]` |
| 40003 | 400 | title_and_body_empty | 标题与正文不能同时为空 |
| 40004 | 400 | cover_image_invalid | 封面格式/大小不符（仅支持 jpg/png/webp，≤5MB） |
| 40005 | 400 | scenario_not_supported | 场景枚举无效 |
| 40101 | 401 | unauthorized | 未登录或 token 失效 |
| 40301 | 403 | forbidden | 无权限访问该资源 |
| 40401 | 404 | analysis_not_found | 分析任务不存在 |
| 40901 | 409 | analysis_not_completed | 任务未完成，不可生成标题/优化稿 |
| 40902 | 409 | analysis_already_processing | 重复提交分析 |
| 42901 | 429 | quota_exceeded | 每日分析次数用尽 |
| 42902 | 429 | title_quota_exceeded | 标题生成次数用尽 |

### validation_failed 示例

```json
{
  "code": 40002,
  "message": "validation_failed",
  "data": {
    "errors": [
      { "field": "title", "message": "maxLength 100" }
    ]
  },
  "requestId": "req_xxx"
}
```

---

## 服务端错误 5xxxx

| code | HTTP | message | 说明 |
|------|------|---------|------|
| 50001 | 500 | internal_error | 未分类内部错误 |
| 50002 | 500 | ai_service_unavailable | 模型服务不可用 |
| 50003 | 500 | ai_response_invalid | 模型返回无法解析为约定 Schema |
| 50004 | 500 | storage_error | 文件存储失败 |
| 50401 | 504 | analysis_timeout | 分析超时（>45s） |

---

## 前端处理建议

| code | 建议 |
|------|------|
| 40002 | 表单字段高亮 |
| 40901 | 轮询等待或禁用按钮 |
| 42901/42902 | 升级引导弹窗 |
| 50002/50401 | 重试 + 联系支持 |
| 50003 | 自动重试 1 次，仍失败提示稍后再试 |

---

## 分析任务 failed 状态 reason

`GET /analysis/{id}` 在 `status=failed` 时，`data.failure`：

| reason | 对应 code |
|--------|-----------|
| `timeout` | 50401 |
| `ai_error` | 50002 / 50003 |
| `storage_error` | 50004 |
| `unknown` | 50001 |
