$ErrorActionPreference = "Stop"

Write-Host "=== IDU-1F Verification ===" -ForegroundColor Cyan

$branch = git rev-parse --abbrev-ref HEAD
if ($branch -ne "feat/stage2-1f-topic-ai-bulk-import") {
    Write-Host "[FAIL] Expected branch 'feat/stage2-1f-topic-ai-bulk-import', got '$branch'" -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] Branch: $branch"

$requiredFiles = @(
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\dto\topic\TopicBulkImportRequest.java",
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\dto\topic\TopicBulkImportResponse.java",
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\util\topic\TopicNormalize.java",
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\controller\v1\TopicPoolController.java",
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\service\TopicPoolService.java",
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\service\TopicPoolServiceImpl.java",
    "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\mapper\TopicPoolMapper.java"
)

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        Write-Host "[FAIL] Missing file: $file" -ForegroundColor Red
        exit 1
    }
}
Write-Host "[PASS] All required files exist"

$controllerContent = Get-Content "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\controller\v1\TopicPoolController.java" -Raw
if ($controllerContent -notmatch "bulk-import") {
    Write-Host "[FAIL] bulk-import endpoint not found in controller" -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] bulk-import endpoint exists"

$serviceContent = Get-Content "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\service\TopicPoolService.java" -Raw
if ($serviceContent -notmatch "bulkImport") {
    Write-Host "[FAIL] bulkImport method not found in service interface" -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] bulkImport method exists"

$normalizeContent = Get-Content "Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\util\topic\TopicNormalize.java" -Raw
if ($normalizeContent -notmatch "normalizeTitle") {
    Write-Host "[FAIL] normalizeTitle method not found in TopicNormalize" -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] TopicNormalize utility exists"

Write-Host ""
Write-Host "=== IDU-1F Verification Complete ===" -ForegroundColor Green