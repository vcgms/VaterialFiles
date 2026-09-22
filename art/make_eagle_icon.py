#!/usr/bin/env python3
"""Turn the generated bald-eagle artwork into adaptive-icon layers."""
import os
import sys
from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else "/Users/vc/.qoder/vibe_images/eagle_icon_ref_1790089266.png"
RES = "/Users/vc/Desktop/code/VaterialFiles/app/src/main/res"

img = Image.open(SRC).convert("RGB")
w, h = img.size
px = img.load()

# Background color sampled from the four corners.
corners = [px[4, 4], px[w - 5, 4], px[4, h - 5], px[w - 5, h - 5]]
bg = tuple(sum(c[i] for c in corners) // 4 for i in range(3))

# Soft chroma-key: alpha ramps from 0 (pure bg) to 255 (subject).
lo, hi = 45.0, 95.0
rgba = Image.new("RGBA", (w, h))
rp = rgba.load()
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        d = ((r - bg[0]) ** 2 + (g - bg[1]) ** 2 + (b - bg[2]) ** 2) ** 0.5
        a = 0.0 if d <= lo else (255.0 if d >= hi else (d - lo) / (hi - lo) * 255.0)
        rp[x, y] = (r, g, b, int(a))

bbox = rgba.getbbox()
eagle = rgba.crop(bbox)
bw, bh = eagle.size

# Fit the subject inside the adaptive-icon safe zone (~60% of the 108dp canvas).
C = 1080
target = int(C * 0.60)
scale = target / max(bw, bh)
nw, nh = int(bw * scale), int(bh * scale)
eagle = eagle.resize((nw, nh), Image.LANCZOS)

canvas = Image.new("RGBA", (C, C), (0, 0, 0, 0))
canvas.paste(eagle, ((C - nw) // 2, (C - nh) // 2), eagle)

# Foreground densities (108dp layer).
dens = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for name, size in dens.items():
    out = canvas.resize((size, size), Image.LANCZOS)
    path = os.path.join(RES, f"mipmap-{name}", "launcher_icon_foreground.png")
    out.save(path)
    print("wrote", path)

# Monochrome silhouette: solid white shape driven by the alpha mask.
mono = Image.new("RGBA", (C, C), (255, 255, 255, 0))
mp = mono.load()
cp = canvas.load()
for y in range(C):
    for x in range(C):
        a = cp[x, y][3]
        if a:
            mp[x, y] = (255, 255, 255, a)
nodpi = os.path.join(RES, "drawable-nodpi")
os.makedirs(nodpi, exist_ok=True)
mono.resize((432, 432), Image.LANCZOS).save(os.path.join(nodpi, "launcher_icon_monochrome.png"))
print("wrote monochrome")
