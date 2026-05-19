# Build all platform modules. Requires Maven on PATH.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
mvn clean package -DskipTests
Write-Host ""
Write-Host "Plugin JARs:" -ForegroundColor Green
Get-ChildItem -Recurse -Filter "*.jar" target | Where-Object { $_.Directory.Name -eq "target" -and $_.Name -notmatch "original" } | ForEach-Object { Write-Host "  $($_.FullName)" }
