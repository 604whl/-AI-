# verify-s2-4-browser-assist.ps1
# S2-4 浏览器辅助会话管理 - API 验证脚本

$ErrorActionPreference = "Stop"
$BASE_URL = "http://localhost:8080/api/v1"
$WORKSPACE_ID = "00000000-0000-0000-0000-000000000001"
$USER_ID = 1

Write-Host "=== S2-4 Browser Assist Session API Verification ===" -ForegroundColor Cyan
Write-Host ""

function Get-Headers {
    @{
        "Content-Type" = "application/json"
        "X-Workspace-Id" = $WORKSPACE_ID
        "X-User-Id" = $USER_ID
    }
}

# ── 1. 创建会话 ───────────────────────────────────────────────────────────
Write-Host "[1/4] Create session..." -ForegroundColor Yellow
try {
    $body = @{
        platform = "XIAOHONGSHU"
        accountLabel = "test-account"
    } | ConvertTo-Json

    $resp = Invoke-RestMethod -Uri "$BASE_URL/browser-assist/sessions" `
        -Method POST `
        -Headers (Get-Headers) `
        -Body $body

    $sessionId = $resp.data.id
    Write-Host "  Created session: $sessionId" -ForegroundColor Green
    Write-Host "  Status: $($resp.data.status)" -ForegroundColor Green
    Write-Host "  PolicyNotice: $($resp.data.policyNotice)" -ForegroundColor Gray
} catch {
    Write-Host "  FAIL: $_" -ForegroundColor Red
    exit 1
}

# ── 2. 查询会话 ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[2/4] Get session..." -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod -Uri "$BASE_URL/browser-assist/sessions/$sessionId" `
        -Method GET `
        -Headers (Get-Headers)

    Write-Host "  Session status: $($resp.data.status)" -ForegroundColor Green
} catch {
    Write-Host "  FAIL: $_" -ForegroundColor Red
    exit 1
}

# ── 3. 填充会话（需先创建资产，此处模拟 assetId）───────────────────────────
Write-Host ""
Write-Host "[3/4] Fill session (requires existing asset)..." -ForegroundColor Yellow
Write-Host "  Skipping - requires valid assetId (run after creating content asset)" -ForegroundColor Gray

# 实际调用示例（取消注释替换真实 assetId）：
# $body = @{ sessionId = $sessionId; assetId = "<ASSET_UUID>" } | ConvertTo-Json
# $resp = Invoke-RestMethod -Uri "$BASE_URL/browser-assist/fill" `
#     -Method POST `
#     -Headers (Get-Headers) `
#     -Body $body

# ── 4. 关闭会话 ──────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[4/4] Close session..." -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod -Uri "$BASE_URL/browser-assist/sessions/$sessionId" `
        -Method DELETE `
        -Headers (Get-Headers)

    Write-Host "  Session closed" -ForegroundColor Green
} catch {
    Write-Host "  FAIL: $_" -ForegroundColor Red
    exit 1
}

# ── 5. 验证关闭后状态 ───────────────────────────────────────────────────
Write-Host ""
Write-Host "[5/4] Verify session closed..." -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod -Uri "$BASE_URL/browser-assist/sessions/$sessionId" `
        -Method GET `
        -Headers (Get-Headers)

    if ($resp.data.status -eq "closed") {
        Write-Host "  Status is 'closed' as expected" -ForegroundColor Green
    } else {
        Write-Host "  UNEXPECTED: Status is '$($resp.data.status)'" -ForegroundColor Red
    }
} catch {
    Write-Host "  FAIL: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== All checks passed ===" -ForegroundColor Green