#!/usr/bin/env python3
"""Generate CatTV Android launcher icons from the transparent cat artwork.

The Android adaptive icon foreground is transparent and keeps the complete mascot
inside Android's 66% adaptive-icon safe circle. Android supplies the white background
and its own mask. Legacy icons get a rounded white backing plate without cropping.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable" / "cattv_logo_clear.webp"
TARGET = RES / "drawable" / "cattv_launcher_foreground.png"
CANVAS = 512

cat = Image.open(SOURCE).convert("RGBA")
# Fit the entire cat (including both ears) within the 66% adaptive-icon safe circle.
# 300/512 = 58.6%, leaving extra margin for OEM launcher masks.
cat = cat.resize((300, 300), Image.Resampling.LANCZOS)
foreground = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
foreground.alpha_composite(cat, ((CANVAS - 300) // 2, (CANVAS - 300) // 2))
foreground.save(TARGET, "PNG", optimize=True)

for density, size in {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144,
    "xxxhdpi": 192,
}.items():
    scale = 4
    mask = Image.new("L", (size * scale, size * scale), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(
        (0, 0, size * scale - 1, size * scale - 1),
        radius=int(size * scale * .24),
        fill=255
    )
    mask = mask.resize((size, size), Image.Resampling.LANCZOS)
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    white.putalpha(mask)
    mascot = foreground.resize((size, size), Image.Resampling.LANCZOS)
    white.alpha_composite(mascot)
    for suffix in ("", "_round"):
        output = RES / f"mipmap-{density}" / f"ic_launcher{suffix}.png"
        white.save(output, "PNG", optimize=True)

print("CatTV launcher: white adaptive background + safe-zone foreground; legacy icons regenerated.")
