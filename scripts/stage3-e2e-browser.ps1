# 阶段3 端到端验收脚本（出口条件 E1-E3 + 8 条必走场景）
#
# 用法：
#   1) 先启动后端：cd Xhs-Backend; mvn -o spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local --app.ai.mock-enabled=true --app.agent.mock-enabled=true"
#   2) 再运行：powershell -ExecutionPolicy Bypass -File scripts/stage3-e2e.ps1
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

function Invoke-Api([string]$method, [string]$path, $body = $null, $extraHeaders = $null) {
  $headers = @{
    Authorization    = "Bearer $($script:token)"
    "X-Workspace-Id" = $script:wsId
  }
  if ($extraHeaders) { foreach ($k in $extraHeaders.Keys) { $headers[$k] = $extraHeaders[$k] } }
  $uri = "$base$path"
  try {
    if ($method -eq "GET") {
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30
    } elseif ($method -eq "DELETE") {
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30 -Method DELETE
    } elseif ($body -is [string]) {
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30 -Method $method -Body $body -ContentType "application/json"
    } elseif ($null -ne $body) {
      $json = $body | ConvertTo-Json -Depth 12
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30 -Method $method -Body $json -ContentType "application/json"
    } else {
      $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 30 -Method $method -ContentType "application/json"
    }
    $parsed = $r.Content | ConvertFrom-Json
    return @{ Status = [int]$r.StatusCode; Body = $parsed; Raw = $r.Content }
  } catch {
    $status = 0
    $raw = ""
    try { $status = [int]$_.Exception.Response.StatusCode } catch {}
    try {
      $e = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      $raw = $e.ReadToEnd()
    } catch {}
    $parsed = $null
    if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch {} }
    return @{ Status = $status; Body = $parsed; Raw = $raw }
  }
}

function Get-Code([hashtable]$resp) {
  if ($null -eq $resp.Body) { return -1 }
  if ($resp.Body.PSObject.Properties.Name -contains "code") { return [int]$resp.Body.code }
  return -1
}

function Get-Data([hashtable]$resp) {
  if ($null -eq $resp.Body) { return $null }
  if ($resp.Body.PSObject.Properties.Name -contains "data") { return $resp.Body.data }
  return $null
}

# ────────────────────────────────────────────────────────────
# 0. 登录
# ────────────────────────────────────────────────────────────
Write-Step "0. 注册 / 登录"
# register 接口按 IP 限流 3 次/小时，因此优先用固定账号登录；仅在账号不存在时注册一次
$email = "stage3e2e@example.com"
$pwd   = "Stage3E2E123!"
# 每次运行的唯一后缀：账号标签 / 竞品名都带上它，避免重复运行撞唯一约束
$runId = [guid]::NewGuid().ToString("N").Substring(0, 8)
$auth = $null

# 1) 先尝试登录（账号已存在时直接命中，不消耗注册额度）
try {
  $login = @{ email = $email; password = $pwd } | ConvertTo-Json
  $r = Invoke-RestMethod -Uri "$base/v1/auth/login" -Method Post -Body $login -ContentType "application/json" -TimeoutSec 30
  $auth = $r.data
  Write-Host ("  logged in: " + $email)
} catch {
  Write-Host ("  login failed (" + $_.Exception.Message + "), trying register")
  # 2) 登录失败才注册（固定账号，仅首次消耗注册额度）
  try {
    $reg = @{ email = $email; password = $pwd; displayName = "E2E" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/v1/auth/register" -Method Post -Body $reg -ContentType "application/json" -TimeoutSec 30
    $auth = $r.data
    Write-Host ("  registered: " + $email)
  } catch {
    Write-Host ("  register failed too: " + $_.Exception.Message)
    # 3) 兜底：注册额度用尽时用随机邮箱新注册一次
    $alt = "stage3e2e-$runId@example.com"
    $reg2 = @{ email = $alt; password = $pwd; displayName = "E2E" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/v1/auth/register" -Method Post -Body $reg2 -ContentType "application/json" -TimeoutSec 30
    $auth = $r.data
    Write-Host ("  registered fallback: " + $alt)
  }
}
if ($null -eq $auth) {
  Write-Host "FATAL: 无法获取凭证，后续用例全部跳过" -ForegroundColor Red
  exit 1
}
$script:token = $auth.accessToken
$script:wsId  = $auth.defaultWorkspaceId
Write-Host ("  workspaceId = " + $script:wsId)
Assert-True ($null -ne $script:token) "获取 accessToken" ("token=" + $script:token)
Assert-True ($null -ne $script:wsId)  "获取 defaultWorkspaceId"

# ────────────────────────────────────────────────────────────
# XHS_BROWSER_SIDEBAR 辅助发布闭环（E1 的浏览器辅助路径）
# 主脚本 stage3-e2e.ps1 覆盖 API 侧闭环，本脚本补浏览器辅助侧。
# ────────────────────────────────────────────────────────────
Write-Step "XHS_BROWSER_SIDEBAR 辅助发布闭环"

$runId2 = [guid]::NewGuid().ToString("N").Substring(0, 8)

# 1) 准备一个审核通过的资产
$ba = Invoke-Api "POST" "/v1/assets" @{
  title = "E2E 浏览器辅助发布验证 $runId2"
  body = "验证 XHS_BROWSER_SIDEBAR 辅助发布闭环"
  persona = "agency"
  scenario = "published"
  platform = "XIAOHONGSHU"
}
$baData = Get-Data $ba
$baAssetId = $baData.id
Assert-True ($null -ne $baAssetId) "创建资产成功" ("code=" + (Get-Code $ba) + " body=" + $ba.Raw)

$bsubmit = Invoke-Api "POST" "/v1/assets/$baAssetId/submit-review" @{}
Assert-True ((Get-Code $bsubmit) -eq 0) "提交审核成功" ("body=" + $bsubmit.Raw)

$bsubs = Invoke-Api "GET" "/v1/reviews/submissions?page=1&size=50"
$bsubsData = Get-Data $bsubs
$bitems = $null
if ($null -ne $bsubsData) {
  if ($bsubsData.PSObject.Properties.Name -contains "items") { $bitems = $bsubsData.items }
  elseif ($bsubsData.PSObject.Properties.Name -contains "content") { $bitems = $bsubsData.content }
}
$btaskId = $null
if ($null -ne $bitems) {
  foreach ($it in $bitems) {
    if ($null -ne $it -and $it.PSObject.Properties.Name -contains "assetId" -and $it.assetId -eq $baAssetId) {
      $btaskId = $it.id
      break
    }
  }
}
Assert-True ($null -ne $btaskId) "取到审核任务 ID" ("subs=" + $bsubs.Raw)

if ($null -ne $btaskId) {
  $bapprove = Invoke-Api "POST" "/v1/reviews/$btaskId/approve" @{}
  Assert-True ((Get-Code $bapprove) -eq 0) "审核通过（资产转 approved）" ("body=" + $bapprove.Raw)
}

# 2) 绑定小红书平台账号（XHS_BROWSER_SIDEBAR）
$bpa = Invoke-Api "POST" "/v1/platform-accounts" @{
  platformCode = "XIAOHONGSHU"
  accountLabel = "e2e-browser-$runId2"
  connectorProviderCode = "XHS_BROWSER_SIDEBAR"
}
Assert-True ((Get-Code $bpa) -eq 0) "创建小红书平台账号成功" ("body=" + $bpa.Raw)
$bpaData = Get-Data $bpa
$bAccountId = $bpaData.id

# 3) 创建浏览器辅助会话
$bsess = Invoke-Api "POST" "/v1/browser-assist/sessions" @{
  platform = "XIAOHONGSHU"
  accountLabel = "e2e-browser-$runId2"
}
Assert-True ((Get-Code $bsess) -eq 0) "创建浏览器辅助会话成功" ("body=" + $bsess.Raw)
$bsessData = Get-Data $bsess
$bSessionId = $bsessData.id

if ($null -ne $bSessionId) {
  $bgot = Invoke-Api "GET" "/v1/browser-assist/sessions/$bSessionId"
  Assert-True ((Get-Code $bgot) -eq 0) "查询会话成功" ("body=" + $bgot.Raw)
  $bgotData = Get-Data $bgot
  Write-Host ("         session status = " + $bgotData.status)
  Write-Host ("         policyNotice = " + $bgotData.policyNotice)
  Assert-True ($bgotData.status -eq "created") "会话初始状态为 created" ("status=" + $bgotData.status)
  Assert-True (-not [string]::IsNullOrWhiteSpace($bgotData.policyNotice)) "会话返回合规提示 policyNotice" ("policyNotice=" + $bgotData.policyNotice)

  $bfill = Invoke-Api "POST" "/v1/browser-assist/fill" @{
    sessionId = $bSessionId
    assetId = $baAssetId
  }
  Assert-True ((Get-Code $bfill) -eq 0) "填充会话内容成功" ("body=" + $bfill.Raw)
  $bfillData = Get-Data $bfill
  Write-Host ("         after fill, status = " + $bfillData.status)
  Assert-True ($bfillData.status -eq "filled") "填充后状态为 filled" ("status=" + $bfillData.status)
  Assert-True ($bfillData.assetId -eq $baAssetId) "填充后绑定到目标资产" ("assetId=" + $bfillData.assetId)

  $bclose = Invoke-Api "DELETE" "/v1/browser-assist/sessions/$bSessionId"
  Assert-True ((Get-Code $bclose) -eq 0) "关闭会话成功" ("body=" + $bclose.Raw)
}

# 4) 会话列表
$blist = Invoke-Api "GET" "/v1/browser-assist/sessions"
Assert-True ((Get-Code $blist) -eq 0) "GET 会话列表成功" ("body=" + $blist.Raw)

# ────────────────────────────────────────────────────────────
# 汇总
# ────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ("通过: " + $script:pass + "   失败: " + $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) {
  Write-Host "失败明细:" -ForegroundColor Red
  foreach ($f in $script:failures) { Write-Host ("  - " + $f) -ForegroundColor Red }
}
Write-Host "========================================" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
