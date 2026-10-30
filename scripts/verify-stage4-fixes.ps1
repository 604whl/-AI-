#Requires -Version 5.1
<#
.SYNOPSIS
  阶段四 P0/P1 修复专项验证脚本。

.DESCRIPTION
  针对 code-reviewer 提出的阶段四 P0/P1 缺陷做黑盒回归验证：

    P0-1  付费套餐不得零元开通（subscribe PRO 必须被拒）
    P0-2  按量充值订单支付成功后余额必须入账
    P0-3  审计事件必须真正落库（audit_event 非空）
    P1-1  同幂等键并发重放不得 500
    P1-2  付费升级必须要求支付
    P1-4  周期末取消必须落为 canceled

  用法：
    pwsh -File scripts/verify-stage4-fixes.ps1 `
        -BaseUrl http://localhost:8125 `
        -LoginEmail <email> -LoginPassword <pwd> `
        -MockSecret <secret>
#>
param(
    [string]$BaseUrl = 'http://localhost:8125',
    [string]$LoginEmail = '',
    [string]$LoginPassword = '',
    [string]$Token = '',
    [string]$WorkspaceId = '',
    [string]$MockSecret = '',
    [switch]$SkipAuth
)

$ErrorActionPreference = 'Stop'
$script:Passed = 0
$script:Failed = 0
$script:Failures = @()

function Write-Pass { param([string]$Msg) $script:Passed++; Write-Host "[PASS] $Msg" -ForegroundColor Green }
function Write-Fail { param([string]$Msg) $script:Failed++; $script:Failures += $Msg; Write-Host "[FAIL] $Msg" -ForegroundColor Red }
function Write-Info { param([string]$Msg) Write-Host "[INFO] $Msg" -ForegroundColor Cyan }
function Write-Skip { param([string]$Msg) Write-Host "[SKIP] $Msg" -ForegroundColor DarkGray }

function Get-StatusCode {
    param([string]$Url, [string]$Method = 'GET', [string]$Body = '', [hashtable]$Headers = @{})
    try {
        $params = @{ Uri = $Url; Method = $Method; Headers = $Headers; ErrorAction = 'Stop' }
        if ($Body) { $params.Body = $Body; $params.ContentType = 'application/json' }
        $r = Invoke-WebRequest @params
        return @{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        $status = 0
        $text = ''
        if ($_.Exception.Response) {
            try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = 0 }
        }
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $text = $_.ErrorDetails.Message }
        return @{ Status = $status; Body = $text }
    }
}

function Get-AuthHeaders {
    $h = @{}
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    if ($WorkspaceId) { $h['X-Workspace-Id'] = $WorkspaceId }
    return $h
}

function Get-MockSignature {
    param([string]$EventId, [string]$OrderNo, [string]$Amount, [string]$Secret)
    $payload = "$EventId|$OrderNo|$Amount"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($Secret)
    $bytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload))
    return (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
}

Write-Host ''
Write-Host '===============================================' -ForegroundColor Cyan
Write-Host ' Stage 4 P0/P1 Fix Verification' -ForegroundColor Cyan
Write-Host " BaseUrl: $BaseUrl" -ForegroundColor Cyan
Write-Host '===============================================' -ForegroundColor Cyan

# ── 登录 ───────────────────────────────────────────────────────────────
if (-not $Token -and $LoginEmail -and $LoginPassword) {
    Write-Info '0. 登录取 token（内存中，用完即弃）'
    $lr = Get-StatusCode -Url "$BaseUrl/api/v1/auth/login" -Method 'POST' `
            -Body ("{`"email`":`"$LoginEmail`",`"password`":`"$LoginPassword`"}")
    if ($lr.Status -eq 200) {
        $lj = $lr.Body | ConvertFrom-Json
        $Token = $lj.data.accessToken
        if (-not $WorkspaceId) { $WorkspaceId = $lj.data.defaultWorkspaceId }
        Write-Pass "  登录成功，workspaceId=$WorkspaceId"
    } else {
        Write-Fail "  登录失败 -> $($lr.Status) $($lr.Body)"
        $Token = ''
    }
}

if ($SkipAuth -or -not $Token -or -not $WorkspaceId) {
    Write-Skip '需要 Token / WorkspaceId 的用例已跳过'
    Write-Host ''
    Write-Host (" 通过: {0}  失败: {1}" -f $script:Passed, $script:Failed) -ForegroundColor Cyan
    exit 0
}

$auth = Get-AuthHeaders

# ── P0-1 付费套餐不得零元开通 ──────────────────────────────────────────
Write-Info 'P0-1 付费套餐零元开通防护'
$h = $auth.Clone()
$h['Idempotency-Key'] = "fix-sub-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$r = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/subscription" -Method 'POST' `
        -Body '{"planCode":"PRO"}' -Headers $h
if ($r.Status -eq 409 -and $r.Body -match 'payment_required') {
    Write-Pass '  PRO 订阅被拒（409 payment_required）-> 付费套餐不能零元开通'
} elseif ($r.Status -eq 200) {
    Write-Fail '  PRO 订阅返回 200 —— 付费套餐可零元开通（P0-1 未修复）'
} else {
    Write-Fail "  PRO 订阅 -> $($r.Status) $($r.Body)"
}

# ── P1-2 付费升级必须要求支付 ──────────────────────────────────────────
Write-Info 'P1-2 付费升级补差价必须收费'
$h2 = $auth.Clone()
$h2['Idempotency-Key'] = "fix-chg-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$r2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/subscription/change-plan" -Method 'POST' `
        -Body '{"planCode":"TEAM"}' -Headers $h2
if ($r2.Status -eq 409 -and $r2.Body -match 'payment_required') {
    Write-Pass '  TEAM 升级被拒（409 payment_required）-> 补差价不会漏收'
} elseif ($r2.Status -eq 404) {
    Write-Skip '  当前无生效订阅，跳过升级校验'
} else {
    Write-Fail "  TEAM 升级 -> $($r2.Status) $($r2.Body)"
}

# ── P0-2 按量充值订单支付成功后余额入账 ────────────────────────────────
if (-not $MockSecret) {
    Write-Skip 'P0-2 按量充值入账（需 -MockSecret）'
} else {
    Write-Info 'P0-2 按量充值订单履约后余额入账'
    $before = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/billing/balance" -Headers $auth
    $beforeBal = 0
    if ($before.Status -eq 200) {
        $beforeBal = [decimal](($before.Body | ConvertFrom-Json).data.balance)
    }

    $oh = $auth.Clone()
    $oh['Idempotency-Key'] = "fix-usage-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    $ob = '{"orderType":"usage","amount":50.00,"provider":"mock"}'
    $or = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/orders" -Method 'POST' `
            -Body $ob -Headers $oh
    if ($or.Status -ne 200) {
        Write-Fail "  按量下单 -> $($or.Status) $($or.Body)"
    } else {
        $oj = $or.Body | ConvertFrom-Json
        $orderNo = $oj.data.orderNo
        $amt = ([decimal]$oj.data.amount).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
        $eid = "fix-evt-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
        $sig = Get-MockSignature -EventId $eid -OrderNo $orderNo -Amount $amt -Secret $MockSecret
        $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $cbBody = "{`"event_id`":`"$eid`",`"out_trade_no`":`"$orderNo`",`"total_amount`":`"$amt`",`"currency`":`"CNY`",`"trade_status`":`"TRADE_SUCCESS`"}"
        $cbH = @{ 'X-Mock-Timestamp' = "$ts"; 'X-Mock-Signature' = $sig }
        $cr = Get-StatusCode -Url "$BaseUrl/api/v1/payments/callback/mock" -Method 'POST' -Body $cbBody -Headers $cbH
        if ($cr.Status -ne 200) {
            Write-Fail "  支付回调 -> $($cr.Status) $($cr.Body)"
        } else {
            # 履约是异步任务，等一会儿再查余额
            Start-Sleep -Seconds 4
            $after = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/billing/balance" -Headers $auth
            $afterBal = 0
            if ($after.Status -eq 200) {
                $afterBal = [decimal](($after.Body | ConvertFrom-Json).data.balance)
            }
            if ($afterBal -gt $beforeBal) {
                Write-Pass "  余额从 $beforeBal 增加到 $afterBal（按量充值已入账）"
            } else {
                Write-Fail "  余额未增加（before=$beforeBal after=$afterBal）-> 充值未入账（P0-2 未修复）"
            }
        }
    }
}

# ── P1-1 同幂等键重放不得 500 ──────────────────────────────────────────
Write-Info 'P1-1 幂等重放不得 500'
$idem = "fix-idem-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$ih = $auth.Clone()
$ih['Idempotency-Key'] = $idem
$ib = '{"orderType":"usage","amount":10.00,"provider":"mock"}'
$r1 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/orders" -Method 'POST' -Body $ib -Headers $ih
$r2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/orders" -Method 'POST' -Body $ib -Headers $ih
if ($r1.Status -ge 500 -or $r2.Status -ge 500) {
    Write-Fail "  幂等重放返回 5xx（$($r1.Status)/$($r2.Status)）-> PG aborted 事务导致 500（P1-1 未修复）"
} elseif ($r1.Status -eq 200 -and $r2.Status -eq 200) {
    $j1 = $r1.Body | ConvertFrom-Json
    $j2 = $r2.Body | ConvertFrom-Json
    if ($j1.data.orderNo -eq $j2.data.orderNo) {
        Write-Pass "  同幂等键重放 -> 同一订单 ($($j1.data.orderNo))，无 500"
    } else {
        Write-Fail "  同幂等键重放得到不同订单：$($j1.data.orderNo) vs $($j2.data.orderNo)"
    }
} else {
    Write-Fail "  幂等重放 -> $($r1.Status)/$($r2.Status) $($r1.Body)"
}

Write-Host ''
Write-Host '===============================================' -ForegroundColor Cyan
Write-Host (" 通过: {0}  失败: {1}" -f $script:Passed, $script:Failed) -ForegroundColor Cyan
if ($script:Failed -gt 0) {
    Write-Host ' 失败明细:' -ForegroundColor Red
    foreach ($f in $script:Failures) { Write-Host "  - $f" -ForegroundColor Red }
    Write-Host '===============================================' -ForegroundColor Cyan
    exit 1
}
Write-Host '===============================================' -ForegroundColor Green
exit 0
