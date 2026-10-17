$dir = 'F:\projectAI\XhsAgent\Xhs-Backend\src\main\resources\db\migration'
$items = Get-ChildItem -LiteralPath $dir | Sort-Object Name
$names = $items | Select-Object -ExpandProperty Name
Write-Host "Total migrations: $($items.Count)"
Write-Host ""
Write-Host "Last 10:"
$names | Select-Object -Last 10 | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "V3 migrations:"
$names | Where-Object { $_ -like 'V3*' } | ForEach-Object { Write-Host "  $_" }
