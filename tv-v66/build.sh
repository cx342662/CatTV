#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
reference="$(realpath "$1")"
out="$(realpath -m "$2")"
[[ "$(sha256sum "$reference" | cut -d' ' -f1)" = d835dc6c3b1e8833a774e7deeeca90f6b4399a25036ec704828957b9b14d5a36 ]]
mkdir -p "$out"
[[ -z "$(ls -A "$out")" ]]
bt="${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/37.0.0"
platform="$ANDROID_HOME/platforms/android-37/android.jar"
[[ -f "$platform" ]] || platform="$ANDROID_HOME/platforms/android-37.0/android.jar"
apktool d "$reference" -o "$out/decoded"
cd "$out/decoded"
git apply "$repo/tv-v66/cattv-v66.patch"
mv smali/wm3.smali smali/zs0.smali smali_classes3/
res="$repo/app/src/main/res"
for dpi in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mkdir -p "res/mipmap-$dpi"
  cp "$res/mipmap-$dpi/ic_launcher.png" "res/mipmap-$dpi/cattv_identity_launcher.png"
  cp "$res/mipmap-$dpi/ic_launcher_round.png" "res/mipmap-$dpi/cattv_identity_launcher_round.png"
done
cp "$res/drawable/cattv_logo_clear.webp" "$res/drawable/cattv_launcher_foreground.png" "$res/drawable/cattv_home_logo.xml" res/drawable/
cp "$repo/app/src/tv/res/drawable/cattv_tv_banner.xml" res/drawable/
apktool b -f . -o "$out/unsigned.apk"
mkdir -p "$out/classes" "$out/dex"
javac --release 8 -encoding UTF-8 -cp "$platform" -d "$out/classes" "$repo/tv-v66/TvUpdate.java"
"$bt/d8" --min-api 24 --lib "$platform" --output "$out/dex" "$out/classes/cattv/update/TvUpdate.class"
mv "$out/dex/classes.dex" "$out/dex/classes4.dex"
(cd "$out/dex" && zip -q "$out/unsigned.apk" classes4.dex)
"$bt/zipalign" -f -p 4 "$out/unsigned.apk" "$out/CatTV-TV-ARM32-v66-unsigned.apk"

