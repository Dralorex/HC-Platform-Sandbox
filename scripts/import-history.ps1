# Merge git history from legacy per-plugin repos into HC-Platform paths.
# Requires: Git for Windows. Run from repo root or any directory.
$ErrorActionPreference = "Stop"
$git = "C:\Program Files\Git\bin\git.exe"
if (-not (Test-Path $git)) { throw "Git not found at $git" }

$plat = Resolve-Path (Join-Path $PSScriptRoot "..")
$legacyRoot = Resolve-Path (Join-Path $plat ".." )

# Local identity for this repo only (no global git config changes)
$gitIdentity = @(
    "-c", "safe.directory=*",
    "-c", "user.name=HollowCrownStudio",
    "-c", "user.email=hollowcrown@users.noreply.github.com"
)

$imports = [ordered]@{
    "api" = "HC-API"
    "foundation" = "HC-Foundation"
    "hub" = "HC-Hub"
    "minigames/iron-legacy" = "HC-Iron-Legacy"
}

$saved = Join-Path $plat "scripts\saved-poms"

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)]$GitArgs)
    & $git @gitIdentity @GitArgs
}

function Restore-MonorepoPoms {
    Copy-Item (Join-Path $saved "root-pom.xml") (Join-Path $plat "pom.xml") -Force
    Copy-Item (Join-Path $saved "api-pom.xml") (Join-Path $plat "api\pom.xml") -Force
    Copy-Item (Join-Path $saved "foundation-pom.xml") (Join-Path $plat "foundation\pom.xml") -Force
    Copy-Item (Join-Path $saved "hub-pom.xml") (Join-Path $plat "hub\pom.xml") -Force
    Copy-Item (Join-Path $saved "iron-legacy-pom.xml") (Join-Path $plat "minigames\iron-legacy\pom.xml") -Force
    $mgReadme = Join-Path $saved "minigames-README.md"
    if (Test-Path $mgReadme) {
        New-Item -ItemType Directory -Force -Path (Join-Path $plat "minigames") | Out-Null
        Copy-Item $mgReadme (Join-Path $plat "minigames\README.md") -Force
    }
    if (-not (Test-Path (Join-Path $plat "minigames\pom.xml"))) {
        Copy-Item (Join-Path $plat "minigames\pom.xml") (Join-Path $saved "minigames-pom.xml") -ErrorAction SilentlyContinue
    }
}

Set-Location $plat

$stashRoot = Join-Path $plat "scripts\import-stash"
if (Test-Path $stashRoot) { Remove-Item $stashRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stashRoot | Out-Null
foreach ($rel in @("README.md", ".gitignore", "docs", "scripts\build.ps1", "scripts\saved-poms", "scripts\import-history.ps1")) {
    $src = Join-Path $plat $rel
    if (Test-Path $src) {
        $dst = Join-Path $stashRoot $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $dst -Parent) | Out-Null
        Copy-Item $src $dst -Recurse -Force
    }
}

if (Test-Path (Join-Path $plat ".git")) {
    Remove-Item (Join-Path $plat ".git") -Recurse -Force
}

foreach ($prefix in $imports.Keys) {
    $dir = Join-Path $plat $prefix
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
}
if (Test-Path (Join-Path $plat "minigames")) {
    Remove-Item (Join-Path $plat "minigames") -Recurse -Force
}

Invoke-Git init
Invoke-Git add .
Invoke-Git commit -m "chore: prepare for legacy history import" --allow-empty

foreach ($prefix in $imports.Keys) {
    $legacyName = $imports[$prefix]
    $legacyPath = Join-Path $legacyRoot $legacyName
    if (-not (Test-Path (Join-Path $legacyPath ".git"))) {
        Write-Warning "Skipping $legacyName - no .git at $legacyPath"
        continue
    }
    $remote = "legacy-" + ($legacyName -replace '[^a-zA-Z0-9]', '-').ToLower()
    $remotes = (Invoke-Git remote) 2>$null
    if ($remotes -contains $remote) { Invoke-Git remote remove $remote }
    Invoke-Git remote add $remote $legacyPath
    Invoke-Git fetch $remote
    $branch = (& $git @gitIdentity -C $legacyPath rev-parse --abbrev-ref HEAD).Trim()
    Write-Host "Importing $legacyName -> $prefix (branch $branch)..."
    Invoke-Git subtree add --prefix=$prefix "${remote}/${branch}" --squash
}

Restore-MonorepoPoms

Get-ChildItem $stashRoot -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($stashRoot.Length + 1)
    $dest = Join-Path $plat $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
    Copy-Item $_.FullName $dest -Force
}

if (Test-Path $stashRoot) { Remove-Item $stashRoot -Recurse -Force }

Invoke-Git add -A
Invoke-Git commit -m "build: Maven platform reactor (parent POM + module layout)"

Write-Host ''
Write-Host 'Done. Verify with: git log --oneline --graph -25' -ForegroundColor Green
