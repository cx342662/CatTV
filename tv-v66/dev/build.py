"""Build an unsigned development APK from the pinned CatTV TV v66 release."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import zipfile

BASE_SHA = "bb081d618c559a2e43bad90fd4c5484710b74012861e23d109267a061bc4f6f2"

p = argparse.ArgumentParser()
p.add_argument("base", type=Path)
p.add_argument("out", type=Path)
p.add_argument("--apktool", required=True, type=Path)
p.add_argument("--aapt", required=True, type=Path)
p.add_argument("--version", type=int, required=True)
p.add_argument("--patch-dir", type=Path, default=Path(__file__).with_name("patches"))
a = p.parse_args()
if not 68 <= a.version < 100000000:
    p.error("Version must be between 68 and 99999999")
base, out = a.base.resolve(), a.out.resolve()
if hashlib.sha256(base.read_bytes()).hexdigest() != BASE_SHA:
    p.error("The base APK must be the published CatTV TV v66")
out.mkdir(parents=True, exist_ok=True)
decoded = out / "decoded"
apk = out / f"CatTV-TV-ARM32-v{a.version}-unsigned.apk"
tool = ["java", "-jar", str(a.apktool.resolve())]
subprocess.run([*tool, "d", str(base), "-o", str(decoded)], check=True)
for patch in sorted(a.patch_dir.resolve().glob("*.patch")):
    subprocess.run(["git", "apply", "--check", str(patch)], cwd=decoded, check=True)
    subprocess.run(["git", "apply", str(patch)], cwd=decoded, check=True)
yml = decoded / "apktool.yml"
data = yml.read_text(encoding="utf-8")
data, n = re.subn(r"(?m)^  versionCode:.*$", f"  versionCode: {a.version}", data)
if n != 1:
    raise ValueError("Unexpected versionCode in decoded APK")
data, n = re.subn(r"(?m)^  versionName:.*$", f"  versionName: 1.3.2-TV-v{a.version}", data)
if n != 1:
    raise ValueError("Unexpected versionName in decoded APK")
yml.write_text(data, encoding="utf-8")
subprocess.run([*tool, "b", "-f", str(decoded), "-o", str(apk)], check=True)
badging = subprocess.check_output([str(a.aapt.resolve()), "dump", "badging", str(apk)], encoding="utf-8")
assert "package: name='com.github.avbox.osc.tv'" in badging
assert f"versionCode='{a.version}'" in badging
assert "leanback-launchable-activity:" in badging
with zipfile.ZipFile(apk) as z:
    assert {n.split("/")[1] for n in z.namelist() if n.startswith("lib/")} == {"armeabi-v7a"}
    assert "classes4.dex" in z.namelist(), "TV update code is missing"
print(apk)
