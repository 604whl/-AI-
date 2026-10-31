#Requires -Version 5.1
<#
.SYNOPSIS
  S4-4 refund + invoice end-to-end verification (ASCII-only on purpose).

.DESCRIPTION
  Black-box verification against a running backend, covering S4-4:
    1. Order creation + idempotent replay
    2. Payment callback (mock channel, HMAC signature) -> order becomes paid
    3. Refund request (OWNER, idempotency, over-amount rejection, tenant isolation)
    4. Admin review (approve -> provider refund -> entitlement reversal; idempotent re-review)
    5. Invoice issue (personal/company, tax calc, company tax_no required,
       idempotent replay, duplicate invoice rejected)
    6. Invoice PDF signed download (HMAC URL, 300s TTL; tampered / expired signature rejected)
    7. Invoice red-flush (origin kept, status -> red_flushed, red record created)

  Prerequisites:
    - backend running with SPRING_PROFILES_ACTIVE=local and PAYMENT_MOCK_SECRET set
    - a platform admin account (app.platform_admin) with known credentials

  Usage:
    pwsh -File scripts/verify-s4-4-refund-invoice.ps1 -MockSecret <secret>
#>
param(
    [string]$BaseUrl = 'http://localhost:8125',
    [string]$MockSecret = '',
    [string]$AdminEmail = 's44admin@example.com',
    [string]$AdminPassword = 'S44Admin#2026',
    [string]$UserEmail = 's44user@example.com',
    [string]$UserPassword = 'S44User#2026',
    [string]$RedisContainer = 'xhsagent-redis-dev',
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
        $code = 0
        $text = ''
        try { if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } } catch { }
        try { if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $text = $_.ErrorDetails.Message } } catch { }
        return @{ Status = $code; Body = $text }
    }
}

function Get-AuthHeaders {
    param([string]$Token, [string]$WorkspaceId)
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

function Login {
    param([string]$Email, [string]$Password)
    $r = Get-StatusCode -Url "$BaseUrl/api/v1/auth/login" -Method 'POST' `
            -Body ("{`"email`":`"$Email`",`"password`":`"$Password`"}")
    if ($r.Status -ne 200) {
        Write-Host "  login response: $($r.Status) $($r.Body)" -ForegroundColor Red
        return $null
    }
    return ($r.Body | ConvertFrom-Json).data
}

function Summary {
    Write-Host ''
    Write-Host '===============================================' -ForegroundColor Cyan
    Write-Host (" passed: {0}  failed: {1}" -f $script:Passed, $script:Failed) -ForegroundColor Cyan
    if ($script:Failed -gt 0) {
        Write-Host ' failures:' -ForegroundColor Red
        foreach ($f in $script:Failures) { Write-Host "  - $f" -ForegroundColor Red }
        Write-Host '===============================================' -ForegroundColor Cyan
        exit 1
    }
    Write-Host '===============================================' -ForegroundColor Green
    exit 0
}

Write-Host ''
Write-Host '===============================================' -ForegroundColor Cyan
Write-Host ' Stage 4 E2E Verification (S4-4 refund + invoice)' -ForegroundColor Cyan
Write-Host " BaseUrl: $BaseUrl" -ForegroundColor DarkGray
Write-Host '===============================================' -ForegroundColor Cyan

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

# -- 0. accounts -----------------------------------------------------
Write-Info '0. prepare accounts'

if (-not $SkipAuth) {
    $reg = Get-StatusCode -Url "$BaseUrl/api/v1/auth/register" -Method 'POST' `
            -Body ("{`"email`":`"$AdminEmail`",`"password`":`"$AdminPassword`",`"displayName`":`"S44 Admin`"}")
    if ($reg.Status -eq 200) {
        Write-Pass "  admin registered: $AdminEmail"
    } else {
        Write-Info "  admin register -> $($reg.Status) $($reg.Body) (existing account is fine)"
    }

    $reg2 = Get-StatusCode -Url "$BaseUrl/api/v1/auth/register" -Method 'POST' `
            -Body ("{`"email`":`"$UserEmail`",`"password`":`"$UserPassword`",`"displayName`":`"S44 User`"}")
    if ($reg2.Status -eq 200) {
        Write-Pass "  user registered: $UserEmail"
    } else {
        Write-Info "  user register -> $($reg2.Status) $($reg2.Body) (existing account is fine)"
    }
}

# -- 1. login --------------------------------------------------------
Write-Info '1. login'
$adminAuth = $null
$userAuth = $null
if (-not $SkipAuth) {
    $adminAuth = Login -Email $AdminEmail -Password $AdminPassword
    if ($adminAuth) { Write-Pass "  admin logged in ws=$($adminAuth.defaultWorkspaceId)" }
    else { Write-Fail '  admin login failed' }

    $userAuth = Login -Email $UserEmail -Password $UserPassword
    if ($userAuth) { Write-Pass "  user logged in ws=$($userAuth.defaultWorkspaceId)" }
    else { Write-Fail '  user login failed' }
}

if ($SkipAuth -or -not $adminAuth -or -not $userAuth) {
    Write-Skip 'remaining cases skipped (no credentials)'
    Summary
}

$adminToken = $adminAuth.accessToken
$adminWs = $adminAuth.defaultWorkspaceId
$userToken = $userAuth.accessToken
$userWs = $userAuth.defaultWorkspaceId
$adminHeaders = Get-AuthHeaders -Token $adminToken -WorkspaceId $adminWs
$userHeaders = Get-AuthHeaders -Token $userToken -WorkspaceId $userWs

# 清理本次要用到的限流计数（退款 3 次/天、订单 10 次/天），保证脚本可重复执行
if ($RedisContainer) {
    foreach ($ws in @($adminWs, $userWs)) {
        if (-not $ws) { continue }
        # 注意：必须用 ${ws} 括起来，否则 PowerShell 会把 "$ws:refund" 解析成带作用域的变量名
        foreach ($k in @("ratelimit:workspace:refund:create:${ws}",
                         "ratelimit:workspace:order:create:${ws}",
                         "ratelimit:workspace:${ws}:refund:create",
                         "ratelimit:workspace:${ws}:order:create")) {
            docker exec $RedisContainer redis-cli DEL $k 2>$null | Out-Null
        }
    }
    Write-Info '  cleared workspace rate limit counters'
}

# -- 2. platform admin gate ------------------------------------------
Write-Info '2. platform admin gate'
$probe = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/refunds" -Headers $adminHeaders
if ($probe.Status -eq 403) {
    Write-Skip '  current admin account is not a platform admin (403); grant app.platform_admin and rerun'
    Summary
}
if ($probe.Status -ne 200) {
    Write-Fail "  GET /platform/admin/refunds -> $($probe.Status) $($probe.Body)"
} else {
    Write-Pass '  GET /platform/admin/refunds -> 200'
}

# -- 3. create order --------------------------------------------------
Write-Info '3. create order (PRO subscription)'
$orderNo = $null
$orderAmount = $null
$oidem = "s44-order-$stamp"
$oh = $userHeaders.Clone(); $oh['Idempotency-Key'] = $oidem
$o1 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/orders" -Method 'POST' `
        -Body '{"planCode":"PRO","provider":"mock"}' -Headers $oh
if ($o1.Status -eq 200) {
    $oj = $o1.Body | ConvertFrom-Json
    $orderNo = $oj.data.orderNo
    $orderAmount = $oj.data.amount
    Write-Pass "  order created -> $orderNo (amount=$orderAmount)"
    $o2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/orders" -Method 'POST' `
            -Body '{"planCode":"PRO","provider":"mock"}' -Headers $oh
    if ($o2.Status -eq 200) {
        $oj2 = $o2.Body | ConvertFrom-Json
        if ($oj2.data.orderNo -eq $orderNo) { Write-Pass '  idempotent replay -> same order' }
        else { Write-Fail "  idempotent replay -> different order: $orderNo vs $($oj2.data.orderNo)" }
    } else { Write-Fail "  order replay -> $($o2.Status) $($o2.Body)" }
} else {
    Write-Fail "  order create -> $($o1.Status) $($o1.Body)"
}

if (-not $orderNo) { Summary }

# -- 4. payment callback ----------------------------------------------
Write-Info '4. payment callback (mock channel)'
if (-not $MockSecret) {
    Write-Skip '  no -MockSecret given; skipping callback (order stays pending)'
} else {
    $eid = "s44-evt-$stamp"
    $amt = ([decimal]$orderAmount).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
    $sig = Get-MockSignature -EventId $eid -OrderNo $orderNo -Amount $amt -Secret $MockSecret
    $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $cbBody = "{`"event_id`":`"$eid`",`"out_trade_no`":`"$orderNo`",`"total_amount`":`"$amt`",`"currency`":`"CNY`",`"trade_status`":`"TRADE_SUCCESS`"}"
    $cbH = @{ 'X-Mock-Timestamp' = "$ts"; 'X-Mock-Signature' = $sig }
    $cr = Get-StatusCode -Url "$BaseUrl/api/v1/payments/callback/mock" -Method 'POST' -Body $cbBody -Headers $cbH
    if ($cr.Status -eq 200) {
        Write-Pass '  valid signature callback -> 200'
        $cr2 = Get-StatusCode -Url "$BaseUrl/api/v1/payments/callback/mock" -Method 'POST' -Body $cbBody -Headers $cbH
        if ($cr2.Status -eq 200) {
            $cj = $cr2.Body | ConvertFrom-Json
            if ($cj.data.result -eq 'duplicate') { Write-Pass '  same event replay -> duplicate (dedupe works)' }
            else { Write-Fail "  same event replay result=$($cj.data.result) (expected duplicate)" }
        } else { Write-Fail "  event replay -> $($cr2.Status)" }
    } else {
        Write-Fail "  valid signature callback -> $($cr.Status) $($cr.Body)"
    }
}

# -- 5. refund request -------------------------------------------------
Write-Info '5. refund request'
$refundNo = $null
$ridem = "s44-refund-$stamp"
$rh = $userHeaders.Clone(); $rh['Idempotency-Key'] = $ridem
$rb = ('{"orderNo":"' + $orderNo + '","reason":"e2e refund"}')
$r1 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/refunds" -Method 'POST' -Body $rb -Headers $rh
if ($r1.Status -eq 200) {
    $refundNo = ($r1.Body | ConvertFrom-Json).data.refundNo
    Write-Pass "  refund requested -> $refundNo"
} elseif ($r1.Status -eq 409) {
    Write-Fail "  refund request -> 409 order_not_refundable (order not paid; check step 4)"
} else {
    Write-Fail "  refund request -> $($r1.Status) $($r1.Body)"
}

if ($refundNo) {
    $r2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/refunds" -Method 'POST' -Body $rb -Headers $rh
    if ($r2.Status -eq 200) {
        $rn2 = ($r2.Body | ConvertFrom-Json).data.refundNo
        if ($rn2 -eq $refundNo) { Write-Pass '  idempotent replay -> same refund order' }
        else { Write-Fail "  idempotent replay -> different refund order: $refundNo vs $rn2" }
    } else { Write-Fail "  refund replay -> $($r2.Status) $($r2.Body)" }

    $rh2 = $userHeaders.Clone(); $rh2['Idempotency-Key'] = "s44-refund-over-$stamp"
    $ro = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/refunds" -Method 'POST' `
            -Body ('{"orderNo":"' + $orderNo + '","reason":"over","amount":99999}') -Headers $rh2
    if ($ro.Status -eq 400 -and $ro.Body -match 'refund_amount_exceeds_order') {
        Write-Pass '  over-amount refund -> 400 refund_amount_exceeds_order'
    } else {
        Write-Fail "  over-amount refund -> $($ro.Status) $($ro.Body) (expected 400 refund_amount_exceeds_order)"
    }

    $x = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$adminWs/refunds/$refundNo" -Headers $adminHeaders
    if ($x.Status -eq 200) {
        $xv = ($x.Body | ConvertFrom-Json).data
        if ($null -eq $xv) { Write-Pass '  cross-workspace refund lookup -> empty (tenant isolation works)' }
        else { Write-Fail '  cross-workspace refund lookup returned data (tenant isolation broken)' }
    } else {
        Write-Pass "  cross-workspace refund lookup -> $($x.Status) (tenant isolation works)"
    }
}

# -- 6. admin review ----------------------------------------------------
Write-Info '6. admin refund review'
if ($refundNo) {
    $ap = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/refunds/$refundNo/approve" -Method 'POST' `
            -Body '{"note":"e2e approve"}' -Headers $adminHeaders
    if ($ap.Status -eq 200) {
        $aj = ($ap.Body | ConvertFrom-Json).data
        Write-Pass "  approve -> status=$($aj.status) accepted=$($aj.accepted) reversed=$($aj.entitlementReversed)"
    } else {
        Write-Fail "  approve -> $($ap.Status) $($ap.Body)"
    }

    $ap2 = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/refunds/$refundNo/approve" -Method 'POST' `
            -Body '{"note":"e2e approve again"}' -Headers $adminHeaders
    if ($ap2.Status -eq 200) { Write-Pass '  repeated approve -> 200 (idempotent)' }
    else { Write-Fail "  repeated approve -> $($ap2.Status) $($ap2.Body)" }

    $rd = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/refunds/$refundNo" -Headers $userHeaders
    if ($rd.Status -eq 200) {
        $rj = ($rd.Body | ConvertFrom-Json).data
        Write-Info "  refund status now: $($rj.status)"
        if ($rj.status -eq 'refunded') { Write-Pass '  refund status = refunded' }
        else { Write-Fail "  refund status = $($rj.status) (expected refunded)" }
    } else {
        Write-Fail "  refund detail -> $($rd.Status) $($rd.Body)"
    }
}

# -- 7. invoice issue ---------------------------------------------------
Write-Info '7. invoice issue'
$invoiceId = $null
$invoiceNo = $null
$ib = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/invoices" -Method 'POST' `
        -Headers (@{ 'Authorization' = "Bearer $adminToken"; 'Idempotency-Key' = "s44-inv-bad-$stamp" }) `
        -Body ('{"invoiceType":"company","title":"Test Co Ltd","orderNo":"' + $orderNo + '"}')
if ($ib.Status -eq 400 -and $ib.Body -match 'tax_no_required_for_company_invoice') {
    Write-Pass '  company invoice without tax_no -> 400 tax_no_required_for_company_invoice'
} else {
    Write-Fail "  company invoice without tax_no -> $($ib.Status) $($ib.Body) (expected 400)"
}

$iidem2 = "s44-inv-$stamp"
$ih = @{ 'Authorization' = "Bearer $adminToken"; 'Idempotency-Key' = $iidem2 }
$i1 = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/invoices" -Method 'POST' -Headers $ih `
        -Body ('{"invoiceType":"personal","title":"Test User","orderNo":"' + $orderNo + '"}')
if ($i1.Status -eq 200) {
    $ij = ($i1.Body | ConvertFrom-Json).data
    $invoiceId = $ij.id
    $invoiceNo = $ij.invoiceNo
    Write-Pass "  invoice issued -> $invoiceNo (amount=$($ij.amount) tax=$($ij.taxAmount) rate=$($ij.taxRate))"
    if ([decimal]$ij.taxAmount -gt 0) { Write-Pass '  tax amount calculated' }
    else { Write-Fail '  tax amount is zero' }
} else {
    Write-Fail "  invoice issue -> $($i1.Status) $($i1.Body)"
}

if ($invoiceId) {
    $i2 = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/invoices" -Method 'POST' -Headers $ih `
            -Body ('{"invoiceType":"personal","title":"Test User","orderNo":"' + $orderNo + '"}')
    if ($i2.Status -eq 200) {
        $ij2 = ($i2.Body | ConvertFrom-Json).data
        if ($ij2.id -eq $invoiceId) { Write-Pass '  idempotent replay -> same invoice' }
        else { Write-Fail "  idempotent replay -> different invoice: $invoiceId vs $($ij2.id)" }
    } else { Write-Fail "  invoice replay -> $($i2.Status) $($i2.Body)" }

    $i3 = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/invoices" -Method 'POST' `
            -Headers (@{ 'Authorization' = "Bearer $adminToken"; 'Idempotency-Key' = "s44-inv-dup-$stamp" }) `
            -Body ('{"invoiceType":"personal","title":"Test User","orderNo":"' + $orderNo + '"}')
    if ($i3.Status -eq 409 -and $i3.Body -match 'order_already_invoiced') {
        Write-Pass '  duplicate invoice -> 409 order_already_invoiced'
    } else {
        Write-Fail "  duplicate invoice -> $($i3.Status) $($i3.Body) (expected 409 order_already_invoiced)"
    }
}

# -- 8. signed pdf download ----------------------------------------------
Write-Info '8. invoice pdf signed download'
if ($invoiceId) {
    $tk = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/invoices/$invoiceId/download-ticket" -Headers $userHeaders
    if ($tk.Status -eq 200) {
        $t = ($tk.Body | ConvertFrom-Json).data
        Write-Pass "  download ticket -> expiresAt=$($t.expiresAt) ttl=$($t.expiresInSeconds)s"
        if ($t.expiresInSeconds -eq 300) { Write-Pass '  ticket ttl = 300s (5 minutes)' }
        else { Write-Fail "  ticket ttl = $($t.expiresInSeconds) (expected 300)" }

        if ($t.url -match 'expiresAt=(\d+)&signature=([A-Za-z0-9_-]+)') {
            $exp = $Matches[1]; $sig = $Matches[2]
            $pdf = Get-StatusCode -Url "$BaseUrl$($t.url)" -Headers $userHeaders
            if ($pdf.Status -eq 200) { Write-Pass '  valid signature download -> 200' }
            else { Write-Fail "  valid signature download -> $($pdf.Status)" }

            $bad = $t.url -replace 'signature=[A-Za-z0-9_-]+', ('signature=' + ('0' * 64))
            $r = Get-StatusCode -Url "$BaseUrl$bad" -Headers $userHeaders
            if ($r.Status -eq 403) { Write-Pass '  tampered signature -> 403' }
            else { Write-Fail "  tampered signature -> $($r.Status) (expected 403)" }

            $expired = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - 600000).ToString()
            $r2 = Get-StatusCode -Url "$BaseUrl/api/v1/workspaces/$userWs/invoices/$invoiceId/pdf?expiresAt=$expired&signature=$sig" -Headers $userHeaders
            if ($r2.Status -eq 403) { Write-Pass '  expired signature -> 403' }
            else { Write-Fail "  expired signature -> $($r2.Status) (expected 403)" }
        } else {
            Write-Fail "  download url does not carry a signature: $($t.url)"
        }
    } else {
        Write-Fail "  download ticket -> $($tk.Status) $($tk.Body)"
    }
}

# -- 9. red flush ---------------------------------------------------------
Write-Info '9. invoice red flush'
if ($invoiceId) {
    $rf = Get-StatusCode -Url "$BaseUrl/api/v1/platform/admin/invoices/$invoiceId/red-flush" -Method 'POST' `
            -Headers $adminHeaders -Body ('{"refundNo":"' + $refundNo + '"}')
    if ($rf.Status -eq 200) {
        $fj = ($rf.Body | ConvertFrom-Json).data
        Write-Pass "  red flush -> origin status=$($fj.status) redFlushInvoiceId=$($fj.redFlushInvoiceId)"
        if ($fj.status -eq 'red_flushed') { Write-Pass '  origin invoice status = red_flushed (origin not deleted)' }
        else { Write-Fail "  origin invoice status = $($fj.status) (expected red_flushed)" }
    } else {
        Write-Fail "  red flush -> $($rf.Status) $($rf.Body)"
    }
}

Summary
