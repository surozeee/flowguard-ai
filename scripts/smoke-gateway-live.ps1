$ErrorActionPreference = "Stop"

$GatewayBase = if ($env:FLOWGUARD_GATEWAY_URL) { $env:FLOWGUARD_GATEWAY_URL } else { "http://localhost:8088" }
$AuthPort = if ($env:FLOWGUARD_AUTH_PORT) { $env:FLOWGUARD_AUTH_PORT } else { "9000" }
$MaxWaitSeconds = 180

function Wait-HttpOk($url) {
    $deadline = (Get-Date).AddSeconds($MaxWaitSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) {
                return
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    throw "Timed out waiting for $url"
}

Write-Host "=== Live gateway smoke ($GatewayBase) ==="

Wait-HttpOk "$GatewayBase/actuator/health"

$loginBody = @{ username = "ops"; password = "ops123" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$GatewayBase/api/v1/auth/login" `
    -ContentType "application/json" -Body $loginBody
if (-not $login.access_token) {
    throw "Login did not return access_token"
}

$headers = @{ Authorization = "Bearer $($login.access_token)" }

$health = Invoke-RestMethod -Uri "$GatewayBase/api/v1/health" -Headers $headers
if ($health.data.status -ne "UP" -and $health.status -ne "UP") {
    throw "Health check failed: $($health | ConvertTo-Json -Compress)"
}

$ops = Invoke-RestMethod -Uri "$GatewayBase/api/v1/ops/outbox/summary" -Headers $headers
if ($null -eq $ops.data -and $null -eq $ops.sagaDlqCount) {
    throw "Ops summary unexpected shape"
}

Write-Host "Live gateway smoke: PASS (login, health, ops)"
