# verify-s2-3-compliance.ps1
# S2-3 合规规则热更新 + 品牌级禁用词 API 验证脚本

$BASE = "http://localhost:8080/api/v1/compliance"
$WS_HEADER = "X-Workspace-Id"
$WS_ID = "00000000-0000-0000-0000-000000000001"
$TOKEN = "Bearer test-token"

Write-Host "=== S2-3 合规 API 验证 ===" -ForegroundColor Cyan

function curl-post($url, $body) {
    $headers = @{
        "Content-Type" = "application/json"
        $WS_HEADER = $WS_ID
        "Authorization" = $TOKEN
    }
    $r = Invoke-RestMethod -Uri $url -Method Post -Body ($body | ConvertTo-Json -Compress) -Headers $headers -TimeoutSec 10
    return $r
}

function curl-get($url) {
    $headers = @{
        $WS_HEADER = $WS_ID
        "Authorization" = $TOKEN
    }
    $r = Invoke-RestMethod -Uri $url -Method Get -Headers $headers -TimeoutSec 10
    return $r
}

function curl-del($url) {
    $headers = @{
        $WS_HEADER = $WS_ID
        "Authorization" = $TOKEN
    }
    Invoke-RestMethod -Uri $url -Method Delete -Headers $headers -TimeoutSec 10
}

# 1. 列出全局规则
Write-Host "`n[1] GET /v1/compliance/rules" -ForegroundColor Yellow
try {
    $rules = curl-get "$BASE/rules"
    $count = if ($rules.data) { $rules.data.Count } else { 0 }
    Write-Host "  -> rules count: $count" -ForegroundColor Green
} catch {
    Write-Host "  -> FAIL: $_" -ForegroundColor Red
}

# 2. 添加品牌禁用词
Write-Host "`n[2] POST /v1/compliance/brand-words" -ForegroundColor Yellow
$brandWord = @{
    ruleCode = "ABSOLUTE_CLAIM"
    keyword = "独家特供"
    riskLevel = "HIGH"
}
try {
    $added = curl-post "$BASE/brand-words" $brandWord
    $bwId = $added.data.id
    Write-Host "  -> added brand word id: $bwId" -ForegroundColor Green

    # 3. 查询品牌禁用词
    Write-Host "`n[3] GET /v1/compliance/brand-words" -ForegroundColor Yellow
    $list = curl-get "$BASE/brand-words"
    $listCount = if ($list.data) { $list.data.Count } else { 0 }
    Write-Host "  -> brand words count: $listCount" -ForegroundColor Green

    # 4. 删除品牌禁用词
    if ($bwId) {
        Write-Host "`n[4] DELETE /v1/compliance/brand-words/$bwId" -ForegroundColor Yellow
        curl-del "$BASE/brand-words/$bwId"
        Write-Host "  -> deleted id: $bwId" -ForegroundColor Green
    }
} catch {
    Write-Host "  -> FAIL: $_" -ForegroundColor Red
}

Write-Host "`n=== 验证完成 ===" -ForegroundColor Cyan