#!/usr/bin/env python3
"""Verifica che le posizioni del SiFu siano Wing Chun corretto.

    python3 tools/verify_poses.py

Un modello 3D puo' essere tecnicamente valido - file ben formato, mesh chiusa,
animazioni presenti - e mostrare posizioni sbagliate. Per un'app che insegna,
quello e' il difetto peggiore, ed e' l'unico che nessun test di formato
intercetta.

Qui i criteri strutturali del sistema diventano asserzioni: il gomito del Tan
Sau sotto il polso, quello del Bong Sau sopra, la mano sulla linea centrale, il
calcio non oltre la vita, il braccio mai in iperestensione.
"""

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import meshgen  # noqa: E402
import poses  # noqa: E402

ARM_REACH = poses.UPPERARM + poses.FOREARM
CENTRE_TOLERANCE = 0.06      # quanto puo' scostarsi dalla linea centrale
FLOOR = 0.10                 # sotto questa quota il piede e' "a terra"


class Checks:
    def __init__(self):
        self.failures = []
        self.count = 0

    def check(self, group, label, ok, detail=''):
        self.count += 1
        if not ok:
            self.failures.append(f'{group}: {label}' + (f' ({detail})' if detail else ''))
        mark = 'ok  ' if ok else 'NO  '
        print(f'  {mark}{label}' + (f'  [{detail}]' if detail and not ok else ''))

    def group(self, name):
        print(f'\n{name}')


def w(skel, joint):
    return skel.world_pos(joint)


def verify_hand_techniques(c):
    """Le mani: linea centrale, altezza del gomito, portata del braccio."""
    for side in ('L', 'R'):
        sign = poses._s(side)

        c.group(f'Tan Sau ({side})')
        s = poses.tan_sau(side).skel
        hand, elbow, shoulder = w(s, f'hand_{side}'), w(s, f'forearm_{side}'), w(s, f'upperarm_{side}')
        c.check('tan_sau', 'la mano occupa la linea centrale',
                abs(hand[0]) < CENTRE_TOLERANCE, f'x={hand[0]:.3f}')
        c.check('tan_sau', 'il gomito sta sotto il polso',
                elbow[1] < hand[1] - 0.05, f'gomito y={elbow[1]:.3f} polso y={hand[1]:.3f}')
        c.check('tan_sau', 'il gomito resta vicino al corpo',
                0.05 < elbow[2] < 0.32, f'z={elbow[2]:.3f}')
        c.check('tan_sau', 'il braccio non e\' in iperestensione',
                np.linalg.norm(hand - shoulder) < ARM_REACH * 0.998)

        c.group(f'Bong Sau ({side})')
        s = poses.bong_sau(side).skel
        hand, elbow = w(s, f'hand_{side}'), w(s, f'forearm_{side}')
        c.check('bong_sau', 'il gomito sta sopra il polso',
                elbow[1] > hand[1] + 0.05, f'gomito y={elbow[1]:.3f} polso y={hand[1]:.3f}')
        c.check('bong_sau', 'il gomito sale verso la spalla',
                elbow[1] > 1.18, f'y={elbow[1]:.3f}')
        c.check('bong_sau', 'il polso rientra verso il centro',
                abs(hand[0]) < 0.12, f'x={hand[0]:.3f}')

        c.group(f'Fook Sau ({side})')
        s = poses.fook_sau(side).skel
        hand, elbow = w(s, f'hand_{side}'), w(s, f'forearm_{side}')
        c.check('fook_sau', 'la mano e\' sulla linea centrale',
                abs(hand[0]) < CENTRE_TOLERANCE, f'x={hand[0]:.3f}')
        c.check('fook_sau', 'il gomito resta basso',
                elbow[1] < hand[1], f'gomito y={elbow[1]:.3f}')

        c.group(f'Pak Sau ({side})')
        s = poses.pak_sau(side).skel
        hand = w(s, f'hand_{side}')
        c.check('pak_sau', 'lo schiaffo attraversa la linea centrale',
                hand[0] * sign < -0.04, f'x={hand[0]:.3f}')

        c.group(f'Yat Ji Chung Kuen ({side})')
        s = poses.chung_kuen(side).skel
        hand, elbow, shoulder = w(s, f'hand_{side}'), w(s, f'forearm_{side}'), w(s, f'upperarm_{side}')
        c.check('chung_kuen', 'il pugno arriva sulla linea centrale',
                abs(hand[0]) < CENTRE_TOLERANCE, f'x={hand[0]:.3f}')
        c.check('chung_kuen', 'il braccio si estende in avanti',
                hand[2] > 0.42, f'z={hand[2]:.3f}')
        c.check('chung_kuen', 'il gomito scende sotto il pugno',
                elbow[1] < hand[1], f'gomito y={elbow[1]:.3f}')
        c.check('chung_kuen', 'nessuna iperestensione del gomito',
                np.linalg.norm(hand - shoulder) < ARM_REACH * 0.998)
        chambered = poses.chung_kuen(side, extension=0.0).skel
        c.check('chung_kuen', 'a riposo il pugno resta al fianco',
                w(chambered, f'hand_{side}')[2] < 0.22)

        c.group(f'Biu Tze ({side})')
        s = poses.biu_tze(side).skel
        hand = w(s, f'hand_{side}')
        c.check('biu_tze', 'le dita arrivano all\'altezza degli occhi',
                hand[1] > 1.46, f'y={hand[1]:.3f}')
        c.check('biu_tze', 'sulla linea centrale',
                abs(hand[0]) < CENTRE_TOLERANCE, f'x={hand[0]:.3f}')


def verify_stance_and_kick(c):
    c.group('Yee Ji Kim Yeung Ma')
    s = poses.yjkym().skel
    for side in ('L', 'R'):
        sign = poses._s(side)
        ankle, knee, toe = w(s, f'foot_{side}'), w(s, f'shin_{side}'), w(s, f'toe_{side}')
        c.check('yjkym', f'il ginocchio {side} adduce verso il centro',
                abs(knee[0]) < abs(ankle[0]) - 0.02,
                f'ginocchio x={knee[0]:.3f} caviglia x={ankle[0]:.3f}')
        c.check('yjkym', f'la punta del piede {side} guarda all\'interno',
                abs(toe[0]) < abs(ankle[0]), f'punta x={toe[0]:.3f}')
        c.check('yjkym', f'il piede {side} e\' a terra', ankle[1] < FLOOR)
        c.check('yjkym', f'il piede {side} sta dal proprio lato',
                ankle[0] * sign > 0.08)

    c.group('Juen Ma (girata del Chum Kiu)')
    for turn in (45.0, -45.0):
        s = poses.juen_ma(turn).skel
        c.check('juen_ma', f'a {turn:+.0f} gradi i piedi restano a terra',
                w(s, 'foot_L')[1] < FLOOR and w(s, 'foot_R')[1] < FLOOR)
        c.check('juen_ma', f'a {turn:+.0f} gradi il bacino resta al centro',
                abs(w(s, 'root')[0]) < 0.05)

    c.group('Jing Geuk (calcio frontale)')
    for side in ('L', 'R'):
        stand = 'R' if side == 'L' else 'L'
        s = poses.jing_geuk(side).skel
        foot, standing = w(s, f'foot_{side}'), w(s, f'foot_{stand}')
        c.check('jing_geuk', f'il piede {side} si solleva', foot[1] > 0.45,
                f'y={foot[1]:.3f}')
        c.check('jing_geuk', 'il calcio non supera la vita', foot[1] < 1.00,
                f'y={foot[1]:.3f}')
        c.check('jing_geuk', 'il calcio va in avanti', foot[2] > 0.25,
                f'z={foot[2]:.3f}')
        c.check('jing_geuk', f'la gamba d\'appoggio {stand} resta a terra',
                standing[1] < FLOOR)


def verify_mesh(c):
    """La mesh: normali verso l'esterno e figura di dimensioni umane."""
    c.group('Mesh')
    mb = meshgen.build_sifu()
    P = np.array(mb.positions)

    for material, tris in mb.tris.items():
        t = np.array(tris)
        vol = float(np.einsum('ij,ij->i', P[t[:, 0]],
                              np.cross(P[t[:, 1]], P[t[:, 2]])).sum() / 6.0)
        c.check('mesh', f'avvolgimento corretto per "{material}"', vol > 0,
                f'volume con segno = {vol:+.5f}')

    height = float(P[:, 1].max() - P[:, 1].min())
    c.check('mesh', 'la figura e\' alta come una persona',
            1.60 < height < 1.90, f'{height:.2f} m')
    c.check('mesh', 'i pesi di skinning sommano a 1',
            bool(np.allclose(np.array(mb.weights).sum(axis=1), 1.0, atol=1e-4)))
    c.check('mesh', 'ogni vertice e\' legato ad almeno un giunto',
            bool((np.array(mb.weights).max(axis=1) > 0).all()))


def main():
    c = Checks()
    verify_hand_techniques(c)
    verify_stance_and_kick(c)
    verify_mesh(c)

    print(f'\n{"-" * 62}')
    if c.failures:
        print(f'{len(c.failures)} verifiche fallite su {c.count}:')
        for f in c.failures:
            print(f'  - {f}')
        return 1
    print(f'Tutte le {c.count} verifiche superate.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
