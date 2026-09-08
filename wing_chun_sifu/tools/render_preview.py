#!/usr/bin/env python3
"""Renderer software di verifica per sifu.glb.

Non serve all'app: serve a dimostrare che il file esportato e' corretto.
Rilegge il GLB da zero - senza riusare le strutture del generatore -
ricostruisce la gerarchia dei nodi, campiona le animazioni, applica lo
skinning e rasterizza. Se le posizioni sono sbagliate, qui si vedono.

    python3 tools/render_preview.py                    # provino di tutte le lezioni
    python3 tools/render_preview.py tan_sau 1.3        # un fotogramma singolo
"""

import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "assets", "models", "sifu.glb")

COMP = {5120: "<i1", 5121: "<u1", 5122: "<i2", 5123: "<u2", 5125: "<u4", 5126: "<f4"}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


class Gltf:
    def __init__(self, path):
        with open(path, "rb") as f:
            magic, version, _ = struct.unpack("<III", f.read(12))
            assert magic == 0x46546C67, "non e' un file GLB"
            assert version == 2, f"versione glTF inattesa: {version}"
            self.json = None
            self.bin = None
            while True:
                head = f.read(8)
                if len(head) < 8:
                    break
                length, kind = struct.unpack("<II", head)
                data = f.read(length)
                if kind == 0x4E4F534A:
                    self.json = json.loads(data)
                elif kind == 0x004E4942:
                    self.bin = data
        assert self.json and self.bin is not None, "chunk mancanti"
        self.g = self.json

    def read(self, index):
        acc = self.g["accessors"][index]
        view = self.g["bufferViews"][acc["bufferView"]]
        offset = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        n = acc["count"] * NCOMP[acc["type"]]
        arr = np.frombuffer(self.bin, dtype=COMP[acc["componentType"]],
                            count=n, offset=offset)
        return arr.reshape(acc["count"], NCOMP[acc["type"]]).astype(np.float64)


def quat_matrix(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def node_matrix(node, override_r=None, override_t=None):
    t = np.array(override_t if override_t is not None
                 else node.get("translation", [0, 0, 0]), dtype=np.float64)
    r = np.array(override_r if override_r is not None
                 else node.get("rotation", [0, 0, 0, 1]), dtype=np.float64)
    m = np.eye(4)
    m[:3, :3] = quat_matrix(r)
    m[:3, 3] = t
    return m


def slerp(a, b, u):
    d = float(np.dot(a, b))
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        out = a + (b - a) * u
    else:
        th0 = math.acos(max(-1.0, min(1.0, d)))
        th = th0 * u
        out = a * (math.sin(th0 - th) / math.sin(th0)) + b * (math.sin(th) / math.sin(th0))
    return out / (np.linalg.norm(out) or 1.0)


def sample_animation(doc, anim, t):
    """Ritorna {indice_nodo: (rotazione, traslazione)} al tempo t."""
    out = {}
    for ch in anim["channels"]:
        s = anim["samplers"][ch["sampler"]]
        times = doc.read(s["input"])[:, 0]
        vals = doc.read(s["output"])
        if t <= times[0]:
            i, u = 0, 0.0
        elif t >= times[-1]:
            i, u = len(times) - 2, 1.0
        else:
            i = int(np.searchsorted(times, t) - 1)
            u = (t - times[i]) / (times[i + 1] - times[i])
        node = ch["target"]["node"]
        cur = out.setdefault(node, {})
        if ch["target"]["path"] == "rotation":
            cur["rotation"] = slerp(vals[i], vals[i + 1], u)
        elif ch["target"]["path"] == "translation":
            cur["translation"] = vals[i] * (1 - u) + vals[i + 1] * u
    return out


def skinned_positions(doc, anim_name=None, time=0.0):
    g = doc.g
    nodes = g["nodes"]
    over = {}
    if anim_name:
        anim = next(a for a in g["animations"] if a["name"] == anim_name)
        over = sample_animation(doc, anim, time)

    world = {}

    def walk(idx, parent):
        o = over.get(idx, {})
        local = node_matrix(nodes[idx], o.get("rotation"), o.get("translation"))
        world[idx] = parent @ local
        for c in nodes[idx].get("children", []):
            walk(c, world[idx])

    for idx in g["scenes"][0]["nodes"]:
        walk(idx, np.eye(4))

    mesh_node = next(i for i, n in enumerate(nodes) if "skin" in n)
    skin = g["skins"][nodes[mesh_node]["skin"]]
    ibm = doc.read(skin["inverseBindMatrices"]).reshape(-1, 4, 4)
    ibm = np.transpose(ibm, (0, 2, 1))          # da column-major a row-major

    mats = np.array([world[j] @ ibm[i] for i, j in enumerate(skin["joints"])])

    prim0 = g["meshes"][0]["primitives"][0]
    pos = doc.read(prim0["attributes"]["POSITION"])
    jnt = doc.read(prim0["attributes"]["JOINTS_0"]).astype(int)
    wgt = doc.read(prim0["attributes"]["WEIGHTS_0"])

    homo = np.concatenate([pos, np.ones((len(pos), 1))], axis=1)
    out = np.zeros((len(pos), 3))
    for k in range(4):
        m = mats[jnt[:, k]]
        contrib = np.einsum("nij,nj->ni", m, homo)[:, :3]
        out += contrib * wgt[:, k : k + 1]
    return out


def render(doc, positions, size=(560, 700), azim=-35.0, polar=80.0, dist=3.3,
           target=(0.0, 1.05, 0.05), bg=(14, 16, 22)):
    g = doc.g
    W, H = size
    img = np.zeros((H, W, 3), dtype=np.float64)
    img[:, :] = np.array(bg) / 255.0
    zbuf = np.full((H, W), 1e18)

    a, p = math.radians(azim), math.radians(polar)
    eye = np.array([dist * math.sin(p) * math.sin(a), dist * math.cos(p),
                    dist * math.sin(p) * math.cos(a)]) + np.array(target)
    fwd = np.array(target) - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)

    cam = (positions - eye) @ np.stack([right, up, fwd], axis=1)
    f = 1.0 / math.tan(math.radians(26.0))
    depth = np.maximum(cam[:, 2], 1e-6)
    sx = (cam[:, 0] / depth) * f * (H / 2) + W / 2
    sy = -(cam[:, 1] / depth) * f * (H / 2) + H / 2
    screen = np.stack([sx, sy], axis=1)

    light = np.array([0.45, 0.72, 0.53])
    light /= np.linalg.norm(light)
    fill = np.array([-0.6, 0.15, 0.4])
    fill /= np.linalg.norm(fill)

    faces = []
    for pi, prim in enumerate(g["meshes"][0]["primitives"]):
        color = np.array(g["materials"][prim["material"]]
                         ["pbrMetallicRoughness"]["baseColorFactor"][:3])
        idx = doc.read(prim["indices"])[:, 0].astype(int).reshape(-1, 3)
        for tri in idx:
            faces.append((tri, color))

    for tri, color in faces:
        v = positions[tri]
        n = np.cross(v[1] - v[0], v[2] - v[0])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        s = screen[tri]
        z = depth[tri]
        if np.any(z <= 0.01):
            continue
        # backface culling in spazio schermo
        area = ((s[1, 0] - s[0, 0]) * (s[2, 1] - s[0, 1])
                - (s[2, 0] - s[0, 0]) * (s[1, 1] - s[0, 1]))
        if area >= -1e-9:
            continue
        shade = (0.16 + 0.78 * max(0.0, float(np.dot(n, light)))
                 + 0.22 * max(0.0, float(np.dot(n, fill))))
        rim = math.pow(max(0.0, 1.0 - abs(float(np.dot(n, (eye - v[0]) /
              (np.linalg.norm(eye - v[0]) or 1.0))))), 3.0) * 0.35
        col = np.clip(color * shade + rim, 0, 1)

        x0 = max(0, int(np.floor(s[:, 0].min())))
        x1 = min(W - 1, int(np.ceil(s[:, 0].max())))
        y0 = max(0, int(np.floor(s[:, 1].min())))
        y1 = min(H - 1, int(np.ceil(s[:, 1].max())))
        if x1 < x0 or y1 < y0:
            continue
        xs = np.arange(x0, x1 + 1) + 0.5
        ys = np.arange(y0, y1 + 1) + 0.5
        gx, gy = np.meshgrid(xs, ys)
        w0 = ((s[1, 0] - s[0, 0]) * (gy - s[0, 1]) - (gx - s[0, 0]) * (s[1, 1] - s[0, 1]))
        w1 = ((s[2, 0] - s[1, 0]) * (gy - s[1, 1]) - (gx - s[1, 0]) * (s[2, 1] - s[1, 1]))
        w2 = ((s[0, 0] - s[2, 0]) * (gy - s[2, 1]) - (gx - s[2, 0]) * (s[0, 1] - s[2, 1]))
        inside = (w0 <= 0) & (w1 <= 0) & (w2 <= 0)
        if not inside.any():
            continue
        tot = w0 + w1 + w2
        tot[np.abs(tot) < 1e-12] = 1e-12
        zz = (w1 / tot) * z[0] + (w2 / tot) * z[1] + (w0 / tot) * z[2]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        mask = inside & (zz < sub)
        sub[mask] = zz[mask]
        img[y0:y1 + 1, x0:x1 + 1][mask] = col

    return Image.fromarray((np.clip(img, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8))


if __name__ == "__main__":
    doc = Gltf(MODEL)
    names = [a["name"] for a in doc.g["animations"]]
    print(f"{len(names)} animazioni nel file: {', '.join(names)}")
    if len(sys.argv) > 2:
        pos = skinned_positions(doc, sys.argv[1], float(sys.argv[2]))
        render(doc, pos).save("/tmp/frame.png")
        print("scritto /tmp/frame.png")
