$ErrorActionPreference = "Stop"
$projectName = "schedule-manager-e2e"
$composeFile = Join-Path $PSScriptRoot "..\compose.e2e.yaml"
$previousFullStack = $env:E2E_FULL_STACK
$previousApiBase = $env:E2E_API_BASE_URL
$previousWebBase = $env:E2E_WEB_BASE_URL
$previousExternalWeb = $env:E2E_EXTERNAL_WEB
$previousMailpitBase = $env:E2E_MAILPIT_BASE_URL

try {
    docker compose --project-name $projectName --file $composeFile up --detach --build --wait
    if ($LASTEXITCODE -ne 0) {
        $composeExitCode = $LASTEXITCODE
        docker compose --project-name $projectName --file $composeFile logs --no-color backend-e2e frontend-e2e
        throw "Could not start the isolated E2E stack (exit code $composeExitCode)."
    }
    $env:E2E_FULL_STACK = "true"
    $env:E2E_API_BASE_URL = "http://localhost:18080/api/v1"
    $env:E2E_WEB_BASE_URL = "http://localhost:3100"
    $env:E2E_EXTERNAL_WEB = "true"
    $env:E2E_MAILPIT_BASE_URL = "http://localhost:18025/api/v1"
    npm run test:e2e
    if ($LASTEXITCODE -ne 0) {
        $playwrightExitCode = $LASTEXITCODE
        docker compose --project-name $projectName --file $composeFile logs --no-color backend-e2e frontend-e2e
        throw "Playwright E2E failed (exit code $playwrightExitCode)."
    }
}
finally {
    if ($null -eq $previousFullStack) { Remove-Item Env:E2E_FULL_STACK -ErrorAction SilentlyContinue } else { $env:E2E_FULL_STACK = $previousFullStack }
    if ($null -eq $previousApiBase) { Remove-Item Env:E2E_API_BASE_URL -ErrorAction SilentlyContinue } else { $env:E2E_API_BASE_URL = $previousApiBase }
    if ($null -eq $previousWebBase) { Remove-Item Env:E2E_WEB_BASE_URL -ErrorAction SilentlyContinue } else { $env:E2E_WEB_BASE_URL = $previousWebBase }
    if ($null -eq $previousExternalWeb) { Remove-Item Env:E2E_EXTERNAL_WEB -ErrorAction SilentlyContinue } else { $env:E2E_EXTERNAL_WEB = $previousExternalWeb }
    if ($null -eq $previousMailpitBase) { Remove-Item Env:E2E_MAILPIT_BASE_URL -ErrorAction SilentlyContinue } else { $env:E2E_MAILPIT_BASE_URL = $previousMailpitBase }
    docker compose --project-name $projectName --file $composeFile down --volumes --remove-orphans
}
