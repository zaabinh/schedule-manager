$ErrorActionPreference = "Stop"
$projectName = "schedule-manager-security"
$composeFile = Join-Path $PSScriptRoot "..\compose.e2e.yaml"
$securityDirectory = (Resolve-Path (Join-Path $PSScriptRoot "..\tests\security")).Path
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$zapWorkDirectory = Join-Path $temporaryRoot ("schedule-manager-zap-" + [Guid]::NewGuid().ToString("N"))
$zapImage = "ghcr.io/zaproxy/zaproxy@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef"

try {
    New-Item -ItemType Directory -Path $zapWorkDirectory | Out-Null
    Copy-Item -LiteralPath (Join-Path $securityDirectory "zap-baseline.conf") -Destination $zapWorkDirectory

    docker compose --project-name $projectName --file $composeFile up --detach --build --wait postgres-e2e backend-e2e frontend-e2e
    if ($LASTEXITCODE -ne 0) { throw "Could not start security scan stack (exit code $LASTEXITCODE)." }

    docker run --rm --network "${projectName}_default" --volume "${zapWorkDirectory}:/zap/wrk:rw" $zapImage `
        zap-baseline.py -t http://frontend-e2e:3000 -m 1 -T 3 -s -I -c /zap/wrk/zap-baseline.conf
    if ($LASTEXITCODE -ne 0) { throw "ZAP baseline reported findings (exit code $LASTEXITCODE)." }
}
finally {
    docker compose --project-name $projectName --file $composeFile down --volumes --remove-orphans
    if (Test-Path -LiteralPath $zapWorkDirectory) {
        $resolvedZapWork = (Resolve-Path -LiteralPath $zapWorkDirectory).Path
        if (-not $resolvedZapWork.StartsWith($temporaryRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolvedZapWork)).StartsWith("schedule-manager-zap-")) {
            throw "Refusing to remove unexpected ZAP work directory: $resolvedZapWork"
        }
        Remove-Item -LiteralPath $resolvedZapWork -Recurse -Force
    }
}
