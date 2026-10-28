#!/usr/bin/env pwsh
# 推送辅助脚本：自动绕过失效的本地代理
#
# 背景：
#   全局 git 配置了 http.proxy / https.proxy = http://127.0.0.1:7897，
#   但该代理端口经常未运行，导致 `git push` 报：
#     fatal: unable to access '...': Failed to connect to github.com port 443 via 127.0.0.1
#   而直连 github.com:443 通常是通的。
#
# 用法：
#   pwsh -File scripts/push.ps1                     # 推送当前分支到 origin
#   pwsh -File scripts/push.ps1 -Branch xhs_agent    # 指定分支
#   pwsh -File scripts/push.ps1 -ForceProxy           # 强制使用已配置的代理
#   pwsh -File scripts/push.ps1 -DryRun               # 只打印将要执行的命令
#
# 设计要点：
#   1. 不修改全局 / 仓库级 git 配置，只在单次命令上用 -c 覆盖。
#   2. 先探测代理端口是否可用：可用则沿用代理；不可用则清空代理直连。
#   3. 推送后回读远程 HEAD 校验，确认真正落到远程。

param(
    [string]$Branch = "",
    [switch]$ForceProxy,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Invoke-Git([string[]]$GitArgs) {
    $out = & git @GitArgs 2>&1
    $code = $LASTEXITCODE
    return [pscustomobject]@{ Output = @($out | ForEach-Object { $_.ToString() }); Code = $code }
}

# 1. 确定分支
if (-not $Branch) {
    $r = Invoke-Git @("rev-parse", "--abbrev-ref", "HEAD")
    $Branch = ($r.Output | Select-Object -First 1).Trim()
}
if (-not $Branch) {
    Write-Output "ERROR: 无法确定当前分支，请用 -Branch 显式指定"
    exit 1
}

# 2. 探测代理可用性
$proxyUrl = (Invoke-Git @("config", "--get", "http.proxy")).Output | Select-Object -First 1
$useProxy = $false

if ($ForceProxy) {
    $useProxy = $true
    Write-Output "[push] -ForceProxy：沿用已配置的代理 $proxyUrl"
} elseif ($proxyUrl -and $proxyUrl.Trim() -ne "") {
    $uri = $null
    try { $uri = [System.Uri]$proxyUrl.Trim() } catch { $uri = $null }
    if ($uri -and $uri.IsLoopback) {
        $hostName = $uri.Host
        $port = $uri.Port
        $tcp = New-Object System.Net.Sockets.TcpClient
        try {
            $task = $tcp.ConnectAsync($hostName, $port)
            if ($task.Wait(1500) -and $tcp.Connected) {
                $useProxy = $true
                Write-Output "[push] 代理可用 ($hostName`:$port)，沿用代理"
            } else {
                Write-Output "[push] 代理不可用 ($hostName`:$port 未监听)，本次改为直连"
            }
        } catch {
            Write-Output "[push] 代理不可用 ($hostName`:$port 连接失败)，本次改为直连"
        } finally {
            $tcp.Close()
        }
    } else {
        $useProxy = $true
        Write-Output "[push] 代理为非回环地址 $proxyUrl，直接沿用"
    }
} else {
    Write-Output "[push] 未配置 git 代理，直连"
}

# 3. 执行推送
if ($useProxy) {
    $pushArgs = @("push", "origin", $Branch)
} else {
    $pushArgs = @("-c", "http.proxy=", "-c", "https.proxy=", "push", "origin", $Branch)
}
Write-Output "[push] 执行: git $($pushArgs -join ' ')"

if ($DryRun) {
    Write-Output "[push] DryRun 模式，未实际推送"
    exit 0
}

$r = Invoke-Git $pushArgs
$r.Output | ForEach-Object { Write-Output $_ }
if ($r.Code -ne 0) {
    Write-Output ""
    Write-Output "[push] 推送失败（exit=$($r.Code)）。排查建议："
    if (-not $useProxy) {
        Write-Output "  - 已直连仍失败，可能是网络不通或认证过期"
        Write-Output "  - 若代理软件本应运行，请启动后加 -ForceProxy 重试"
    } else {
        Write-Output "  - 代理链路异常，检查代理软件与端口配置"
    }
    exit $r.Code
}

# 4. 回读远程 HEAD 校验
$remoteLine = (Invoke-Git @("-c", "http.proxy=", "-c", "https.proxy=", "ls-remote", "origin", $Branch)).Output |
    Select-Object -First 1
if ($remoteLine -match "^([0-9a-f]{40})") {
    $remoteSha = $Matches[1]
    $localSha = ((Invoke-Git @("rev-parse", "HEAD")).Output | Select-Object -First 1).Trim()
    if ($remoteSha -eq $localSha) {
        Write-Output "[push] 校验通过：远程 $Branch HEAD = $remoteSha（与本地一致）"
    } else {
        Write-Output "[push] 注意：远程 HEAD=$remoteSha，本地 HEAD=$localSha 不一致"
        exit 1
    }
} else {
    Write-Output "[push] 无法读取远程 HEAD，请手动核对"
}

exit 0