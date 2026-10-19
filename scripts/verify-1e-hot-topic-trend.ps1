# verify-1e-hot-topic-trend.ps1
# Manual verification script for IDU-1E Hot Topic Trend API.
# Prerequisites:
#   - Backend is running on http://localhost:8080
#   - Valid auth token in $AUTH_TOKEN, workspace ID in $WORKSPACE_ID, user ID in $USER_ID
#
# 4 smoke-test cases:
#   Case 1: rising  - hot score increasing > 20%% over window
#   Case 2: falling - hot score decreasing > 20%% over window
#   Case 3: flat    - hot score within 20%% band
#   Case 4: new     - fewer than 3 data points

$BASE_URL     = "http://localhost:8080"
$AUTH_TOKEN   = "Bearer <your-token-here>"
$WORKSPACE_ID = "<your-workspace-id>"
$USER_ID      = "<your-user-id>"

$headers = @{
    "Authorization"  = $AUTH_TOKEN
    "X-Workspace-Id" = $WORKSPACE_ID
    "X-User-Id"     = $USER_ID
    "Content-Type"   = "application/json"
}

function Test-ApiCase {
    param(
        [string]$CaseName,
        [string]$Title,
        [int]   $WindowDays = 7,
        [string]$ExpectedTrend
    )
    $url = "$BASE_URL/api/v1/hot-topics/trend?title=$([uri]::EscapeDataString($Title))&windowDays=$WindowDays"
    Write-Host ""
    Write-Host "=== Case $CaseName ===" -ForegroundColor Cyan
    Write-Host "GET $url"
    try {
        $resp = Invoke-WebRequest -Uri $url -Method GET -Headers $headers -TimeoutSec 15
        $data = $resp.Content | ConvertFrom-Json
        if ($resp.StatusCode -eq 200 -and $data.code -eq 0) {
            $body = $data.data
            Write-Host "  trend : $($body.trend)"
            Write-Host "  delta : $($body.delta)"
            Write-Host "  latest: $($body.latest)"
            Write-Host "  points: $($body.points.Count)"
            $match = $body.trend -eq $ExpectedTrend
            if ($match) {
                Write-Host "  [PASS] trend == '$ExpectedTrend'" -ForegroundColor Green
            } else {
                Write-Host "  [WARN] expected '$ExpectedTrend', got '$($body.trend)'" -ForegroundColor Yellow
            }
            return $match
        } else {
            Write-Host "  [FAIL] HTTP $($resp.StatusCode) - $($data.message)" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "  [FAIL] $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

Write-Host "========================================" -ForegroundColor Magenta
Write-Host "IDU-1E Hot Topic Trend — Manual Test" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta

# Case 1: rising
Test-ApiCase -CaseName "1 - rising" -Title "留学生求职" -WindowDays 7 -ExpectedTrend "rising"

# Case 2: falling
Test-ApiCase -CaseName "2 - falling" -Title "海外留学" -WindowDays 7 -ExpectedTrend "falling"

# Case 3: flat
Test-ApiCase -CaseName "3 - flat" -Title "小红书运营" -WindowDays 7 -ExpectedTrend "flat"

# Case 4: new (never-exists title produces < 3 points)
Test-ApiCase -CaseName "4 - new" -Title "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXnever-exist-$(Get-Random)" -WindowDays 7 -ExpectedTrend "new"

# Case 5: /list smoke test
Write-Host ""
Write-Host "=== Case 5 - list endpoint ===" -ForegroundColor Cyan
$listUrl = "$BASE_URL/api/v1/hot-topics/list?platform=xiaohongshu&page=1&size=5"
Write-Host "GET $listUrl"
try {
    $resp = Invoke-WebRequest -Uri $listUrl -Method GET -Headers $headers -TimeoutSec 15
    if ($resp.StatusCode -eq 200) {
        Write-Host "  [PASS] HTTP 200" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] HTTP $($resp.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Done. Review [PASS]/[FAIL] above." -ForegroundColor Magenta