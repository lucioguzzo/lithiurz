"""Esportatore glTF 2.0 binario (.glb) per il modello del SiFu.

Scrive un singolo file autonomo con mesh skinnata, scheletro, matrici di
bind inverse e tutte le animazioni con nome. Il formato e' quello standard:
il modello puo' essere aperto in Blender, in three.js o sostituito in futuro
con un personaggio realizzato da un artista senza toccare il codice
dell'app, purche' conservi i nomi delle animazioni.
"""

import json
import struct

import numpy as np

from rig import JOINTS, JOINT_NAMES, JOINT_INDEX, JOINT_LOCAL_T, BIND_ROT, euler_to_quat
from rig import bind_world_positions

COMP_FLOAT = 5126
COMP_USHORT = 5123
COMP_UINT = 5125

MATERIALS = {
    "skin":     {"color": [0.836, 0.650, 0.523, 1.0], "rough": 0.72, "metal": 0.0},
    "jacket":   {"color": [0.902, 0.893, 0.868, 1.0], "rough": 0.52, "metal": 0.0},
    "trim":     {"color": [0.804, 0.792, 0.762, 1.0], "rough": 0.46, "metal": 0.0},
    "buttons":  {"color": [0.168, 0.160, 0.152, 1.0], "rough": 0.38, "metal": 0.0},
    "trousers": {"color": [0.176, 0.176, 0.196, 1.0], "rough": 0.68, "metal": 0.0},
    "shoes":    {"color": [0.070, 0.070, 0.078, 1.0], "rough": 0.44, "metal": 0.0},
    "hair":     {"color": [0.058, 0.054, 0.062, 1.0], "rough": 0.40, "metal": 0.0},
    "eyes":     {"color": [0.075, 0.062, 0.055, 1.0], "rough": 0.18, "metal": 0.0},
    "mouth":    {"color": [0.612, 0.400, 0.353, 1.0], "rough": 0.55, "metal": 0.0},
}
MATERIAL_ORDER = ["skin", "jacket", "trim", "buttons", "trousers", "shoes",
                  "hair", "eyes", "mouth"]


class GlbWriter:
    def __init__(self):
        self.blob = bytearray()
        self.buffer_views = []
        self.accessors = []

    def _view(self, data: bytes, target=None, stride=None):
        while len(self.blob) % 4:
            self.blob.append(0)
        offset = len(self.blob)
        self.blob.extend(data)
        view = {"buffer": 0, "byteOffset": offset, "byteLength": len(data)}
        if target is not None:
            view["target"] = target
        if stride is not None:
            view["byteStride"] = stride
        self.buffer_views.append(view)
        return len(self.buffer_views) - 1

    def accessor(self, array, comp_type, type_str, target=None, minmax=False):
        arr = np.asarray(array)
        dtype = {COMP_FLOAT: "<f4", COMP_USHORT: "<u2", COMP_UINT: "<u4"}[comp_type]
        flat = arr.astype(dtype).tobytes()
        count = arr.shape[0]
        view = self._view(flat, target=target)
        acc = {"bufferView": view, "componentType": comp_type,
               "count": int(count), "type": type_str}
        if minmax:
            acc["min"] = [float(v) for v in np.min(arr, axis=0).ravel()]
            acc["max"] = [float(v) for v in np.max(arr, axis=0).ravel()]
        self.accessors.append(acc)
        return len(self.accessors) - 1


def _bind_matrices():
    """Matrici globali della bind pose e loro inverse."""
    mats = {}
    for name, parent, t in JOINTS:
        rx, ry, rz = BIND_ROT.get(name, (0.0, 0.0, 0.0))
        q = euler_to_quat(rx, ry, rz)
        x, y, z, w = q
        r = np.array([
            [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), t[0]],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), t[1]],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), t[2]],
            [0.0, 0.0, 0.0, 1.0],
        ])
        mats[name] = r if parent is None else mats[parent] @ r
    return mats


def export(mesh, clips, path, ground_offset=0.0):
    w = GlbWriter()

    # --- geometria -----------------------------------------------------
    positions = np.array(mesh.positions, dtype=np.float32)
    normals = np.array(mesh.normals, dtype=np.float32)
    joints = np.array(mesh.joints, dtype=np.uint16)
    weights = np.array(mesh.weights, dtype=np.float32)

    acc_pos = w.accessor(positions, COMP_FLOAT, "VEC3", target=34962, minmax=True)
    acc_nrm = w.accessor(normals, COMP_FLOAT, "VEC3", target=34962)
    acc_jnt = w.accessor(joints, COMP_USHORT, "VEC4", target=34962)
    acc_wgt = w.accessor(weights, COMP_FLOAT, "VEC4", target=34962)

    primitives = []
    for mat_name in MATERIAL_ORDER:
        tris = mesh.tris.get(mat_name)
        if not tris:
            continue
        idx = np.array(tris, dtype=np.uint32).reshape(-1)
        comp = COMP_USHORT if len(positions) < 65535 else COMP_UINT
        acc_idx = w.accessor(idx, comp, "SCALAR", target=34963)
        primitives.append({
            "attributes": {"POSITION": acc_pos, "NORMAL": acc_nrm,
                           "JOINTS_0": acc_jnt, "WEIGHTS_0": acc_wgt},
            "indices": acc_idx,
            "material": MATERIAL_ORDER.index(mat_name),
        })

    # --- scheletro -----------------------------------------------------
    bind = _bind_matrices()
    ibm = np.zeros((len(JOINTS), 16), dtype=np.float32)
    for i, name in enumerate(JOINT_NAMES):
        inv = np.linalg.inv(bind[name])
        ibm[i] = inv.T.reshape(-1)          # glTF: column-major
    acc_ibm = w.accessor(ibm, COMP_FLOAT, "MAT4")

    # nodi: 0 = mesh, 1 = armatura, 2.. = giunti
    NODE_MESH, NODE_ARM, JOINT_BASE = 0, 1, 2
    nodes = [
        {"name": "SiFu", "mesh": 0, "skin": 0},
        {"name": "Armature", "translation": [0.0, float(ground_offset), 0.0],
         "children": [JOINT_BASE + JOINT_INDEX["root"]]},
    ]
    for name, parent, t in JOINTS:
        node = {"name": name, "translation": [float(v) for v in t]}
        rx, ry, rz = BIND_ROT.get(name, (0.0, 0.0, 0.0))
        if (rx, ry, rz) != (0.0, 0.0, 0.0):
            node["rotation"] = [float(v) for v in euler_to_quat(rx, ry, rz)]
        kids = [JOINT_BASE + JOINT_INDEX[c[0]] for c in JOINTS if c[1] == name]
        if kids:
            node["children"] = kids
        nodes.append(node)

    skin = {"inverseBindMatrices": acc_ibm,
            "skeleton": JOINT_BASE + JOINT_INDEX["root"],
            "joints": [JOINT_BASE + JOINT_INDEX[n] for n in JOINT_NAMES]}

    # --- animazioni ----------------------------------------------------
    animations = []
    for clip in clips:
        times, channels, root_translations = clip.bake()
        acc_time = w.accessor(np.array(times, dtype=np.float32).reshape(-1, 1),
                              COMP_FLOAT, "SCALAR", minmax=True)
        samplers, chans = [], []
        for joint, (_, quats) in channels.items():
            arr = np.array([q for q in quats], dtype=np.float32)
            acc_rot = w.accessor(arr, COMP_FLOAT, "VEC4")
            samplers.append({"input": acc_time, "output": acc_rot,
                             "interpolation": "LINEAR"})
            chans.append({"sampler": len(samplers) - 1,
                          "target": {"node": JOINT_BASE + JOINT_INDEX[joint],
                                     "path": "rotation"}})
        if root_translations is not None:
            base = np.array(JOINT_LOCAL_T["root"], dtype=np.float32)
            arr = np.array([base + o for o in root_translations], dtype=np.float32)
            acc_tr = w.accessor(arr, COMP_FLOAT, "VEC3")
            samplers.append({"input": acc_time, "output": acc_tr,
                             "interpolation": "LINEAR"})
            chans.append({"sampler": len(samplers) - 1,
                          "target": {"node": JOINT_BASE + JOINT_INDEX["root"],
                                     "path": "translation"}})
        animations.append({"name": clip.name, "samplers": samplers,
                           "channels": chans})

    gltf = {
        "asset": {"version": "2.0",
                  "generator": "Wing Chun SiFu Online - generatore modello 3D"},
        "scene": 0,
        "scenes": [{"nodes": [NODE_MESH, NODE_ARM]}],
        "nodes": nodes,
        "meshes": [{"name": "SiFu", "primitives": primitives}],
        "skins": [skin],
        "materials": [
            {"name": m,
             "pbrMetallicRoughness": {
                 "baseColorFactor": MATERIALS[m]["color"],
                 "metallicFactor": MATERIALS[m]["metal"],
                 "roughnessFactor": MATERIALS[m]["rough"]},
             "doubleSided": False}
            for m in MATERIAL_ORDER if mesh.tris.get(m)
        ],
        "animations": animations,
        "accessors": w.accessors,
        "bufferViews": w.buffer_views,
        "buffers": [{"byteLength": len(w.blob)}],
    }

    # --- confezionamento GLB -------------------------------------------
    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    while len(json_bytes) % 4:
        json_bytes += b" "
    bin_bytes = bytes(w.blob)
    while len(bin_bytes) % 4:
        bin_bytes += b"\0"

    total = 12 + 8 + len(json_bytes) + 8 + len(bin_bytes)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(json_bytes), 0x4E4F534A))
        f.write(json_bytes)
        f.write(struct.pack("<II", len(bin_bytes), 0x004E4942))
        f.write(bin_bytes)
    return total, len(json_bytes), len(bin_bytes)
