[CmdletBinding()]
param(
    [string]$ProjectName = "schedule-manager-provisioning-local"
)

$ErrorActionPreference = "Stop"

if ($ProjectName -notmatch '^schedule-manager-provisioning-[a-z0-9-]+$') {
    throw "ProjectName must start with schedule-manager-provisioning- and contain only lowercase letters, digits, or hyphens."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeArguments = @(
    "compose",
    "--project-name", $ProjectName,
    "--env-file", "tests/config/production-config.valid.env",
    "--file", "compose.production.yaml"
)
$bootstrapKeys = @(
    "BOOTSTRAP_ADMIN_EMAIL",
    "BOOTSTRAP_ADMIN_PASSWORD",
    "BOOTSTRAP_ADMIN_DISPLAY_NAME",
    "BOOTSTRAP_ADMIN_DEPARTMENT_NAME"
)
$previousEnvironment = @{}

function Invoke-CheckedCompose {
    param([string[]]$CommandArguments)

    & docker @composeArguments @CommandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed: $($CommandArguments -join ' ')"
    }
}

foreach ($key in $bootstrapKeys) {
    $existing = Get-Item -Path "Env:$key" -ErrorAction SilentlyContinue
    $previousEnvironment[$key] = if ($null -eq $existing) { $null } else { $existing.Value }
}

Push-Location $repositoryRoot
try {
    Invoke-CheckedCompose -CommandArguments @("up", "--detach", "--wait", "postgres")
    Invoke-CheckedCompose -CommandArguments @("build", "backend")

    $env:BOOTSTRAP_ADMIN_EMAIL = "provision-check@fixture.invalid"
    $env:BOOTSTRAP_ADMIN_PASSWORD = "SyntheticAdmin@2026"
    $env:BOOTSTRAP_ADMIN_DISPLAY_NAME = "Provision Check"
    $env:BOOTSTRAP_ADMIN_DEPARTMENT_NAME = "Operations"

    $provisionArguments = @(
        "run", "--rm", "--no-deps",
        "-e", "DB_URL=",
        "-e", "DB_USER=",
        "-e", "DB_PASSWORD=",
        "-e", "DATABASE_URL=postgresql://schedule_app:fixture-db-password-32-characters-long@postgres:5432/schedule_manager",
        "-e", "APP_PROVISIONING_MODE=true",
        "-e", "BOOTSTRAP_ADMIN_ENABLED=true",
        "-e", "BOOTSTRAP_ADMIN_EMAIL",
        "-e", "BOOTSTRAP_ADMIN_PASSWORD",
        "-e", "BOOTSTRAP_ADMIN_DISPLAY_NAME",
        "-e", "BOOTSTRAP_ADMIN_DEPARTMENT_NAME",
        "backend"
    )
    Invoke-CheckedCompose -CommandArguments $provisionArguments
    Invoke-CheckedCompose -CommandArguments $provisionArguments

    $adminCount = (& docker @composeArguments exec -T postgres psql -U schedule_app -d schedule_manager -tAc "SELECT count(*) FROM users WHERE system_role='ADMIN' AND status='ACTIVE';").Trim()
    if ($LASTEXITCODE -ne 0) { throw "Unable to verify the provisioned Admin." }
    $auditCount = (& docker @composeArguments exec -T postgres psql -U schedule_app -d schedule_manager -tAc "SELECT count(*) FROM audit_logs WHERE action='ADMIN_BOOTSTRAPPED';").Trim()
    if ($LASTEXITCODE -ne 0) { throw "Unable to verify the provisioning audit record." }

    if ($adminCount -ne "1" -or $auditCount -ne "1") {
        throw "Provisioning assertions failed (admin=$adminCount audit=$auditCount)."
    }

    Write-Output "PRODUCTION_PROVISIONING_REHEARSAL_PASS admin=$adminCount audit=$auditCount idempotent=true"
}
catch {
    & docker @composeArguments logs --no-color --tail 300 postgres backend
    throw
}
finally {
    & docker @composeArguments down --volumes --remove-orphans
    foreach ($key in $bootstrapKeys) {
        if ($null -eq $previousEnvironment[$key]) {
            Remove-Item -Path "Env:$key" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item -Path "Env:$key" -Value $previousEnvironment[$key]
        }
    }
    Pop-Location
}
