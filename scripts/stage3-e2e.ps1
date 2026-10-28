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
Assert-True ($null -ne $script:wsId)  "获取 defaultWorkspaceId" ("ws=" + $script:wsId)

# ────────────────────────────────────────────────────────────
# 1. 连接器门禁（场景 1）
# ────────────────────────────────────────────────────────────
Write-Step "1. 连接器门禁：未授权平台必须 403 CONNECTOR_NOT_AUTHORIZED"

$conn = Invoke-Api "GET" "/v1/connectors"
$connData = Get-Data $conn
Assert-True ((Get-Code $conn) -eq 0) "GET /connectors 返回 0" ("code=" + (Get-Code $conn))

$unauth = Invoke-Api "POST" "/v1/platform-accounts" @{ platformCode = "KUAISHOU"; accountLabel = "e2e-unauth-$runId"; connectorProviderCode = "KUAISHOU_CSV_IMPORT" }
Assert-True ($unauth.Status -eq 403) "未授权平台创建账号返回 403" ("status=" + $unauth.Status + " body=" + $unauth.Raw)
Assert-True (($unauth.Raw -like "*CONNECTOR_NOT_AUTHORIZED*") -or ($unauth.Raw -like "*403*")) "错误含 CONNECTOR_NOT_AUTHORIZED" ($unauth.Raw)

# 授权清单内的小红书可以创建
$xhsAcc = Invoke-Api "POST" "/v1/platform-accounts" @{ platformCode = "XIAOHONGSHU"; accountLabel = "e2e-xhs-$runId"; connectorProviderCode = "XHS_BROWSER_SIDEBAR" }
$accData = Get-Data $xhsAcc
$script:accountId = $null
if ((Get-Code $xhsAcc) -eq 0 -and $null -ne $accData) { $script:accountId = $accData.id }
Assert-True ($null -ne $script:accountId) "授权平台(小红书)创建账号成功" ("code=" + (Get-Code $xhsAcc) + " body=" + $xhsAcc.Raw)

# ────────────────────────────────────────────────────────────
# 2. 准备内容资产（发布闭环前置）
# ────────────────────────────────────────────────────────────
Write-Step "2. 准备内容资产（E1/E2 前置）"
$assetBody = @{
  title = "E2E 阶段3 验收测试笔记"
  body  = "这是阶段3 端到端验收用的测试内容，用于验证发布回执、指标快照与复盘建议链路。"
  persona = "agency"
  scenario = "published"
  platform = "XIAOHONGSHU"
}
$asset = Invoke-Api "POST" "/v1/assets" $assetBody
$assetData = Get-Data $asset
$script:assetId = $null
if ((Get-Code $asset) -eq 0 -and $null -ne $assetData) { $script:assetId = $assetData.id }
Assert-True ($null -ne $script:assetId) "创建内容资产成功" ("code=" + (Get-Code $asset) + " body=" + $asset.Raw)

# 走真实状态机 DRAFT -> IN_REVIEW -> APPROVED（未过审的资产不允许排期/发布）
$script:reviewTaskId = $null
if ($null -ne $script:assetId) {
  $submit = Invoke-Api "POST" "/v1/assets/$script:assetId/submit-review" @{}
  Assert-True ((Get-Code $submit) -eq 0) "提交审核成功" ("code=" + (Get-Code $submit) + " body=" + $submit.Raw)

  # submit-review 返回的是资产本身；审核任务要通过 GET /v1/reviews/submissions 查
  $subs = Invoke-Api "GET" "/v1/reviews/submissions?page=1&size=50"
  $subsData = Get-Data $subs
  $items = $null
  if ($null -ne $subsData) {
    if ($subsData.PSObject.Properties.Name -contains "content") { $items = $subsData.content }
    elseif ($subsData.PSObject.Properties.Name -contains "records") { $items = $subsData.records }
    elseif ($subsData.PSObject.Properties.Name -contains "items") { $items = $subsData.items }
    elseif ($subsData -is [System.Array]) { $items = $subsData }
  }
  if ($null -ne $items) {
    foreach ($it in $items) {
      if ($null -ne $it -and $it.PSObject.Properties.Name -contains "assetId" -and $it.assetId -eq $script:assetId) {
        if ($it.PSObject.Properties.Name -contains "id") { $script:reviewTaskId = $it.id }
        elseif ($it.PSObject.Properties.Name -contains "reviewTaskId") { $script:reviewTaskId = $it.reviewTaskId }
        elseif ($it.PSObject.Properties.Name -contains "taskId") { $script:reviewTaskId = $it.taskId }
        break
      }
    }
  }
  Assert-True ($null -ne $script:reviewTaskId) "取到审核任务 ID" ("subs=" + $subs.Raw)

  if ($null -ne $script:reviewTaskId) {
    $approve = Invoke-Api "POST" "/v1/reviews/$script:reviewTaskId/approve" @{ comment = "e2e approve" }
    Assert-True ((Get-Code $approve) -eq 0) "审核通过（资产转 approved）" ("code=" + (Get-Code $approve) + " body=" + $approve.Raw)
  }

  $assetNow = Invoke-Api "GET" "/v1/assets/$script:assetId"
  $nowData = Get-Data $assetNow
  $nowStatus = $null
  if ($null -ne $nowData -and $nowData.PSObject.Properties.Name -contains "status") { $nowStatus = $nowData.status }
  Assert-True ($nowStatus -eq "approved") "资产状态推进到 approved" ("status=" + $nowStatus)
}

# ────────────────────────────────────────────────────────────
# 3. 场景 4：幂等（同一幂等键重复提交发布任务）
# ────────────────────────────────────────────────────────────
Write-Step "3. 幂等：同一 Idempotency-Key 重复提交发布任务"

$idemKey = [guid]::NewGuid().ToString()
$schedAt = (Get-Date).AddMinutes(10).ToUniversalTime().ToString("o")
$taskBody = @{
  assetId = $script:assetId
  platformCode = "XIAOHONGSHU"
  accountId = $script:accountId
  scheduledAt = $schedAt
}
$task1 = Invoke-Api "POST" "/v1/publish-tasks" $taskBody @{ "Idempotency-Key" = $idemKey }
$taskData = Get-Data $task1
$script:taskId = $null
if ($null -ne $taskData -and $taskData.PSObject.Properties.Name -contains "id") { $script:taskId = $taskData.id }
Assert-True ((Get-Code $task1) -eq 0) "首次提交发布任务成功" ("code=" + (Get-Code $task1) + " body=" + $task1.Raw)

$task2 = Invoke-Api "POST" "/v1/publish-tasks" $taskBody @{ "Idempotency-Key" = $idemKey }
$taskData2 = Get-Data $task2
$sameId = ($null -ne $script:taskId -and $null -ne $taskData2 -and $taskData2.id -eq $script:taskId)
Assert-True $sameId "重复幂等键返回同一 taskId（重复发布率 0）" ("first=" + $script:taskId + " second=" + $taskData2.id)

$taskList = Invoke-Api "GET" "/v1/publish-tasks/$script:taskId"
Assert-True ((Get-Code $taskList) -eq 0) "GET 发布任务详情成功" ("code=" + (Get-Code $taskList))

# ────────────────────────────────────────────────────────────
# 4. 排期（场景 2）
# ────────────────────────────────────────────────────────────
Write-Step "4. 排期日历：创建排期 + 冲突检测"
$schedBody = @{
  assetId = $script:assetId
  platformCode = "XIAOHONGSHU"
  accountId = $script:accountId
  scheduledAt = $schedAt
}
$sched = Invoke-Api "POST" "/v1/publish-schedules" $schedBody
$schedData = Get-Data $sched
$script:scheduleId = $null
if ($null -ne $schedData -and $schedData.PSObject.Properties.Name -contains "id") { $script:scheduleId = $schedData.id }
Assert-True ((Get-Code $sched) -eq 0) "创建排期成功" ("code=" + (Get-Code $sched) + " body=" + $sched.Raw)

$schedDup = Invoke-Api "POST" "/v1/publish-schedules" $schedBody
Assert-True ($schedDup.Status -eq 409) "同账号同时间排期冲突被拒(409)" ("status=" + $schedDup.Status + " body=" + $schedDup.Raw)

$cal = Invoke-Api "GET" "/v1/publish-schedules/calendar"
Assert-True ((Get-Code $cal) -eq 0) "GET 排期日历成功" ("code=" + (Get-Code $cal))

# ────────────────────────────────────────────────────────────
# 5. 场景 2 / E1：发布回执 → 资产 PUBLISHED
# ────────────────────────────────────────────────────────────
Write-Step "5. E1 发布回执：回填外部内容 ID → 资产状态 PUBLISHED"
$receiptBody = @{
  publishTaskId = $script:taskId
  externalContentId = "e2e-xhs-note-0001"
  sourceUrl = "https://www.xiaohongshu.com/explore/e2e-xhs-note-0001"
  publishedAt = (Get-Date).ToUniversalTime().ToString("o")
}
$receipt = Invoke-Api "POST" "/v1/publish-receipts" $receiptBody
Assert-True ((Get-Code $receipt) -eq 0) "创建发布回执成功" ("code=" + (Get-Code $receipt) + " body=" + $receipt.Raw)

$receipts = Invoke-Api "GET" "/v1/assets/$script:assetId/receipts"
$rcData = Get-Data $receipts
$rcCount = 0
if ($null -ne $rcData) { $rcCount = @($rcData).Count }
Assert-True ((Get-Code $receipts) -eq 0) "GET 资产回执列表成功" ("code=" + (Get-Code $receipts))
Assert-True ($rcCount -ge 1) "资产回执数量 >= 1" ("count=" + $rcCount)

$assetAfter = Invoke-Api "GET" "/v1/assets/$script:assetId"
$aData = Get-Data $assetAfter
$assetStatus = $null
if ($null -ne $aData -and $aData.PSObject.Properties.Name -contains "status") { $assetStatus = $aData.status }
Assert-True ($assetStatus -eq "published") "资产状态推进到 published" ("status=" + $assetStatus)

# ────────────────────────────────────────────────────────────
# 6. 场景 3 / E2：抖音手动导入 → 指标快照
# ────────────────────────────────────────────────────────────
Write-Step "6. E2 手动发布回收：CSV 回填指标 → 生成指标快照"
$snapBody = @{
  assetId = $script:assetId
  platformCode = "XIAOHONGSHU"
  accountId = $script:accountId
  externalContentId = "e2e-xhs-note-0001"
  publishedAt = (Get-Date).ToUniversalTime().ToString("o")
  metrics = @{
    impressions = 1000; plays = 800; clicks = 120; likes = 60; collects = 40
    comments = 12; shares = 8; follows = 5; dm_inquiries = 3; conversions = 2
  }
  metricSource = "MANUAL_IMPORT"
  confidence = "LOW"
}
$snap = Invoke-Api "POST" "/v1/metric-snapshots" $snapBody
$snapData = Get-Data $snap
$script:snapshotId = $null
if ($null -ne $snapData -and $snapData.PSObject.Properties.Name -contains "id") { $script:snapshotId = $snapData.id }
Assert-True ((Get-Code $snap) -eq 0) "手动录入指标快照成功" ("code=" + (Get-Code $snap) + " body=" + $snap.Raw)

$snapSource = $null; $snapConf = $null
if ($null -ne $snapData) {
  if ($snapData.PSObject.Properties.Name -contains "metricSource") { $snapSource = $snapData.metricSource }
  if ($snapData.PSObject.Properties.Name -contains "confidence") { $snapConf = $snapData.confidence }
}
Assert-True ($snapSource -eq "MANUAL_IMPORT") "快照来源标记 MANUAL_IMPORT" ("source=" + $snapSource)
Assert-True ($snapConf -eq "LOW") "快照可信度标记 LOW" ("confidence=" + $snapConf)

# 修订链：修订产生新快照，不覆盖原快照
$revise = Invoke-Api "POST" "/v1/metric-snapshots/$script:snapshotId/revise" @{ metrics = @{ likes = 75 }; confidence = "MEDIUM"; reason = "e2e 修订" }
Assert-True ((Get-Code $revise) -eq 0) "修订快照成功" ("code=" + (Get-Code $revise) + " body=" + $revise.Raw)

$snapDetail = Invoke-Api "GET" "/v1/metric-snapshots/$script:snapshotId"
Assert-True ((Get-Code $snapDetail) -eq 0) "GET 快照详情(含修订链)成功" ("code=" + (Get-Code $snapDetail))

$trend = Invoke-Api "GET" "/v1/metric-snapshots/trend?granularity=day"
Assert-True ((Get-Code $trend) -eq 0) "GET 指标趋势成功" ("code=" + (Get-Code $trend))

# ────────────────────────────────────────────────────────────
# 7. 场景 6：预测 → 对比 → 复盘（E3）
# ────────────────────────────────────────────────────────────
Write-Step "7. E3 复盘：预测 → 对比 → 复盘报告 ≥3 条带证据建议"

$forecast = Invoke-Api "POST" "/v1/forecasts" @{
  assetId = $script:assetId
  platformCode = "XIAOHONGSHU"
  forecastType = "IMPRESSION"
  predictedMetrics = @{
    impressions = 1200; plays = 900; clicks = 150; likes = 50; collects = 60
    comments = 10; shares = 5; dm_inquiries = 2; conversions = 1
  }
  predictedPublishedAt = (Get-Date).ToUniversalTime().ToString("o")
}
$fcData = Get-Data $forecast
$script:forecastId = $null
if ($null -ne $fcData -and $fcData.PSObject.Properties.Name -contains "id") { $script:forecastId = $fcData.id }
Assert-True ((Get-Code $forecast) -eq 0) "创建预测成功" ("code=" + (Get-Code $forecast) + " body=" + $forecast.Raw)

$comparison = Invoke-Api "POST" "/v1/comparisons" @{ forecastId = $script:forecastId; snapshotId = $script:snapshotId }
Assert-True ((Get-Code $comparison) -eq 0) "计算预测vs实际对比成功" ("code=" + (Get-Code $comparison) + " body=" + $comparison.Raw)

$report = Invoke-Api "POST" "/v1/retrospectives" @{
  scopeType = "ASSET"
  scopeKey = $script:assetId
  assetId = $script:assetId
  platformCode = "XIAOHONGSHU"
}
$rptData = Get-Data $report
$script:reportId = $null
if ($null -ne $rptData) {
  if ($rptData.PSObject.Properties.Name -contains "reportId") { $script:reportId = $rptData.reportId }
  elseif ($rptData.PSObject.Properties.Name -contains "id") { $script:reportId = $rptData.id }
}
Assert-True ((Get-Code $report) -eq 0) "创建复盘报告请求成功" ("code=" + (Get-Code $report) + " body=" + $report.Raw)

# 报告可能是异步任务，轮询等待完成
$script:rptFinal = $null
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  $r = Invoke-Api "GET" "/v1/retrospectives/$script:reportId"
  $d = Get-Data $r
  if ($null -ne $d -and $d.PSObject.Properties.Name -contains "status") {
    $st = $d.status
    if ($st -eq "COMPLETED" -or $st -eq "DEGRADED" -or $st -eq "INSUFFICIENT_DATA" -or $st -eq "completed" -or $st -eq "degraded" -or $st -eq "insufficient_data") {
      $script:rptFinal = $d
      break
    }
  }
}
Assert-True ($null -ne $script:rptFinal) "复盘报告生成到终态" ("reportId=" + $script:reportId)

if ($null -ne $script:rptFinal) {
  $sugg = @()
  if ($script:rptFinal.PSObject.Properties.Name -contains "suggestions" -and $null -ne $script:rptFinal.suggestions) { $sugg = @($script:rptFinal.suggestions) }
  $st = $script:rptFinal.status
  Write-Host ("  报告 status = " + $st + "，suggestions = " + $sugg.Count)
  if ($st -eq "COMPLETED" -or $st -eq "completed") {
    Assert-True ($sugg.Count -ge 3) "E3: COMPLETED 报告建议数 >= 3" ("count=" + $sugg.Count)
    $withEvidence = 0
    foreach ($s in $sugg) {
      if ($s.PSObject.Properties.Name -contains "evidenceRefs" -and $null -ne $s.evidenceRefs -and @($s.evidenceRefs).Count -gt 0) { $withEvidence++ }
    }
    Assert-True ($withEvidence -eq $sugg.Count) "E3: 每条建议 evidenceRefs 非空" ("withEvidence=" + $withEvidence + "/" + $sugg.Count)
  } else {
    Assert-True ($st -eq "INSUFFICIENT_DATA" -or $st -eq "insufficient_data" -or $st -eq "DEGRADED" -or $st -eq "degraded") "数据不足时降级标记正确(非伪成功)" ("status=" + $st)
  }
}

# ────────────────────────────────────────────────────────────
# 8. 场景 7：复盘回写审批（PENDING 不改统计，APPROVED 才改）
# ────────────────────────────────────────────────────────────
Write-Step "8. 回写审批：PENDING 状态不生效"
$fb = Invoke-Api "POST" "/v1/retrospectives/$script:reportId/feedback" @{
  targetType = "TEMPLATE"
  targetId = "00000000-0000-0000-0000-000000000000"
  proposedContent = "e2e 复盘回写内容"
  proposedStats = @{ sampleSize = 1; avgImpressions = 1000 }
}
$fbData = Get-Data $fb
$fbStatus = $null
if ($null -ne $fbData -and $fbData.PSObject.Properties.Name -contains "status") { $fbStatus = $fbData.status }
Assert-True ($fbStatus -eq "PENDING") "回写申请初始状态 PENDING" ("status=" + $fbStatus + " body=" + $fb.Raw)

$fbList = Invoke-Api "GET" "/v1/retrospectives/$script:reportId/feedback"
Assert-True ((Get-Code $fbList) -eq 0) "GET 回写列表成功" ("code=" + (Get-Code $fbList))

# ────────────────────────────────────────────────────────────
# 9. 场景 8：热点与竞品产品化
# ────────────────────────────────────────────────────────────
Write-Step "9. 热点产品化：来源 / 可信度 / 是否实时 / 降级可见"
$hot = Invoke-Api "GET" "/v1/hot-topics?page=1&size=5"
$hotData = Get-Data $hot
Assert-True ((Get-Code $hot) -eq 0) "GET 热点列表成功" ("code=" + (Get-Code $hot))
if ($null -ne $hotData) {
  $hasDegradedField = ($hotData.PSObject.Properties.Name -contains "degraded")
  Assert-True $hasDegradedField "热点响应含顶层 degraded 字段" ("fields=" + ($hotData.PSObject.Properties.Name -join ","))
  $items = @()
  if ($hotData.PSObject.Properties.Name -contains "items") { $items = @($hotData.items) }
  elseif ($hotData -is [System.Array]) { $items = @($hotData) }
  $nonRealtimeMarked = $true
  foreach ($it in $items) {
    if ($it.PSObject.Properties.Name -contains "sourceType" -and $it.sourceType -eq "SEED") {
      if (-not ($it.PSObject.Properties.Name -contains "isRealtime") -or $it.isRealtime -ne $false) { $nonRealtimeMarked = $false }
    }
  }
  Assert-True $nonRealtimeMarked "SEED 种子数据标记 isRealtime=false" ""
}

$hotImport = Invoke-Api "POST" "/v1/hot-topics/import" @{ items = @(@{ title = "E2E 手动导入热点"; platform = "XIAOHONGSHU"; heatScore = 77; sourceName = "e2e"; sourceUrl = "https://example.com/e2e" }) }
Assert-True ((Get-Code $hotImport) -eq 0) "手动导入热点成功" ("code=" + (Get-Code $hotImport) + " body=" + $hotImport.Raw)

$comp = Invoke-Api "GET" "/v1/competitors?page=1&size=5"
Assert-True ((Get-Code $comp) -eq 0) "GET 竞品列表成功" ("code=" + (Get-Code $comp))

# 竞品笔记证据必填
$compAcc = Invoke-Api "POST" "/v1/competitors" @{ platformCode = "XIAOHONGSHU"; accountName = "E2E 竞品账号 $runId"; category = "career_fair" }
$compAccData = Get-Data $compAcc
$compAccId = $null
if ($null -ne $compAccData -and $compAccData.PSObject.Properties.Name -contains "id") { $compAccId = $compAccData.id }
Assert-True ($null -ne $compAccId) "创建竞品账号成功" ("code=" + (Get-Code $compAcc) + " body=" + $compAcc.Raw)

if ($null -ne $compAccId) {
  $noteNoEvidence = Invoke-Api "POST" "/v1/competitors/$compAccId/notes" @{ noteTitle = "E2E 笔记"; noteUrl = "https://example.com/n1"; sourceType = "MANUAL_IMPORT" }
  Assert-True ($noteNoEvidence.Status -eq 400) "竞品笔记缺 evidenceText 返回 400" ("status=" + $noteNoEvidence.Status + " body=" + $noteNoEvidence.Raw)

  $noteOk = Invoke-Api "POST" "/v1/competitors/$compAccId/notes" @{
    noteTitle = "E2E 笔记"
    noteUrl = "https://example.com/n1"
    sourceType = "MANUAL_IMPORT"
    evidenceText = "原文片段：留学生求职第一份实习怎么找"
    publishedAt = (Get-Date).ToUniversalTime().ToString("o")
    engagement = @{ likes = 100; collects = 50; comments = 10 }
  }
  Assert-True ((Get-Code $noteOk) -eq 0) "带 evidenceText 的竞品笔记入库成功" ("code=" + (Get-Code $noteOk) + " body=" + $noteOk.Raw)

  $notes = Invoke-Api "GET" "/v1/competitors/$compAccId/notes"
  Assert-True ((Get-Code $notes) -eq 0) "GET 竞品笔记列表成功" ("code=" + (Get-Code $notes))
}

# ────────────────────────────────────────────────────────────
# 10. 场景 5：降级路径 + 内容编辑不阻塞
# ────────────────────────────────────────────────────────────
Write-Step "10. 降级路径可见 + 内容编辑不阻塞"
$deg = Invoke-Api "GET" "/v1/connectors/degradation"
Assert-True ((Get-Code $deg) -eq 0) "GET 降级路径成功" ("code=" + (Get-Code $deg))

$health = Invoke-Api "GET" "/v1/connectors/XHS_BROWSER_SIDEBAR/health"
Assert-True ((Get-Code $health) -eq 0) "GET 连接器健康成功" ("code=" + (Get-Code $health) + " body=" + $health.Raw)

# 连接器异常不阻塞内容编辑
# 用一个全新的草稿资产：前面的发布闭环已经把 $script:assetId 推进到 published
# （终态，本来就不允许编辑），而本用例要验证的是"连接器降级不阻塞编辑"。
$editAsset = Invoke-Api "POST" "/v1/assets" @{
  title = "E2E 降级路径内容编辑验证"
  body = "验证连接器降级时内容编辑仍然可用"
  persona = "agency"
  scenario = "published"
  platform = "XIAOHONGSHU"
}
$editAssetData = Get-Data $editAsset
$editAssetId = $null
if ($null -ne $editAssetData -and $editAssetData.PSObject.Properties.Name -contains "id") { $editAssetId = $editAssetData.id }
Assert-True ($null -ne $editAssetId) "创建可编辑草稿资产成功" ("code=" + (Get-Code $editAsset) + " body=" + $editAsset.Raw)

if ($null -ne $editAssetId) {
  $edit = Invoke-Api "PATCH" "/v1/assets/$editAssetId" @{ title = "E2E 降级路径内容编辑验证(已编辑)" }
  Assert-True ((Get-Code $edit) -eq 0) "连接器异常不阻塞内容编辑" ("code=" + (Get-Code $edit) + " body=" + $edit.Raw)
} else {
  Assert-True $false "连接器异常不阻塞内容编辑" "草稿资产创建失败，无法验证编辑"
}

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
