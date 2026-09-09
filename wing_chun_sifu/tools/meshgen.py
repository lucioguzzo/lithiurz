"""Costruzione della mesh skinnata del SiFu.

La figura e' costruita per "loft": ogni parte del corpo e' una sequenza di
anelli (centro, raggi, pesi di skinning) che vengono cuciti fra loro. Ogni
anello dichiara a quali giunti appartiene e con che peso, per cui la
transizione dei pesi lungo l'arto e' esplicita e controllata: e' questo che
evita il collasso "a caramella" del gomito e della spalla.
"""

import math
import numpy as np

from rig import JOINT_INDEX, bind_world_positions

TAU = math.pi * 2.0


class MeshBuilder:
    def __init__(self):
        self.positions = []
        self.normals = []
        self.joints = []     # 4 indici per vertice
        self.weights = []    # 4 pesi per vertice
        self.tris = {}       # materiale -> lista di triangoli

    # ---------------------------------------------------------------
    def _add_vertex(self, pos, weights):
        self.positions.append([float(pos[0]), float(pos[1]), float(pos[2])])
        self.normals.append([0.0, 0.0, 0.0])
        pairs = sorted(weights, key=lambda kv: -kv[1])[:4]
        total = sum(w for _, w in pairs) or 1.0
        js = [0, 0, 0, 0]
        ws = [0.0, 0.0, 0.0, 0.0]
        for i, (name, w) in enumerate(pairs):
            js[i] = JOINT_INDEX[name]
            ws[i] = w / total
        self.joints.append(js)
        self.weights.append(ws)
        return len(self.positions) - 1

    def _tri(self, material, a, b, c):
        self.tris.setdefault(material, []).append((a, b, c))

    # ---------------------------------------------------------------
    def loft(self, rings, material, sides=14, cap_start=True, cap_end=True,
             twist=0.0):
        """Cuce una sequenza di anelli in un tubo chiuso.

        Ogni anello e' un dict con:
          center  (x, y, z)
          rx, rz  raggi lungo i due assi del piano dell'anello
          weights lista di (nome_giunto, peso)
          dir     direzione opzionale dell'asse del tubo in quel punto
        """
        centers = [np.array(r["center"], dtype=np.float64) for r in rings]
        n = len(rings)

        # direzione dell'asse in ogni anello
        dirs = []
        for i in range(n):
            if "dir" in rings[i]:
                d = np.array(rings[i]["dir"], dtype=np.float64)
            elif i == 0:
                d = centers[1] - centers[0]
            elif i == n - 1:
                d = centers[-1] - centers[-2]
            else:
                d = centers[i + 1] - centers[i - 1]
            norm = np.linalg.norm(d)
            dirs.append(d / norm if norm > 1e-9 else np.array([0.0, 1.0, 0.0]))

        # trasporto parallelo del frame per evitare torsioni
        seed = np.array([1.0, 0.0, 0.0])
        if abs(float(np.dot(seed, dirs[0]))) > 0.9:
            seed = np.array([0.0, 0.0, 1.0])
        u = seed - dirs[0] * float(np.dot(seed, dirs[0]))
        u /= np.linalg.norm(u)
        frames = []
        for i in range(n):
            if i > 0:
                u = u - dirs[i] * float(np.dot(u, dirs[i]))
                ln = np.linalg.norm(u)
                u = u / ln if ln > 1e-9 else np.array([1.0, 0.0, 0.0])
            v = np.cross(dirs[i], u)
            frames.append((u.copy(), v))

        ring_ids = []
        for i, r in enumerate(rings):
            u, v = frames[i]
            ids = []
            wfn = r.get("weights_fn")
            for s in range(sides):
                a = TAU * s / sides + twist * i
                p = centers[i] + u * (r["rx"] * math.cos(a)) + v * (r["rz"] * math.sin(a))
                ids.append(self._add_vertex(p, wfn(p) if wfn else r["weights"]))
            ring_ids.append(ids)

        # Avvolgimento antiorario visto da fuori, come richiede glTF: con
        # l'ordine opposto le normali puntano verso l'interno e la superficie
        # sparisce quando il motore scarta le facce posteriori.
        for i in range(n - 1):
            a_ring, b_ring = ring_ids[i], ring_ids[i + 1]
            for s in range(sides):
                t = (s + 1) % sides
                self._tri(material, a_ring[s], b_ring[t], b_ring[s])
                self._tri(material, a_ring[s], a_ring[t], b_ring[t])

        for do_cap, idx, flip in ((cap_start, 0, True), (cap_end, n - 1, False)):
            if not do_cap:
                continue
            r = rings[idx]
            ids = ring_ids[idx]
            if r.get("weights_fn"):
                # il centro del tappo prende la media dei pesi del bordo:
                # se prendesse i propri, il disco si strapperebbe dall'anello
                acc = {}
                for vid in ids:
                    for j, wv in zip(self.joints[vid], self.weights[vid]):
                        if wv > 0.0:
                            acc[j] = acc.get(j, 0.0) + wv
                total = sum(acc.values()) or 1.0
                inv = {v: k for k, v in JOINT_INDEX.items()}
                hub_w = [(inv[j], v / total) for j, v in acc.items()]
            else:
                hub_w = r["weights"]
            hub = self._add_vertex(r["center"], hub_w)
            for s in range(sides):
                t = (s + 1) % sides
                if flip:
                    self._tri(material, hub, ids[t], ids[s])
                else:
                    self._tri(material, hub, ids[s], ids[t])
        return ring_ids

    # ---------------------------------------------------------------
    def sphere(self, center, radius, weights, material, segments=16, rings=12,
               squash=(1.0, 1.0, 1.0)):
        c = np.array(center, dtype=np.float64)
        grid = []
        for i in range(rings + 1):
            phi = math.pi * i / rings
            row = []
            for j in range(segments):
                th = TAU * j / segments
                p = c + np.array([
                    radius * squash[0] * math.sin(phi) * math.cos(th),
                    radius * squash[1] * math.cos(phi),
                    radius * squash[2] * math.sin(phi) * math.sin(th),
                ])
                row.append(self._add_vertex(p, weights))
            grid.append(row)
        for i in range(rings):
            for j in range(segments):
                k = (j + 1) % segments
                a, b, cc, d = grid[i][j], grid[i][k], grid[i + 1][k], grid[i + 1][j]
                self._tri(material, a, b, cc)
                self._tri(material, a, cc, d)

    # ---------------------------------------------------------------
    def compute_normals(self):
        pos = np.array(self.positions, dtype=np.float64)
        nrm = np.zeros_like(pos)
        for tris in self.tris.values():
            for a, b, c in tris:
                fn = np.cross(pos[b] - pos[a], pos[c] - pos[a])
                nrm[a] += fn
                nrm[b] += fn
                nrm[c] += fn
        lengths = np.linalg.norm(nrm, axis=1, keepdims=True)
        lengths[lengths < 1e-12] = 1.0
        self.normals = (nrm / lengths).tolist()


def _w(*pairs):
    return list(pairs)


def build_sifu():
    """Costruisce il SiFu nella bind pose.

    L'abito segue il riferimento classico del maestro di Wing Chun: giacca
    bianca tradizionale con collo alla coreana e alamari sul davanti,
    pantaloni scuri ampi, scarpe basse nere.
    """
    P, _ = bind_world_positions()
    mb = MeshBuilder()

    def p(name):
        return np.array(P[name], dtype=np.float64)

    def lerp(a, b, t):
        return a + (b - a) * t

    hips, spine, chest, neck = p("root"), p("spine"), p("chest"), p("neck")
    head = p("head")

    # --- CORPO SOTTO L'ABITO -------------------------------------------
    # Un torso sottile che riempie la giacca. Si ferma sotto il colletto e
    # ha il colore della pelle: e' cio' che si intravede dentro il collo,
    # non un secondo strato bianco che sbuca dal davanti.
    mb.loft([
        {"center": hips + np.array([0, -0.14, 0]), "rx": 0.110, "rz": 0.086,
         "weights": _w(("root", 1.0)), "dir": (0, 1, 0)},
        {"center": hips, "rx": 0.124, "rz": 0.094, "weights": _w(("root", 1.0))},
        {"center": spine, "rx": 0.116, "rz": 0.086, "weights": _w(("spine", 1.0))},
        {"center": chest, "rx": 0.146, "rz": 0.098, "weights": _w(("chest", 1.0))},
        {"center": lerp(chest, neck, 0.72), "rx": 0.108, "rz": 0.086,
         "weights": _w(("chest", 1.0))},
        {"center": neck + np.array([0, 0.010, 0]), "rx": 0.066, "rz": 0.063,
         "weights": _w(("chest", 0.55), ("neck", 0.45)), "dir": (0, 1, 0)},
    ], "skin", sides=20)

    # --- GIACCA ---------------------------------------------------------
    # Giacca, collo, profilo e alamari condividono la stessa funzione di
    # pesatura. Non e' un dettaglio: se la striscia dei bottoni usasse pesi
    # anche solo leggermente diversi dalla stoffa su cui e' cucita, in
    # animazione si staccherebbe dalla giacca.
    def jacket_weights(v):
        y, x, z = float(v[1]), float(v[0]), float(v[2])

        def smooth(a, b, t):
            t = max(0.0, min(1.0, (t - a) / (b - a)))
            return t * t * (3.0 - 2.0 * t)

        to_spine = smooth(0.930, 1.075, y)
        to_chest = smooth(1.060, 1.235, y)
        to_neck = smooth(1.330, 1.430, y)
        w = {"root": (1.0 - to_spine),
             "spine": to_spine * (1.0 - to_chest),
             "chest": to_chest * (1.0 - to_neck),
             "neck": to_neck}
        # la falda anteriore segue in parte la coscia, cosi' si apre quando
        # la gamba sale invece di essere attraversata
        pull = 0.26 * smooth(0.0, 0.085, z) * (1.0 - smooth(0.760, 0.960, y))
        if pull > 0.001:
            side = smooth(-0.12, 0.12, x)
            for k in w:
                w[k] *= (1.0 - pull)
            w["thigh_L"] = pull * side
            w["thigh_R"] = pull * (1.0 - side)
        return [(k, v) for k, v in w.items() if v > 0.001]

    JW = {"weights_fn": jacket_weights, "weights": _w(("root", 1.0))}

    mb.loft([
        {"center": np.array([0.0, 0.760, 0.0]), "rx": 0.178, "rz": 0.126,
         "dir": (0, 1, 0), **JW},
        {"center": np.array([0.0, 0.790, 0.0]), "rx": 0.180, "rz": 0.128,
         "dir": (0, 1, 0), **JW},
        {"center": np.array([0.0, 0.860, 0.0]), "rx": 0.174, "rz": 0.124, **JW},
        {"center": np.array([0.0, 0.930, 0.0]), "rx": 0.166, "rz": 0.120, **JW},
        {"center": hips + np.array([0, 0.055, 0]), "rx": 0.158, "rz": 0.114, **JW},
        {"center": spine + np.array([0, 0.010, 0]), "rx": 0.156, "rz": 0.112, **JW},
        {"center": lerp(spine, chest, 0.55), "rx": 0.176, "rz": 0.120, **JW},
        {"center": chest, "rx": 0.196, "rz": 0.128, **JW},
        {"center": lerp(chest, neck, 0.45), "rx": 0.208, "rz": 0.132, **JW},
        {"center": lerp(chest, neck, 0.66), "rx": 0.206, "rz": 0.130, **JW},
        {"center": lerp(chest, neck, 0.84), "rx": 0.188, "rz": 0.120, **JW},
        {"center": lerp(chest, neck, 0.97), "rx": 0.134, "rz": 0.102, **JW},
        {"center": neck + np.array([0, 0.020, 0]), "rx": 0.088, "rz": 0.083,
         "dir": (0, 1, 0), **JW},
    ], "jacket", sides=22, cap_start=True, cap_end=False)

    # collo alla coreana
    mb.loft([
        {"center": neck + np.array([0, 0.016, 0]), "rx": 0.080, "rz": 0.075,
         "dir": (0, 1, 0), **JW},
        {"center": neck + np.array([0, 0.062, -0.004]), "rx": 0.078, "rz": 0.073,
         "dir": (0, 1, 0), **JW},
    ], "trim", sides=22, cap_start=False, cap_end=False)

    # profilo degli alamari: segue il rigonfiamento del petto
    def jacket_rz(y):
        prof = [(0.760, 0.126), (0.860, 0.124), (0.930, 0.120), (1.005, 0.114),
                (1.080, 0.112), (1.160, 0.118), (1.230, 0.124), (1.316, 0.126),
                (1.355, 0.123), (1.390, 0.114), (1.415, 0.098)]
        for (y0, r0), (y1, r1) in zip(prof, prof[1:]):
            if y <= y1:
                t = (y - y0) / (y1 - y0)
                return r0 + (r1 - r0) * max(0.0, min(1.0, t))
        return prof[-1][1]

    placket = []
    for i in range(11):
        t = i / 10.0
        y = 0.800 + t * (1.400 - 0.800)
        placket.append({"center": np.array([0.013, y, jacket_rz(y) - 0.009]),
                        "rx": 0.020, "rz": 0.013, "dir": (0, 1, 0.10), **JW})
    mb.loft(placket, "trim", sides=10)

    for i in range(5):
        y = 0.895 + i * 0.118
        c = np.array([0.013, y, jacket_rz(y) + 0.006])
        mb.sphere(c, 0.0155, jacket_weights(c), "buttons",
                  segments=10, rings=7, squash=(1.0, 1.35, 0.7))

    # --- COLLO E TESTA ---------------------------------------------------
    mb.loft([
        {"center": neck + np.array([0, -0.010, 0]), "rx": 0.061, "rz": 0.057,
         "weights": _w(("chest", 0.4), ("neck", 0.6)), "dir": (0, 1, 0)},
        {"center": neck + np.array([0, 0.062, 0]), "rx": 0.056, "rz": 0.054,
         "weights": _w(("neck", 0.7), ("head", 0.3)), "dir": (0, 1, 0)},
    ], "skin", sides=18, cap_start=False, cap_end=False)

    mb.sphere(head + np.array([0, 0.095, 0.004]), 0.108, _w(("head", 1.0)),
              "skin", segments=22, rings=16, squash=(0.95, 1.16, 1.02))
    mb.sphere(head + np.array([0, 0.121, -0.010]), 0.111, _w(("head", 1.0)),
              "hair", segments=22, rings=12, squash=(0.965, 0.99, 1.005))

    # lineamenti essenziali: bastano a dare una direzione allo sguardo,
    # e uno sguardo che segue il bersaglio e' esso stesso un insegnamento.
    hw = _w(("head", 1.0))
    face = head + np.array([0.0, 0.095, 0.0])
    for sx in (1.0, -1.0):
        mb.sphere(face + np.array([sx * 0.038, 0.019, 0.093]), 0.0145, hw,
                  "eyes", segments=10, rings=8, squash=(1.25, 0.85, 0.55))
        mb.sphere(face + np.array([sx * 0.040, 0.044, 0.090]), 0.0185, hw,
                  "hair", segments=10, rings=6, squash=(1.35, 0.32, 0.42))
    mb.loft([
        {"center": face + np.array([0.0, 0.020, 0.098]), "rx": 0.010, "rz": 0.010,
         "weights": hw, "dir": (0, -1, 0.25)},
        {"center": face + np.array([0.0, -0.012, 0.109]), "rx": 0.016, "rz": 0.014,
         "weights": hw, "dir": (0, -1, 0.25)},
        {"center": face + np.array([0.0, -0.024, 0.100]), "rx": 0.013, "rz": 0.010,
         "weights": hw, "dir": (0, -1, -0.3)},
    ], "skin", sides=10, cap_start=False)
    mb.loft([
        {"center": face + np.array([0.0, -0.049, 0.092]), "rx": 0.020, "rz": 0.007,
         "weights": hw, "dir": (0, 0, 1)},
        {"center": face + np.array([0.0, -0.050, 0.098]), "rx": 0.017, "rz": 0.006,
         "weights": hw, "dir": (0, 0, 1)},
    ], "mouth", sides=10)

    # --- MANICHE E BRACCIA ----------------------------------------------
    for side, sign in (("L", 1.0), ("R", -1.0)):
        sh = p(f"shoulder_{side}")
        ua = p(f"upperarm_{side}")
        fa = p(f"forearm_{side}")
        hd = p(f"hand_{side}")
        sj, uj, fj, hj = (f"shoulder_{side}", f"upperarm_{side}",
                          f"forearm_{side}", f"hand_{side}")

        # manica lunga e ampia, che si ferma al polso lasciando la mano nuda
        mb.loft([
            {"center": lerp(sh, ua, -0.85) + np.array([0, -0.030, 0]),
             "rx": 0.048, "rz": 0.050,
             "weights": _w(("chest", 0.88), (sj, 0.12)), "dir": tuple(ua - sh)},
            {"center": lerp(sh, ua, 0.45) + np.array([0, -0.030, 0]),
             "rx": 0.064, "rz": 0.066,
             "weights": _w(("chest", 0.22), (sj, 0.78)), "dir": tuple(ua - sh)},
            {"center": ua + np.array([0, -0.046, 0]), "rx": 0.070, "rz": 0.071,
             "weights": _w((sj, 0.30), (uj, 0.70))},
            {"center": lerp(ua, fa, 0.35), "rx": 0.066, "rz": 0.065,
             "weights": _w((uj, 1.0))},
            {"center": lerp(ua, fa, 0.80), "rx": 0.061, "rz": 0.060,
             "weights": _w((uj, 0.85), (fj, 0.15))},
            {"center": fa, "rx": 0.060, "rz": 0.059,
             "weights": _w((uj, 0.40), (fj, 0.60))},
            {"center": lerp(fa, hd, 0.30), "rx": 0.058, "rz": 0.056,
             "weights": _w((fj, 1.0))},
            {"center": lerp(fa, hd, 0.78), "rx": 0.050, "rz": 0.048,
             "weights": _w((fj, 1.0))},
            {"center": lerp(fa, hd, 0.90), "rx": 0.047, "rz": 0.045,
             "weights": _w((fj, 0.9), (hj, 0.1))},
        ], "jacket", sides=16, cap_start=True, cap_end=False)

        # polso e mano scoperti
        mb.loft([
            {"center": lerp(fa, hd, 0.86), "rx": 0.033, "rz": 0.032,
             "weights": _w((fj, 1.0)), "dir": tuple(hd - fa)},
            {"center": lerp(fa, hd, 0.97), "rx": 0.031, "rz": 0.030,
             "weights": _w((fj, 0.8), (hj, 0.2))},
        ], "skin", sides=12, cap_start=False, cap_end=False)

        _build_hand(mb, P, side, sign)

    # --- PANTALONI E SCARPE ----------------------------------------------
    for side in ("L", "R"):
        th = p(f"thigh_{side}")
        sh = p(f"shin_{side}")
        ft = p(f"foot_{side}")
        to = p(f"toe_{side}")
        tj, sj, fj, oj = (f"thigh_{side}", f"shin_{side}",
                          f"foot_{side}", f"toe_{side}")
        mb.loft([
            {"center": th + np.array([0, 0.110, 0]), "rx": 0.108, "rz": 0.108,
             "weights": _w(("root", 0.8), (tj, 0.2)), "dir": (0, -1, 0)},
            {"center": th, "rx": 0.104, "rz": 0.105, "weights": _w((tj, 1.0))},
            {"center": lerp(th, sh, 0.45), "rx": 0.094, "rz": 0.096,
             "weights": _w((tj, 1.0))},
            {"center": lerp(th, sh, 0.88), "rx": 0.081, "rz": 0.083,
             "weights": _w((tj, 0.75), (sj, 0.25))},
            {"center": sh, "rx": 0.079, "rz": 0.081,
             "weights": _w((tj, 0.4), (sj, 0.6))},
            {"center": lerp(sh, ft, 0.30), "rx": 0.079, "rz": 0.081,
             "weights": _w((sj, 1.0))},
            {"center": lerp(sh, ft, 0.72), "rx": 0.070, "rz": 0.072,
             "weights": _w((sj, 1.0))},
            {"center": lerp(sh, ft, 0.90), "rx": 0.062, "rz": 0.064,
             "weights": _w((sj, 0.85), (fj, 0.15))},
        ], "trousers", sides=16, cap_start=True, cap_end=True)

        mb.loft([
            {"center": ft + np.array([0, 0.012, -0.012]), "rx": 0.043, "rz": 0.047,
             "weights": _w((sj, 0.4), (fj, 0.6)), "dir": (0, 0, 1)},
            {"center": ft + np.array([0, -0.016, 0.042]), "rx": 0.045, "rz": 0.033,
             "weights": _w((fj, 1.0)), "dir": (0, 0, 1)},
            {"center": to + np.array([0, 0.010, -0.010]), "rx": 0.046, "rz": 0.028,
             "weights": _w((fj, 0.6), (oj, 0.4)), "dir": (0, 0, 1)},
            {"center": to + np.array([0, 0.008, 0.050]), "rx": 0.036, "rz": 0.023,
             "weights": _w((oj, 1.0)), "dir": (0, 0, 1)},
        ], "shoes", sides=14)

    mb.compute_normals()
    return mb


def _build_hand(mb, P, side, sign):
    """Palmo e cinque dita, ciascuna legata alle proprie due falangi.

    Le dita non sono decorazione: reggono le forme di mano che distinguono
    una tecnica dall'altra. Ogni falange e' pesata sul proprio giunto, con
    una fascia di transizione sulla nocca perche' la piega non si spezzi.
    """
    from rig import _FINGER_LEN, FINGERS

    def p(name):
        return np.array(P[name], dtype=np.float64)

    hd, fa = p(f"hand_{side}"), p(f"forearm_{side}")
    hj, fj = f"hand_{side}", f"forearm_{side}"
    palm_dir = (hd - fa) / np.linalg.norm(hd - fa)
    across = np.cross(np.array([0.0, 0.0, 1.0]), palm_dir)
    n = np.linalg.norm(across)
    across = across / n if n > 1e-6 else np.array([1.0, 0.0, 0.0])

    # palmo: sezione ovale schiacciata, piu' largo verso le nocche
    mb.loft([
        {"center": hd, "rx": 0.034, "rz": 0.030,
         "weights": _w((fj, 0.35), (hj, 0.65)), "dir": tuple(palm_dir)},
        {"center": hd + palm_dir * 0.038, "rx": 0.044, "rz": 0.023,
         "weights": _w((hj, 1.0)), "dir": tuple(palm_dir)},
        {"center": hd + palm_dir * 0.078, "rx": 0.047, "rz": 0.021,
         "weights": _w((hj, 1.0)), "dir": tuple(palm_dir)},
        {"center": hd + palm_dir * 0.098, "rx": 0.046, "rz": 0.020,
         "weights": _w((hj, 1.0)), "dir": tuple(palm_dir)},
    ], "skin", sides=16, cap_start=True, cap_end=True)

    RADIUS = {"idx": 0.0115, "mid": 0.0120, "rng": 0.0110,
              "pnk": 0.0098, "thb": 0.0140}
    for f in FINGERS:
        j1, j2 = f"{f}1_{side}", f"{f}2_{side}"
        a, b = p(j1), p(j2)
        l2 = _FINGER_LEN[f][1]
        d = b - a
        d /= np.linalg.norm(d) or 1.0
        tip = b + d * l2
        r = RADIUS[f]
        mb.loft([
            {"center": a - d * 0.012, "rx": r * 1.10, "rz": r * 1.06,
             "weights": _w((hj, 0.65), (j1, 0.35)), "dir": tuple(d)},
            {"center": a + d * 0.010, "rx": r, "rz": r * 0.97,
             "weights": _w((hj, 0.15), (j1, 0.85)), "dir": tuple(d)},
            {"center": b - d * 0.009, "rx": r * 0.92, "rz": r * 0.90,
             "weights": _w((j1, 0.80), (j2, 0.20))},
            {"center": b + d * 0.008, "rx": r * 0.88, "rz": r * 0.86,
             "weights": _w((j1, 0.20), (j2, 0.80))},
            {"center": tip - d * 0.010, "rx": r * 0.80, "rz": r * 0.78,
             "weights": _w((j2, 1.0))},
            {"center": tip, "rx": r * 0.52, "rz": r * 0.50,
             "weights": _w((j2, 1.0))},
        ], "skin", sides=9, cap_start=False, cap_end=True)
