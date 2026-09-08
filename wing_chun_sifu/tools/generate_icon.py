#!/usr/bin/env python3
"""Genera l'icona dell'app in tutte le dimensioni per Android e iOS.

    python3 tools/generate_icon.py

Il segno e' il carattere 詠 (wing, "eterna") in oro su fondo d'inchiostro:
si legge anche a 48 pixel, dove una figura umana diventerebbe una macchia.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, 'assets', 'fonts', 'NotoSerifTC-SemiBold.ttf')

INK = (10, 12, 17)
INK_TOP = (26, 22, 16)
GOLD = (217, 177, 92)
GOLD_SOFT = (140, 117, 57)

ANDROID = {
    'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192,
}
IOS = {
    'Icon-App-20x20@1x.png': 20, 'Icon-App-20x20@2x.png': 40,
    'Icon-App-20x20@3x.png': 60, 'Icon-App-29x29@1x.png': 29,
    'Icon-App-29x29@2x.png': 58, 'Icon-App-29x29@3x.png': 87,
    'Icon-App-40x40@1x.png': 40, 'Icon-App-40x40@2x.png': 80,
    'Icon-App-40x40@3x.png': 120, 'Icon-App-60x60@2x.png': 120,
    'Icon-App-60x60@3x.png': 180, 'Icon-App-76x76@1x.png': 76,
    'Icon-App-76x76@2x.png': 152, 'Icon-App-83.5x83.5@2x.png': 167,
    'Icon-App-1024x1024@1x.png': 1024,
}


def render(size=1024):
    """Disegna l'icona alla massima risoluzione."""
    img = Image.new('RGB', (size, size), INK)
    px = img.load()
    # fondo: alone caldo che sale dal basso verso il centro
    cx, cy = size * 0.5, size * 0.56
    maxd = size * 0.78
    for y in range(size):
        for x in range(0, size, 2):
            d = math.hypot(x - cx, y - cy) / maxd
            t = max(0.0, 1.0 - d) ** 2.2
            c = tuple(int(INK[i] + (INK_TOP[i] - INK[i]) * t) for i in range(3))
            px[x, y] = c
            if x + 1 < size:
                px[x + 1, y] = c

    d = ImageDraw.Draw(img)
    # cornice sottile, come il bordo di un sigillo
    inset = size * 0.085
    d.rounded_rectangle(
        [inset, inset, size - inset, size - inset],
        radius=size * 0.10, outline=GOLD_SOFT, width=max(1, int(size * 0.010)))

    # il carattere
    font = ImageFont.truetype(FONT, int(size * 0.50))
    glyph = '詠'
    box = d.textbbox((0, 0), glyph, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    d.text((size / 2 - w / 2 - box[0], size * 0.50 - h / 2 - box[1]),
           glyph, font=font, fill=GOLD)
    return img


def main():
    master = render(1024)
    master.save(os.path.join(ROOT, 'assets', 'icon.png'), optimize=True)

    for folder, s in ANDROID.items():
        path = os.path.join(ROOT, 'android', 'app', 'src', 'main', 'res', folder)
        os.makedirs(path, exist_ok=True)
        master.resize((s, s), Image.LANCZOS).save(
            os.path.join(path, 'ic_launcher.png'), optimize=True)
    print(f'Android: {len(ANDROID)} densita\'')

    ios_dir = os.path.join(ROOT, 'ios', 'Runner', 'Assets.xcassets',
                           'AppIcon.appiconset')
    for name, s in IOS.items():
        master.resize((s, s), Image.LANCZOS).save(
            os.path.join(ios_dir, name), optimize=True)
    print(f'iOS: {len(IOS)} dimensioni')


if __name__ == '__main__':
    main()
