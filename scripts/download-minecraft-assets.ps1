param(
    [string]$Version = "",
    [string]$AssetRoot = ""
)

# Downloads the vanilla Minecraft assets used by the HTTPD live inventory view
# into the same minecraft-assets cache layout used by the running module.
#
# Usage:
#   ./scripts/download-minecraft-assets.ps1             # latest release
#   ./scripts/download-minecraft-assets.ps1 26.1.2      # specific version
#   ./scripts/download-minecraft-assets.ps1 -AssetRoot "plugins/Plex/modules/<HTTPD module>/minecraft-assets"

$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($AssetRoot)) {
    $AssetRoot = if ($env:PLEX_HTTPD_ASSET_ROOT) { $env:PLEX_HTTPD_ASSET_ROOT } else { Join-Path $ProjectRoot "minecraft-assets" }
}
$ManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

$manifest = Invoke-RestMethod -Uri $ManifestUrl -TimeoutSec 30
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = $manifest.latest.release
}

$versionEntry = $manifest.versions | Where-Object { $_.id -eq $Version } | Select-Object -First 1
if ($null -eq $versionEntry) {
    throw "Minecraft version '$Version' was not found in Mojang's manifest"
}

$versionJson = Invoke-RestMethod -Uri $versionEntry.url -TimeoutSec 30
$clientUrl = $versionJson.downloads.client.url

Write-Host "Downloading Minecraft $Version client assets..."
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("plex-httpd-assets-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempDir | Out-Null
$jarPath = Join-Path $tempDir "minecraft-$Version.jar"

try {
    Invoke-WebRequest -Uri $clientUrl -OutFile $jarPath -TimeoutSec 300

    foreach ($directory in @("textures", "models", "items")) {
        $path = Join-Path $AssetRoot $directory
        if (Test-Path $path) {
            Remove-Item -Recurse -Force $path
        }
        New-Item -ItemType Directory -Path $path -Force | Out-Null
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    $extracted = 0
    try {
        foreach ($entry in $zip.Entries) {
            if ([string]::IsNullOrEmpty($entry.Name)) {
                continue
            }

            $name = $entry.FullName -replace '\\', '/'
            $wanted = $name.StartsWith("assets/minecraft/textures/item/") -or
                      $name.StartsWith("assets/minecraft/textures/block/") -or
                      ($name -eq "assets/minecraft/textures/entity/shield/shield_base_nopattern.png") -or
                      $name.StartsWith("assets/minecraft/models/item/") -or
                      $name.StartsWith("assets/minecraft/models/block/") -or
                      $name.StartsWith("assets/minecraft/items/")
            if (-not $wanted) {
                continue
            }

            $relative = $name.Substring("assets/minecraft/".Length)
            $target = Join-Path $AssetRoot ($relative -replace '/', [System.IO.Path]::DirectorySeparatorChar)
            $targetParent = Split-Path -Parent $target
            New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
            $extracted++
        }
    }
    finally {
        $zip.Dispose()
    }

    Set-Content -Path (Join-Path $AssetRoot "version.txt") -Value $Version -Encoding UTF8
    Write-Host "Extracted $extracted files to $AssetRoot"
}
finally {
    if (Test-Path $tempDir) {
        Remove-Item -Recurse -Force $tempDir
    }
}
