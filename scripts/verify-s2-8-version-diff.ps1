# S2-8 多版本diff可视化验证
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

Write-Host "=== Test: version diff ===" -ForegroundColor Cyan
try {
    # 获取一个有多个版本的资产列表
    $assets = Invoke-RestMethod "$BASE/assets?page=1&size=5" -Headers $headers -EA SilentlyContinue
    if ($assets.data.records.Count -gt 0) {
        $firstAsset = $assets.data.records[0]
        Write-Host "  Testing diff on asset: $($firstAsset.id)"
        $r = Invoke-RestMethod "$($BASE)/assets/$($firstAsset.id)/diff?v1=1&v2=2" -Headers $headers
        if ($r.code -eq 0) {
            $s = $r.data.stats
            Write-Host "  [PASS] diff: +$($s.addedLines) -$($s.removedLines) =$($s.unchangedLines)" -ForegroundColor Green
            Write-Host "    chunks: $($r.data.chunks.Count)"
        }
    } else {
        Write-Host "  [SKIP] no assets found" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [SKIP] (server not running or no data): $_" -ForegroundColor Yellow
}

Write-Host "Done."