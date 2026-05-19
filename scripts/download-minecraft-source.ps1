param(
    [string]$Version = "",
    [string]$Filter  = "**Shield*,**BlockEntityWithoutLevelRenderer*,**ItemStackRenderState*,**ItemRenderer*"
)

# Downloads + decompiles selected Minecraft client classes for reference.
# Output goes to minecraft-source/ (gitignored). Tools and per-version
# downloads are cached under .gradle/minecraft-source-tools/.
#
# Modern Mojang client jars (26.1+) ship semi-deobfuscated classes with the
# real net.minecraft.* layout, so no remap step is needed.
#
# Usage:
#   ./scripts/download-minecraft-source.ps1
#   ./scripts/download-minecraft-source.ps1 -Version 26.1.2
#   ./scripts/download-minecraft-source.ps1 -Filter "**Shield*"

$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$OutRoot     = Join-Path $ProjectRoot "minecraft-source"
$ToolsRoot   = Join-Path $ProjectRoot ".gradle/minecraft-source-tools"
$ManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
$VineflowerUrl = "https://repo1.maven.org/maven2/org/vineflower/vineflower/1.12.0/vineflower-1.11.0.jar"

function Download-IfMissing($Url, $Target) {
    if (Test-Path $Target) { return }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Target) -Force | Out-Null
    Invoke-WebRequest -Uri $Url -OutFile $Target -TimeoutSec 600
}

# Resolve version (explicit > cached > latest)
$manifest = Invoke-RestMethod -Uri $ManifestUrl -TimeoutSec 30
if ([string]::IsNullOrWhiteSpace($Version)) {
    $cachedFile = Join-Path $ProjectRoot "src/main/resources/httpd/assets/.minecraft-version"
    if (Test-Path $cachedFile) {
        $Version = (Get-Content $cachedFile -Raw).Trim()
    } else {
        $Version = $manifest.latest.release
    }
}
$entry = $manifest.versions | Where-Object { $_.id -eq $Version } | Select-Object -First 1
if (!$entry) { throw "Minecraft version '$Version' not found in Mojang manifest" }

$versionJson = Invoke-RestMethod -Uri $entry.url -TimeoutSec 30
$clientUrl   = $versionJson.downloads.client.url
if ([string]::IsNullOrEmpty($clientUrl)) { throw "No client jar URL for version $Version" }

$workDir       = Join-Path $ToolsRoot "v$Version"
$vineflowerJar = Join-Path $ToolsRoot "vineflower.jar"
$clientJar     = Join-Path $workDir   "client.jar"

Download-IfMissing $VineflowerUrl $vineflowerJar
Download-IfMissing $clientUrl     $clientJar

# Extract only the classes matching the filter into a staging dir so
# Vineflower has a small input set and a single run finishes in seconds.
$staging = Join-Path $workDir "staging"
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

$globs = $Filter -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
function Matches-AnyGlob($name, $globs) {
    foreach ($g in $globs) {
        # Convert Vineflower-style glob (**, *) into a regex against the
        # class-file basename ("ShieldModel.class").
        $regex = "^" + ([Regex]::Escape($g) -replace "\\\*\\\*", ".*" -replace "\\\*", "[^/]*") + "$"
        $leaf = Split-Path -Leaf ($name -replace "\.class$", "")
        if ($leaf -match $regex.Replace("\.\*", ".*")) { return $true }
        if ($name -match $regex) { return $true }
    }
    return $false
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($clientJar)
$extracted = 0
try {
    foreach ($entry in $zip.Entries) {
        $name = $entry.FullName -replace '\\', '/'
        if (-not $name.EndsWith(".class")) { continue }
        if (-not (Matches-AnyGlob $name $globs)) { continue }
        $target = Join-Path $staging ($name -replace '/', [System.IO.Path]::DirectorySeparatorChar)
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        $extracted++
    }
} finally {
    $zip.Dispose()
}
Write-Host "Extracted $extracted matching class files."

if (Test-Path $OutRoot) { Remove-Item -Recurse -Force $OutRoot }
New-Item -ItemType Directory -Path $OutRoot -Force | Out-Null

Write-Host "Decompiling with Vineflower..."
& java -jar $vineflowerJar --silent=1 $staging $OutRoot
if ($LASTEXITCODE -ne 0) { throw "Vineflower failed (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "Decompiled sources written to $OutRoot"
