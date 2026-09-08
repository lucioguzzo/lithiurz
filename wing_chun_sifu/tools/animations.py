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
from kinematics import slerp
from rig import JOINT_NAMES

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


class Clip:
    """Una lezione animata: posizioni chiave nel tempo."""

    def __init__(self, name, title, description, camera="front", tempo=None):
        self.name = name
        self.title = title
        self.description = description
        self.camera = camera
        self.tempo = tempo or []
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
            root_t.append(o0 + (o1 - o0) * u)

        # elimina i canali che non cambiano mai: meno dati, stesso risultato
        out = {}
        for j in JOINT_NAMES:
            arr = rot[j]
            if any(float(np.dot(arr[0], q)) < 0.99999 for q in arr[1:]):
                out[j] = (times, arr)
        moved = any(float(np.linalg.norm(root_t[0] - o)) > 1e-5 for o in root_t[1:])
        return times, out, (root_t if moved else None)


# ======================================================================
#  LE LEZIONI
# ======================================================================
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
    c.key(0.0, stand)
    heels = poses.Pose().torso(sink=0.01)
    for side in ("L", "R"):
        heels.leg(side, ankle=(poses._s(side) * 0.145, 0.055, 0.0),
                  knee_pole=(0.0, 0.0, 1.0), foot_yaw=poses._s(side) * 24.0)
    c.key(1.6, heels, "inout")
    c.key(3.2, Y(sink=0.03), "inout")
    c.key(4.6, Y(sink=0.075), "sink")
    c.key(6.0, Y(sink=0.075), "hold")
    clips.append(c)

    # -- 2. guardia -----------------------------------------------------
    c = Clip("guardia", "Man Sau / Wu Sau",
             "La guardia: la mano che interroga davanti, la mano che protegge "
             "dietro, entrambe sulla linea centrale.", camera="three_quarter")
    c.key(0.0, Y(0.075)).key(1.4, G(0.075, "L"), "inout")
    c.key(3.0, G(0.075, "L"), "hold").key(4.4, G(0.075, "R"), "inout")
    c.key(6.0, G(0.075, "R"), "hold")
    clips.append(c)

    # -- 3-10. le mani --------------------------------------------------
    hands = [
        ("tan_sau", "Tan Sau", poses.tan_sau,
         "La mano che disperde. L'avambraccio sale dal gomito basso, il palmo "
         "guarda in alto, la mano occupa il centro.", "three_quarter"),
        ("bong_sau", "Bong Sau", poses.bong_sau,
         "L'ala. Il gomito sale, l'avambraccio scende: la forza scivola via "
         "sul piano inclinato invece di essere fermata.", "side"),
        ("fook_sau", "Fook Sau", poses.fook_sau,
         "La mano che controlla, posata sopra il ponte avversario senza "
         "spingere. Il gomito resta basso e vivo.", "three_quarter"),
        ("wu_sau", "Wu Sau", poses.wu_sau,
         "La mano che protegge. Palmo verticale sulla linea centrale, pronta "
         "a prendere il posto della mano davanti.", "front"),
        ("pak_sau", "Pak Sau", poses.pak_sau,
         "La mano che schiaffeggia. Taglia la linea centrale con un colpo "
         "corto e secco, senza inseguire.", "front"),
        ("gaan_sau", "Gaan Sau", poses.gaan_sau,
         "La mano che divide, in diagonale verso il basso: apre la linea "
         "bassa mantenendo la struttura.", "three_quarter"),
        ("jut_sau", "Jut Sau", poses.jut_sau,
         "Lo strappo. Un movimento brevissimo verso il basso che rompe "
         "l'equilibrio dell'avversario e apre il bersaglio.", "three_quarter"),
        ("jum_sau", "Jum Sau", poses.jum_sau,
         "Il gomito che affonda. Chiude la linea dall'alto verso il basso "
         "usando il peso, non la forza del braccio.", "side"),
        ("lap_sau", "Lap Sau", poses.lap_sau,
         "La mano che afferra e tira, portando via l'equilibrio lungo la "
         "direzione in cui l'avversario e' gia' sbilanciato.", "three_quarter"),
    ]
    for name, title, fn, desc, cam in hands:
        c = Clip(name, title, desc, camera=cam)
        ease = "snap" if name in ("pak_sau", "jut_sau", "lap_sau") else "inout"
        c.key(0.0, G(0.075, "L"))
        c.key(1.3, fn("L"), ease)
        c.key(2.6, fn("L"), "hold")
        c.key(3.8, G(0.075, "L"), "inout")
        c.key(4.4, G(0.075, "R"), "inout")
        c.key(5.7, fn("R"), ease)
        c.key(7.0, fn("R"), "hold")
        c.key(8.2, G(0.075, "L"), "inout")
        clips.append(c)

    # -- 11. Biu Tze ----------------------------------------------------
    c = Clip("biu_tze", "Biu Tze",
             "Le dita che infilzano: la linea piu' breve verso il bersaglio. "
             "Si usa quando la struttura e' gia' compromessa, per questo si "
             "impara per ultimo.", camera="three_quarter")
    c.key(0.0, G(0.075, "L")).key(0.9, poses.biu_tze("L"), "snap")
    c.key(1.9, poses.biu_tze("L"), "hold").key(2.9, G(0.075, "L"), "inout")
    c.key(3.8, poses.biu_tze("R"), "snap")
    c.key(4.8, poses.biu_tze("R"), "hold").key(5.8, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 12. Huen Sau ---------------------------------------------------
    c = Clip("huen_sau", "Huen Sau",
             "La mano che circola. Il polso disegna un cerchio attorno al "
             "ponte avversario: il contatto non si perde mai.", camera="close")
    base = poses.tan_sau("L")
    c.key(0.0, base)
    for i in range(1, 9):
        a = math.radians(360.0 * i / 8.0)
        pp = poses.yjkym(0.075)
        pp.arm("L", hand=(0.045 * math.sin(a), poses.Y_GOLA - 0.075 + 0.045 * math.cos(a), 0.44),
               elbow_pole=(0.0, -1.0, 0.30),
               palm=(0.5 * math.sin(a), math.cos(a), 0.15))
        poses._other_hand(pp, "L", "wu", 0.075)
        c.key(0.35 * i, pp, "linear")
    c.key(3.4, base, "inout")
    clips.append(c)

    # -- 13. pugno singolo ----------------------------------------------
    c = Clip("chung_kuen", "Yat Ji Chung Kuen",
             "Il pugno verticale. Parte dal centro, arriva al centro: la "
             "potenza viene dalla struttura allineata, non dal braccio.",
             camera="three_quarter")
    c.key(0.0, G(0.075, "L"))
    c.key(0.55, poses.chung_kuen("L", extension=0.0), "inout")
    c.key(0.95, poses.chung_kuen("L", extension=1.0), "snap")
    c.key(1.9, poses.chung_kuen("L", extension=1.0), "hold")
    c.key(2.7, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 14. catena di pugni --------------------------------------------
    c = Clip("lin_wan_kuen", "Lin Wan Kuen",
             "I pugni a catena. Ogni pugno parte mentre l'altro rientra: la "
             "linea centrale non resta mai scoperta.", camera="front")
    t = 0.0
    c.key(t, G(0.075, "L"))
    t += 0.5
    for i in range(6):
        side = "L" if i % 2 == 0 else "R"
        c.key(t, poses.chung_kuen(side, extension=1.0, other="chamber"), "snap")
        t += 0.42
        c.key(t, poses.chung_kuen(side, extension=0.55, other="chamber"), "linear")
        t += 0.16
    c.key(t + 0.5, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 15. girata del Chum Kiu ----------------------------------------
    c = Clip("juen_ma", "Juen Ma",
             "La girata sui talloni. L'asse e' la linea centrale del corpo: "
             "girando, la struttura devia la forza invece di opporvisi.",
             camera="top_front")
    c.key(0.0, poses.juen_ma(0.0, lead="L"))
    c.key(1.4, poses.juen_ma(48.0, lead="R"), "inout")
    c.key(2.4, poses.juen_ma(48.0, lead="R"), "hold")
    c.key(3.8, poses.juen_ma(0.0, lead="L"), "inout")
    c.key(5.2, poses.juen_ma(-48.0, lead="L"), "inout")
    c.key(6.2, poses.juen_ma(-48.0, lead="L"), "hold")
    c.key(7.6, poses.juen_ma(0.0, lead="L"), "inout")
    clips.append(c)

    # -- 16. calcio frontale --------------------------------------------
    c = Clip("jing_geuk", "Jing Geuk",
             "Il calcio frontale di tallone. Non sale sopra la vita: la gamba "
             "resta un'arma bassa che non scopre la struttura.", camera="side")
    c.key(0.0, Y(0.075))
    c.key(0.7, poses.jing_geuk("R", extension=0.25), "inout")
    c.key(1.05, poses.jing_geuk("R", extension=1.0), "snap")
    c.key(1.7, poses.jing_geuk("R", extension=1.0), "hold")
    c.key(2.4, poses.jing_geuk("R", extension=0.2), "inout")
    c.key(3.1, Y(0.075), "inout")
    clips.append(c)

    # -- 17. Pak Da: deviare e colpire nello stesso tempo ----------------
    c = Clip("pak_da", "Pak Da",
             "Deviare e colpire nello stesso tempo (Lin Siu Die Dar). Non c'e' "
             "un tempo per difendersi e uno per attaccare: e' un tempo solo.",
             camera="three_quarter")
    hit = poses.yjkym(0.075, turn=-7.0)
    hit.arm("R", hand=(0.10, poses.Y_GOLA - 0.05 - 0.075, 0.38),
            elbow_pole=(-0.25, -1.0, 0.30), palm=(1.0, 0.0, 0.35))
    hit.fist("L", (0.0, poses.Y_PLESSO + 0.04 - 0.075, 0.495),
             elbow_pole=(0.05, -1.0, 0.5), knuckles=(-1.0, 0.0, 0.0))
    c.key(0.0, G(0.075, "L"))
    c.key(0.85, hit, "snap")
    c.key(1.9, hit, "hold")
    c.key(2.9, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 18. Bong Lap Sau ------------------------------------------------
    c = Clip("bong_lap_sau", "Bong Lap Sau",
             "L'esercizio che lega ala e strappo: il Bong cede, il Lap tira, "
             "il colpo arriva sulla linea che si e' aperta.", camera="three_quarter")
    c.key(0.0, G(0.075, "L"))
    c.key(1.0, poses.bong_sau("L"), "inout")
    c.key(1.9, poses.lap_sau("L", other="wu"), "snap")
    strike = poses.yjkym(0.075, turn=7.0)
    strike.arm("L", hand=(0.26, poses.Y_PLESSO - 0.175, 0.20),
               elbow_pole=(0.5, -1.0, 0.1), palm=(0.0, -1.0, 0.0))
    strike.fist("R", (0.0, poses.Y_PLESSO + 0.04 - 0.075, 0.495),
                elbow_pole=(-0.05, -1.0, 0.5), knuckles=(1.0, 0.0, 0.0))
    c.key(2.4, strike, "snap")
    c.key(3.4, strike, "hold")
    c.key(4.4, G(0.075, "L"), "inout")
    clips.append(c)

    # -- 19. Kwan Sau ----------------------------------------------------
    c = Clip("kwan_sau", "Kwan Sau",
             "La rotazione: mentre un braccio diventa Bong, l'altro diventa "
             "Tan. Le due mani ruotano attorno allo stesso asse.",
             camera="three_quarter")
    a = poses.yjkym(0.075)
    a.arm("L", hand=(-0.03, poses.Y_PLESSO - 0.055, 0.36),
          elbow_pole=(0.55, 1.0, 0.45), palm=(0.0, -0.8, 0.6))
    a.arm("R", hand=(0.0, poses.Y_GOLA - 0.075, 0.42),
          elbow_pole=(0.0, -1.0, 0.30), palm=(0.0, 1.0, 0.15))
    b = poses.yjkym(0.075)
    b.arm("R", hand=(0.03, poses.Y_PLESSO - 0.055, 0.36),
          elbow_pole=(-0.55, 1.0, 0.45), palm=(0.0, -0.8, 0.6))
    b.arm("L", hand=(0.0, poses.Y_GOLA - 0.075, 0.42),
          elbow_pole=(0.0, -1.0, 0.30), palm=(0.0, 1.0, 0.15))
    c.key(0.0, a).key(1.5, b, "inout").key(2.3, b, "hold")
    c.key(3.8, a, "inout").key(4.6, a, "hold")
    clips.append(c)

    # -- 20. respiro in posizione (idle) ---------------------------------
    c = Clip("idle", "In posizione",
             "Il SiFu attende in Yee Ji Kim Yeung Ma.", camera="front")
    c.key(0.0, Y(0.075)).key(2.0, Y(0.086), "inout")
    c.key(4.0, Y(0.075), "inout")
    clips.append(c)

    return clips
