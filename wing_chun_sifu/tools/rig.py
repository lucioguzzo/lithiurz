"""Scheletro e posa di riposo del modello 3D del SiFu.

Convenzioni (glTF 2.0):
  - unita': metri. Il SiFu e' alto ~1.75 m.
  - Y verso l'alto, X a destra del SiFu, Z verso lo spettatore.
  - Il SiFu guarda verso +Z, cioe' verso l'allievo.

Ogni giunto e' definito dalla sua traslazione *locale* rispetto al padre
nella posa di riposo (bind pose). La posa di riposo e' una A-pose naturale:
braccia lungo i fianchi leggermente aperte, che riduce gli artefatti di
skinning sulle spalle nelle posizioni tipiche del Wing Chun.
"""

import math

# --- proporzioni (metri) ------------------------------------------------
HEIGHT = 1.75
HIP_Y = 0.95          # altezza del bacino da terra
SHOULDER_W = 0.214    # semi-larghezza spalle (0,43 m totali)
HIP_W = 0.095         # semi-larghezza anche

# Un giunto: (nome, padre, traslazione locale)
JOINTS = [
    ("root",        None,        (0.0, HIP_Y, 0.0)),
    ("spine",       "root",      (0.0, 0.120, 0.0)),
    ("chest",       "spine",     (0.0, 0.160, 0.0)),
    ("neck",        "chest",     (0.0, 0.190, 0.0)),
    ("head",        "neck",      (0.0, 0.090, 0.0)),

    ("shoulder_L",  "chest",     (SHOULDER_W * 0.42, 0.125, 0.0)),
    ("upperarm_L",  "shoulder_L", (SHOULDER_W * 0.58, 0.0, 0.0)),
    ("forearm_L",   "upperarm_L", (0.0, -0.285, 0.0)),
    ("hand_L",      "forearm_L",  (0.0, -0.255, 0.0)),

    ("shoulder_R",  "chest",     (-SHOULDER_W * 0.42, 0.125, 0.0)),
    ("upperarm_R",  "shoulder_R", (-SHOULDER_W * 0.58, 0.0, 0.0)),
    ("forearm_R",   "upperarm_R", (0.0, -0.285, 0.0)),
    ("hand_R",      "forearm_R",  (0.0, -0.255, 0.0)),

    ("thigh_L",     "root",      (HIP_W, -0.055, 0.0)),
    ("shin_L",      "thigh_L",   (0.0, -0.425, 0.0)),
    ("foot_L",      "shin_L",    (0.0, -0.415, 0.0)),
    ("toe_L",       "foot_L",    (0.0, -0.065, 0.115)),

    ("thigh_R",     "root",      (-HIP_W, -0.055, 0.0)),
    ("shin_R",      "thigh_R",   (0.0, -0.425, 0.0)),
    ("foot_R",      "shin_R",    (0.0, -0.415, 0.0)),
    ("toe_R",       "foot_R",    (0.0, -0.065, 0.115)),
]

# --- dita ---------------------------------------------------------------
# Nel Wing Chun la forma della mano fa parte della tecnica: un Biu Tze e'
# definito dalle dita tese, un Chung Kuen dal pugno chiuso, un Fook Sau dalla
# mano rilassata. Senza falangi articolate tutte le tecniche avrebbero la
# stessa mano, e sarebbe la piu' visibile delle imprecisioni.
FINGERS = ("idx", "mid", "rng", "pnk", "thb")

# radice di ogni dito nel sistema locale della mano
# (la mano punta lungo -Y, il palmo guarda +Z)
_FINGER_ROOT = {
    "idx": (0.034, -0.092, 0.004),
    "mid": (0.011, -0.099, 0.002),
    "rng": (-0.012, -0.095, -0.002),
    "pnk": (-0.033, -0.084, -0.005),
    "thb": (0.034, -0.028, 0.016),
}
_FINGER_LEN = {
    "idx": (0.042, 0.034), "mid": (0.046, 0.036),
    "rng": (0.042, 0.033), "pnk": (0.034, 0.027),
    "thb": (0.036, 0.030),
}


def _finger_joints():
    out = []
    for side, sign in (("L", 1.0), ("R", -1.0)):
        for f in FINGERS:
            x, y, z = _FINGER_ROOT[f]
            l1, _ = _FINGER_LEN[f]
            out.append((f"{f}1_{side}", f"hand_{side}", (x * sign, y, z)))
            if f == "thb":
                # il pollice si oppone: esce in diagonale rispetto alle dita
                out.append((f"{f}2_{side}", f"{f}1_{side}",
                            (0.010 * sign, -l1 * 0.92, 0.012)))
            else:
                out.append((f"{f}2_{side}", f"{f}1_{side}", (0.0, -l1, 0.0)))
    return out


JOINTS = JOINTS + _finger_joints()

JOINT_NAMES = [j[0] for j in JOINTS]
JOINT_INDEX = {n: i for i, n in enumerate(JOINT_NAMES)}
JOINT_PARENT = {j[0]: j[1] for j in JOINTS}
JOINT_LOCAL_T = {j[0]: j[2] for j in JOINTS}

# Rotazioni locali della bind pose (gradi, ordine XYZ intrinseco).
# A riposo le braccia gia' pendono lungo -Y: qui le apriamo di ~12 gradi
# ottenendo la classica A-pose, che dimezza gli artefatti di skinning
# sulla spalla rispetto a una T-pose quando il braccio sale in guardia.
BIND_ROT = {
    "upperarm_L": (0.0, 0.0, 12.0),
    "upperarm_R": (0.0, 0.0, -12.0),
}


def joint_children(name):
    return [j[0] for j in JOINTS if j[1] == name]


def bind_world_positions():
    """Posizione nel mondo di ogni giunto nella bind pose."""
    import numpy as np
    out = {}
    mats = {}
    for name, parent, t in JOINTS:
        rx, ry, rz = BIND_ROT.get(name, (0.0, 0.0, 0.0))
        local = trs_matrix(t, euler_to_quat(rx, ry, rz))
        mats[name] = local if parent is None else mats[parent] @ local
        out[name] = tuple(mats[name][:3, 3])
    return out, mats


# --- utilita' matematiche ----------------------------------------------
def euler_to_quat(rx, ry, rz):
    """Euler XYZ in gradi -> quaternione (x, y, z, w)."""
    hx, hy, hz = (math.radians(a) * 0.5 for a in (rx, ry, rz))
    cx, sx = math.cos(hx), math.sin(hx)
    cy, sy = math.cos(hy), math.sin(hy)
    cz, sz = math.cos(hz), math.sin(hz)
    return (
        sx * cy * cz + cx * sy * sz,
        cx * sy * cz - sx * cy * sz,
        cx * cy * sz + sx * sy * cz,
        cx * cy * cz - sx * sy * sz,
    )


def quat_to_matrix(q):
    import numpy as np
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0.0],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0.0],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ], dtype=np.float64)


def trs_matrix(t, q=(0.0, 0.0, 0.0, 1.0)):
    import numpy as np
    m = quat_to_matrix(q)
    m[0, 3], m[1, 3], m[2, 3] = t
    return m
