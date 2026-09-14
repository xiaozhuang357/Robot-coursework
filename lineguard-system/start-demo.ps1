$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDirectory = Join-Path $projectRoot "backend"
$frontendDirectory = Join-Path $projectRoot "frontend"
$runtimeLogDirectory = Join-Path $projectRoot ".run-logs"
$pidRecordPath = Join-Path $projectRoot ".lineguard-demo.pids.json"

New-Item -ItemType Directory -Path $runtimeLogDirectory -Force | Out-Null

if (Test-Path -LiteralPath $pidRecordPath) {
    throw "An existing demo process record was found. Run stop-demo.ps1 first."
}

if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory "node_modules"))) {
    Write-Host "[LineGuard] Installing frontend dependencies..." -ForegroundColor Cyan
    Push-Location $frontendDirectory
    try {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { throw "Frontend dependency installation failed." }
    }
    finally {
        Pop-Location
    }
}

Write-Host "[LineGuard] Building backend package..." -ForegroundColor Cyan
Push-Location $backendDirectory
try {
    & mvn.cmd -q "-DskipTests" package
    if ($LASTEXITCODE -ne 0) { throw "Backend build failed." }
}
finally {
    Pop-Location
}

$backendJar = Get-ChildItem -LiteralPath (Join-Path $backendDirectory "target") -Filter "lineguard-backend-*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $backendJar) { throw "Backend executable package was not found." }

$javaCommand = (Get-Command java.exe).Source
$npmCommand = (Get-Command npm.cmd).Source

Write-Host "[LineGuard] Starting Java backend..." -ForegroundColor Cyan
$backendProcess = Start-Process -FilePath $javaCommand `
    -ArgumentList @("-jar", "`"$($backendJar.FullName)`"") `
    -WorkingDirectory $backendDirectory `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $runtimeLogDirectory "backend.log") `
    -RedirectStandardError (Join-Path $runtimeLogDirectory "backend-error.log") `
    -PassThru

Write-Host "[LineGuard] Starting Vue frontend..." -ForegroundColor Cyan
$frontendProcess = Start-Process -FilePath $npmCommand `
    -ArgumentList @("run", "dev") `
    -WorkingDirectory $frontendDirectory `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $runtimeLogDirectory "frontend.log") `
    -RedirectStandardError (Join-Path $runtimeLogDirectory "frontend-error.log") `
    -PassThru

@{
    backendPid = $backendProcess.Id
    frontendPid = $frontendProcess.Id
    startedAt = (Get-Date).ToString("o")
} | ConvertTo-Json | Set-Content -LiteralPath $pidRecordPath -Encoding UTF8

function Wait-LineGuardEndpoint {
    param([string]$Address, [string]$Name)
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $Address -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "[LineGuard] $Name is ready." -ForegroundColor Green
                return
            }
        }
        catch {
            Start-Sleep -Milliseconds 750
        }
    }
    throw "$Name startup timed out. Check the .run-logs directory."
}

try {
    Wait-LineGuardEndpoint -Address "http://127.0.0.1:8080/api/v1/devices" -Name "Backend"
    Wait-LineGuardEndpoint -Address "http://127.0.0.1:5173" -Name "Frontend"
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "Run stop-demo.ps1 to clean up the started processes." -ForegroundColor Yellow
    throw
}

Write-Host ""
Write-Host "LineGuard is ready: http://localhost:5173" -ForegroundColor Green
Write-Host "Run .\stop-demo.ps1 to stop the demo." -ForegroundColor DarkGray
Start-Process "http://localhost:5173"
