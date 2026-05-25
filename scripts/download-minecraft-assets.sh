#!/usr/bin/env sh
set -eu

# Downloads the vanilla Minecraft assets used by the HTTPD live inventory view
# into the same minecraft-assets cache layout used by the running module.
#
# Usage:
#   ./scripts/download-minecraft-assets.sh              # latest release
#   ./scripts/download-minecraft-assets.sh 26.1.2       # specific version
#   ./scripts/download-minecraft-assets.sh 26.1.2 "plugins/Plex/modules/<HTTPD module>/minecraft-assets"

VERSION="${1:-}"
PROJECT_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ASSET_ROOT="${2:-${PLEX_HTTPD_ASSET_ROOT:-$PROJECT_ROOT/minecraft-assets}}"

python3 - "$VERSION" "$ASSET_ROOT" <<'PY'
import json
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

version_arg = sys.argv[1].strip()
asset_root = Path(sys.argv[2])
manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def get_json(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


manifest = get_json(manifest_url)
version = version_arg or manifest["latest"]["release"]
version_entry = next((v for v in manifest["versions"] if v["id"] == version), None)
if version_entry is None:
    raise SystemExit(f"Minecraft version {version!r} was not found in Mojang's manifest")

version_json = get_json(version_entry["url"])
client_url = version_json["downloads"]["client"]["url"]

print(f"Downloading Minecraft {version} client assets...")
with tempfile.TemporaryDirectory() as tmp:
    jar_path = Path(tmp) / f"minecraft-{version}.jar"
    with urllib.request.urlopen(client_url, timeout=300) as response, jar_path.open("wb") as out:
        shutil.copyfileobj(response, out)

    for directory in ("textures", "models", "items"):
        shutil.rmtree(asset_root / directory, ignore_errors=True)
        (asset_root / directory).mkdir(parents=True, exist_ok=True)

    prefixes = (
        "assets/minecraft/textures/item/",
        "assets/minecraft/textures/block/",
        "assets/minecraft/textures/entity/shield/shield_base_nopattern.png",
        "assets/minecraft/models/item/",
        "assets/minecraft/models/block/",
        "assets/minecraft/items/",
    )

    extracted = 0
    with zipfile.ZipFile(jar_path) as jar:
        for info in jar.infolist():
            if info.is_dir() or not info.filename.startswith(prefixes):
                continue
            relative = info.filename[len("assets/minecraft/"):]
            target = asset_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with jar.open(info) as source, target.open("wb") as out:
                shutil.copyfileobj(source, out)
            extracted += 1

(asset_root / "version.txt").write_text(version + "\n", encoding="utf-8")
print(f"Extracted {extracted} files to {asset_root}")
PY
