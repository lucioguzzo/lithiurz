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


class Pose:
    """Costruttore di una posa: prima il corpo, poi gli arti."""

    def __init__(self):
        self.skel = Skeleton()

    # -- tronco ---------------------------------------------------------
    def torso(self, turn=0.0, lean=0.0, sink=0.0):
        """turn: rotazione della vita in gradi (+ = verso sinistra del SiFu).
        lean: inclinazione avanti/indietro. sink: abbassamento del bacino (m).
        """
        self.skel.set_euler("root", lean * 0.25, turn * 0.55, 0.0)
        self.skel.set_euler("spine", lean * 0.4, turn * 0.25, 0.0)
        self.skel.set_euler("chest", lean * 0.35, turn * 0.20, 0.0)
        self.skel.root_offset = np.array([0.0, -sink, 0.0])
        self.skel._dirty = True
        return self

    def head_look(self, yaw=0.0, pitch=0.0):
        self.skel.set_euler("neck", pitch * 0.4, yaw * 0.4, 0.0)
        self.skel.set_euler("head", pitch * 0.6, yaw * 0.6, 0.0)
        return self

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
def yjkym(sink=0.075, turn=0.0):
    """Yee Ji Kim Yeung Ma - la posizione del carattere 'due' che adduce.

    Piedi larghi quanto le spalle, punte rivolte verso l'interno, ginocchia
    addotte verso la linea centrale, bacino retroverso. E' la posizione madre
    da cui nasce tutta la struttura del sistema.
    """
    p = Pose().torso(sink=sink, turn=turn)
    for side, sign in (("L", 1.0), ("R", -1.0)):
        # il pole del ginocchio punta verso l'interno: e' l'adduzione
        # ("kim") che da' il nome alla posizione e chiude la linea bassa
        p.leg(side, ankle=(sign * 0.155, 0.055, 0.0),
              knee_pole=(-sign * 0.30, 0.0, 1.0), foot_yaw=-sign * 26.0,
              foot_pitch=3.0)
    return p


def guardia(sink=0.075, avanti="L"):
    """Man Sau / Wu Sau: la guardia del Wing Chun.

    La mano avanzata (Man Sau, 'mano che interroga') sta sulla linea
    centrale all'altezza della gola; la mano arretrata (Wu Sau, 'mano che
    protegge') copre davanti al plesso, pronta a sostituire la prima.
    """
    dietro = "R" if avanti == "L" else "L"
    sa = -1.0 if avanti == "L" else 1.0
    sd = -1.0 if dietro == "L" else 1.0
    p = yjkym(sink)
    p.arm(avanti, hand=(0.0, Y_GOLA - sink, 0.46),
          elbow_pole=(0.0, -1.0, 0.25), palm=(sa, 0.35, 0.0))
    p.arm(dietro, hand=(0.0, Y_GOLA - 0.10 - sink, 0.24),
          elbow_pole=(0.0, -1.0, 0.2), palm=(sd, 0.30, 0.0))
    return p


def _s(side):
    """+1 per il lato sinistro del SiFu (x positivo), -1 per il destro."""
    return 1.0 if side == "L" else -1.0


# ---------------------------------------------------------------- mani
def tan_sau(side, sink=0.075, other="wu"):
    """Tan Sau - la mano che disperde.

    L'avambraccio sale dal gomito basso, il palmo e' rivolto verso l'alto,
    la mano occupa la linea centrale all'altezza della gola. Il gomito resta
    a circa un pugno dal petto: e' il gomito, non la mano, a reggere la
    struttura.
    """
    p = yjkym(sink)
    p.arm(side, hand=(0.0, Y_GOLA - sink, 0.44),
          elbow_pole=(0.0, -1.0, 0.30), palm=(0.0, 1.0, 0.15))
    _other_hand(p, side, other, sink)
    return p


def bong_sau(side, sink=0.075, other="wu"):
    """Bong Sau - l'ala.

    Il gomito sale all'altezza della spalla, l'avambraccio scende in diagonale
    verso la linea centrale: la forza in arrivo scivola via lungo il piano
    inclinato invece di essere bloccata. Non e' una parata, e' una deviazione.
    """
    p = yjkym(sink)
    p.arm(side, hand=(-_s(side) * 0.03, Y_PLESSO - sink + 0.02, 0.36),
          elbow_pole=(_s(side) * 0.55, 1.0, 0.45), palm=(0.0, -0.8, 0.6))
    _other_hand(p, side, other, sink)
    return p


def fook_sau(side, sink=0.075, other="wu"):
    """Fook Sau - la mano che controlla, posata sopra il ponte avversario."""
    p = yjkym(sink)
    p.arm(side, hand=(0.0, Y_GOLA - 0.06 - sink, 0.42),
          elbow_pole=(0.0, -1.0, 0.28), palm=(0.0, -1.0, 0.2),
          wrist=(28.0, 0.0, 0.0))
    _other_hand(p, side, other, sink)
    return p


def wu_sau(side, sink=0.075, other="wu"):
    """Wu Sau - la mano che protegge, palmo verticale sulla linea centrale."""
    p = yjkym(sink)
    p.arm(side, hand=(0.0, Y_GOLA - 0.10 - sink, 0.24),
          elbow_pole=(0.0, -1.0, 0.18), palm=(-_s(side), 0.30, 0.0))
    _other_hand(p, side, other, sink)
    return p


def pak_sau(side, sink=0.075, other="wu"):
    """Pak Sau - la mano che schiaffeggia, taglia la linea centrale."""
    p = yjkym(sink)
    p.arm(side, hand=(-_s(side) * 0.11, Y_GOLA - 0.05 - sink, 0.38),
          elbow_pole=(_s(side) * 0.25, -1.0, 0.30), palm=(-_s(side), 0.0, 0.35))
    _other_hand(p, side, other, sink)
    return p


def gaan_sau(side, sink=0.075, other="wu"):
    """Gaan Sau - la mano che divide, in diagonale verso il basso."""
    p = yjkym(sink)
    p.arm(side, hand=(_s(side) * 0.18, Y_DANTIAN + 0.06 - sink, 0.44),
          elbow_pole=(_s(side) * 0.2, -0.4, 1.0), palm=(_s(side) * 0.5, 0.4, 0.6))
    _other_hand(p, side, other, sink)
    return p


def jut_sau(side, sink=0.075, other="wu"):
    """Jut Sau - lo strappo secco verso il basso che rompe l'equilibrio."""
    p = yjkym(sink)
    p.arm(side, hand=(0.0, Y_PLESSO - sink, 0.38),
          elbow_pole=(0.0, -1.0, 0.22), palm=(0.0, -1.0, 0.15),
          wrist=(20.0, 0.0, 0.0))
    _other_hand(p, side, other, sink)
    return p


def lap_sau(side, sink=0.075, other="wu"):
    """Lap Sau - la mano che afferra e tira, portando via l'equilibrio."""
    p = yjkym(sink)
    p.arm(side, hand=(_s(side) * 0.26, Y_PLESSO - 0.10 - sink, 0.20),
          elbow_pole=(_s(side) * 0.5, -1.0, 0.1), palm=(0.0, -1.0, 0.0))
    _other_hand(p, side, other, sink)
    return p


def jum_sau(side, sink=0.075, other="wu"):
    """Jum Sau - il gomito che affonda e chiude la linea."""
    p = yjkym(sink)
    p.arm(side, hand=(0.0, Y_PLESSO - 0.04 - sink, 0.44),
          elbow_pole=(0.0, -1.0, 0.15), palm=(0.0, -0.7, 0.5))
    _other_hand(p, side, other, sink)
    return p


def biu_tze(side, sink=0.075, other="wu"):
    """Biu Tze - le dita che infilzano, sulla linea piu' breve verso il bersaglio."""
    p = yjkym(sink, turn=-_s(side) * 6.0)
    p.arm(side, hand=(0.0, Y_OCCHI - sink - 0.02, 0.44),
          elbow_pole=(0.0, -1.0, 0.45), palm=(-_s(side), 0.15, 0.0))
    _other_hand(p, side, other, sink)
    return p


def chung_kuen(side, sink=0.075, extension=1.0, other="chamber"):
    """Yat Ji Chung Kuen - il pugno verticale lungo la linea centrale.

    Il pugno parte dal centro e arriva al centro. `extension` va da 0
    (pugno in camera, al fianco) a 1 (braccio disteso).
    """
    # la spalla del braccio che colpisce avanza appena: il pugno arriva
    # dal corpo, non dal braccio. Non e' una torsione da boxe, sono pochi
    # gradi che restano dentro la struttura.
    p = yjkym(sink, turn=-_s(side) * 7.0 * extension)
    near = np.array([_s(side) * 0.12, Y_PLESSO - 0.06 - sink, 0.14])
    far = np.array([0.0, Y_PLESSO + 0.04 - sink, 0.495])
    target = near + (far - near) * extension
    p.fist(side, tuple(target),
           elbow_pole=(_s(side) * (0.35 - 0.3 * extension), -1.0, 0.15 + 0.35 * extension),
           knuckles=(-_s(side), 0.0, 0.0))
    _other_hand(p, side, other, sink)
    return p


def _other_hand(p, side, mode, sink):
    """Posiziona la mano che non esegue la tecnica."""
    o = "R" if side == "L" else "L"
    if mode == "wu":
        p.arm(o, hand=(0.0, Y_GOLA - 0.11 - sink, 0.23),
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
def jing_geuk(side, sink=0.06, height=0.62, extension=1.0):
    """Jing Geuk - il calcio frontale, di tallone, lungo la linea centrale.

    Il peso passa sulla gamba d'appoggio; il calcio non sale sopra la vita:
    nel Wing Chun la gamba resta un'arma bassa che non scopre la struttura.
    """
    stand = "R" if side == "L" else "L"
    p = Pose().torso(lean=-6.0, sink=sink)
    # tutto il peso sulla gamba d'appoggio, sotto il baricentro
    p.leg(stand, ankle=(_s(stand) * 0.055, 0.055, -0.04),
          knee_pole=(-_s(stand) * 0.15, 0.0, 1.0), foot_yaw=-_s(stand) * 12.0)
    rest = np.array([_s(side) * 0.10, 0.10, 0.10])
    kick = np.array([_s(side) * 0.03, height, 0.44])
    ankle = rest + (kick - rest) * extension
    p.leg(side, ankle=tuple(ankle), knee_pole=(0.0, 0.15, 1.0),
          foot_yaw=0.0, foot_pitch=-40.0 * extension)
    p.arm("L", hand=(0.0, Y_GOLA - 0.06 - sink, 0.40),
          elbow_pole=(0.0, -1.0, 0.25), palm=(-1.0, 0.3, 0.0))
    p.arm("R", hand=(0.0, Y_GOLA - 0.16 - sink, 0.22),
          elbow_pole=(0.0, -1.0, 0.18), palm=(1.0, 0.3, 0.0))
    return p


# ------------------------------------------------------- Chum Kiu: girata
def juen_ma(turn=45.0, sink=0.075, arms="bong_wu", lead="L"):
    """Juen Ma - la girata sui talloni del Chum Kiu.

    L'asse non e' il piede: e' la linea centrale del corpo. Ruotando, la
    struttura devia la forza invece di opporvisi, e il corpo intero -
    non il braccio - genera la potenza.
    """
    p = Pose().torso(turn=turn, sink=sink)
    sgn = 1.0 if turn >= 0 else -1.0
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
        other = "R" if lead == "L" else "L"
        hand2 = np.array([0.0, Y_GOLA - 0.11 - sink, 0.0]) + d * 0.23
        p.arm(other, hand=tuple(hand2), elbow_pole=(0.0, -1.0, 0.18),
              palm=(-_s(other), 0.30, 0.0))
    return p
