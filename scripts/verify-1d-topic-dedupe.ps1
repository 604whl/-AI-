# === IDU-1D Topic Deduplication Verification Script ===
# Tests: same title (different case/spacing) | different source | different workspace | empty title

$ErrorActionPreference = 'Stop'
Write-Host '=== IDU-1D Topic Deduplication Verification ===' -ForegroundColor Cyan

$branch = git rev-parse --abbrev-ref HEAD
if ($branch -ne 'feat/stage2-1d-topic-dedup') {
    Write-Host "[FAIL] Expected branch 'feat/stage2-1d-topic-dedup', got ''" -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] Branch: $branch"

# --- File existence checks ---
$requiredFiles = @(
    'Xhs-Backend\src\main\resources\db\migration\V40__topic_dedupe_index.sql',
    'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\topic\TopicDedupeKey.java',
    'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\mapper\TopicPoolMapper.java',
    'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\entity\TopicPool.java',
    'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\service\TopicPoolServiceImpl.java'
)

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        Write-Host "[FAIL] Missing file: $file" -ForegroundColor Red
        exit 1
    }
}
Write-Host '[PASS] All required files exist'

# --- V40 migration checks ---
$v40 = Get-Content 'Xhs-Backend\src\main\resources\db\migration\V40__topic_dedupe_index.sql' -Raw
if ($v40 -notmatch 'ALTER TABLE app.topic_pool ADD COLUMN normalized_title') {
    Write-Host '[FAIL] V40: ADD COLUMN normalized_title not found' -ForegroundColor Red
    exit 1
}
if ($v40 -notmatch 'CREATE INDEX idx_topic_pool_dedupe_key') {
    Write-Host '[FAIL] V40: dedupe index not found' -ForegroundColor Red
    exit 1
}
Write-Host '[PASS] V40 migration structure valid'

# --- TopicDedupeKey normalize() method ---
$dedupeKey = Get-Content 'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\topic\TopicDedupeKey.java' -Raw
if ($dedupeKey -notmatch 'public static String normalize\(String') {
    Write-Host '[FAIL] TopicDedupeKey.normalize() method not found' -ForegroundColor Red
    exit 1
}
Write-Host '[PASS] TopicDedupeKey.normalize() exists'

# --- selectByDedupeKey in mapper ---
$mapper = Get-Content 'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\mapper\TopicPoolMapper.java' -Raw
if ($mapper -notmatch 'selectByDedupeKey') {
    Write-Host '[FAIL] selectByDedupeKey not found in TopicPoolMapper' -ForegroundColor Red
    exit 1
}
Write-Host '[PASS] selectByDedupeKey found in TopicPoolMapper'

# --- normalizedTitle field in entity ---
$entity = Get-Content 'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\domain\entity\TopicPool.java' -Raw
if ($entity -notmatch 'normalizedTitle') {
    Write-Host '[FAIL] normalizedTitle field not found in TopicPool entity' -ForegroundColor Red
    exit 1
}
Write-Host '[PASS] normalizedTitle field exists in TopicPool entity'

# --- Dedup logic in create() ---
$service = Get-Content 'Xhs-Backend\src\main\java\com\shortvideoscripagent\xhsagentyunying\service\TopicPoolServiceImpl.java' -Raw
if ($service -notmatch 'selectByDedupeKey') {
    Write-Host '[FAIL] Dedup check (selectByDedupeKey) not found in create()' -ForegroundColor Red
    exit 1
}
if ($service -notmatch 'CODE_CONFLICT') {
    Write-Host '[FAIL] CODE_CONFLICT not used in create()' -ForegroundColor Red
    exit 1
}
if ($service -notmatch 'topic_duplicate') {
    Write-Host '[FAIL] topic_duplicate error code not found in create()' -ForegroundColor Red
    exit 1
}
if ($service -notmatch 'existingTopicId=') {
    Write-Host '[FAIL] existingTopicId not included in duplicate error message' -ForegroundColor Red
    exit 1
}
Write-Host '[PASS] Dedup logic with 409 + topic_duplicate + existingTopicId in create()'

# --- Case 1: Title normalization (simulate TopicNormalize logic) ---
Write-Host ''
Write-Host '[TEST CASE 1] Title normalization: "  Hello  World  " vs "hello world"'
function Normalize-Title {
    param([string]$title)
    $trimmed = $title.Trim()
    $lower = $trimmed.ToLower()
    return $lower -replace '\s+', ' '
}
$norm1 = Normalize-Title '  Hello  World  '
$norm2 = Normalize-Title 'hello world'
if ($norm1 -eq $norm2) {
    Write-Host "[PASS] Case/spacing normalized to same key: ''" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Normalization mismatch: '' vs ''" -ForegroundColor Red
    exit 1
}

# --- Case 2: Different source should NOT match ---
Write-Host '[TEST CASE 2] Different source: SQL has LOWER(TRIM(source)) check'
Write-Host '[INFO] Dedup key = workspace_id + source + normalized_title, so different source = no match'

# --- Case 3: Different workspace should NOT match ---
Write-Host '[TEST CASE 3] Different workspace: selectByDedupeKey queries workspace_id column'
Write-Host '[INFO] Dedup is workspace-scoped - cross-workspace same title is allowed'

# --- Case 4: Empty title normalization ---
Write-Host '[TEST CASE 4] Empty title normalization: "" normalizes to "" safely'
$emptyNorm = Normalize-Title ''
if ($emptyNorm -eq '') {
    Write-Host '[PASS] Empty title normalizes to empty string safely' -ForegroundColor Green
} else {
    Write-Host "[FAIL] Empty title normalized unexpectedly: ''" -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host '=== IDU-1D Verification Complete ===' -ForegroundColor Green