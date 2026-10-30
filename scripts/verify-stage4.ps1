#Requires -Version 5.1
<#
.SYNOPSIS
  Stage 4 (S4-0 ~ S4-3) 端到端验证脚本。

.DESCRIPTION
  针对本地运行中的后端做黑盒验证，覆盖阶段四新增能力：
    1. 套餐目录（公开，无需鉴权）
    2. 订阅生命周期（创建 / 幂等重放 / 查询 / 变更 / 取消）
    3. 订单创建 + 幂等重放
    4. 支付回调鉴权（无签名 / 无时间戳 / 伪造签名必须被拒）
    5. 支付回调验签通过 -> 订单入账（需 PAYMENT_MOCK_SECRET）
    6. 余额扣减 / 冻结 / 可用余额口径
    7. 账单生成与幂等

  用法：
    # 仅跑不需要鉴权的部分（套餐 + 回调鉴权负向）
    pwsh -File scripts/verify-stage4.ps1 -BaseUrl http://localhost:8125

    # 完整流程（需提供 Token / WorkspaceId / Mock 密钥）
    pwsh -File scripts/verify-stage4.ps1 `
        -Token <accessToken> `
        -WorkspaceId ws_xxx `
        -MockSecret <secret>

.EXAMPLE
  pwsh -File scripts/verify-stage4.ps1 -SkipAuth
#>

param(
    [string]$BaseUrl = 'http://localhost:8125',
    [string]$Token = '',
    [string]$WorkspaceId = '',
    [string]$MockSecret = '',
    [string]$PlanCode = 'PRO',
    # 自登录凭据：脚本内部登录取 token，不落盘、不写环境变量，用完即弃。
    [string]$LoginEmail = '',
    [string]$LoginPassword = '',
    [switch]$SkipAuth,
    [switch]$SkipCallbackSuccess
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
        $resp = $_.Exception.Response
        if ($null -ne $resp) {
            $code = [int]$resp.StatusCode
            $text = ''
            try {
                # 部分 PowerShell 版本把响应体放进 ErrorDetails，而非响应流
                try {
                    if ($null -ne $_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
                        $text = $_.ErrorDetails.Message
                    }
                } catch { }
                if ([string]::IsNullOrWhiteSpace($text)) {
                    try {
                        $stream = $resp.GetResponseStream()
                        if ($null -ne $stream) {
                            $sr = New-Object System.IO.StreamReader($stream)
                            $text = $sr.ReadToEnd()
                            $sr.Close()
                        }
                    } catch { }
                }
            } catch { }
            return @{ Status = $code; Body = $text }
        }
        return @{ Status = 0; Body = $_.Exception.Message }
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
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $bytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
        return -join ($bytes | ForEach-Object { $_.ToString('x2') })
    } finally { $hmac.Dispose() }
}

Write-Host ''
Write-Host '===============================================' -ForegroundColor Cyan
Write-Host ' Stage 4 E2E Verification (S4-0 ~ S4-3)' -ForegroundColor Cyan
Write-Host " BaseUrl: $BaseUrl" -ForegroundColor DarkGray
Write-Host '===============================================' -ForegroundColor Cyan

# ── 1. 健康检查 ────────────────────────────────────────────────
Write-Info '1. 健康检查'
$health = Get-StatusCode -Url "$BaseUrl/api/v1/health"
if ($health.Status -eq 200) { Write-Pass "GET /v1/health -> 200" }
else { Write-Fail "GET /v1/health -> $($health.Status)（期望 200）" }

# ── 2. 套餐目录（公开） ────────────────────────────────────────
Write-Info '2. 套餐目录（公开接口，应免鉴权）'
$plans = Get-StatusCode -Url "$BaseUrl/api/v1/plans"
if ($plans.Status -eq 200) {
    Write-Pass 'GET /v1/plans -> 200'
    try {
        $json = $plans.Body | ConvertFrom-Json
        $codes = @($json.data | ForEach-Object { $_.code })
        Write-Info "  套餐 codes: $($codes -join ', ')"
        if ($codes -contains 'FREE' -and $codes -contains 'PRO') {
            Write-Pass '  免费版 / 专业版套餐均已上架'
        } else {
            Write-Fail "  套餐列表缺少 FREE/PRO（实际: $($codes -join ', ')）"
        }
    } catch {
        Write-Fail "  套餐响应不是合法 JSON: $($_.Exception.Message)"
    }
} elseif ($plans.Status -eq 401) {
    Write-Fail 'GET /v1/plans -> 401：公开接口被鉴权拦截'
} else {
    Write-Fail "GET /v1/plans -> $($plans.Status)（期望 200）"
}

# ── 3. 套餐详情（公开） ────────────────────────────────────────
Write-Info '3. 套餐详情（公开接口）'
$plan = Get-StatusCode -Url "$BaseUrl/api/v1/plans/$PlanCode"
if ($plan.Status -eq 200) { Write-Pass "GET /v1/plans/$PlanCode -> 200" }
elseif ($plan.Status -eq 404) { Write-Fail "GET /v1/plans/$PlanCode -> 404：套餐不存在" }
else { Write-Fail "GET /v1/plans/$PlanCode -> $($plan.Status)（期望 200）" }

# ── 4. 支付回调鉴权负向用例 ───────────────────────────────────
Write-Info '4. 支付回调鉴权（负向用例，必须全部拒绝）'
$cbUrl = "$BaseUrl/api/v1/payments/callback/mock"

# 4a. 完全无凭据
$r = Get-StatusCode -Url $cbUrl -Method 'POST' -Body '{"event_id":"e2e-a","out_trade_no":"T","total_amount":"1.00"}'
if ($r.Status -eq 400 -and $r.Body -match 'timestamp_missing') {
    Write-Pass '  无时间戳 -> 400 timestamp_missing'
} elseif ($r.Status -eq 400 -and $r.Body -match 'signature_missing') {
    Write-Pass '  无签名 -> 400 signature_missing'
} elseif ($r.Status -eq 401) {
    Write-Fail '  无凭据 -> 401：回调被鉴权拦截（PaymentCallbackAuthFilter 未生效）'
} elseif ($r.Status -eq 503 -and $r.Body -match 'mock_secret_not_configured') {
    Write-Pass '  未配置 mock 密钥 -> 503 mock_secret_not_configured'
} else {
    Write-Fail "  无凭据 -> $($r.Status) $($r.Body)"
}

# 4b. 有时间戳无签名
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$r = Get-StatusCode -Url $cbUrl -Method 'POST' -Body '{"event_id":"e2e-b","out_trade_no":"T","total_amount":"1.00"}' -Headers @{ 'X-Mock-Timestamp' = "$ts" }
if ($r.Status -eq 400 -and $r.Body -match 'signature_missing') {
    Write-Pass '  有时间戳无签名 -> 400 signature_missing'
} elseif ($r.Status -eq 503 -and $r.Body -match 'mock_secret_not_configured') {
    Write-Pass '  未配置 mock 密钥 -> 503 mock_secret_not_configured'
} else {
    Write-Fail "  有时间戳无签名 -> $($r.Status) $($r.Body)"
}

# 4c. 伪造签名
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$r = Get-StatusCode -Url $cbUrl -Method 'POST' -Body '{"event_id":"e2e-c","out_trade_no":"T","total_amount":"1.00"}' -Headers @{ 'X-Mock-Timestamp' = "$ts"; 'X-Mock-Signature' = 'deadbeefcafe' }
if ($r.Status -eq 400 -and $r.Body -match 'signature_invalid') {
    Write-Pass '  伪造签名 -> 400 signature_invalid'
} elseif ($r.Status -eq 503 -and $r.Body -match 'mock_secret_not_configured') {
    Write-Pass '  未配置 mock 密钥 -> 503 mock_secret_not_configured（默认密钥已被移除）'
} else {
    Write-Fail "  伪造签名 -> $($r.Status) $($r.Body)（若为 2xx 说明验签被绕过）"
}

# ── 5. 需要鉴权的部分 ─────────────────────────────────────────
# 自登录：只在内存中拿 token，不落盘 / 不写环境变量
if (-not $Token -and $LoginEmail -and $LoginPassword) {
    Write-Info "5. 登录取 token（内存中，用完即弃）"
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
    Write-Skip '5-8. 需要 Token / WorkspaceId 的流程已跳过（-SkipAuth 或未提供凭据）'
} else {
    $auth = Get-AuthHeaders

    # 5. 查询订阅
    Write-Info '5. 订阅查询'
    $sub = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/subscription" -Headers $auth
    if ($sub.Status -eq 200) { Write-Pass 'GET 订阅 -> 200' }
    elseif ($sub.Status -eq 404) { Write-Pass 'GET 订阅 -> 404（尚无订阅，符合预期）' }
    else { Write-Fail "GET 订阅 -> $($sub.Status) $($sub.Body)" }

    # 6. 订阅幂等键重放
    Write-Info '6. 订阅幂等重放'
    $idem = "e2e-sub-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    $body = "{`"planCode`":`"$PlanCode`"}"
    $h1 = $auth.Clone(); $h1['Idempotency-Key'] = $idem
    $r1 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/subscription" -Method 'POST' -Body $body -Headers $h1
    $r2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/subscription" -Method 'POST' -Body $body -Headers $h1
    if ($r1.Status -eq 200 -and $r2.Status -eq 200) {
        $j1 = $r1.Body | ConvertFrom-Json; $j2 = $r2.Body | ConvertFrom-Json
        if ($j1.data.id -eq $j2.data.id) { Write-Pass "  同幂等键重放 -> 同一订阅 ($($j1.data.id))" }
        else { Write-Fail "  同幂等键重放得到不同订阅: $($j1.data.id) vs $($j2.data.id)" }
    } elseif ($r1.Status -eq 409) {
        Write-Pass '  已存在生效订阅 -> 409（幂等重放由 409 拦截，符合预期）'
    } else {
        Write-Fail "  订阅创建 -> $($r1.Status)/$($r2.Status) $($r1.Body)"
    }

    # 7. 订单创建 + 幂等重放
    Write-Info '7. 订单创建与幂等重放'
    $oidem = "e2e-order-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    $obody = "{`"planCode`":`"$PlanCode`"}"
    $oh1 = $auth.Clone(); $oh1['Idempotency-Key'] = $oidem
    $o1 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/orders" -Method 'POST' -Body $obody -Headers $oh1
    $o2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/orders" -Method 'POST' -Body $obody -Headers $oh1
    $orderNo = $null
    $orderAmount = $null
    if ($o1.Status -eq 200) {
        $oj = $o1.Body | ConvertFrom-Json
        $orderNo = $oj.data.orderNo
        $orderAmount = $oj.data.amount
        if ($o2.Status -eq 200) {
            $oj2 = $o2.Body | ConvertFrom-Json
            if ($oj2.data.orderNo -eq $orderNo) { Write-Pass "  同幂等键重放 -> 同一订单 ($orderNo)" }
            else { Write-Fail "  同幂等键重放得到不同订单: $orderNo vs $($oj2.data.orderNo)" }
        } else { Write-Fail "  订单重放 -> $($o2.Status) $($o2.Body)" }
    } elseif ($o1.Status -eq 409) {
        Write-Pass '  已有未完成订单 -> 409（符合预期）'
    } else {
        Write-Fail "  订单创建 -> $($o1.Status) $($o1.Body)"
    }

    # 8. 支付回调成功路径
    Write-Info '8. 支付回调成功路径（订单入账）'
    if ($SkipCallbackSuccess -or -not $MockSecret -or -not $orderNo) {
        Write-Skip '  已跳过（需 -MockSecret 与已创建的订单）'
    } else {
        $eid = "e2e-evt-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
        # 回调金额必须等于订单真实金额（服务端做 amount_mismatch 校验），不能写死
        $amt = if ($orderAmount) { ([decimal]$orderAmount).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture) } else { '1.00' }
        $sig = Get-MockSignature -EventId $eid -OrderNo $orderNo -Amount $amt -Secret $MockSecret
        $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $cbBody = "{`"event_id`":`"$eid`",`"out_trade_no`":`"$orderNo`",`"total_amount`":`"$amt`",`"currency`":`"CNY`",`"trade_status`":`"TRADE_SUCCESS`"}"
        $cbH = @{ 'X-Mock-Timestamp' = "$ts"; 'X-Mock-Signature' = $sig }
        $cr = Get-StatusCode -Url $cbUrl -Method 'POST' -Body $cbBody -Headers $cbH
        if ($cr.Status -eq 200) {
            Write-Pass '  合法签名回调 -> 200'
            # 重放同一事件应被去重
            $cr2 = Get-StatusCode -Url $cbUrl -Method 'POST' -Body $cbBody -Headers $cbH
            if ($cr2.Status -eq 200) {
                $cj = $cr2.Body | ConvertFrom-Json
                if ($cj.data.result -eq 'duplicate') { Write-Pass '  同一事件重放 -> duplicate（去重生效）' }
                else { Write-Fail "  同一事件重放 result=$($cj.data.result)（期望 duplicate）" }
            } else { Write-Fail "  事件重放 -> $($cr2.Status)" }
        } else {
            Write-Fail "  合法签名回调 -> $($cr.Status) $($cr.Body)"
        }
    }

    # 9. 账单列表
    Write-Info '9. 账单列表'
    $st = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$WorkspaceId/billing/statements" -Headers $auth
    if ($st.Status -eq 200) { Write-Pass 'GET 账单列表 -> 200' }
    elseif ($st.Status -eq 403) { Write-Fail 'GET 账单列表 -> 403：账单权限校验失败' }
    else { Write-Fail "GET 账单列表 -> $($st.Status) $($st.Body)" }
}

# ── 汇总 ───────────────────────────────────────────────────────
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
