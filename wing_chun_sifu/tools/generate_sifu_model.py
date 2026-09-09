#!/usr/bin/env python3
"""Genera il modello 3D animato del SiFu per la sezione "3D SiFu - Lessons".

    python3 tools/generate_sifu_model.py

Produce:
  assets/models/sifu.glb        modello skinnato con tutte le lezioni animate
  assets/data/lessons.json      indice delle lezioni letto dall'app

Il modello e' un mannequin tecnico: la priorita' e' la leggibilita' del gesto
- angoli dei giunti, altezza del gomito, posizione della mano sulla linea
centrale - non il realismo della pelle. Le posizioni non sono disegnate a
mano ma risolte con cinematica inversa a partire da target anatomici, ed
esiste un test che le verifica (test/test_poses.py).
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np

import animations
import gltf_export
import meshgen

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(ROOT, "assets", "models", "sifu.glb")
LESSONS_PATH = os.path.join(ROOT, "assets", "data", "lessons.json")

# Come inquadrare ogni lezione. theta = azimut, phi = angolo polare,
# radius = distanza in metri; target = punto guardato dalla camera.
#
# I raggi sono misurati, non stimati: model-viewer usa un campo visivo
# verticale di 30 gradi, non i 45 che verrebbe naturale supporre, e con i
# valori calcolati per 45 gradi la figura veniva tagliata sopra e sotto.
# A 3,8 m il SiFu occupa circa l'85% dell'altezza del riquadro.
CAMERAS = {
    "front":         {"theta":   0.0, "phi": 82.0, "radius": 3.90,
                      "targetY": 0.92, "targetZ": 0.05},
    "three_quarter": {"theta": -38.0, "phi": 79.0, "radius": 3.80,
                      "targetY": 0.95, "targetZ": 0.08},
    "side":          {"theta": -86.0, "phi": 82.0, "radius": 3.90,
                      "targetY": 0.92, "targetZ": 0.05},
    "top_front":     {"theta":  -8.0, "phi": 58.0, "radius": 3.90,
                      "targetY": 0.86, "targetZ": 0.0},
    # l'unica inquadratura volutamente stretta: taglia le gambe per mostrare
    # il lavoro delle mani, che e' il soggetto della lezione Huen Sau
    "close":         {"theta": -30.0, "phi": 76.0, "radius": 2.60,
                      "targetY": 1.20, "targetZ": 0.16},
}


def main():
    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(LESSONS_PATH), exist_ok=True)

    print("Costruzione della mesh...")
    mesh = meshgen.build_sifu()
    tri_count = sum(len(t) for t in mesh.tris.values())
    print(f"  {len(mesh.positions)} vertici, {tri_count} triangoli")

    ground = -float(np.array(mesh.positions)[:, 1].min())
    print(f"  offset a terra: {ground:+.3f} m")

    print("Costruzione delle lezioni animate...")
    clips = animations.build_clips()
    print(f"  {len(clips)} clip")

    total, js, bs = gltf_export.export(mesh, clips, MODEL_PATH, ground_offset=ground)
    print(f"Scritto {MODEL_PATH}")
    print(f"  {total/1024:.0f} KB totali (JSON {js/1024:.0f} KB, binario {bs/1024:.0f} KB)")

    index = [{
        "id": c.name,
        "title": c.title,
        "description": c.description,
        "duration": round(c.duration, 2),
        "camera": CAMERAS.get(c.camera, CAMERAS["front"]),
    } for c in clips]
    with open(LESSONS_PATH, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
    print(f"Scritto {LESSONS_PATH} ({len(index)} lezioni)")


if __name__ == "__main__":
    main()
