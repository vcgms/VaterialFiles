#!/usr/bin/env python3
"""Render the final adaptive-icon look (background + foreground) to a single
square PNG, cropping away the outer adaptive-icon safe-zone reserve."""
import sys
from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else "/Users/vc/.qoder/vibe_images/eagle_folder_cute2_1790096827.png"
OUT = sys.argv[2] if len(sys.argv) > 2 else "/Users/vc/Desktop/code/VaterialFiles/icon.png"
BG = (0x1A, 0x73, 0xE8)  # color_primary_light / google_blue_600

img = Image.open(SRC).convert("RGB")
w, h = img.size
px = img.load()
corners = [px[4, 4], px[w - 5, 4], px[4, h - 5], px[w - 5, h - 5]]
bg = tuple(sum(c[i] for c in corners) // 4 for i in range(3))

lo, hi = 45.0, 95.0
rgba = Image.new("RGBA", (w, h))
rp = rgba.load()
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        d = ((r - bg[0]) ** 2 + (g - bg[1]) ** 2 + (b - bg[2]) ** 2) ** 0.5
        a = 0.0 if d <= lo else (255.0 if d >= hi else (d - lo) / (hi - lo) * 255.0)
        rp[x, y] = (r, g, b, int(a))

eagle = rgba.crop(rgba.getbbox())
bw, bh = eagle.size

# 108dp adaptive canvas; subject occupies ~60% (inside the safe zone).
C = 1080
target = int(C * 0.60)
scale = target / max(bw, bh)
nw, nh = int(bw * scale), int(bh * scale)
eagle = eagle.resize((nw, nh), Image.LANCZOS)

canvas = Image.new("RGBA", (C, C), BG + (255,))
canvas.paste(eagle, ((C - nw) // 2, (C - nh) // 2), eagle)

# Crop away the 18dp-per-side reserve -> keep the inner 72dp visible square.
keep = int(C * 72 / 108)
off = (C - keep) // 2
icon = canvas.crop((off, off, off + keep, off + keep)).resize((512, 512), Image.LANCZOS)
icon.save(OUT)
print("wrote", OUT, icon.size)
