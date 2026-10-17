# 后端启动脚本（PowerShell）
# 使用方式： .\run-backend.ps1 -DeepseekKey "sk-xxx"
# 或设置环境变量： $env:DEEPSEEK_API_KEY="sk-xxx"  然后  .\run-backend.ps1

param(
    [string]$DeepseekKey = ""
)

if ($DeepseekKey -ne "") {
    $env:DEEPSEEK_API_KEY = $DeepseekKey
}

if (-not $env:DEEPSEEK_API_KEY) {
    Write-Host "[WARN] DEEPSEEK_API_KEY 未设置，Agent / 分析 / 标题生成将无法调用真实模型" -ForegroundColor Yellow
}

$env:SPRING_PROFILES_ACTIVE = "local"

Set-Location $PSScriptRoot\Xhs-Backend

if (-not (Test-Path ".\target\xhs-agent-yunying-*.jar")) {
    Write-Host "[INFO] 未发现 jar，先执行打包..." -ForegroundColor Cyan
    .\mvnw.cmd -B -ntp -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 打包失败" -ForegroundColor Red
        exit 1
    }
}

$jar = Get-ChildItem ".\target\xhs-agent-yunying-*.jar" | Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } | Select-Object -First 1
Write-Host "[INFO] 启动后端：$($jar.Name)" -ForegroundColor Green
Write-Host "[INFO] 监听端口：8125  context-path: /api" -ForegroundColor Green
Write-Host "[INFO] 健康检查： http://localhost:8125/api/actuator/health" -ForegroundColor Green

java -jar $jar.FullName