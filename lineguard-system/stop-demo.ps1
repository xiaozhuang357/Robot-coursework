$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidRecordPath = Join-Path $projectRoot ".lineguard-demo.pids.json"

if (-not (Test-Path -LiteralPath $pidRecordPath)) {
    Write-Host "No LineGuard demo process record was found." -ForegroundColor Yellow
    exit 0
}

$processRecord = Get-Content -LiteralPath $pidRecordPath -Raw | ConvertFrom-Json

function Stop-LineGuardProcessTree {
    param([int]$TargetProcessId)
    $childProcesses = Get-CimInstance Win32_Process -Filter "ParentProcessId = $TargetProcessId" -ErrorAction SilentlyContinue
    foreach ($childProcess in $childProcesses) {
        Stop-LineGuardProcessTree -TargetProcessId $childProcess.ProcessId
    }
    Stop-Process -Id $TargetProcessId -Force -ErrorAction SilentlyContinue
}

Stop-LineGuardProcessTree -TargetProcessId ([int]$processRecord.frontendPid)
Stop-LineGuardProcessTree -TargetProcessId ([int]$processRecord.backendPid)
Remove-Item -LiteralPath $pidRecordPath -Force

Write-Host "LineGuard demo services stopped. Runtime data remains in backend/runtime-data." -ForegroundColor Green
