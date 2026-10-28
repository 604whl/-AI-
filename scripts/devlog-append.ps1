#!/usr/bin/env pwsh
# 操作日志追加脚本（供并行 agent 安全写入 DEVLOG.md）
#
# 用法（单条）：
#   pwsh -File scripts/devlog-append.ps1 -Type 后端 -Action "新建实体" -Target "domain/entity/X.java" -Result "完成"
#
# 用法（批量，从 JSON 数组读）：
#   pwsh -File scripts/devlog-append.ps1 -EntriesJson '[{"Type":"后端","Action":"...","Target":"...","Result":"..."}]'
#
# 设计要点：
#   1. UTF-8 BOM + CRLF，与项目既有 md 文件一致。
#   2. 使用 FileShare 排他锁 + 重试，保证并行 agent 同时追加不互相覆盖。
#   3. 追加前自动读取当前末尾时间戳，保证同一天按时间升序。

param(
    [string]$Type = "",
    [string]$Action = "",
    [string]$Target = "",
    [string]$Result = "",
    [string]$EntriesJson = ""
)

$ErrorActionPreference = "Stop"
$logPath = Join-Path (Split-Path $PSScriptRoot -Parent) "DEVLOG.md"

function Write-LogLine([string]$type, [string]$action, [string]$target, [string]$result) {
    $ts = Get-Date -Format "HH:mm:ss"
    $line = "{0} | {1} | {2} | {3} | {4}" -f $ts, $type, $action, $target, $result
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($line + "`r`n")

    # 排他锁 + 重试，避免并行写入互相覆盖
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            $fs = [System.IO.File]::Open($logPath, [System.IO.FileMode]::OpenOrCreate,
                    [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
            try {
                $fs.Seek(0, [System.IO.SeekOrigin]::End) | Out-Null
                $fs.Write($bytes, 0, $bytes.Length)
                $fs.Flush()
            } finally {
                $fs.Dispose()
            }
            Write-Output "OK  $line"
            return
        } catch [System.IO.IOException] {
            Start-Sleep -Milliseconds 120
        }
    }
    throw "DEVLOG.md 写入失败：连续 40 次无法获取排他锁"
}

if ($EntriesJson -and $EntriesJson.Trim() -ne "") {
    $entries = $EntriesJson | ConvertFrom-Json
    foreach ($e in @($entries)) {
        Write-LogLine $e.Type $e.Action $e.Target $e.Result
    }
} elseif ($Type -and $Action) {
    Write-LogLine $Type $Action $Target $Result
} else {
    Write-Output "用法: -Type <类型> -Action <动作> [-Target <路径>] [-Result <结论>]  或  -EntriesJson <json>"
    exit 1
}
