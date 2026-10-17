# 前端启动脚本（PowerShell）
# 使用方式： .\run-frontend.ps1

Set-Location $PSScriptRoot\Xhs-Frontend

if (-not (Test-Path ".\node_modules")) {
    Write-Host "[INFO] 安装依赖..." -ForegroundColor Cyan
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] npm install 失败" -ForegroundColor Red
        exit 1
    }
}

Write-Host "[INFO] 启动前端 dev 服务（端口 5173）" -ForegroundColor Green
Write-Host "[INFO] 后端代理： /api -> http://localhost:8125" -ForegroundColor Green
Write-Host "[INFO] Swagger UI： http://localhost:8125/api/swagger-ui.html" -ForegroundColor Green

npm run dev