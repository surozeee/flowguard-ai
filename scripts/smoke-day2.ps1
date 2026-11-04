$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

& "$PSScriptRoot\preflight.ps1"

Write-Host "=== Day 2: compile ==="
.\gradlew.bat clean compileJava compileTestJava

Write-Host "=== Day 2: immutable oracle unit tests ==="
.\gradlew.bat :trusted-tests:test --tests "ai.flowguard.oracles.FinancialOracle*"

Write-Host "=== Day 2: 20/20 idempotency race + safe control ==="
.\gradlew.bat :fixture-wallet:day2Verification

Write-Host "=== Day 2 gate: PASS ==="
Write-Host "Replay evidence: fixture-wallet\build\reports\day2\idempotency-replay.json"
