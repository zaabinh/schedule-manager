param([Parameter(Mandatory=$true)][string]$BackupName,[Parameter(Mandatory=$true)][string]$ConfirmDatabase,[string]$EnvFile='.env.production',[string]$ProjectName='schedule-manager')
$ErrorActionPreference = 'Stop'
if ($BackupName -notmatch '^[A-Za-z0-9._-]+\.dump$') { throw 'Backup name contains unsafe characters.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$file = Join-Path (Join-Path $root 'backups') $BackupName
if (!(Test-Path -LiteralPath $file)) { throw "Backup not found: $file" }
$database = docker compose -p $ProjectName --env-file $EnvFile -f compose.production.yaml exec -T postgres sh -c 'printf %s "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0 -or $ConfirmDatabase -ne $database) { throw 'Database confirmation does not match the running target.' }
$restoreCommand = 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner /backups/' + $BackupName
docker compose -p $ProjectName --env-file $EnvFile -f compose.production.yaml exec -T postgres sh -c $restoreCommand
if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }
Write-Output "Restore completed for database: $database"
