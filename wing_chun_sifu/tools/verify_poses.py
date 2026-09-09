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

import kinematics as K  # noqa: E402
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


def elbow_angle(skel, side):
    """Angolo interno del gomito in gradi. 180 = braccio bloccato disteso.

    E' il numero che dice se una posizione e' Wing Chun o no. Il sistema
    tiene il gomito piegato e vivo: un braccio teso non regge pressione,
    non sente nulla e non ha piu' niente da dare.
    """
    a = w(skel, f'upperarm_{side}') - w(skel, f'forearm_{side}')
    b = w(skel, f'hand_{side}') - w(skel, f'forearm_{side}')
    a = a / (np.linalg.norm(a) or 1.0)
    b = b / (np.linalg.norm(b) or 1.0)
    return float(np.degrees(np.arccos(max(-1.0, min(1.0, float(np.dot(a, b)))))))


# Intervallo dell'angolo del gomito ammesso per ogni tecnica.
# Il limite superiore e' il vero vincolo: oltre i 150 gradi il braccio e'
# praticamente disteso e la struttura non regge piu' nulla.
ELBOW_RANGE = {
    'guardia': (100.0, 140.0), 'tan_sau': (100.0, 140.0),
    'fook_sau': (95.0, 140.0), 'bong_sau': (55.0, 130.0),
    'wu_sau': (65.0, 125.0), 'pak_sau': (75.0, 140.0),
    'biu_tze': (95.0, 155.0), 'chung_kuen': (100.0, 155.0),
    'jum_sau': (75.0, 140.0), 'gaan_sau': (85.0, 150.0),
}


def verify_elbows(c):
    c.group('Angolo del gomito (il braccio non si distende mai del tutto)')
    cases = [
        ('guardia', lambda s: poses.guardia(avanti=s), True),
        ('tan_sau', poses.tan_sau, False),
        ('fook_sau', poses.fook_sau, False),
        ('bong_sau', poses.bong_sau, False),
        ('wu_sau', poses.wu_sau, False),
        ('pak_sau', poses.pak_sau, False),
        ('jum_sau', poses.jum_sau, False),
        ('gaan_sau', poses.gaan_sau, False),
        ('biu_tze', poses.biu_tze, False),
        ('chung_kuen', poses.chung_kuen, False),
    ]
    for name, fn, is_guard in cases:
        lo, hi = ELBOW_RANGE[name]
        for side in ('L', 'R'):
            skel = fn(side).skel
            ang = elbow_angle(skel, side)
            c.check(name, f'{name} ({side}): gomito piegato',
                    lo <= ang <= hi, f'{ang:.0f} gradi, atteso {lo:.0f}-{hi:.0f}')


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


def verify_animations(c):
    """Il gomito deve restare piegato *durante* il movimento, non solo alla fine.

    E' il controllo che conta di piu': due posizioni chiave corrette possono
    essere collegate da una traiettoria sbagliata, e il braccio che si
    distende a meta' strada e' esattamente cio' che fa sembrare scoordinato
    un movimento altrimenti giusto. L'unico caso in cui il braccio puo'
    essere disteso e' quando pende lungo il fianco.
    """
    import animations
    from kinematics import Skeleton

    # Un colpo arriva quasi disteso: e' cosi' che deve essere. Una
    # deviazione no - se il braccio si distende ha gia' perso la struttura.
    # Un limite unico per entrambi sarebbe sbagliato in un senso o nell'altro.
    STRIKES = {"chung_kuen", "lin_wan_kuen", "pak_da", "bong_lap_sau",
               "biu_tze"}

    def at(ch_times, quats, t):
        """Valore di un canale al tempo t.

        Dopo la semplificazione ogni canale ha i propri tempi: indicizzarli
        per numero di fotogramma confronterebbe istanti diversi fra loro.
        """
        if t <= ch_times[0]:
            return quats[0]
        if t >= ch_times[-1]:
            return quats[-1]
        i = int(np.searchsorted(ch_times, t)) - 1
        i = max(0, min(i, len(ch_times) - 2))
        span = ch_times[i + 1] - ch_times[i]
        u = 0.0 if span <= 0 else (t - ch_times[i]) / span
        return K.slerp(quats[i], quats[i + 1], u)

    c.group('Traiettoria del gomito lungo le animazioni')
    for clip in animations.build_clips():
        times, channels, _ = clip.bake()
        worst = {'L': (0.0, 0.0), 'R': (0.0, 0.0)}
        # campiona a passo fisso, indipendente dai tempi dei singoli canali
        steps = max(24, int(clip.duration * 20))
        for k in range(steps + 1):
            t = clip.duration * k / steps
            skel = Skeleton()
            for j, (ch_times, quats) in channels.items():
                skel.set(j, at(ch_times, quats, t))
            for side in ('L', 'R'):
                ang = elbow_angle(skel, side)
                hand_y = float(w(skel, f'hand_{side}')[1])
                hip_y = float(w(skel, 'root')[1])
                # braccio disteso ammesso solo se pende sotto il bacino
                if hand_y < hip_y and ang > 150.0:
                    continue
                if ang > worst[side][0]:
                    worst[side] = (ang, t)
        limit = 158.0 if clip.name in STRIKES else 150.0
        for side in ('L', 'R'):
            ang, when = worst[side]
            c.check(clip.name, f'{clip.name} ({side}): gomito mai bloccato',
                    ang <= limit,
                    f'{ang:.0f} gradi a {when:.2f}s, limite {limit:.0f}')


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
    verify_elbows(c)
    verify_hand_techniques(c)
    verify_stance_and_kick(c)
    verify_animations(c)
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
