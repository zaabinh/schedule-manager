param([string]$Name = (Get-Date -Format 'yyyyMMdd-HHmmss'),[string]$EnvFile='.env.production',[string]$ProjectName='schedule-manager')
$ErrorActionPreference = 'Stop'
if ($Name -notmatch '^[A-Za-z0-9._-]+$') { throw 'Backup name contains unsafe characters.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backupDirectory = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$dumpCommand = 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /backups/' + $Name + '.dump'
docker compose -p $ProjectName --env-file $EnvFile -f compose.production.yaml exec -T postgres sh -c $dumpCommand
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
$file = Join-Path $backupDirectory "$Name.dump"
if (!(Test-Path -LiteralPath $file) -or (Get-Item -LiteralPath $file).Length -eq 0) { throw 'Backup file is missing or empty.' }
Write-Output "Backup created: $file"
