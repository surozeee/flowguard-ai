$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$env:GRADLE_USER_HOME = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:TEMP\gradle-fg-clean" }

foreach ($key in @(
        "SPRING_DATASOURCE_URL", "DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
        "KAFKA_BOOTSTRAP_SERVERS", "SPRING_DATA_REDIS_HOST", "SPRING_DATA_REDIS_PORT", "MODEL_API_KEY"
    )) {
    if (Test-Path "Env:$key") { Remove-Item "Env:$key" }
}

Write-Host "=== 1/5 Java (Gradle test + E2E suites) ==="
& .\gradlew.bat :fixture-wallet:test :backend:test :api-gateway:test :auth-server:test :trusted-tests:test :integration:e2eProduction :integration:e2eAuth :integration:e2eEnterprise :integration:day1Integration --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== 2/5 Meta API smoke ==="
& .\scripts\smoke-meta-api.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== 3/5 Python (evidence-processor) ==="
Push-Location python-services/evidence-processor
python -m pip install -q pytest
python -m pytest tests -q
$pyCode = $LASTEXITCODE
Pop-Location
if ($pyCode -ne 0) { exit $pyCode }

Write-Host "=== 4/5 Frontend build ==="
Push-Location frontend
if (-not (Test-Path "node_modules")) { npm install }
npm run build
$feCode = $LASTEXITCODE
Pop-Location
if ($feCode -ne 0) { exit $feCode }

Write-Host "=== 5/5 UI E2E (Playwright) ==="
& .\scripts\run-ui-e2e.ps1
exit $LASTEXITCODE
