$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

& "$PSScriptRoot\preflight.ps1"

Write-Host "=== Production readiness: Gradle E2E (Testcontainers) ==="
.\gradlew.bat e2eAll
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== Production readiness: build boot jars ==="
.\gradlew.bat :auth-server:bootJar :api-gateway:bootJar :fixture-wallet:bootJar :backend:bootJar -x test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== Production readiness: Compose prod overlay ==="
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build `
    postgres redis kafka fixture-wallet flowguard-auth-server flowguard-backend flowguard-api-gateway
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

try {
    & "$PSScriptRoot\smoke-gateway-live.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Write-Host "Leaving Compose stack running for manual UI smoke (npm run dev -> http://localhost:5173/login)"
}

Write-Host "=== Production E2E gate: PASS ==="
