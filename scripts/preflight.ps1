$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "=== FlowGuard Day 1 Preflight ==="

function Require-Command($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Missing required command: $name"
    }
}

Require-Command java
Require-Command docker

$javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1)
Write-Host "Java: $javaVersion"

cmd /c "docker info >nul 2>&1"
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not running — start Docker Desktop for Day 1 integration"
}
Write-Host "Docker: OK"

if (-not (Test-Path ".\gradlew.bat")) {
    throw "Gradle wrapper not found"
}
Write-Host "Gradle wrapper: OK"

Write-Host "Preflight passed."
