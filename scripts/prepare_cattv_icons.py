#!/usr/bin/env python3
"""Clean CatTV's existing launcher artwork and regenerate legacy density icons.

The checked-in PNG contains a checkerboard fringe outside the rounded tile.
We preserve the cat/play artwork, replace the fringe with solid white, and
render density-specific legacy launcher PNGs before Android resource merging.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable" / "cattv_launcher_foreground.png"

image = Image.open(SOURCE).convert("RGBA")
width, height = image.size
if width != height:
    raise SystemExit(f"Expected square CatTV icon, got {width}x{height}")

# Composite all visible artwork onto white so transparent/black fringe pixels
# can never become a dark rim on launchers or in the About card.
white = Image.new("RGBA", image.size, (255, 255, 255, 255))
white.alpha_composite(image)

# A smooth inset rounded-square mask removes the old checkerboard perimeter.
scale = 4
mask = Image.new("L", (width * scale, height * scale), 0)
draw = ImageDraw.Draw(mask)
inset = max(24, round(width * 0.04))
radius = round(width * 0.23)
draw.rounded_rectangle(
    (inset * scale, inset * scale, (width - inset) * scale, (height - inset) * scale),
    radius=radius * scale,
    fill=255,
)
mask = mask.resize((width, height), Image.Resampling.LANCZOS)

clean = Image.new("RGBA", image.size, (255, 255, 255, 255))
clean.paste(white, (0, 0), mask)
clean.save(SOURCE, format="PNG", optimize=True)

# Keep old launcher fallbacks visually consistent with the adaptive icon.
for density, size in {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}.items():
    icon = clean.resize((size, size), Image.Resampling.LANCZOS).convert("RGB")
    for suffix in ("", "_round"):
        destination = RES / f"mipmap-{density}" / f"ic_launcher{suffix}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        icon.save(destination, format="PNG", optimize=True)

print("CatTV launcher artwork cleaned; legacy density icons regenerated.")
