$ErrorActionPreference = "Stop"

Write-Host "=== Starting Schedule Manager local environment ==="

# ------------------------------------------------------------
# 1. Environment variables
# ------------------------------------------------------------

$env:SPRING_PROFILES_ACTIVE = "local"

$env:DB_URL = "jdbc:postgresql://localhost:5433/schedule_manager"
$env:DB_USER = "schedule_app"
$env:DB_PASSWORD = "schedule_local_password"

$env:DB_POOL_MAX_SIZE = "5"
$env:DB_POOL_MIN_IDLE = "1"
$env:DB_CONNECTION_TIMEOUT_MS = "5000"

$env:AUTH_RATE_LIMIT_PER_MINUTE = "5"
$env:AUTH_RATE_LIMIT_PER_HOUR = "30"

$env:CORS_ALLOWED_ORIGINS = "http://localhost:3000"

$env:SESSION_COOKIE_NAME = "session"
$env:SESSION_COOKIE_SECURE = "false"
$env:SESSION_PEPPER = "local-development-pepper-change-me"

$env:SCHOOL_TIME_ZONE = "Asia/Ho_Chi_Minh"

if ([string]::IsNullOrWhiteSpace($env:EMAIL_PROVIDER)) {
    $env:EMAIL_PROVIDER = "log"
}

if ($env:EMAIL_PROVIDER -eq "smtp") {
    $requiredSmtpVariables = @("EMAIL_FROM", "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD")
    $missingSmtpVariables = $requiredSmtpVariables | Where-Object {
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
    }
    if ($missingSmtpVariables.Count -gt 0) {
        throw "Missing SMTP environment variables: $($missingSmtpVariables -join ', ')"
    }
    if ([string]::IsNullOrWhiteSpace($env:SMTP_AUTH)) { $env:SMTP_AUTH = "true" }
    if ([string]::IsNullOrWhiteSpace($env:SMTP_STARTTLS)) { $env:SMTP_STARTTLS = "true" }
}

# Tùy chọn: kiểm tra reminder nhanh hơn trong local
$env:REMINDER_POLL_DELAY_MS = "5000"
$env:REMINDER_INITIAL_DELAY_MS = "5000"

$env:BOOTSTRAP_ADMIN_ENABLED = "false"
$env:APP_PROVISIONING_MODE = "false"

Write-Host "Environment variables loaded."

# ------------------------------------------------------------
# 2. Start Docker services
# ------------------------------------------------------------

Write-Host "[1/4] Checking Docker..."

function Test-DockerReady {
    try {
        docker info *> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
}

if (-not (Test-DockerReady)) {

    Write-Host "Docker Engine is not ready."

    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"

    if (-not (Test-Path $dockerDesktop)) {
        throw "Docker Desktop not found at: $dockerDesktop"
    }

    $dockerProcess = Get-Process "Docker Desktop" -ErrorAction SilentlyContinue

    if (-not $dockerProcess) {
        Write-Host "Starting Docker Desktop..."
        Start-Process $dockerDesktop
    }
    else {
        Write-Host "Docker Desktop is already running."
    }

    Write-Host "Waiting for Docker Engine..."

    $maxAttempts = 90
    $attempt = 0
    $dockerReady = $false

    while (-not $dockerReady -and $attempt -lt $maxAttempts) {
        Start-Sleep -Seconds 2
        $attempt++

        $dockerReady = Test-DockerReady

        if (-not $dockerReady) {
            Write-Host "Waiting for Docker Engine... ($attempt/$maxAttempts)"
        }
    }

    if (-not $dockerReady) {
        throw "Docker Engine did not become ready after 180 seconds."
    }
}

Write-Host "Docker Engine is ready."
docker compose up -d
# ------------------------------------------------------------
# 3. Start backend
# ------------------------------------------------------------

Write-Host "Starting Spring Boot backend..."

$backendProcess = Start-Process powershell `
    -ArgumentList "-NoExit", "-Command", @"
cd backend

if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run
}
elseif (Test-Path ".\mvn.cmd") {
    .\mvn.cmd spring-boot:run
}
else {
    mvn spring-boot:run
}
"@ `
    -PassThru

# ------------------------------------------------------------
# 4. Start frontend
# ------------------------------------------------------------

Write-Host "Starting Next.js frontend..."

$frontendProcess = Start-Process powershell `
    -ArgumentList "-NoExit", "-Command", @"
npm run dev
"@ `
    -PassThru

Write-Host ""
Write-Host "=== Local environment started ==="
Write-Host "Frontend: http://localhost:3000"
Write-Host "Backend : http://localhost:8080"
Write-Host "Database: localhost:5433"
Write-Host ""
Write-Host "Backend PID : $($backendProcess.Id)"
Write-Host "Frontend PID: $($frontendProcess.Id)"
