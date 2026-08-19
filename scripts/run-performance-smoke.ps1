$ErrorActionPreference = "Stop"
$projectName = "schedule-manager-performance"
$composeFile = Join-Path $PSScriptRoot "..\compose.e2e.yaml"

try {
    docker compose --project-name $projectName --file $composeFile up --detach --build --wait postgres-e2e backend-e2e
    if ($LASTEXITCODE -ne 0) { throw "Could not start performance stack (exit code $LASTEXITCODE)." }

    docker compose --project-name $projectName --file $composeFile --profile performance run --rm performance
    if ($LASTEXITCODE -ne 0) {
        $performanceExitCode = $LASTEXITCODE
        docker compose --project-name $projectName --file $composeFile logs --no-color --tail 300 backend-e2e
        throw "Performance thresholds failed (exit code $performanceExitCode)."
    }
}
finally {
    docker compose --project-name $projectName --file $composeFile --profile performance down --volumes --remove-orphans
}
