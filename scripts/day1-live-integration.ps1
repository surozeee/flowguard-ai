$ErrorActionPreference = "Stop"
$Root = Join-Path $PSScriptRoot ".."
Set-Location $Root

& "$PSScriptRoot\preflight.ps1"

Write-Host "=== Starting infrastructure (Postgres, Redis, Kafka) ==="
docker compose up -d postgres redis kafka
$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 3
    $pg = docker compose exec -T postgres pg_isready -U flowguard -d flowguard 2>$null
    $ready = ($LASTEXITCODE -eq 0)
} while (-not $ready -and (Get-Date) -lt $deadline)
if (-not $ready) { throw "Postgres did not become ready in time" }

Write-Host "=== Building boot jars ==="
.\gradlew.bat :fixture-wallet:bootJar :backend:bootJar -x test

$fixtureJar = Get-ChildItem "fixture-wallet\build\libs\fixture-wallet-*.jar" | Where-Object { $_.Name -notmatch "plain" } | Select-Object -First 1
$backendJar = Get-ChildItem "backend\build\libs\flowguard-backend-*.jar" | Where-Object { $_.Name -notmatch "plain" } | Select-Object -First 1

$env:FIXTURE_VARIANT = "dup-credit-vulnerable"
$env:DATASOURCE_URL = "jdbc:postgresql://localhost:5432/flowguard?currentSchema=fixture"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/flowguard?currentSchema=metadata"
$env:SPRING_DATASOURCE_USERNAME = "flowguard"
$env:SPRING_DATASOURCE_PASSWORD = "flowguard"
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = "6379"
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:FIXTURE_WALLET_URL = "http://localhost:8081"

Write-Host "=== Starting fixture-wallet (8081) ==="
$fixtureProc = Start-Process java -ArgumentList "-jar", $fixtureJar.FullName, "--server.port=8081" -PassThru -WindowStyle Hidden

Write-Host "=== Starting backend (8080) ==="
$backendProc = Start-Process java -ArgumentList "-jar", $backendJar.FullName, "--server.port=8080" -PassThru -WindowStyle Hidden

function Unwrap-ApiData($envelope) {
    if ($null -ne $envelope.data) {
        return $envelope.data
    }
    return $envelope
}

function Wait-Http($url, $timeoutSec = 120) {
    $end = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $end) {
        try {
            Invoke-RestMethod -Uri $url -TimeoutSec 5 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "Timeout waiting for $url"
}

try {
    Wait-Http "http://localhost:8081/health"
    Wait-Http "http://localhost:8080/api/v1/health"

    Write-Host "=== Fixture reset + credit ==="
    Invoke-RestMethod -Method Post -Uri "http://localhost:8081/internal/reset" | Out-Null
    $creditBody = @{
        clientReference = "live-day1-ref"
        idempotencyKey  = "live-day1-key"
        targetAccountId = "00000000-0000-4000-8000-000000000002"
        amountMinor     = 10000
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "http://localhost:8081/payments" -Body $creditBody -ContentType "application/json" | Out-Null

    Write-Host "=== Backend projects API ==="
    $projects = @(Unwrap-ApiData (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/projects"))
    $slug = if ($projects.Count -gt 0) { $projects[0].slug } else { $null }
    if ($slug -ne "sample-wallet") {
        throw "sample-wallet project not found (got slug=$slug)"
    }

    Write-Host "=== Investigation smoke ==="
    $invBody = @{
        projectId = "sample-wallet"
        objective = "Day 1 live integration investigation"
        maxProbes = 10
    } | ConvertTo-Json
    $inv = Unwrap-ApiData (Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/investigations" -Body $invBody -ContentType "application/json")
    $invId = $inv.id
    $deadline = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 2
        $status = Unwrap-ApiData (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/investigations/$invId")
        $state = $status.state
    } while ($state -notin @("VIOLATION_FOUND", "COMPLETED", "FAILED") -and (Get-Date) -lt $deadline)

    if ($state -eq "FAILED") { throw "Investigation failed: $($status.errorCode)" }
    $experiments = @(Unwrap-ApiData (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/investigations/$invId/experiments"))
    $verdict = $experiments[0].oracleVerdict
    if ($verdict -ne "VIOLATION_REPRODUCED") {
        throw "Expected VIOLATION_REPRODUCED, got $verdict"
    }

    Write-Host "=== Day 1 live integration: PASS (verdict=$verdict) ==="
} finally {
    if ($backendProc -and -not $backendProc.HasExited) { Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue }
    if ($fixtureProc -and -not $fixtureProc.HasExited) { Stop-Process -Id $fixtureProc.Id -Force -ErrorAction SilentlyContinue }
}
