$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

& "$PSScriptRoot\preflight.ps1"

Write-Host "=== Day 1: compile ==="
.\gradlew.bat clean compileJava compileTestJava

Write-Host "=== Day 1: module Postgres smoke ==="
.\gradlew.bat :fixture-wallet:test --tests "ai.flowguard.fixture.Day1PostgresIntegrationTest"

Write-Host "=== Day 1: live Compose + boot jar stack ==="
& "$PSScriptRoot\day1-live-integration.ps1"

Write-Host "=== Day 1: optional Testcontainers stack ==="
.\gradlew.bat day1Integration

Write-Host "=== Day 1 gate: PASS ==="
