"""Le lezioni animate del SiFu.

Ogni clip e' una sequenza di posizioni chiave con un tempo e una curva di
accelerazione. Le curve non sono decorative: nel Wing Chun il *tempo* di una
tecnica e' parte della tecnica. Una deviazione cede e accompagna (ease in-out),
un colpo esplode e si ferma (ease out esponenziale), una girata parte dal
centro e ci torna.
"""

import math
import numpy as np

import poses
from kinematics import default_pose, qmul, slerp
from rig import JOINT_NAMES, euler_to_quat

FPS = 24


# --- curve di accelerazione --------------------------------------------
def _linear(t):
    return t


def _inout(t):
    return t * t * (3.0 - 2.0 * t)


def _snap(t):
    """Esplosivo: quasi tutto il movimento nel primo terzo, poi si ferma."""
    return 1.0 - math.pow(1.0 - t, 4.0)


def _hold(t):
    return 0.0


def _sink(t):
    """Affonda e si assesta: usato per scendere nella posizione."""
    return 1.0 - math.pow(1.0 - t, 2.5)


EASES = {"linear": _linear, "inout": _inout, "snap": _snap,
         "hold": _hold, "sink": _sink}



# Tolleranza della semplificazione delle curve, in gradi. Sotto il decimo di
# grado nessun occhio distingue un fotogramma interpolato da uno campionato,
# ma i dati si dimezzano.
SIMPLIFY_TOLERANCE_DEG = 0.12


def _simplify_channel(times, quats, tol_deg=SIMPLIFY_TOLERANCE_DEG):
    """Toglie i campioni che l'interpolazione lineare gia' ricostruisce.

    Campionare a 24 fotogrammi al secondo produce moltissimi valori che
    stanno gia' sulla retta fra il precedente e il successivo. Tenerli
    triplica la dimensione del file senza cambiare di un grado il risultato.
    """
    n = len(quats)
    if n <= 2:
        return times, quats
    cos_tol = math.cos(math.radians(tol_deg) * 0.5)
    keep = [False] * n
    keep[0] = keep[-1] = True
    stack = [(0, n - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        worst, worst_i = 1.0, -1
        qa, qb = quats[a], quats[b]
        span = times[b] - times[a]
        for i in range(a + 1, b):
            u = 0.0 if span <= 0 else (times[i] - times[a]) / span
            approx = slerp(qa, qb, u)
            d = abs(float(np.dot(approx, quats[i])))
            if d < worst:
                worst, worst_i = d, i
        if worst_i >= 0 and worst < cos_tol:
            keep[worst_i] = True
            stack.append((a, worst_i))
            stack.append((worst_i, b))
    idx = [i for i in range(n) if keep[i]]
    return [times[i] for i in idx], [quats[i] for i in idx]


def blend(a, b, u):
    """Posa intermedia fra due pose.

    Con `u` fuori da [0, 1] estrapola, e questo e' il punto: `u = -0.1` da'
    l'anticipo (il piccolo contromovimento che precede ogni gesto reale),
    `u = 1.05` la sovracorsa che precede l'assestamento. Sono i due dettagli
    che separano un movimento vivo da un'interpolazione fra due fotogrammi.
    """
    a = a.build() if hasattr(a, "build") else a
    b = b.build() if hasattr(b, "build") else b
    out = {}
    for j in JOINT_NAMES:
        out[j] = slerp(a[j], b[j], u)
    oa = a.get("_root_offset", np.zeros(3))
    ob = b.get("_root_offset", np.zeros(3))
    out["_root_offset"] = oa + (ob - oa) * u
    return out


def breath_offset(t, amount=1.0):
    """Respiro: due gradi scarsi di torace e quattro millimetri di bacino.

    Serve perche' nessun fotogramma sia mai identico al precedente. Una
    figura perfettamente immobile fra due tecniche legge come un manichino,
    anche quando la posizione e' corretta.
    """
    phase = math.sin(t * 2.0 * math.pi / 4.2)
    slow = math.sin(t * 2.0 * math.pi / 6.7 + 1.1)
    return (
        amount * 0.9 * phase,          # gradi di apertura del torace
        amount * 0.0045 * phase,       # metri di sollevamento del bacino
        amount * 0.5 * slow,           # gradi di oscillazione laterale
    )


class Clip:
    """Una lezione animata: posizioni chiave nel tempo."""

    def __init__(self, name, title, description, camera="front", breath=1.0):
        self.name = name
        self.title = title
        self.description = description
        self.camera = camera
        self.breath = breath    # 0 = trattenuto (colpi), 1 = respiro pieno
        self.keys = []          # (tempo, posa, ease del segmento che arriva qui)

    def key(self, t, pose, ease="inout"):
        built = pose.build() if hasattr(pose, "build") else pose
        self.keys.append((float(t), built, ease))
        return self

    @property
    def duration(self):
        return self.keys[-1][0] if self.keys else 0.0

    def bake(self, fps=FPS):
        """Campiona la clip restituendo {giunto: (tempi, quaternioni)}."""
        assert len(self.keys) >= 2, f"clip {self.name}: servono almeno 2 chiavi"
        n = max(2, int(round(self.duration * fps)) + 1)
        times = [i / fps for i in range(n)]
        if times[-1] < self.duration - 1e-6:
            times.append(self.duration)

        rot = {j: [] for j in JOINT_NAMES}
        root_t = []
        for t in times:
            i = 0
            while i < len(self.keys) - 2 and self.keys[i + 1][0] <= t:
                i += 1
            t0, p0, _ = self.keys[i]
            t1, p1, ease = self.keys[i + 1]
            span = max(1e-6, t1 - t0)
            u = min(1.0, max(0.0, (t - t0) / span))
            u = EASES[ease](u)
            for j in JOINT_NAMES:
                rot[j].append(slerp(p0[j], p1[j], u))
            o0 = p0.get("_root_offset", np.zeros(3))
            o1 = p1.get("_root_offset", np.zeros(3))
            offset = o0 + (o1 - o0) * u

            # il respiro si somma alla posa invece di sostituirla
            chest_deg, rise, sway_deg = breath_offset(t, self.breath)
            if self.breath > 0.0:
                rot["chest"][-1] = qmul(
                    rot["chest"][-1], np.array(euler_to_quat(-chest_deg, 0.0, 0.0)))
                rot["spine"][-1] = qmul(
                    rot["spine"][-1],
                    np.array(euler_to_quat(chest_deg * 0.4, 0.0, sway_deg * 0.5)))
                offset = offset + np.array([sway_deg * 0.0015, rise, 0.0])
            root_t.append(offset)

        # Si possono eliminare i canali che non cambiano mai, ma solo se il
        # loro valore costante coincide con la posa di riposo: un canale
        # rimosso fa tornare il giunto al riposo, non al valore costante.
        # Senza questo controllo il braccio che non lavora, invece di restare
        # in guardia, penzola lungo il fianco per tutta la lezione.
        rest = default_pose()
        out = {}
        simplify = _simplify_channel
        for j in JOINT_NAMES:
            arr = rot[j]
            moves = any(float(np.dot(arr[0], q)) < 0.99999 for q in arr[1:])
            if moves:
                out[j] = simplify(times, arr)
            elif abs(float(np.dot(arr[0], rest[j]))) < 0.99999:
                # costante ma diverso dal riposo: bastano due campioni
                out[j] = ([times[0], times[-1]], [arr[0], arr[0]])
        moved = any(float(np.linalg.norm(root_t[0] - o)) > 1e-5 for o in root_t[1:])
        return times, out, (root_t if moved else None)


# ======================================================================
#  LE LEZIONI
# ======================================================================
# Andamento di una tecnica nel tempo: (istante, quanta tecnica, curva).
# Le chiavi intermedie non sono un vezzo: ogni valore viene risolto di nuovo
# con l'IK, ed e' cosi' che il braccio percorre una traiettoria sensata
# invece di interpolare fra due soluzioni distanti.
_FLOW = [(0.00, 0.00, "inout"), (0.30, -0.06, "inout"), (0.44, -0.10, "inout"),
         (0.64, 0.22, "inout"), (0.88, 0.55, "inout"), (1.12, 0.85, "inout"),
         (1.34, 1.05, "inout"), (1.60, 1.00, "inout"), (2.85, 1.00, "hold"),
         (3.20, 0.72, "inout"), (3.50, 0.42, "inout"), (3.76, 0.18, "inout"),
         (4.05, 0.00, "inout")]

_SNAP = [(0.00, 0.00, "inout"), (0.28, -0.08, "inout"), (0.42, -0.17, "inout"),
         (0.54, 0.30, "snap"), (0.64, 0.74, "snap"), (0.72, 1.08, "snap"),
         (0.86, 1.00, "inout"), (1.95, 1.00, "hold"), (2.25, 0.70, "inout"),
         (2.52, 0.38, "inout"), (2.90, 0.00, "inout")]


# Quanto indietro va l'anticipo, per tecnica. Dove il bersaglio e' molto
# laterale, estrapolare troppo all'indietro porta la mano fuori portata e
# il braccio si distende: meglio un anticipo breve.
_ANTICIPATION = {"lap_sau": 0.35, "gaan_sau": 0.6, "jum_sau": 0.7}

# Quanto puo' andare oltre la sovracorsa. Le tecniche gia' molto estese -
# Gaan Sau, Biu Tze - non hanno margine: un cinque per cento in piu' e il
# gomito si blocca.
_OVERSHOOT = {"gaan_sau": 0.35, "biu_tze": 0.35, "jum_sau": 0.6}


def _technique_clip(name, title, description, camera, style="flow"):
    """Una tecnica di mano eseguita a destra e a sinistra.

    La struttura del gesto e' sempre la stessa - guardia, anticipo, tecnica,
    assestamento, ritorno - ma i tempi cambiano con la natura della tecnica:
    un Tan Sau accompagna, un Pak Sau esplode. Dare a tutte lo stesso ritmo
    e' il modo piu' rapido per far sembrare meccanico un movimento corretto.
    """
    steps, breath = (_SNAP, 0.45) if style == "snap" else (_FLOW, 1.0)
    antic = _ANTICIPATION.get(name, 1.0)
    over = _OVERSHOOT.get(name, 1.0)
    c = Clip(name, title, description, camera=camera, breath=breath)
    t = 0.0
    for side in ("L", "R"):
        for dt, amount, ease in steps:
            if amount < 0.0:
                amount *= antic
            elif amount > 1.0:
                amount = 1.0 + (amount - 1.0) * over
            c.key(t + dt, poses.technique(name, side, amount=amount), ease)
        t += steps[-1][0] + 0.40
    return c


def build_clips():
    clips = []
    G = poses.guardia
    Y = poses.yjkym

    # -- 1. la posizione base -------------------------------------------
    c = Clip("yee_ji_kim_yeung_ma", "Yee Ji Kim Yeung Ma",
             "La posizione madre. Dalle gambe unite si aprono i talloni, poi "
             "le punte, poi si affonda: le ginocchia si adducono verso la "
             "linea centrale e il bacino si retroverte.", camera="front")
    stand = poses.Pose().torso(sink=0.0)
    for side in ("L", "R"):
        stand.leg(side, ankle=(poses._s(side) * 0.065, 0.055, 0.0),
                  knee_pole=(0.0, 0.0, 1.0), foot_yaw=0.0)
    stand.hands("relax").look()
    c.key(0.0, stand)
    heels = poses.Pose().torso(sink=0.012)
    for side in ("L", "R"):
        heels.leg(side, ankle=(poses._s(side) * 0.145, 0.055, 0.0),
                  knee_pole=(0.0, 0.0, 1.0), foot_yaw=poses._s(side) * 24.0)
    heels.hands("relax").look()
    c.key(1.7, heels, "inout")
    c.key(3.3, Y(sink=0.03), "inout")
    c.key(4.5, Y(sink=0.086), "sink")      # affonda un filo oltre
    c.key(5.1, Y(sink=0.075), "inout")     # e si assesta
    c.key(7.0, Y(sink=0.075), "hold")
    clips.append(c)

    # -- 2. guardia -----------------------------------------------------
    c = Clip("guardia", "Man Sau / Wu Sau",
             "La guardia: la mano che interroga davanti, la mano che protegge "
             "dietro, entrambe sulla linea centrale.", camera="three_quarter")
    c.key(0.0, Y(0.075))
    for dt, u in ((0.45, 0.20), (0.75, 0.50), (1.05, 0.78), (1.35, 1.02),
                  (1.60, 1.00)):
        c.key(dt, G(0.075, "L", amount=u), "inout")
    c.key(3.2, G(0.075, "L"), "hold")
    c.key(3.7, G(0.075, "L", amount=0.55), "inout")
    c.key(4.1, G(0.075, "R", amount=0.55), "inout")
    c.key(4.7, G(0.075, "R"), "inout").key(6.4, G(0.075, "R"), "hold")
    clips.append(c)

    # -- 3-11. le mani ---------------------------------------------------
    hands = [
        ("tan_sau", "Tan Sau",
         "La mano che disperde. L'avambraccio sale dal gomito basso, il palmo "
         "guarda in alto, la mano occupa il centro.", "three_quarter", "flow"),
        ("bong_sau", "Bong Sau",
         "L'ala. Il gomito sale, l'avambraccio scende: la forza scivola via "
         "sul piano inclinato invece di essere fermata.", "side", "flow"),
        ("fook_sau", "Fook Sau",
         "La mano che controlla, posata sopra il ponte avversario senza "
         "spingere. Il gomito resta basso e vivo.", "three_quarter", "flow"),
        ("wu_sau", "Wu Sau",
         "La mano che protegge. Palmo verticale sulla linea centrale, pronta "
         "a prendere il posto della mano davanti.", "front", "flow"),
        ("pak_sau", "Pak Sau",
         "La mano che schiaffeggia. Taglia la linea centrale con un colpo "
         "corto e secco, senza inseguire.", "front", "snap"),
        ("gaan_sau", "Gaan Sau",
         "La mano che divide, in diagonale verso il basso: apre la linea "
         "bassa mantenendo la struttura.", "three_quarter", "flow"),
        ("jut_sau", "Jut Sau",
         "Lo strappo. Un movimento brevissimo verso il basso che rompe "
         "l'equilibrio dell'avversario e apre il bersaglio.", "three_quarter",
         "snap"),
        ("jum_sau", "Jum Sau",
         "Il gomito che affonda. Chiude la linea dall'alto verso il basso "
         "usando il peso, non la forza del braccio.", "side", "flow"),
        ("lap_sau", "Lap Sau",
         "La mano che afferra e tira, portando via l'equilibrio lungo la "
         "direzione in cui l'avversario e' gia' sbilanciato.", "three_quarter",
         "snap"),
    ]
    for name, title, desc, cam, style in hands:
        clips.append(_technique_clip(name, title, desc, cam, style))

    # -- 12. Biu Tze ----------------------------------------------------
    c = Clip("biu_tze", "Biu Tze",
             "Le dita che infilzano: la linea piu' breve verso il bersaglio. "
             "Si usa quando la struttura e' gia' compromessa, per questo si "
             "impara per ultimo.", camera="three_quarter", breath=0.4)
    for side, t0 in (("L", 0.0), ("R", 3.2)):
        for dt, amount, ease in [
                (0.00, 0.00, "inout"), (0.42, -0.18, "inout"),
                (0.56, 0.32, "snap"), (0.66, 0.78, "snap"),
                (0.74, 1.02, "snap"), (0.88, 1.00, "inout"),
                (1.90, 1.00, "hold"), (2.35, 0.55, "inout"),
                (2.85, 0.00, "inout")]:
            c.key(t0 + dt, poses.technique("biu_tze", side, amount=amount), ease)
    clips.append(c)

    # -- 13. Huen Sau ---------------------------------------------------
    c = Clip("huen_sau", "Huen Sau",
             "La mano che circola. Il polso disegna un cerchio attorno al "
             "ponte avversario: il contatto non si perde mai.", camera="close")
    base = poses.technique("tan_sau", "L")
    c.key(0.0, base)
    for i in range(1, 13):
        a = math.radians(360.0 * i / 12.0)
        pp = poses.yjkym(0.075, turn=poses._ew("L", 3.0))
        pp.arm("L", hand=(0.048 * math.sin(a),
                          poses.Y_GOLA - 0.075 + 0.048 * math.cos(a), 0.44),
               elbow_pole=(0.0, -1.0, 0.30),
               palm=(0.55 * math.sin(a), math.cos(a), 0.15))
        poses._other_hand(pp, "L", "wu", 0.075)
        pp.hand("L", "open").hand("R", "open").look()
        c.key(0.30 * i, pp, "linear")
    c.key(4.2, base, "inout")
    clips.append(c)

    # -- 14. pugno singolo ----------------------------------------------
    c = Clip("chung_kuen", "Yat Ji Chung Kuen",
             "Il pugno verticale. Parte dal centro, arriva al centro: la "
             "potenza viene dalla struttura allineata, non dal braccio.",
             camera="three_quarter", breath=0.35)
    ck = poses.chung_kuen
    c.key(0.0, G(0.075, "L"), "inout")
    c.key(0.50, ck("L", extension=0.0), "inout")
    c.key(0.66, blend(ck("L", extension=0.0), ck("L", extension=1.0), -0.10), "inout")
    c.key(0.92, ck("L", extension=1.015), "snap")
    c.key(1.06, ck("L", extension=1.0), "inout")
    c.key(2.10, ck("L", extension=1.0), "hold")
    c.key(2.95, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 15. catena di pugni --------------------------------------------
    c = Clip("lin_wan_kuen", "Lin Wan Kuen",
             "I pugni a catena. Ogni pugno parte mentre l'altro rientra: la "
             "linea centrale non resta mai scoperta.", camera="front",
             breath=0.25)
    t = 0.0
    c.key(t, G(0.075, "L"))
    t += 0.45
    for i in range(8):
        side = "L" if i % 2 == 0 else "R"
        c.key(t, ck(side, extension=1.015, other="chamber"), "snap")
        t += 0.30
        c.key(t, ck(side, extension=0.98, other="chamber"), "inout")
        t += 0.10
        c.key(t, ck(side, extension=0.45, other="chamber"), "inout")
        t += 0.13
    c.key(t + 0.55, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 16. girata del Chum Kiu ----------------------------------------
    c = Clip("juen_ma", "Juen Ma",
             "La girata sui talloni. L'asse e' la linea centrale del corpo: "
             "girando, la struttura devia la forza invece di opporvisi.",
             camera="top_front")
    jm = poses.juen_ma
    c.key(0.0, jm(0.0, lead="L"))
    c.key(0.35, jm(-9.0, lead="L"), "inout")        # anticipo controrotante
    c.key(1.45, jm(50.0, lead="R"), "inout")
    c.key(1.70, jm(46.0, lead="R"), "inout")        # assestamento
    c.key(2.80, jm(46.0, lead="R"), "hold")
    c.key(3.60, jm(0.0, lead="L"), "inout")
    c.key(3.95, jm(9.0, lead="L"), "inout")
    c.key(5.05, jm(-50.0, lead="L"), "inout")
    c.key(5.30, jm(-46.0, lead="L"), "inout")
    c.key(6.40, jm(-46.0, lead="L"), "hold")
    c.key(7.30, jm(0.0, lead="L"), "inout")
    clips.append(c)

    # -- 17. calcio frontale --------------------------------------------
    c = Clip("jing_geuk", "Jing Geuk",
             "Il calcio frontale di tallone. Non sale sopra la vita: la gamba "
             "resta un'arma bassa che non scopre la struttura.", camera="side",
             breath=0.3)
    jg = poses.jing_geuk
    c.key(0.0, jg("R", extension=0.0), "inout")
    c.key(0.42, jg("R", extension=0.12), "inout")   # carica il peso
    c.key(0.80, jg("R", extension=0.45), "inout")   # ginocchio in camera
    c.key(1.06, jg("R", extension=1.03), "snap")    # la tibia parte
    c.key(1.20, jg("R", extension=1.0), "inout")
    c.key(1.75, jg("R", extension=1.0), "hold")
    c.key(2.25, jg("R", extension=0.42), "inout")   # richiama la gamba
    c.key(2.70, jg("R", extension=0.14), "inout")
    c.key(3.10, jg("R", extension=0.0), "inout")
    clips.append(c)

    # -- 18. Pak Da ------------------------------------------------------
    c = Clip("pak_da", "Pak Da",
             "Deviare e colpire nello stesso tempo (Lin Siu Die Dar). Non c'e' "
             "un tempo per difendersi e uno per attaccare: e' un tempo solo.",
             camera="three_quarter", breath=0.3)
    hit = poses.yjkym(0.070, turn=-9.0, shift=(0.0, 0.022))
    hit.arm("R", hand=(0.10, poses.Y_GOLA - 0.05 - 0.070, 0.38),
            elbow_pole=(-0.25, -1.0, 0.30), palm=(1.0, 0.0, 0.35))
    hit.fist("L", (0.0, poses.Y_PLESSO + 0.04 - 0.070, 0.495),
             elbow_pole=(0.05, -1.0, 0.5), knuckles=(-1.0, 0.0, 0.0))
    hit.hand("R", "palm").hand("L", "fist").look()
    g = G(0.075, "L")
    c.key(0.0, g, "inout")
    c.key(0.40, blend(g, hit, -0.16), "inout")
    c.key(0.68, blend(g, hit, 1.06), "snap")
    c.key(0.82, hit, "inout")
    c.key(2.00, hit, "hold")
    c.key(2.95, g, "inout")
    clips.append(c)

    # -- 19. Bong Lap Sau ------------------------------------------------
    c = Clip("bong_lap_sau", "Bong Lap Sau",
             "L'esercizio che lega ala e strappo: il Bong cede, il Lap tira, "
             "il colpo arriva sulla linea che si e' aperta.",
             camera="three_quarter", breath=0.5)
    strike = poses.yjkym(0.070, turn=9.0, shift=(0.0, 0.020))
    strike.arm("L", hand=(0.26, poses.Y_PLESSO - 0.175, 0.20),
               elbow_pole=(0.5, -1.0, 0.1), palm=(0.0, -1.0, 0.0))
    strike.fist("R", (0.0, poses.Y_PLESSO + 0.04 - 0.070, 0.495),
                elbow_pole=(-0.05, -1.0, 0.5), knuckles=(1.0, 0.0, 0.0))
    strike.hand("L", "grab").hand("R", "fist").look()
    g = G(0.075, "L")
    c.key(0.0, g, "inout")
    for dt, u in ((0.35, 0.28), (0.65, 0.62), (0.95, 0.92), (1.10, 1.0)):
        c.key(dt, poses.technique("bong_sau", "L", amount=u), "inout")
    c.key(1.60, poses.technique("bong_sau", "L"), "hold")
    c.key(1.85, poses.technique("bong_sau", "L", amount=0.45), "inout")
    c.key(2.10, poses.technique("lap_sau", "L", amount=0.55), "snap")
    c.key(2.30, poses.technique("lap_sau", "L"), "snap")
    c.key(2.60, blend(poses.technique("lap_sau", "L"), strike, 1.05), "snap")
    c.key(2.75, strike, "inout")
    c.key(3.85, strike, "hold")
    c.key(4.35, blend(g, strike, 0.45), "inout")
    c.key(4.95, g, "inout")
    clips.append(c)

    # -- 20. Kwan Sau ----------------------------------------------------
    c = Clip("kwan_sau", "Kwan Sau",
             "La rotazione: mentre un braccio diventa Bong, l'altro diventa "
             "Tan. Le due mani ruotano attorno allo stesso asse.",
             camera="three_quarter")

    def kwan(bong_side):
        tan_side = poses._o(bong_side)
        q = poses.yjkym(0.075, turn=poses._ew(bong_side, -3.0))
        q.arm(bong_side, hand=(-poses._s(bong_side) * 0.03,
                               poses.Y_PLESSO - 0.055, 0.36),
              elbow_pole=(poses._s(bong_side) * 0.55, 1.0, 0.45),
              palm=(0.0, -0.8, 0.6))
        q.arm(tan_side, hand=(0.0, poses.Y_GOLA - 0.075, 0.42),
              elbow_pole=(0.0, -1.0, 0.30), palm=(0.0, 1.0, 0.15))
        return q.hand(bong_side, "relax").hand(tan_side, "open").look()

    a, b = kwan("L"), kwan("R")
    c.key(0.0, a).key(0.30, blend(a, b, -0.07), "inout")
    c.key(1.60, b, "inout").key(2.45, b, "hold")
    c.key(2.75, blend(b, a, -0.07), "inout")
    c.key(4.05, a, "inout").key(4.90, a, "hold")
    clips.append(c)

    # -- 21. respiro in posizione (idle) ---------------------------------
    c = Clip("idle", "In posizione",
             "Il SiFu attende in Yee Ji Kim Yeung Ma.", camera="front")
    c.key(0.0, Y(0.075)).key(2.6, Y(0.079), "inout")
    c.key(5.2, Y(0.075), "inout")
    clips.append(c)

    return clips
