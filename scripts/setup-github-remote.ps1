# Adds origin remote only — does NOT push.
param(
    [string]$RemoteUrl = "https://github.com/Dralorex/HC-Platform.git"
)
$ErrorActionPreference = "Stop"
$git = "C:\Program Files\Git\bin\git.exe"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root
$id = @("-c", "safe.directory=*")

$remotes = & $git @id remote 2>$null
if ($remotes -contains "origin") {
    Write-Host "origin already exists:" -ForegroundColor Yellow
    & $git @id remote -v
} else {
    & $git @id remote add origin $RemoteUrl
    Write-Host "Added origin: $RemoteUrl" -ForegroundColor Green
}

Write-Host ""
Write-Host "When YOU are ready to upload commits, run:" -ForegroundColor Cyan
Write-Host "  git push -u origin main" -ForegroundColor White
