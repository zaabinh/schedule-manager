$ErrorActionPreference = "Stop"
$projectName = "schedule-manager-security"
$composeFile = Join-Path $PSScriptRoot "..\compose.e2e.yaml"
$securityDirectory = (Resolve-Path (Join-Path $PSScriptRoot "..\tests\security")).Path
$zapImage = "ghcr.io/zaproxy/zaproxy@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef"

try {
    docker compose --project-name $projectName --file $composeFile up --detach --build --wait postgres-e2e backend-e2e frontend-e2e
    if ($LASTEXITCODE -ne 0) { throw "Could not start security scan stack (exit code $LASTEXITCODE)." }

    docker run --rm --network "${projectName}_default" --volume "${securityDirectory}:/zap/config:ro" $zapImage `
        zap-baseline.py -t http://frontend-e2e:3000 -m 1 -T 3 -s -I -c /zap/config/zap-baseline.conf
    if ($LASTEXITCODE -ne 0) { throw "ZAP baseline reported findings (exit code $LASTEXITCODE)." }
}
finally {
    docker compose --project-name $projectName --file $composeFile down --volumes --remove-orphans
}
