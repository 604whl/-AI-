param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Paths)

# Convert UTF-16 files (with or without BOM) to UTF-8 (no BOM).
#
# Why: some editors / tools on Windows write .java / .sql / .ps1 as UTF-16LE.
# If not converted:
#   - javac fails with "illegal character" / whole-file parse errors;
#   - Flyway writes mojibake into the database;
#   - git diff shows the entire file as changed.
#
# Detection order:
#   1. UTF-16 BOM (FF FE / FE FF) -> decode per BOM.
#   2. No BOM, but >= 25% of all bytes are 0x00 -> UTF-16LE (ASCII text has a
#      0x00 high byte for every character, so roughly half the bytes are zero).
#   3. Otherwise -> already UTF-8 / ASCII, skip.
#
# NOTE: this file is intentionally ASCII-only so that it runs identically
# under Windows PowerShell 5.1 (which assumes ANSI for BOM-less scripts) and
# under PowerShell 7 (which assumes UTF-8).

foreach ($p in $Paths) {
  if (-not (Test-Path $p)) { continue }
  $bytes = [System.IO.File]::ReadAllBytes($p)
  if ($bytes.Length -lt 4) { continue }

  $encoding = $null
  if ($bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) {
    $encoding = [System.Text.Encoding]::Unicode
  } elseif ($bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) {
    $encoding = [System.Text.Encoding]::BigEndianUnicode
  } else {
    $zeros = 0
    foreach ($b in $bytes) { if ($b -eq 0) { $zeros++ } }
    if (($zeros / $bytes.Length) -gt 0.25) {
      $encoding = [System.Text.Encoding]::Unicode
    }
  }

  if ($null -eq $encoding) { continue }

  $text = [System.IO.File]::ReadAllText($p, $encoding)
  [System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false)))
  Write-Output "converted: $p"
}
