# verify-s2-1-asset.ps1
# S2-1 手工验证脚本：资产标签 + 批量归档 + Autosave 端点
param(
    [string]`$BaseUrl = "http://localhost:8080",
    [string]`$Token = "Bearer dummy-token-for-local-dev"
)

`$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = `$Token
}

Write-Host "=== S2-1 手工验证 ===" -ForegroundColor Cyan
Write-Host "BaseUrl: `$BaseUrl" -ForegroundColor Gray

# 1. PATCH /v1/assets/{id}/tags (替换模式)
Write-Host "`n[1/3] PATCH /v1/assets/{id}/tags (替换模式)" -ForegroundColor Yellow
`$assetId = "REPLACE_WITH_REAL_ASSET_UUID"
`$body1 = @{
    tags = @("留学", "求职", "澳洲")
} | ConvertTo-Json -Compress

Write-Host "curl -X PATCH `"`$BaseUrl/api/v1/assets/`$assetId/tags`" ..."

try {
    `$r1 = Invoke-RestMethod -Uri "`$BaseUrl/api/v1/assets/`$assetId/tags" -Method PATCH -Headers `$headers -Body `$body1 -TimeoutSec 10
    Write-Host "响应: `$(`$r1 | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "请求失败: `$(`$_.Exception.Message)" -ForegroundColor Red
}

# 2. PATCH /v1/assets/{id}/tags (增量模式)
Write-Host "`n[2/3] PATCH /v1/assets/{id}/tags (增量模式)" -ForegroundColor Yellow
`$body2 = @{
    addTags    = @("留学")
    removeTags = @("澳洲")
} | ConvertTo-Json -Compress

try {
    `$r2 = Invoke-RestMethod -Uri "`$BaseUrl/api/v1/assets/`$assetId/tags" -Method PATCH -Headers `$headers -Body `$body2 -TimeoutSec 10
    Write-Host "响应: `$(`$r2 | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "请求失败: `$(`$_.Exception.Message)" -ForegroundColor Red
}

# 3. POST /v1/assets/batch-archive
Write-Host "`n[3/5] POST /v1/assets/batch-archive" -ForegroundColor Yellow
`$body3 = @{
    assetIds = @("REPLACE_WITH_REAL_ASSET_UUID_1", "REPLACE_WITH_REAL_ASSET_UUID_2")
} | ConvertTo-Json -Compress

try {
    `$r3 = Invoke-RestMethod -Uri "`$BaseUrl/api/v1/assets/batch-archive" -Method POST -Headers `$headers -Body `$body3 -TimeoutSec 10
    Write-Host "响应: `$(`$r3 | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "请求失败: `$(`$_.Exception.Message)" -ForegroundColor Red
}

# 4. POST /v1/assets/{id}/autosave
Write-Host "`n[4/5] POST /v1/assets/{id}/autosave" -ForegroundColor Yellow
`$body4 = @{
    title = "Autosave 测试标题"
    body  = "Autosave 测试正文内容"
} | ConvertTo-Json -Compress

try {
    `$r4 = Invoke-RestMethod -Uri "`$BaseUrl/api/v1/assets/`$assetId/autosave" -Method POST -Headers `$headers -Body `$body4 -TimeoutSec 10
    Write-Host "响应: `$(`$r4 | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "请求失败: `$(`$_.Exception.Message)" -ForegroundColor Red
}

# 5. Autosave 节流验证
Write-Host "`n[5/5] Autosave 节流验证（30s 内重复调用）" -ForegroundColor Yellow
try {
    `$r5 = Invoke-RestMethod -Uri "`$BaseUrl/api/v1/assets/`$assetId/autosave" -Method POST -Headers `$headers -Body `$body4 -TimeoutSec 10
    `$v4 = `$r4.data.currentVersion
    `$v5 = `$r5.data.currentVersion
    Write-Host "第一次 currentVersion: `$(`$v4), 第二次 currentVersion: `$(`$v5)"
    if (`$v4 -eq `$v5) {
        Write-Host "节流成功！version 未增长" -ForegroundColor Green
    } else {
        Write-Host "节流失效！version 增长了" -ForegroundColor Red
    }
} catch {
    Write-Host "请求失败: `$(`$_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== 验证完成 ===" -ForegroundColor Cyan