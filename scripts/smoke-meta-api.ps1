$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$envFile = Join-Path $PWD ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            if ($name -eq "MODEL_API_KEY" -or $name -eq "MODEL_API_BASE_URL" -or $name -eq "MODEL_ID") {
                Set-Item -Path "env:$name" -Value $value
            }
        }
    }
}

if (-not $env:MODEL_API_KEY) {
    Write-Host "MODEL_API_KEY not set — skipping live Meta API smoke (stub path covered by unit tests)."
    exit 0
}

Write-Host "=== Meta API smoke (Gradle live test) ==="
$env:GRADLE_USER_HOME = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:TEMP\gradle-fg-clean" }
.\gradlew.bat :backend:test --tests "ai.flowguard.meta.MetaModelClientTest.liveMetaApiReturnsInvestigationPlan" --no-daemon
exit $LASTEXITCODE
