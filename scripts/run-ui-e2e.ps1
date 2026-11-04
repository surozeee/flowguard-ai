$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$GatewayBase = if ($env:FLOWGUARD_GATEWAY_URL) { $env:FLOWGUARD_GATEWAY_URL } else { "http://localhost:8088" }

Write-Host "=== UI E2E (Playwright) — gateway must be up at $GatewayBase ==="
try {
    Invoke-WebRequest -Uri "$GatewayBase/actuator/health" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
    Write-Host "Start the stack first, e.g.: docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d"
    exit 1
}

Push-Location frontend
if (-not (Test-Path "node_modules")) {
    npm install
}
npx playwright install chromium --with-deps 2>$null
$env:VITE_API_GATEWAY_URL = $GatewayBase
npm run test:e2e
$code = $LASTEXITCODE
Pop-Location
exit $code
