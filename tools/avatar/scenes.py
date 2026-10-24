"""The three avatar options. python -I scenes.py [a|b|c|all] [outdir]"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixr as P

HERE = os.path.dirname(os.path.abspath(__file__))
BG = os.path.join(HERE, 'background.png')
SIZE = 200

GOLD = [(255, 249, 191), (255, 235, 140), (251, 220, 125)]
PALE = (255, 249, 191)


def corners(inst):
    pts = []
    for b in inst.boxes:
        for x in (b.frm[0], b.to[0]):
            for y in (b.frm[1], b.to[1]):
                for z in (b.frm[2], b.to[2]):
                    p = b.R @ (np.array([x, y, z]) - b.origin) + b.origin
                    pts.append(inst.M[:3, :3] @ p + inst.M[:3, 3])
    return np.array(pts)


def fit(instances, cam, rect):
    """Scale and centre so the scene's projected bounds fill rect=(x0, y0, x1, y1) in pixels."""
    pts = np.concatenate([corners(i) for i in instances])
    sx, sy = pts @ cam[0], pts @ cam[1]
    x0, y0, x1, y1 = rect
    scale = min((x1 - x0) / (sx.max() - sx.min()), (y1 - y0) / (sy.max() - sy.min()))
    cx = (x0 + x1) / 2 - (sx.max() + sx.min()) / 2 * scale
    cy = (y0 + y1) / 2 + (sy.max() + sy.min()) / 2 * scale
    return scale, (cx, cy)


def screen(cam, scale, center, p):
    p = np.asarray(p, dtype=float)
    return center[0] + (p @ cam[0]) * scale, center[1] - (p @ cam[1]) * scale


def finish(layer, effects_behind=(), effects_front=(), colors=34):
    layer = P.contour(layer)
    layer = P.rim(layer)
    obj = P.quantize(layer['rgba'], colors)
    glow = layer['glow']
    obj[glow] = layer['rgba'][glow]  # lights keep their own colour through the palette cut
    fx_b = np.zeros_like(obj)
    for e in effects_behind:
        fx_b = P.over(fx_b, e)
    fx_f = np.zeros_like(obj)
    for e in effects_front:
        fx_f = P.over(fx_f, e)
    art = P.over(P.over(fx_b, obj), fx_f)
    art = P.outline(art)
    bg = P.background(BG, SIZE)
    return P.over(bg, art)


# ---- A: the deployer sets a brass ingot down on a depot ------------------------------------------------------

EXTEND = 7.0


GOLD_HAND = None


def gold_hand():
    global GOLD_HAND
    if GOLD_HAND is None:
        GOLD_HAND = P.grade('create:block/deployer', sat=1.55, warm=(18, 6, -26), gain=1.04)
    return GOLD_HAND


def deployer_parts(pointing=True):
    """Create's deployer facing +Z: casing (+ pole pushed out by EXTEND), and the hand pushed out with it."""
    item = P.load_model('block/deployer/item', overrides={'18': gold_hand()})
    names = [b.name for b in item]
    cap_i = names.index('Cap')
    casing = item[:cap_i]
    casing += [b for b in item if b.name == 'Axis']
    hand = item[cap_i:len(item) - 1] if pointing else P.load_model('block/deployer/hand_holding', overrides={'18': gold_hand()})
    poles = [b for b in casing if b.name.startswith('Pole')]
    rest = [b for b in casing if not b.name.startswith('Pole')]
    d = np.array([0, 0, EXTEND])
    moved = []
    for b in poles:
        m = b.moved(d)
        m.frm = m.frm.copy()
        m.frm[2] = max(m.frm[2], 2.0)  # nothing of the pole behind the casing's back plate
        moved.append(m)
    return rest + moved, [b.moved(d) for b in hand]


def scene_a(hand_scale=1.3):
    cam = P.camera(45, 30)
    depot = P.Instance(P.load_model('block/depot/block'), P.affine(), 0)
    # a small cogwheel being pressed onto the plate
    cog_tex = {'1_2': P.grade('create:block/cogwheel', sat=1.25, gain=1.12, warm=(6, 2, -4))}
    cog = P.Instance(P.load_model('block/cogwheel_shaftless', overrides=cog_tex),
                     P.around(P.rot_axis('y', 12) * 0.66, (8, 8, 8), (8.0, 14.7, 8.5)), 1)
    # the deployer's axis lies in the picture plane, along the diagonal to the bottom-left corner
    R_dep = P.rot_axis('y', -18.4) @ P.rot_axis('x', 37.6)
    casing, hand = deployer_parts()
    tip = np.array([9, 9, 25 + EXTEND])  # the pointing finger's end
    M_dep = P.around(R_dep, tip, (9.0, 16.9, 7.0))
    cap = np.array([8, 8, 13 + EXTEND])
    S = np.eye(4)
    S[:3, :3] *= hand_scale
    S[:3, 3] = cap - cap * hand_scale
    insts = [depot, cog, P.Instance(casing, M_dep, 2), P.Instance(hand, M_dep @ S, 3)]
    scale, center = fit(insts, cam, (14, 16, 186, 186))
    layer = P.render(insts, cam, SIZE, scale, center)
    hx, hy = screen(cam, scale, center, (M_dep @ S @ np.append(cap + [0, 0, 6], 1))[:3])
    ix, iy = screen(cam, scale, center, (8.5, 14, 8.5))
    behind = [
        P.arc(SIZE, hx + 6, hy + 6, 26, 192, 268, 6, GOLD),
        P.arc(SIZE, hx + 6, hy + 6, 37, 200, 260, 4, GOLD[:2]),
    ]
    front = [
        P.sparkle(SIZE, int(ix - 36), int(iy - 4), 4, GOLD[1]),
        P.sparkle(SIZE, int(ix - 26), int(iy + 22), 2, GOLD[1]),
        P.sparkle(SIZE, int(ix + 32), int(iy - 20), 3, GOLD[1]),
    ]
    return finish(layer, behind, front)


# ---- B: a movie camera built from Create parts ---------------------------------------------------------------

def rotation_between(a, b):
    a = np.asarray(a, float) / np.linalg.norm(a)
    b = np.asarray(b, float) / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(a @ b)
    if np.linalg.norm(v) < 1e-9:
        return np.eye(3) if c > 0 else -np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx * (1 / (1 + c))


def ring_texture(color=(247, 203, 108), dark=(206, 150, 64), border=3):
    t = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge < border:
                t[y, x, :3] = color if (edge == 0 or (x + y) % 6) else dark
                if edge == border - 1:
                    t[y, x, :3] = dark
                t[y, x, 3] = 255
    return t


def lens_texture():
    """Front of the lens: a brass rim, an andesite ring, dark glass shading to blue, a white glint top left."""
    t = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            edge = min(x, y, 15 - x, 15 - y)
            if edge < 2:
                c = (251, 220, 125) if edge == 0 and (x + y) % 5 else (226, 172, 80)
            elif r > 5.9:
                c = (116, 120, 116)
            elif r > 5.0:
                c = (78, 82, 80)
            else:
                k = min(1, r / 5.0)
                c = np.array([16, 20, 32]) * (1 - k) + np.array([52, 78, 122]) * k
            t[y, x, :3] = c
            t[y, x, 3] = 255
    for x, y in ((5, 5), (6, 5), (5, 6), (6, 6), (7, 5), (5, 7), (9, 10), (10, 9)):
        t[y, x, :3] = (236, 244, 255)
    return t


def scene_b():
    cam = P.camera(45, 26)
    casing = 'create:block/andesite_casing'
    body = [P.box([2, 0, -3], [14, 11, 15], casing)]
    gold = P.grade('create:block/brass_casing', sat=1.25, gain=1.08)
    lens = [
        # a brass-rimmed lens on a short andesite barrel
        P.box([3, 0.5, 15], [13, 10.5, 20], {'south': lens_texture(), 'east': 'create:block/andesite_casing_short',
                                            'west': 'create:block/andesite_casing_short', 'up': 'create:block/andesite_casing_short',
                                            'down': 'create:block/andesite_casing_short'}),
    ]
    rec = [P.box([14, 8, 11], [14.6, 9.6, 12.6], P.solid((240, 58, 46)), tag='glow'),
           P.box([14, 8.5, 11.5], [14.7, 9.1, 12.1], P.solid((255, 170, 150)), tag='glow')]
    turn = P.around(P.rot_axis('y', -24), (8, 6, 6), (8, 6, 6))
    insts = [P.Instance(body + lens + rec, turn, 0)]
    # two cogwheels on top as the film reels, their axles across the body
    reel_R = P.rot_axis('z', 90)
    cog_tex = {'1_2': P.grade('create:block/cogwheel', sat=1.3, gain=1.28, warm=(10, 4, -6))}
    insts.append(P.Instance(P.load_model('block/cogwheel', overrides=cog_tex), turn @ P.around(reel_R * 0.72, (8, 8, 8), (8, 16.2, 1.5)), 1))
    insts.append(P.Instance(P.load_model('block/cogwheel', overrides=cog_tex), turn @ P.around(reel_R * 0.6, (8, 8, 8), (8, 15.0, 10.0)), 2))
    scale, center = fit(insts, cam, (26, 22, 174, 178))
    layer = P.render(insts, cam, SIZE, scale, center)
    bx, by = screen(cam, scale, center, (turn @ [8, 16.2, 1.5, 1])[:3])
    sx, sy = screen(cam, scale, center, (turn @ [8, 15.0, 10.0, 1])[:3])
    lx, ly = screen(cam, scale, center, (turn @ [8, 5.5, 20, 1])[:3])
    behind = [
        P.arc(SIZE, bx, by, 27, 215, 320, 5, GOLD),
        P.arc(SIZE, sx, sy, 20, 140, 215, 4, GOLD[:2]),
    ]
    front = [
        P.sparkle(SIZE, int(lx + 6), int(ly + 30), 4, GOLD[1]),
        P.sparkle(SIZE, int(bx - 44), int(by - 14), 2, GOLD[1]),
        P.sparkle(SIZE, int(bx + 30), int(by - 24), 3, GOLD[1]),
    ]
    return finish(layer, behind, front)


# ---- C: the press moment: a Mechanical Press slams onto a depot ----------------------------------------------

def scene_c():
    cam = P.camera(40, 24)
    depot = P.Instance(P.load_model('block/depot/block'), P.affine(), 0)
    sheet_R = P.rot_axis('y', 8) @ P.rot_axis('x', -90)
    sheet = P.Instance(P.sprite_boxes('create:item/brass_sheet'), P.around(sheet_R * 0.9, (8, 8, 8), (8, 13.4, 8)), 1)
    press = P.load_model('block/mechanical_press/block')
    press_i = P.Instance(press, P.affine(t=(0, 32, 0)), 2)
    head = P.Instance(P.load_model('block/mechanical_press/head'), P.affine(t=(0, 14.0, 0)), 3)
    # the shaft that drives it, a cogwheel on its end
    shaft = [P.box([-14, 38, 6], [30, 42, 10], {'north': 'create:block/axis', 'south': 'create:block/axis', 'up': 'create:block/axis',
                                               'down': 'create:block/axis', 'east': 'create:block/axis_top', 'west': 'create:block/axis_top'},
                   uvs={'north': [6, 0, 10, 16], 'south': [6, 0, 10, 16], 'up': [6, 0, 10, 16], 'down': [6, 0, 10, 16]})]
    shaft_R = P.rot_axis('y', 0)
    shaft_i = P.Instance(shaft, P.affine(), 4)
    cog = P.Instance(P.load_model('block/cogwheel_shaftless', overrides={'1_2': P.grade('create:block/cogwheel', sat=1.3, gain=1.28, warm=(10, 4, -6))}),
                     P.around(P.rot_axis('z', 90) * 0.85, (8, 8, 8), (25, 40, 8)), 5)
    insts = [depot, sheet, press_i, head, shaft_i, cog]
    scale, center = fit(insts, cam, (30, 10, 172, 190))
    layer = P.render(insts, cam, SIZE, scale, center)
    ix, iy = screen(cam, scale, center, (8, 13.6, 8))
    cx, cy = screen(cam, scale, center, (25, 40, 8))
    behind = [
        P.ellipse_arc(SIZE, ix, iy + 2, 40, 13, 200, 340, (255, 235, 140), 2),
        P.arc(SIZE, cx, cy, 24, 250, 350, 5, GOLD),
    ]
    front = [
        P.sparks(SIZE, ix - 2, iy, 34, 30, (150, 250), seed=4),
        P.sparks(SIZE, ix + 2, iy, 30, 28, (-75, 25), seed=9),
        P.sparkle(SIZE, int(ix), int(iy - 1), 5, (255, 249, 191)),
        P.sparkle(SIZE, int(ix - 44), int(iy - 30), 4, GOLD[1]),
        P.sparkle(SIZE, int(ix + 46), int(iy - 40), 3, GOLD[1]),
    ]
    return finish(layer, behind, front)


SCENES = {'a': scene_a, 'b': scene_b, 'c': scene_c}

if __name__ == '__main__':
    which = sys.argv[1] if len(sys.argv) > 1 else 'all'
    outdir = sys.argv[2] if len(sys.argv) > 2 else HERE
    os.makedirs(outdir, exist_ok=True)
    for k, fn in SCENES.items():
        if which in ('all', k):
            img = fn()
            P.save(img, os.path.join(outdir, f'option_{k}.png'), 10)
            P.save(img, os.path.join(outdir, f'option_{k}_preview.png'), 4)
            print('wrote', k)
