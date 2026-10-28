param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Paths)
foreach ($p in $Paths) {
  if (-not (Test-Path $p)) { continue }
  $bytes = [System.IO.File]::ReadAllBytes($p)
  if ($bytes.Length -lt 2) { continue }
  if ($bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) {
    $text = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::Unicode)
    [System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output "converted: $p"
  }
}
