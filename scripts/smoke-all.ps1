$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "FlowGuard milestone verification (Docker required)..."
& .\gradlew.bat milestoneVerification
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Milestone verification PASSED."
