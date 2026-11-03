$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

Write-Host "Building backend and fixture..."
& .\gradlew.bat :fixture-wallet:bootJar :backend:bootJar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting Docker dependencies..."
docker compose up -d postgres redis kafka evidence-processor

$fixtureJar = Get-ChildItem -Path "fixture-wallet\build\libs" -Filter "fixture-wallet*.jar" | Select-Object -First 1
$backendJar = Get-ChildItem -Path "backend\build\libs" -Filter "flowguard-backend*.jar" | Select-Object -First 1

Write-Host "Launch fixture-wallet (8081) and backend (8080) in separate terminals if not already running:"
Write-Host "  java -jar $($fixtureJar.FullName) --server.port=8081 --flowguard.fixture.variant=dup-credit-vulnerable"
Write-Host "  java -jar $($backendJar.FullName) --flowguard.fixture.base-url=http://localhost:8081"
Write-Host "Frontend: cd frontend; npm run dev"
