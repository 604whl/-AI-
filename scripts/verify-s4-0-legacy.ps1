# S4-0 遗留项清理验证脚本（L1 CSV 真实落库 / L3 浏览器辅助诚实降级）
#
# 用法：先启动后端（mvn -o spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local ..."），
#       再执行：pwsh -File scripts/verify-s4-0-legacy.ps1
#
# 脚本自行注册/登录获取 token，不硬编码任何凭证。

$ErrorActionPreference = "Continue"
$base = "http://localhost:8125/api"

$script:pass = 0
$script:fail = 0
$script:failures = New-Object System.Collections.ArrayList

function Write-Step([string]$msg) { Write-Host "" ; Write-Host "=== $msg ===" -ForegroundColor Cyan }
function Assert-True([bool]$cond, [string]$label, [string]$detail = "") {
  if ($cond) {
    $script:pass++
    Write-Host ("  [PASS] " + $label) -ForegroundColor Green
  } else {
    $script:fail++
    [void]$script:failures.Add("$label | $detail")
    Write-Host ("  [FAIL] " + $label + "  -> " + $detail) -ForegroundColor Red
  }
}
function Invoke-Api([string]$method, [string]$path, $body = $null) {
  $headers = @{ Authorization = "Bearer $($script:token)"; "X-Workspace-Id" = $script:wsId }
  $uri = "$base$path"
  try {
    if ($method -eq "GET") {
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30
    } elseif ($null -ne $body) {
      $json = $body | ConvertTo-Json -Depth 12
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30 -Method $method -Body $json -ContentType "application/json"
    } else {
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30 -Method $method
    }
    return @{ Status = [int]$r.StatusCode; Body = ($r.Content | ConvertFrom-Json); Raw = $r.Content }
  } catch {
    $status = 0; $raw = ""
    try { $status = [int]$_.Exception.Response.StatusCode } catch {}
    # PowerShell 7 下 HttpResponseException 没有 GetResponseStream()，错误体在 ErrorDetails.Message 里；
    # 旧写法只兼容 PS5.1，会导致 409 等业务错误响应体丢失（code 读成 -1）。
    try {
      if ($null -ne $_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
        $raw = $_.ErrorDetails.Message
      }
    } catch {}
    if ([string]::IsNullOrWhiteSpace($raw)) {
      try {
        $e = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $raw = $e.ReadToEnd(); $e.Close()
      } catch {}
    }
    $parsed = $null
    if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch {} }
    return @{ Status = $status; Body = $parsed; Raw = $raw }
  }
}
function Get-Data([hashtable]$resp) {
  if ($null -eq $resp.Body) { return $null }
  if ($resp.Body.PSObject.Properties.Name -contains "data") { return $resp.Body.data }
  return $null
}
function Get-Code([hashtable]$resp) {
  if ($null -eq $resp.Body) { return -1 }
  if ($resp.Body.PSObject.Properties.Name -contains "code") { return [int]$resp.Body.code }
  return -1
}

# ── 0. 登录 ────────────────────────────────────────────────────────────
Write-Step "0. 注册 / 登录"
$email = "s4e2e@example.com"
$pwd = "S4E2E123!"
$runId = [guid]::NewGuid().ToString("N").Substring(0, 8)
$auth = $null
try {
  $login = @{ email = $email; password = $pwd } | ConvertTo-Json
  $r = Invoke-RestMethod -Uri "$base/v1/auth/login" -Method Post -Body $login -ContentType "application/json" -TimeoutSec 30
  $auth = $r.data
} catch {
  try {
    $reg = @{ email = $email; password = $pwd; displayName = "S4E2E" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/v1/auth/register" -Method Post -Body $reg -ContentType "application/json" -TimeoutSec 30
    $auth = $r.data
  } catch {
    $alt = "s4e2e-$runId@example.com"
    $reg2 = @{ email = $alt; password = $pwd; displayName = "S4E2E" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/v1/auth/register" -Method Post -Body $reg2 -ContentType "application/json" -TimeoutSec 30
    $auth = $r.data
  }
}
if ($null -eq $auth) { Write-Host "FATAL: 无法获取凭证" -ForegroundColor Red; exit 1 }
$script:token = $auth.accessToken
$script:wsId = $auth.defaultWorkspaceId
Write-Host ("  workspaceId = " + $script:wsId)
Assert-True ($null -ne $script:token) "获取 accessToken"
Assert-True ($null -ne $script:wsId) "获取 defaultWorkspaceId"

# ── 1. L1：CSV 导入资产真实落库 ────────────────────────────────────────
Write-Step "1. L1 CSV 导入资产（ASSET）真实落库"
$assetTitle = "S4-0 E2E 导入资产 $runId"
$csvAsset = "title,body,persona,industry`n`"$assetTitle`",`"正文内容`",agency,beauty`n"
$assetB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($csvAsset))
$imp = Invoke-Api "POST" "/v1/import/csv" @{ importType = "ASSET"; csvContent = $assetB64; filename = "s4-0-assets.csv" }
Assert-True ((Get-Code $imp) -eq 0) "资产导入接口返回成功" ("raw=" + $imp.Raw)
$impData = Get-Data $imp
if ($null -ne $impData) {
  Assert-True ($impData.importedRows -eq 1) "importedRows=1（真实落库计数）" ("imported=" + $impData.importedRows + " skipped=" + $impData.skippedRows + " error=" + $impData.errorRows)
  Assert-True ($impData.totalRows -eq 1) "totalRows=1" ("total=" + $impData.totalRows)
}
# 用资产列表接口反查，确认真的写进了 content_asset
$assets = Invoke-Api "GET" "/v1/assets?page=1&size=50"
$assetsData = Get-Data $assets
$found = $false
if ($null -ne $assetsData) {
  $items = $null
  if ($assetsData.PSObject.Properties.Name -contains "items") { $items = $assetsData.items }
  elseif ($assetsData.PSObject.Properties.Name -contains "content") { $items = $assetsData.content }
  if ($null -ne $items) {
    foreach ($it in $items) { if ($it.title -eq $assetTitle) { $found = $true; break } }
  }
}
Assert-True $found "导入的资产可在资产列表中查到（真实入库）" ("title=" + $assetTitle)

# ── 2. L1：CSV 导入选题真实落库 + 去重 ──────────────────────────────────
Write-Step "2. L1 CSV 导入选题（TOPIC）真实落库 + 去重"
$topicTitle = "S4-0 E2E 导入选题 $runId"
$csvTopic = "title,source,heat_score,tags`n`"$topicTitle`",csv_import,88,美妆|穿搭`n"
$topicB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($csvTopic))
$imp2 = Invoke-Api "POST" "/v1/import/csv" @{ importType = "TOPIC"; csvContent = $topicB64; filename = "s4-0-topics.csv" }
Assert-True ((Get-Code $imp2) -eq 0) "选题导入接口返回成功" ("raw=" + $imp2.Raw)
$imp2Data = Get-Data $imp2
if ($null -ne $imp2Data) {
  Assert-True ($imp2Data.importedRows -eq 1) "选题 importedRows=1" ("imported=" + $imp2Data.importedRows)
}
# 同一 CSV 再导入一次：应全部命中去重 → importedRows=0, skippedRows=1
$imp3 = Invoke-Api "POST" "/v1/import/csv" @{ importType = "TOPIC"; csvContent = $topicB64; filename = "s4-0-topics-dup.csv" }
$imp3Data = Get-Data $imp3
if ($null -ne $imp3Data) {
  Assert-True ($imp3Data.importedRows -eq 0) "重复导入 importedRows=0（去重生效）" ("imported=" + $imp3Data.importedRows)
  Assert-True ($imp3Data.skippedRows -eq 1) "重复导入 skippedRows=1" ("skipped=" + $imp3Data.skippedRows)
}

# ── 3. L3：浏览器辅助诚实降级 ──────────────────────────────────────────
Write-Step "3. L3 浏览器辅助填充应诚实降级为 failed（需人工）"
$sess = Invoke-Api "POST" "/v1/browser-assist/sessions" @{ platform = "XIAOHONGSHU"; accountLabel = "s4-0-$runId" }
Assert-True ((Get-Code $sess) -eq 0) "创建浏览器辅助会话成功" ("raw=" + $sess.Raw)
$sessData = Get-Data $sess
$sessId = $null
if ($null -ne $sessData) { $sessId = $sessData.id; Assert-True ($sessData.status -eq "created") "会话初始状态 created" ("status=" + $sessData.status) }

# 造一个资产用于填充
$as = Invoke-Api "POST" "/v1/assets" @{ title = "S4-0 浏览器辅助资产 $runId"; body = "验证诚实降级"; persona = "agency"; scenario = "published"; platform = "XIAOHONGSHU" }
$asData = Get-Data $as
$assetId = $null
if ($null -ne $asData) { $assetId = $asData.id }

if ($null -ne $sessId -and $null -ne $assetId) {
  $fill = Invoke-Api "POST" "/v1/browser-assist/fill" @{ sessionId = $sessId; assetId = $assetId }
  Assert-True ($fill.Status -eq 409) "填充返回 HTTP 409（而非伪装成功 200）" ("status=" + $fill.Status + " raw=" + $fill.Raw)
  $fillCode = Get-Code $fill
  Assert-True ($fillCode -eq 40950) "业务错误码 40950 browser_manual_required" ("code=" + $fillCode + " raw=" + $fill.Raw)
  # 会话状态必须落库为 failed
  $got = Invoke-Api "GET" "/v1/browser-assist/sessions/$sessId"
  $gotData = Get-Data $got
  if ($null -ne $gotData) {
    Assert-True ($gotData.status -eq "failed") "会话状态落库为 failed（不是 filled）" ("status=" + $gotData.status)
  }
  # 清理：关闭会话
  $null = Invoke-Api "DELETE" "/v1/browser-assist/sessions/$sessId"
}

# ── 汇总 ────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ("通过: " + $script:pass + "   失败: " + $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) {
  Write-Host "失败明细:" -ForegroundColor Red
  foreach ($f in $script:failures) { Write-Host ("  - " + $f) -ForegroundColor Red }
}
Write-Host "========================================" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
