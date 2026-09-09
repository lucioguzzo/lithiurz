"""Libreria delle posizioni del Wing Chun.

Ogni posizione e' descritta come la descriverebbe un SiFu:
"la mano sulla linea centrale all'altezza della gola, il gomito basso a un
pugno dal petto, il palmo verso l'alto". L'IK converte questa descrizione
in angoli dei giunti.

Riferimenti di altezza sul modello (metri da terra):
  plesso solare 1.25 - gola 1.42 - mento 1.50 - occhi 1.60
Linea centrale: x = 0. Il SiFu guarda verso +Z (verso l'allievo).
"""

import math
import numpy as np

import kinematics as K
from kinematics import Skeleton, aim, two_bone_ik, qrot, qconj, qmul, axis_angle

UPPERARM = 0.285
FOREARM = 0.255
THIGH = 0.425
SHIN = 0.415

# altezze notevoli
Y_DANTIAN = 1.02
Y_PLESSO = 1.25
Y_SPALLA = 1.355
Y_GOLA = 1.42
Y_MENTO = 1.50
Y_OCCHI = 1.60


def _signed_roll(current, desired, axis):
    """Angolo con segno da `current` a `desired` intorno ad `axis`."""
    axis = axis / (np.linalg.norm(axis) or 1.0)
    c = current - axis * float(np.dot(current, axis))
    d = desired - axis * float(np.dot(desired, axis))
    nc, nd = np.linalg.norm(c), np.linalg.norm(d)
    if nc < 1e-6 or nd < 1e-6:
        return 0.0
    c, d = c / nc, d / nd
    cosang = max(-1.0, min(1.0, float(np.dot(c, d))))
    sinang = float(np.dot(np.cross(c, d), axis))
    return math.atan2(sinang, cosang)



# ======================================================================
#  FORME DELLA MANO
# ======================================================================
# Per ogni dito: (piega della prima falange, piega della seconda,
# apertura laterale) in gradi. E' quello che distingue visivamente un
# Biu Tze da un Fook Sau anche quando il braccio e' nella stessa posizione.
HAND_SHAPES = {
    # mano aperta e viva: la forma di Tan Sau, Fook Sau, Wu Sau
    "open": {"idx": (10, 8, 4), "mid": (7, 6, 1), "rng": (9, 7, -3),
             "pnk": (14, 10, -7), "thb": (12, 10, 26)},
    # dita tese e unite: Biu Tze
    "spear": {"idx": (3, 2, 1), "mid": (2, 2, 0), "rng": (3, 2, -1),
              "pnk": (6, 4, -2), "thb": (34, 28, 12)},
    # pugno verticale del Wing Chun
    "fist": {"idx": (92, 88, 2), "mid": (94, 90, 0), "rng": (94, 88, -2),
             "pnk": (96, 86, -4), "thb": (48, 44, -28)},
    # mano rilassata: transizioni, riposo
    "relax": {"idx": (24, 20, 3), "mid": (22, 20, 0), "rng": (26, 22, -3),
              "pnk": (32, 26, -6), "thb": (20, 16, 22)},
    # mano che aggancia: Lap Sau
    "grab": {"idx": (58, 48, 2), "mid": (60, 50, 0), "rng": (58, 46, -2),
             "pnk": (60, 44, -5), "thb": (40, 34, 14)},
    # palmo che colpisce: dita leggermente indietro, polso in avanti
    "palm": {"idx": (-6, 2, 5), "mid": (-8, 2, 1), "rng": (-6, 2, -4),
             "pnk": (-2, 4, -9), "thb": (16, 12, 32)},
}


class Pose:
    """Costruttore di una posa: prima il corpo, poi gli arti."""

    def __init__(self):
        self.skel = Skeleton()
        self._torso_yaw = 0.0

    # -- tronco ---------------------------------------------------------
    def torso(self, hips=0.0, waist=0.0, lean=0.0, sink=0.0,
              shift_x=0.0, shift_z=0.0):
        """Orientamento e peso del tronco.

        `hips` ruota il bacino: le gambe lo seguono, ed e' la girata del
        Chum Kiu. `waist` ruota solo colonna e torace lasciando i piedi
        dove sono: e' la piccola apertura della spalla che accompagna un
        colpo. Tenerle separate e' importante - applicare la rotazione al
        bacino durante un pugno trascina le gambe e la posizione si torce.

        Angoli positivi girano verso la sinistra del SiFu. `shift_x` e
        `shift_z` spostano il peso, `sink` abbassa il bacino.
        """
        self.skel.set_euler("root", lean * 0.20, hips, 0.0)
        self.skel.set_euler("spine", lean * 0.40, waist * 0.55, 0.0)
        self.skel.set_euler("chest", lean * 0.40, waist * 0.45, 0.0)
        self.skel.root_offset = np.array([shift_x, -sink, shift_z])
        self.skel._dirty = True
        self._torso_yaw = hips + waist
        return self

    def head_look(self, yaw=0.0, pitch=0.0):
        """Dove guarda il SiFu. Una testa immobile mentre il corpo lavora e'
        il tratto che piu' di ogni altro fa sembrare una figura un manichino."""
        self.skel.set_euler("neck", pitch * 0.4, yaw * 0.4, 0.0)
        self.skel.set_euler("head", pitch * 0.6, yaw * 0.6, 0.0)
        return self

    def look(self, yaw=0.0, pitch=0.0):
        """Direzione dello sguardo nel mondo, non rispetto al torace.

        Il collo compensa da solo la rotazione del tronco: girando il corpo
        il SiFu continua a guardare l'avversario, invece di portarsi via lo
        sguardo come farebbe un manichino incollato al proprio petto.
        """
        return self.head_look(yaw=yaw - self._torso_yaw, pitch=pitch)

    # -- forma della mano -----------------------------------------------
    def hand(self, side, shape, amount=1.0, base="open"):
        """Chiude le dita nella forma richiesta dalla tecnica.

        `amount` interpola dalla forma `base`: le dita si chiudono mentre il
        braccio si muove, invece di scattare da aperte a chiuse.
        """
        sign = 1.0 if side == "L" else -1.0
        src, dst = HAND_SHAPES[base], HAND_SHAPES[shape]
        u = max(-0.3, min(1.3, amount))
        for finger in dst:
            a, b = src[finger], dst[finger]
            c1, c2, spread = (a[i] + (b[i] - a[i]) * u for i in range(3))
            self.skel.set_euler(f"{finger}1_{side}", -c1, 0.0, spread * sign)
            self.skel.set_euler(f"{finger}2_{side}", -c2, 0.0, 0.0)
        return self

    def hands(self, shape):
        return self.hand("L", shape).hand("R", shape)

    # -- braccia --------------------------------------------------------
    def arm(self, side, hand, elbow_pole=(0.0, -1.0, 0.15), palm=(0.0, 1.0, 0.0),
            wrist=(0.0, 0.0, 0.0), shoulder_shrug=0.0):
        """Porta la mano su `hand` con il gomito verso `elbow_pole`
        e il palmo rivolto verso `palm`."""
        s = self.skel
        sj, uj, fj, hj = (f"shoulder_{side}", f"upperarm_{side}",
                          f"forearm_{side}", f"hand_{side}")
        s.set_euler(sj, 0.0, 0.0, shoulder_shrug * (1 if side == "L" else -1))

        origin = s.world_pos(uj)
        target = np.asarray(hand, dtype=np.float64)
        _, d1, d2 = two_bone_ik(origin, target, UPPERARM, FOREARM,
                                np.asarray(elbow_pole, dtype=np.float64))
        s.set(uj, aim(s, uj, d1))
        q0 = aim(s, fj, d2)
        s.set(fj, q0)

        # correzione assiale: porta il palmo dove richiesto
        current_palm = qrot(s.world_rot(fj), np.array([0.0, 0.0, 1.0]))
        roll = _signed_roll(current_palm, np.asarray(palm, dtype=np.float64), d2)
        s.set(fj, qmul(q0, axis_angle(K.BONE_REST_DIR, -roll)))

        s.set_euler(hj, *wrist)
        return self

    def fist(self, side, target, elbow_pole=(0.0, -1.0, 0.2), knuckles=(0.0, 1.0, 0.0)):
        """Pugno verticale del Wing Chun: nocche verticali, gomito che scende."""
        return self.arm(side, target, elbow_pole, knuckles, wrist=(0.0, 0.0, 0.0))

    # -- gambe ----------------------------------------------------------
    def leg(self, side, ankle, knee_pole=(0.0, 0.0, 1.0), foot_yaw=0.0,
            foot_pitch=0.0):
        s = self.skel
        tj, shj, fj = f"thigh_{side}", f"shin_{side}", f"foot_{side}"
        origin = s.world_pos(tj)
        _, d1, d2 = two_bone_ik(origin, np.asarray(ankle, dtype=np.float64),
                                THIGH, SHIN,
                                np.asarray(knee_pole, dtype=np.float64))
        s.set(tj, aim(s, tj, d1))
        s.set(shj, aim(s, shj, d2))
        # il piede resta piatto a terra, orientato secondo foot_yaw
        yaw = math.radians(foot_yaw)
        toe_dir = np.array([math.sin(yaw), -math.sin(math.radians(foot_pitch)),
                            math.cos(yaw)])
        toe_dir /= np.linalg.norm(toe_dir)
        rest = np.array([0.0, -0.065, 0.115])
        rest /= np.linalg.norm(rest)
        pq = s.parent_rot(fj)
        local_dir = qrot(qconj(pq), toe_dir)
        s.set(fj, K.shortest_arc(rest, local_dir))
        return self

    def build(self):
        out = {k: v.copy() for k, v in self.skel.pose.items()}
        out["_root_offset"] = self.skel.root_offset.copy()
        return out


# ======================================================================
#  POSIZIONI FONDAMENTALI
# ======================================================================
def yjkym(sink=0.075, turn=0.0, shift=(0.0, 0.0)):
    """Yee Ji Kim Yeung Ma - la posizione del carattere 'due' che adduce.

    Piedi larghi quanto le spalle, punte rivolte verso l'interno, ginocchia
    addotte verso la linea centrale, bacino retroverso. E' la posizione madre
    da cui nasce tutta la struttura del sistema.
    """
    p = Pose().torso(sink=sink, waist=turn,
                     shift_x=shift[0], shift_z=shift[1])
    for side, sign in (("L", 1.0), ("R", -1.0)):
        # il pole del ginocchio punta verso l'interno: e' l'adduzione
        # ("kim") che da' il nome alla posizione e chiude la linea bassa
        p.leg(side, ankle=(sign * 0.155, 0.055, 0.0),
              knee_pole=(-sign * 0.30, 0.0, 1.0), foot_yaw=-sign * 26.0,
              foot_pitch=3.0)
    return p.hands("relax")


def guardia(sink=0.075, avanti="L", amount=1.0):
    """Man Sau / Wu Sau: la guardia del Wing Chun.

    La mano avanzata (Man Sau, 'mano che interroga') sta sulla linea
    centrale all'altezza della gola; la mano arretrata (Wu Sau, 'mano che
    protegge') copre davanti al plesso, pronta a sostituire la prima.

    `amount` sale da 0 (braccia lungo i fianchi) a 1. Serve alle transizioni:
    alzando le braccia da fermo il gomito deve piegarsi mentre sale, non
    restare disteso fino all'ultimo istante.
    """
    dietro = "R" if avanti == "L" else "L"
    sa = -1.0 if avanti == "L" else 1.0
    sd = -1.0 if dietro == "L" else 1.0
    u = amount
    if u < 0.999:
        p = yjkym(sink, turn=-sa * 2.5 * u)
        rest = lambda side: (_s(side) * 0.30, 0.80 - sink, 0.03)
        for side, sgn, tgt in (
                (avanti, sa, (0.0, Y_GOLA - sink, 0.425)),
                (dietro, sd, (0.0, Y_GOLA - 0.10 - sink, 0.275))):
            p.arm(side, hand=_lerp_t(rest(side), tgt, u),
                  elbow_pole=_lerp_t((_s(side) * 0.35, -1.0, 0.15),
                                     (0.0, -1.0, 0.22), u),
                  palm=_lerp_t((_s(side) * 0.8, 0.2, 0.0), (sgn, 0.33, 0.0), u))
        p.hand(avanti, "open", amount=u, base="relax")
        p.hand(dietro, "open", amount=u, base="relax")
        return p.look()
    p = yjkym(sink, turn=-sa * 2.5)
    # La distanza della mano avanzata non e' estetica: e' l'angolo del
    # gomito. Piu' avanti di cosi' il braccio si distende e la guardia
    # smette di reggere pressione.
    p.arm(avanti, hand=(0.0, Y_GOLA - sink, 0.425),
          elbow_pole=(0.0, -1.0, 0.25), palm=(sa, 0.35, 0.0))
    p.arm(dietro, hand=(0.0, Y_GOLA - 0.10 - sink, 0.275),
          elbow_pole=(0.0, -1.0, 0.2), palm=(sd, 0.30, 0.0))
    return p.hands("open").look()


def _s(side):
    """+1 per il lato sinistro del SiFu (x positivo), -1 per il destro."""
    return 1.0 if side == "L" else -1.0


def _ew(side, degrees):
    """Ingaggio della vita per una tecnica eseguita con `side`.

    La spalla che lavora avanza di pochi gradi. Non e' la torsione dell'anca
    della boxe: nel Wing Chun la struttura resta chiusa, e questa apertura
    minima e' cio' che distingue un corpo che partecipa da un busto immobile
    con due braccia attaccate.
    """
    return -degrees if side == "L" else degrees


# altezza a cui il SiFu tiene lo sguardo: gli occhi di chi ha davanti
GAZE_PITCH = 0.0


# ======================================================================
#  LE TECNICHE DI MANO
# ======================================================================
# Ogni tecnica e' descritta dai suoi *bersagli* - dove va la mano, dove
# punta il gomito, dove guarda il palmo - non dai suoi angoli. Serve per il
# parametro `amount`: interpolando i bersagli e risolvendo l'IK a ogni
# passo, il braccio percorre una traiettoria plausibile.
#
# Interpolare invece le rotazioni fra due soluzioni IK corrette produce
# posizioni intermedie sbagliate: il gomito si distende a meta' strada e il
# movimento sembra scoordinato anche se partenza e arrivo sono giusti.

def _chamber_arm(side, sink):
    """Il punto di partenza di ogni tecnica: il pugno in camera al fianco.

    E' la posizione da cui il Siu Nim Tau fa uscire ogni mano, ed e' anche
    la scelta didattica giusta: partendo dalla guardia, un Tan Sau e un Man
    Sau finiscono quasi nello stesso posto e la tecnica non si legge. Dal
    fianco il percorso e' lungo e la forma della mano si vede nascere.
    """
    return dict(hand=(_s(side) * 0.145, Y_PLESSO - 0.08 - sink, -0.02),
                pole=(_s(side) * 0.6, -1.0, -0.5),
                palm=(0.0, 1.0, 0.0),
                wrist=(0.0, 0.0, 0.0))


TECHNIQUES = {
    "tan_sau": dict(
        hand=lambda s, k: (0.0, Y_GOLA - k, 0.44),
        pole=lambda s: (0.0, -1.0, 0.30), palm=lambda s: (0.0, 1.0, 0.15),
        wrist=(0.0, 0.0, 0.0), shape="open",
        waist=3.0, sink=0.0, shift=(0.0, 0.0), pitch=0.0),
    "bong_sau": dict(
        hand=lambda s, k: (-_s(s) * 0.03, Y_PLESSO - k + 0.02, 0.36),
        pole=lambda s: (_s(s) * 0.55, 1.0, 0.45),
        palm=lambda s: (0.0, -0.8, 0.6),
        wrist=(0.0, 0.0, 0.0), shape="relax",
        waist=-4.5, sink=0.0, shift=(0.0, 0.0), pitch=0.0),
    "fook_sau": dict(
        hand=lambda s, k: (0.0, Y_GOLA - 0.06 - k, 0.42),
        pole=lambda s: (0.0, -1.0, 0.28), palm=lambda s: (0.0, -1.0, 0.2),
        wrist=(28.0, 0.0, 0.0), shape="open",
        waist=2.5, sink=0.0, shift=(0.0, 0.0), pitch=0.0),
    "wu_sau": dict(
        hand=lambda s, k: (0.0, Y_GOLA - 0.10 - k, 0.275),
        pole=lambda s: (0.0, -1.0, 0.18), palm=lambda s: (-_s(s), 0.30, 0.0),
        wrist=(0.0, 0.0, 0.0), shape="open",
        waist=0.0, sink=0.0, shift=(0.0, 0.0), pitch=0.0),
    "pak_sau": dict(
        hand=lambda s, k: (-_s(s) * 0.11, Y_GOLA - 0.05 - k, 0.38),
        pole=lambda s: (_s(s) * 0.25, -1.0, 0.30),
        palm=lambda s: (-_s(s), 0.0, 0.35),
        wrist=(0.0, 0.0, 0.0), shape="palm",
        waist=6.0, sink=0.0, shift=(-0.008, 0.010), pitch=0.0),
    "gaan_sau": dict(
        hand=lambda s, k: (_s(s) * 0.18, Y_DANTIAN + 0.06 - k, 0.44),
        pole=lambda s: (_s(s) * 0.2, -0.4, 1.0),
        palm=lambda s: (_s(s) * 0.5, 0.4, 0.6),
        wrist=(0.0, 0.0, 0.0), shape="open",
        waist=4.0, sink=0.0, shift=(0.0, 0.0), pitch=-6.0),
    "jut_sau": dict(
        hand=lambda s, k: (0.0, Y_PLESSO - k, 0.38),
        pole=lambda s: (0.0, -1.0, 0.22), palm=lambda s: (0.0, -1.0, 0.15),
        wrist=(20.0, 0.0, 0.0), shape="grab",
        waist=3.0, sink=0.012, shift=(0.0, 0.0), pitch=-4.0),
    "jum_sau": dict(
        hand=lambda s, k: (0.0, Y_PLESSO - 0.04 - k, 0.44),
        pole=lambda s: (0.0, -1.0, 0.15), palm=lambda s: (0.0, -0.7, 0.5),
        wrist=(0.0, 0.0, 0.0), shape="open",
        waist=2.0, sink=0.020, shift=(0.0, 0.0), pitch=-4.0),
    "lap_sau": dict(
        hand=lambda s, k: (_s(s) * 0.26, Y_PLESSO - 0.10 - k, 0.20),
        pole=lambda s: (_s(s) * 0.5, -1.0, 0.1), palm=lambda s: (0.0, -1.0, 0.0),
        wrist=(0.0, 0.0, 0.0), shape="grab",
        waist=-7.0, sink=0.015, shift=(0.012, -0.012), pitch=0.0),
    "biu_tze": dict(
        hand=lambda s, k: (0.0, Y_OCCHI - k - 0.02, 0.44),
        pole=lambda s: (0.0, -1.0, 0.45), palm=lambda s: (-_s(s), 0.15, 0.0),
        wrist=(0.0, 0.0, 0.0), shape="spear",
        waist=8.0, sink=-0.010, shift=(0.0, 0.018), pitch=4.0),
}


def _lerp_t(a, b, u):
    return tuple(x + (y - x) * u for x, y in zip(a, b))


def technique(name, side, sink=0.075, amount=1.0, other="chamber"):
    """Una tecnica di mano, eseguibile parzialmente.

    `amount` va da 0 (pugno in camera al fianco) a 1 (tecnica completa).
    Valori appena fuori dall'intervallo danno l'anticipo e la sovracorsa. A
    ogni valore la posizione viene risolta di nuovo con l'IK: e' questo che
    tiene il gomito dove deve stare anche a meta' del movimento.
    """
    spec = TECHNIQUES[name]
    g = _chamber_arm(side, sink)
    u = amount
    p = yjkym(sink + spec["sink"] * u,
              turn=_ew(side, spec["waist"]) * u,
              shift=(spec["shift"][0] * _s(side) * u, spec["shift"][1] * u))
    p.arm(side,
          hand=_lerp_t(g["hand"], spec["hand"](side, sink), u),
          elbow_pole=_lerp_t(g["pole"], spec["pole"](side), u),
          palm=_lerp_t(g["palm"], spec["palm"](side), u),
          wrist=_lerp_t(g["wrist"], spec["wrist"], u))
    _other_hand(p, side, other, sink)
    p.hand(side, spec["shape"], amount=u, base="fist")
    p.hand(_o(side), _shape_for(other))
    return p.look(pitch=spec["pitch"] * max(0.0, min(1.0, u)))


def _named(name):
    def make(side, sink=0.075, other="chamber", amount=1.0):
        return technique(name, side, sink=sink, amount=amount, other=other)
    make.__name__ = name
    make.__doc__ = _DOCS[name]
    return make


_DOCS = {
    "tan_sau": "Tan Sau - la mano che disperde. L'avambraccio sale dal gomito "
               "basso, il palmo guarda in alto, la mano occupa la linea "
               "centrale. E' il gomito, non la mano, a reggere la struttura.",
    "bong_sau": "Bong Sau - l'ala. Il gomito sale, l'avambraccio scende in "
                "diagonale: la forza scivola via sul piano inclinato invece "
                "di essere fermata. Non e' una parata, e' una deviazione.",
    "fook_sau": "Fook Sau - la mano che controlla, posata sopra il ponte "
                "avversario senza spingere.",
    "wu_sau": "Wu Sau - la mano che protegge, palmo verticale sulla linea "
              "centrale.",
    "pak_sau": "Pak Sau - la mano che schiaffeggia, taglia la linea centrale.",
    "gaan_sau": "Gaan Sau - la mano che divide, in diagonale verso il basso.",
    "jut_sau": "Jut Sau - lo strappo secco verso il basso che rompe "
               "l'equilibrio.",
    "jum_sau": "Jum Sau - il gomito che affonda e chiude la linea.",
    "lap_sau": "Lap Sau - la mano che afferra e tira.",
    "biu_tze": "Biu Tze - le dita che infilzano, sulla linea piu' breve verso "
               "il bersaglio.",
}

tan_sau = _named("tan_sau")
bong_sau = _named("bong_sau")
fook_sau = _named("fook_sau")
wu_sau = _named("wu_sau")
pak_sau = _named("pak_sau")
gaan_sau = _named("gaan_sau")
jut_sau = _named("jut_sau")
jum_sau = _named("jum_sau")
lap_sau = _named("lap_sau")
biu_tze = _named("biu_tze")


def chung_kuen(side, sink=0.075, extension=1.0, other="chamber"):
    """Yat Ji Chung Kuen - il pugno verticale lungo la linea centrale.

    Il pugno parte dal centro e arriva al centro. `extension` va da 0
    (pugno in camera, al fianco) a 1 (braccio disteso).
    """
    # la spalla avanza appena e il peso scivola in avanti: il pugno arriva
    # dal corpo, non dal braccio
    p = yjkym(sink - 0.008 * extension, turn=_ew(side, 9.0 * extension),
              shift=(0.0, 0.022 * extension))
    near = np.array([_s(side) * 0.12, Y_PLESSO - 0.06 - sink, 0.14])
    # 96% dell'estensione del braccio: il pugno del Wing Chun arriva quasi
    # disteso ma non si blocca mai. A 0,495 restava a 132 gradi di gomito,
    # troppo piegato per un colpo che deve arrivare a segno.
    far = np.array([0.0, Y_PLESSO + 0.09 - sink, 0.530])
    target = near + (far - near) * extension
    p.fist(side, tuple(target),
           elbow_pole=(_s(side) * (0.35 - 0.3 * extension), -1.0,
                       0.15 + 0.35 * extension),
           knuckles=(-_s(side), 0.0, 0.0))
    _other_hand(p, side, other, sink)
    p.hand(side, "fist").hand(_o(side), _shape_for(other))
    return p.look()


def _o(side):
    return "R" if side == "L" else "L"


def _shape_for(mode):
    return {"wu": "open", "chamber": "fist", "down": "relax"}.get(mode, "open")


def _other_hand(p, side, mode, sink):
    """Posiziona la mano che non esegue la tecnica."""
    o = _o(side)
    if mode == "wu":
        p.arm(o, hand=(0.0, Y_GOLA - 0.11 - sink, 0.265),
              elbow_pole=(0.0, -1.0, 0.18), palm=(-_s(o), 0.30, 0.0))
    elif mode == "chamber":
        # pugno in camera al fianco, gomito indietro
        p.arm(o, hand=(_s(o) * 0.145, Y_PLESSO - 0.08 - sink, -0.02),
              elbow_pole=(_s(o) * 0.6, -1.0, -0.5), palm=(0.0, 1.0, 0.0))
    elif mode == "down":
        p.arm(o, hand=(_s(o) * 0.20, Y_DANTIAN - 0.12 - sink, 0.08),
              elbow_pole=(_s(o) * 0.3, -1.0, 0.2), palm=(0.0, -1.0, 0.0))
    return p


# ---------------------------------------------------------------- calci
def jing_geuk(side, sink=0.06, height=0.60, extension=1.0):
    """Jing Geuk - il calcio frontale, di tallone, lungo la linea centrale.

    Il peso passa sulla gamba d'appoggio; il calcio non sale sopra la vita:
    nel Wing Chun la gamba resta un'arma bassa che non scopre la struttura.
    A piena estensione la gamba e' quasi distesa - un calcio che resta
    piegato non arriva da nessuna parte.
    """
    stand = _o(side)
    p = Pose().torso(lean=-8.0 * extension, sink=sink,
                     shift_x=_s(stand) * 0.045 * extension,
                     shift_z=-0.030 * extension)
    # tutto il peso sulla gamba d'appoggio, sotto il baricentro
    p.leg(stand, ankle=(_s(stand) * 0.055, 0.055, -0.045),
          knee_pole=(-_s(stand) * 0.15, 0.0, 1.0), foot_yaw=-_s(stand) * 12.0)
    # la gamba parte raccolta e si estende: il ginocchio sale, poi la tibia parte
    rest = np.array([_s(side) * 0.10, 0.11, 0.10])
    chamber = np.array([_s(side) * 0.05, 0.50, 0.30])
    strike = np.array([_s(side) * 0.02, height, 0.715])
    if extension < 0.45:
        t = extension / 0.45
        ankle = rest + (chamber - rest) * (t * t * (3 - 2 * t))
    else:
        t = (extension - 0.45) / 0.55
        ankle = chamber + (strike - chamber) * (t * t * (3 - 2 * t))
    p.leg(side, ankle=tuple(ankle), knee_pole=(0.0, 0.30, 1.0),
          foot_yaw=0.0, foot_pitch=-52.0 * extension)
    # il tronco si inclina indietro per contrappesare il calcio: se le mani
    # restassero ferme nel mondo, il braccio dovrebbe allungarsi per
    # raggiungerle. Le mani arretrano con il corpo.
    back = 0.055 * extension
    p.arm("L", hand=(0.0, Y_GOLA - 0.06 - sink, 0.385 - back),
          elbow_pole=(0.0, -1.0, 0.25), palm=(-1.0, 0.3, 0.0))
    p.arm("R", hand=(0.0, Y_GOLA - 0.16 - sink, 0.235 - back * 0.6),
          elbow_pole=(0.0, -1.0, 0.18), palm=(1.0, 0.3, 0.0))
    return p.hands("open").look(pitch=-8.0 * extension)


# ------------------------------------------------------- Chum Kiu: girata
def juen_ma(turn=45.0, sink=0.075, arms="bong_wu", lead="L"):
    """Juen Ma - la girata sui talloni del Chum Kiu.

    L'asse non e' il piede: e' la linea centrale del corpo. Ruotando, la
    struttura devia la forza invece di opporvisi, e il corpo intero -
    non il braccio - genera la potenza. La testa resta sull'avversario.
    """
    p = Pose().torso(hips=turn * 0.72, waist=turn * 0.28, sink=sink)
    for side in ("L", "R"):
        s = _s(side)
        p.leg(side, ankle=(s * 0.155, 0.055, 0.0),
              knee_pole=(-s * 0.30, 0.0, 1.0),
              foot_yaw=-s * 26.0 + turn * 0.85, foot_pitch=3.0)
    if arms == "bong_wu":
        fwd = math.radians(turn)
        d = np.array([math.sin(fwd), 0.0, math.cos(fwd)])
        hand = np.array([0.0, Y_PLESSO - sink + 0.02, 0.0]) + d * 0.36
        p.arm(lead, hand=tuple(hand),
              elbow_pole=(_s(lead) * 0.55, 1.0, 0.45), palm=(0.0, -0.8, 0.6))
        other = _o(lead)
        hand2 = np.array([0.0, Y_GOLA - 0.11 - sink, 0.0]) + d * 0.265
        p.arm(other, hand=tuple(hand2), elbow_pole=(0.0, -1.0, 0.18),
              palm=(-_s(other), 0.30, 0.0))
        p.hand(lead, "relax").hand(other, "open")
    return p.look()
