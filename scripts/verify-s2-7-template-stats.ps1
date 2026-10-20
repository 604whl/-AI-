# S2-7 模板效果统计验证
$ErrorActionPreference = "Stop"
$BASE = "http://localhost:8080/api/v1"

function Get-Token {
    $r = Invoke-RestMethod "$BASE/auth/login" -Method POST -ContentType "application/json" -Body (ConvertTo-Json @{username="admin";password="admin123"} 2>$Null) 2>$Null
    return $r?.data?.token
}

$token = Get-Token
if (-not $token) {
    Write-Host "[SKIP] No auth token (server not running)" -ForegroundColor Yellow
    exit 0
}
$headers = @{ Authorization = "Bearer $token"; Workspace-Id = "00000000-0000-0000-0000-000000000001" }

Write-Host "=== Test: template leaderboard ===" -ForegroundColor Cyan
try {
    $r = Invoke-RestMethod "$BASE/templates/stats/leaderboard?limit=5" -Headers $headers
    if ($r.code -eq 0) {
        Write-Host "  [PASS] leaderboard: $($r.data.Count) entries" -ForegroundColor Green
        $r.data | ForEach-Object { Write-Host "    $($_.title) -> $($_.applyCount30d) refs, $($_.adoptionRate)% adoption" }
    }
} catch {
    Write-Host "  [SKIP] leaderboard (server not running): $_" -ForegroundColor Yellow
}

Write-Host "Done."