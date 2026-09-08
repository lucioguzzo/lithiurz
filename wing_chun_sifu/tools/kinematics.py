"""Cinematica diretta e inversa per il rig del SiFu.

Le posizioni del Wing Chun non si descrivono bene con angoli di Eulero
scritti a mano: si descrivono con "dove va la mano", "dove punta il gomito"
e "dove guarda il palmo". Il gomito basso e sulla linea centrale e' la
firma strutturale dello stile, ed e' esattamente il pole vector dell'IK.

Per questo le pose sono definite da target anatomici e risolte
numericamente: gli angoli dei giunti sono un risultato, non un'ipotesi.
"""

import math
import numpy as np

from rig import JOINTS, JOINT_PARENT, JOINT_LOCAL_T, BIND_ROT, euler_to_quat

IDENT = np.array([0.0, 0.0, 0.0, 1.0])


# --- quaternioni (x, y, z, w) ------------------------------------------
def qmul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return np.array([
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz,
    ])


def qconj(q):
    return np.array([-q[0], -q[1], -q[2], q[3]])


def qrot(q, v):
    qv = np.array([v[0], v[1], v[2], 0.0])
    return qmul(qmul(q, qv), qconj(q))[:3]


def qnorm(q):
    n = np.linalg.norm(q)
    return q / n if n > 1e-12 else IDENT.copy()


def axis_angle(axis, angle):
    a = np.asarray(axis, dtype=np.float64)
    n = np.linalg.norm(a)
    if n < 1e-12:
        return IDENT.copy()
    a = a / n
    h = angle * 0.5
    s = math.sin(h)
    return np.array([a[0] * s, a[1] * s, a[2] * s, math.cos(h)])


def shortest_arc(u, v):
    """Rotazione minima che porta il versore u sul versore v."""
    u = np.asarray(u, dtype=np.float64)
    v = np.asarray(v, dtype=np.float64)
    u = u / (np.linalg.norm(u) or 1.0)
    v = v / (np.linalg.norm(v) or 1.0)
    d = float(np.dot(u, v))
    if d > 0.999999:
        return IDENT.copy()
    if d < -0.999999:
        ortho = np.array([1.0, 0.0, 0.0])
        if abs(u[0]) > 0.9:
            ortho = np.array([0.0, 1.0, 0.0])
        axis = np.cross(u, ortho)
        return axis_angle(axis, math.pi)
    axis = np.cross(u, v)
    return qnorm(np.array([axis[0], axis[1], axis[2], 1.0 + d]))


def slerp(a, b, t):
    d = float(np.dot(a, b))
    if d < 0.0:
        b, d = -b, -d
    if d > 0.9995:
        return qnorm(a + (b - a) * t)
    th0 = math.acos(max(-1.0, min(1.0, d)))
    th = th0 * t
    s0 = math.sin(th0 - th) / math.sin(th0)
    s1 = math.sin(th) / math.sin(th0)
    return qnorm(a * s0 + b * s1)


# --- posa ---------------------------------------------------------------
BONE_REST_DIR = np.array([0.0, -1.0, 0.0])   # ogni osso punta lungo -Y locale


def default_pose():
    pose = {}
    for name, _, _ in JOINTS:
        rx, ry, rz = BIND_ROT.get(name, (0.0, 0.0, 0.0))
        pose[name] = np.array(euler_to_quat(rx, ry, rz))
    return pose


class Skeleton:
    """Cinematica diretta: dalla posa locale alle trasformate nel mondo."""

    def __init__(self, pose=None, root_offset=(0.0, 0.0, 0.0)):
        self.pose = pose if pose is not None else default_pose()
        self.root_offset = np.array(root_offset, dtype=np.float64)
        self._dirty = True
        self._wq = {}
        self._wp = {}

    def set(self, joint, quat):
        self.pose[joint] = qnorm(np.asarray(quat, dtype=np.float64))
        self._dirty = True

    def set_euler(self, joint, rx, ry, rz):
        self.set(joint, np.array(euler_to_quat(rx, ry, rz)))

    def _solve(self):
        for name, parent, t in JOINTS:
            lt = np.array(JOINT_LOCAL_T[name], dtype=np.float64)
            lq = self.pose[name]
            if parent is None:
                self._wq[name] = lq
                self._wp[name] = lt + self.root_offset
            else:
                pq, pp = self._wq[parent], self._wp[parent]
                self._wq[name] = qmul(pq, lq)
                self._wp[name] = pp + qrot(pq, lt)
        self._dirty = False

    def world_pos(self, joint):
        if self._dirty:
            self._solve()
        return self._wp[joint].copy()

    def world_rot(self, joint):
        if self._dirty:
            self._solve()
        return self._wq[joint].copy()

    def parent_rot(self, joint):
        parent = JOINT_PARENT[joint]
        return IDENT.copy() if parent is None else self.world_rot(parent)


def aim(skel, joint, world_dir, roll=0.0):
    """Orienta l'osso `joint` lungo `world_dir`, con rotazione assiale `roll`.

    Restituisce il quaternione locale da assegnare al giunto.
    """
    pq = skel.parent_rot(joint)
    local_dir = qrot(qconj(pq), np.asarray(world_dir, dtype=np.float64))
    q = shortest_arc(BONE_REST_DIR, local_dir)
    if abs(roll) > 1e-9:
        q = qmul(q, axis_angle(BONE_REST_DIR, roll))
    return q


def two_bone_ik(origin, target, len1, len2, pole):
    """IK analitica a due ossa.

    origin  posizione della spalla (o dell'anca)
    target  posizione desiderata del polso (o della caviglia)
    pole    direzione verso cui deve puntare il gomito (o il ginocchio)

    Ritorna (posizione_gomito, direzione_osso1, direzione_osso2).
    """
    origin = np.asarray(origin, dtype=np.float64)
    target = np.asarray(target, dtype=np.float64)
    to_target = target - origin
    dist = float(np.linalg.norm(to_target))
    reach = len1 + len2
    # non superare l'estensione massima (il braccio resta sempre "vivo",
    # mai bloccato in iperestensione: 99.5% della lunghezza)
    dist = max(abs(len1 - len2) + 1e-4, min(dist, reach * 0.995))
    axis = to_target / (np.linalg.norm(to_target) or 1.0)

    # proiezione del gomito lungo l'asse spalla-polso
    a = (dist * dist + len1 * len1 - len2 * len2) / (2.0 * dist)
    h2 = len1 * len1 - a * a
    h = math.sqrt(max(0.0, h2))

    pole = np.asarray(pole, dtype=np.float64)
    perp = pole - axis * float(np.dot(pole, axis))
    n = np.linalg.norm(perp)
    if n < 1e-6:
        fallback = np.array([0.0, 0.0, 1.0])
        perp = fallback - axis * float(np.dot(fallback, axis))
        n = np.linalg.norm(perp) or 1.0
    perp = perp / n

    elbow = origin + axis * a + perp * h
    d1 = elbow - origin
    d2 = (origin + axis * dist) - elbow
    d1 = d1 / (np.linalg.norm(d1) or 1.0)
    d2 = d2 / (np.linalg.norm(d2) or 1.0)
    return elbow, d1, d2
