# verify-s2-2-preview.ps1
# 手工验证脚本 — 发布前预览平台规格检查 IDU stage2-s2-2
# 说明：2 个平台（小红书 / 抖音）的预览验证案例
# 运行前请确保后端服务已启动

$BASE = 'http://localhost:8080'

Write-Host '=== IDU stage2-s2-2 发布前预览 — 手工验证脚本 ===' -ForegroundColor Cyan
Write-Host ''

# 平台 1：小红书
Write-Host '[平台1] 小红书 (XIAOHONGSHU) 预览' -ForegroundColor Yellow
Write-Host ('curl -X GET "' + $BASE + '/api/v1/assets/{assetId}/preview?platform=xiaohongshu"')
Write-Host ''
Write-Host '说明：返回 warnings（超限警告）、info（建议信息）、spec（平台规格参数）'
Write-Host '期望：code=0, platform=XIAOHONGSHU, 包含 spec.maxTitleLength=1000 等字段'
Write-Host ''
Write-Host ''

# 平台 2：抖音
Write-Host '[平台2] 抖音 (DOUYIN) 预览' -ForegroundColor Yellow
Write-Host ('curl -X GET "' + $BASE + '/api/v1/assets/{assetId}/preview?platform=douyin"')
Write-Host ''
Write-Host '说明：抖音标题限制 55 字，正文限制 2000 字'
Write-Host '期望：code=0, platform=DOUYIN, 包含 spec.maxTitleLength=55 等字段'
Write-Host ''
Write-Host ''

Write-Host '=== 验证说明 ===' -ForegroundColor Cyan
Write-Host '1. 将 {assetId} 替换为实际的资产 UUID'
Write-Host '2. 需要在请求头中携带有效的 workspace-token'
Write-Host '3. 预期返回结构：'
Write-Host '   {"'
Write-Host '     "code": 0,'
Write-Host '     "msg": "success",'
Write-Host '     "data": {'
Write-Host '       "assetId": "xxx",'
Write-Host '       "platform": "XIAOHONGSHU",'
Write-Host '       "warnings": ["标题超过限制..."],'
Write-Host '       "info": ["标题接近限制..."],'
Write-Host '       "spec": {...}'
Write-Host '     }'
Write-Host '   }'
Write-Host ''