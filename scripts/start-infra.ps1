Set-Location $PSScriptRoot + "\.."
docker compose up -d postgres redis kafka evidence-processor
Write-Host "Infrastructure started. Run: ./gradlew :fixture-wallet:bootRun and ./gradlew :backend:bootRun"
