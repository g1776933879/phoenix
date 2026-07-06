# 🐦 Phoenix AI Agent - Windows 一键安装脚本 (PowerShell)
# 用法: irm https://bit.ly/phoenix-install | iex

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Stop'

$PHOENIX_DIR = "$env:LOCALAPPDATA\phoenix"
$REPO_URL = "https://github.com/g1776933879/phoenix.git"

Write-Host "🐦 Phoenix AI Agent 安装中..." -ForegroundColor Cyan

# 1. 检查 Java
try {
    $javaVer = java -version 2>&1
    if ($javaVer -match '21') {
        Write-Host "✅ Java 21 已安装" -ForegroundColor Green
    } else {
        throw "需要 Java 21"
    }
} catch {
    Write-Host "📦 安装 Java 21..." -ForegroundColor Yellow
    winget install EclipseAdoptium.Temurin.21.JDK 2>$null
    $env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine")
}

# 2. 检查 Git
try { git --version 2>$null | Out-Null; Write-Host "✅ Git 已安装" -ForegroundColor Green }
catch {
    Write-Host "📦 安装 Git..." -ForegroundColor Yellow
    winget install Git.Git 2>$null
}

# 3. 克隆仓库
if (!(Test-Path "$PHOENIX_DIR")) {
    Write-Host "📦 克隆 Phoenix 仓库..." -ForegroundColor Yellow
    git clone --depth 1 $REPO_URL $PHOENIX_DIR 2>$null
}

# 4. 编译
Write-Host "📦 编译中 (首次约3-5分钟)..." -ForegroundColor Yellow
cd $PHOENIX_DIR
mvn clean install -Dmaven.test.skip=true -pl your-agent-core,your-agent-spring-boot-starter,your-business-app -am -q 2>$null

# 5. 创建快捷方式
$phoenixBat = "$PHOENIX_DIR\phoenix.bat"
@"
@echo off
cd /d "$PHOENIX_DIR"
mvn spring-boot:run -pl your-business-app -Dspring-boot.run.profiles=sensenova
"@ | Out-File -FilePath $phoenixBat -Encoding ASCII

# 6. 添加到 PATH
[Environment]::SetEnvironmentVariable("Path", "$env:Path;$PHOENIX_DIR", "User")

Write-Host ""
Write-Host "✅ Phoenix 安装完成！" -ForegroundColor Green
Write-Host ""
Write-Host "启动: phoenix.bat" -ForegroundColor Cyan
Write-Host "或:   cd $PHOENIX_DIR && bash phoenix.sh"
Write-Host ""
Write-Host "浏览器打开: http://localhost:8080" -ForegroundColor Cyan