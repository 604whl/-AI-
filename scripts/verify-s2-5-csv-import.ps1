# verify-s2-5-csv-import.ps1
# S2-5 CSV 手动导入功能验证脚本

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Token = "YOUR_JWT_TOKEN"
)

$headers = @{
    "Authorization" = "Bearer $Token"
    "Content-Type"  = "application/json"
}

Write-Host "=== S2-5 CSV 导入验证 ===" -ForegroundColor Cyan

# ============================================================
# 1. 资产导入测试
# ============================================================
Write-Host "
[1/2] 测试资产导入 (ASSET)..." -ForegroundColor Yellow

$assetCsvContent = @"
title,body,industry
""测试标题1"","测试正文内容1"","科技"
"\"测试标题2"\","\"测试正文内容2\","\"教育\""
"@
$assetBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($assetCsvContent))

$assetBody = @{
    importType = "ASSET"
    csvContent = $assetBase64
    filename = "test-assets.csv"
} | ConvertTo-Json

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/import/csv" 
        -Method Post 
        -Headers $headers 
        -Body $assetBody 
        -TimeoutSec 30
    
    Write-Host "  导入成功!" -ForegroundColor Green
    Write-Host "  LogId: $(.data.logId)"
    Write-Host "  Total: $(.data.totalRows), Imported: $(.data.importedRows), Skipped: $(.data.skippedRows), Error: $(.data.errorRows)"
    Write-Host "  Status: $(.data.status)"
} catch {
    Write-Host "  资产导入失败: $_" -ForegroundColor Red
}

# ============================================================
# 2. 选题导入测试
# ============================================================
Write-Host "
[2/2] 测试选题导入 (TOPIC)..." -ForegroundColor Yellow

$topicCsvContent = @"
title,description
"\"选题标题1\"\",""\"选题描述1\""
"\"选题标题2\","\"选题描述2\""
"@
$topicBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($topicCsvContent))

$topicBody = @{
    importType = "TOPIC"
    csvContent = $topicBase64
    filename = "test-topics.csv"
} | ConvertTo-Json

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/import/csv" 
        -Method Post 
        -Headers $headers 
        -Body $topicBody 
        -TimeoutSec 30
    
    Write-Host "  导入成功!" -ForegroundColor Green
    Write-Host "  LogId: $(.data.logId)"
    Write-Host "  Total: $(.data.totalRows), Imported: $(.data.importedRows), Skipped: $(.data.skippedRows), Error: $(.data.errorRows)"
    Write-Host "  Status: $(.data.status)"
} catch {
    Write-Host "  选题导入失败: $_" -ForegroundColor Red
}

Write-Host "
=== 验证完成 ===" -ForegroundColor Cyan