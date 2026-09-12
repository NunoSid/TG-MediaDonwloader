$ErrorActionPreference = "Stop"
$repo = "AkashPriyadarshii/tdlib-android"
$release = Invoke-RestMethod -Headers @{ "User-Agent" = "TelegramMediaLibrary" } -Uri "https://api.github.com/repos/$repo/releases/latest"
$core = $release.assets | Where-Object { $_.name -eq "core-release.aar" } | Select-Object -First 1
if (-not $core) { throw "core-release.aar não encontrado no latest release de $repo" }
New-Item -ItemType Directory -Force -Path "$PSScriptRoot/../app/libs" | Out-Null
Invoke-WebRequest -Headers @{ "User-Agent" = "TelegramMediaLibrary" } -Uri $core.browser_download_url -OutFile "$PSScriptRoot/../app/libs/core-release.aar"
Write-Host "TDLib AAR instalado em app/libs/core-release.aar"
