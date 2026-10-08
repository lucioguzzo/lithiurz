"""Texture di servizio: grana pellicola e maschera "inchiostro" per i timbri."""
import sys, random
from PIL import Image, ImageFilter
import numpy as np
out = sys.argv[1]
rng = np.random.default_rng(7)
# grana: rumore grigio
g = (rng.normal(128, 40, (600, 600))).clip(0, 255).astype("uint8")
Image.fromarray(g, "L").convert("RGBA").save(f"{out}/grain.png")
# maschera timbro: opaca con buchi e graffi irregolari
W, H = 1200, 600
n = rng.random((H // 4, W // 4))
im = Image.fromarray((n * 255).astype("uint8"), "L").resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(3))
a = np.array(im).astype(float) / 255
fine = rng.random((H, W))
alpha = np.where((a < 0.26) | (fine < 0.05), 0, 255).astype("uint8")
for _ in range(40):  # graffi
    y = rng.integers(0, H); x0 = rng.integers(0, W); L = rng.integers(40, 260)
    alpha[y:y + rng.integers(1, 3), x0:x0 + L] = 0
m = Image.fromarray(alpha, "L").filter(ImageFilter.GaussianBlur(0.6))
rgba = Image.new("RGBA", (W, H), (255, 255, 255, 255)); rgba.putalpha(m)
rgba.save(f"{out}/grunge.png")
print("ok")
