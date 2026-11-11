import argparse
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=pathlib.Path)
parser.add_argument("--aapt", required=True)
parser.add_argument("--output", type=pathlib.Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)

def dump(*arguments):
    return subprocess.check_output([args.aapt, "dump", *arguments], encoding="utf-8")

badging = dump("badging", str(args.apk))
manifest = dump("xmltree", str(args.apk), "AndroidManifest.xml")
(args.output / "badging.txt").write_text(badging, encoding="utf-8")
(args.output / "manifest.txt").write_text(manifest, encoding="utf-8")
assert "package: name='com.github.avbox.osc.tv'" in badging
assert "launchable-activity: name='com.github.avbox.osc.ui.tv.TvHomeActivity'" in badging
assert re.search(r"screenOrientation[^\n]*0x0", manifest)
assert "android.software.leanback" in manifest
assert "android.hardware.touchscreen" in manifest

libraries = []
with zipfile.ZipFile(args.apk) as archive:
    names = archive.namelist()
    abis = sorted({name.split("/")[1] for name in names if name.startswith("lib/")})
    assert abis == ["armeabi-v7a"], abis
    for name in names:
        if not name.startswith("lib/") or not name.endswith(".so"):
            continue
        data = archive.read(name)
        assert data[:4] == b"\x7fELF", name
        assert data[4] == 1, f"ELF is not 32-bit: {name}"
        assert data[5] == 1, f"ELF is not little-endian: {name}"
        assert struct.unpack_from("<H", data, 18)[0] == 40, f"ELF is not ARM: {name}"
        libraries.append(name)
    assert any("python" in name for name in libraries), "Python ARM32 runtime missing"

result = {
    "apk": args.apk.name,
    "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    "bytes": args.apk.stat().st_size,
    "abis": abis,
    "launcher": "com.github.avbox.osc.ui.tv.TvHomeActivity",
    "orientation": "landscape",
    "elf_libraries_verified": libraries,
    "runtime_tv_test": "not performed",
}
(args.output / "verification.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result, indent=2))
