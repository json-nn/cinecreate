"""2D finishing pass: background, white sticker outline, painted effects. python -I post.py <prefix> <outdir>"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
BG = os.path.join(HERE, '..', 'background.png')
SS = 3  # supersampling for the painted effects

PALE = (255, 249, 214)
GOLD = (255, 226, 132)
DEEP = (246, 190, 86)
WHITE = (255, 255, 255)


def grow(mask, radius):
    m = Image.fromarray((mask * 255).astype(np.uint8), 'L')
    done = 0
    while done < radius:
        step = min(3, radius - done)
        m = m.filter(ImageFilter.MaxFilter(2 * step + 1))
        done += step
    m = m.filter(ImageFilter.GaussianBlur(1.4))
    return np.array(m).astype(float) / 255


class Layer:
    """A transparent drawing layer at SS x the final size, drawn with anti-aliasing when scaled down."""

    def __init__(self, size):
        self.size = size
        self.img = Image.new('RGBA', (size * SS, size * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def P(self, x, y):
        return x * SS, y * SS

    def star(self, x, y, r, color=GOLD, core=WHITE, glow=True):
        pts = []
        for k in range(8):
            a = math.pi / 4 * k - math.pi / 2
            rr = r if k % 2 == 0 else r * 0.24
            pts.append(self.P(x + math.cos(a) * rr, y + math.sin(a) * rr))
        self.d.polygon(pts, fill=color + (255,))
        inner = []
        for k in range(8):
            a = math.pi / 4 * k - math.pi / 2
            rr = r * 0.42 if k % 2 == 0 else r * 0.12
            inner.append(self.P(x + math.cos(a) * rr, y + math.sin(a) * rr))
        self.d.polygon(inner, fill=core + (255,))

    def dot(self, x, y, r, color):
        self.d.ellipse([*self.P(x - r, y - r), *self.P(x + r, y + r)], fill=color)

    def swoosh(self, cx, cy, r, a0, a1, width, color=GOLD, edge=WHITE, edge_w=7):
        """A tapered arc (angles in degrees, 0 = right, 90 = down), thick in the middle, a white rim round it."""
        n = 64
        outer, inner = [], []
        for i in range(n + 1):
            u = i / n
            a = math.radians(a0 + (a1 - a0) * u)
            w = width * math.sin(math.pi * u) ** 0.7
            outer.append(self.P(cx + math.cos(a) * (r + w / 2), cy + math.sin(a) * (r + w / 2)))
            inner.append(self.P(cx + math.cos(a) * (r - w / 2), cy + math.sin(a) * (r - w / 2)))
        poly = outer + inner[::-1]
        self.d.line(poly + [poly[0]], fill=edge + (255,), width=edge_w * SS, joint='curve')
        self.d.polygon(poly, fill=color + (255,))

    def streak(self, p0, p1, width, bow=0.08, color=GOLD, edge=WHITE, edge_w=7):
        """A tapered motion stroke from p0 (thin tail) to p1 (thick head), slightly bowed."""
        p0, p1 = np.array(p0, float), np.array(p1, float)
        d = p1 - p0
        n = np.array([-d[1], d[0]]) / (np.linalg.norm(d) + 1e-9)
        mid = (p0 + p1) / 2 + n * np.linalg.norm(d) * bow
        left, right = [], []
        for i in range(41):
            t = i / 40
            q = (1 - t) ** 2 * p0 + 2 * (1 - t) * t * mid + t ** 2 * p1
            tang = 2 * (1 - t) * (mid - p0) + 2 * t * (p1 - mid)
            nn = np.array([-tang[1], tang[0]]) / (np.linalg.norm(tang) + 1e-9)
            w = width * (t ** 0.8) * (1 - t ** 6)
            left.append(self.P(*(q + nn * w / 2)))
            right.append(self.P(*(q - nn * w / 2)))
        poly = left + right[::-1]
        self.d.line(poly + [poly[0]], fill=edge + (255,), width=edge_w * SS, joint='curve')
        self.d.polygon(poly, fill=color + (255,))

    def puff(self, x, y, r, color=(236, 240, 250, 230)):
        for dx, dy, k in ((0, 0, 1), (-0.8, 0.25, 0.7), (0.8, 0.3, 0.75), (0.1, -0.45, 0.6)):
            self.dot(x + dx * r, y + dy * r, r * k, color)

    def done(self, glow=0):
        out = self.img.resize((self.size, self.size), Image.Resampling.LANCZOS)
        if glow:
            g = out.filter(ImageFilter.GaussianBlur(glow))
            a = np.array(g).astype(float)
            a[..., 3] *= 0.55
            g = Image.fromarray(a.astype(np.uint8), 'RGBA')
            g.alpha_composite(out)
            out = g
        return out


def effects(variant, m, hw, size):
    """Effects placed in units of hw, half the depot's width on screen."""
    behind, front = Layer(size), Layer(size)
    px, py = m['plate']
    gx, gy = m['glow']
    sx, sy = m['shoulder']
    arm = np.array([sx - px, sy - py])
    arm /= np.linalg.norm(arm)
    side = np.array([-arm[1], arm[0]])  # to the upper left of the arm
    if side[1] > 0:
        side = -side
    if variant == 'rest':
        front.star(gx - 0.78 * hw, gy - 0.40 * hw, 0.15 * hw)
        front.star(gx + 0.74 * hw, gy - 0.02 * hw, 0.10 * hw)
        front.star(gx - 0.50 * hw, gy + 0.46 * hw, 0.07 * hw)
        for dx, dy, r in ((-0.52, -0.06, 0.024), (-0.40, -0.30, 0.018), (-0.62, -0.46, 0.014), (0.50, 0.12, 0.018)):
            front.dot(gx + dx * hw, gy + dy * hw, r * hw, GOLD + (240,))
    elif variant == 'flat':
        front.star(px - 0.88 * hw, py - 0.50 * hw, 0.15 * hw)
        front.star(px + 0.80 * hw, py - 0.08 * hw, 0.095 * hw)
        front.star(px - 0.56 * hw, py + 0.50 * hw, 0.07 * hw)
        for dx, dy, r in ((-0.98, -0.16, 0.022), (-0.78, -0.78, 0.017), (0.96, -0.42, 0.019)):
            front.dot(px + dx * hw, py + dy * hw, r * hw, GOLD + (240,))
    else:  # place: streaks beside the arm show the way it came down
        base = np.array([px, py]) - np.array([0, 0.55 * hw])
        for off, a, b, w in ((0.58, 0.30, 0.95, 0.085), (0.86, 0.42, 0.98, 0.06), (0.34, 0.80, 1.18, 0.05)):
            p0 = base + side * off * hw + arm * b * hw
            p1 = base + side * off * hw + arm * a * hw
            behind.streak(p0, p1, w * hw)
        front.puff(px - 0.60 * hw, py + 0.10 * hw, 0.085 * hw)
        front.puff(px + 0.58 * hw, py + 0.16 * hw, 0.07 * hw)
        front.star(px - 0.92 * hw, py - 0.62 * hw, 0.13 * hw)
        front.star(px + 0.86 * hw, py - 0.30 * hw, 0.09 * hw)
        front.star(px - 0.30 * hw, py + 0.56 * hw, 0.065 * hw)
    return behind.done(), front.done(glow=0.025 * hw)


def compose(prefix, variant, outdir):
    art = Image.open(os.path.join(HERE, f'{prefix}_{variant}.png')).convert('RGBA')
    size = art.width
    with open(os.path.join(HERE, f'{prefix}_{variant}.json')) as f:
        info = json.load(f)
    marks, hw = info['marks'], info['hw']
    bg = Image.open(BG).convert('RGBA').resize((size, size), Image.Resampling.LANCZOS)
    circle = np.array(bg)[..., 3].astype(float) / 255
    a = np.array(art).astype(float)
    a[..., 3] *= circle  # nothing past the edge of the badge: the sleeve comes in from outside it
    art = Image.fromarray(a.astype(np.uint8), 'RGBA')
    behind, front = effects(variant, marks, hw, size)
    for layer in (behind, front):  # effects stay on the badge too
        la = np.array(layer).astype(float)
        la[..., 3] *= circle
        layer.paste(Image.fromarray(la.astype(np.uint8), 'RGBA'))
    # one silhouette for the sticker outline: the art and its effects
    sil = np.maximum(np.array(art)[..., 3], np.maximum(np.array(behind)[..., 3], np.array(front)[..., 3])) / 255 > 0.5
    ring = grow(sil.astype(float), int(size * 0.0085)) * circle
    white = np.zeros((size, size, 4), np.uint8)
    white[..., :3] = 255
    white[..., 3] = (np.clip(ring, 0, 1) * 255).astype(np.uint8)
    out = bg.copy()
    out.alpha_composite(Image.fromarray(white, 'RGBA'))
    out.alpha_composite(behind)
    out.alpha_composite(art)
    out.alpha_composite(front)
    os.makedirs(outdir, exist_ok=True)
    path = os.path.join(outdir, f'avatar_hand_{variant}_{size}.png')
    out.save(path)
    out.resize((512, 512), Image.Resampling.LANCZOS).save(os.path.join(outdir, f'avatar_hand_{variant}_512.png'))
    return out


if __name__ == '__main__':
    prefix = sys.argv[1] if len(sys.argv) > 1 else 'final'
    outdir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, 'out')
    outs = [compose(prefix, v, outdir) for v in ('rest', 'flat', 'place')]
    tile = 640
    sheet = Image.new('RGBA', (3 * tile + 4 * 30, tile + 60), (236, 238, 242, 255))
    for i, o in enumerate(outs):
        t = o.resize((tile, tile), Image.Resampling.LANCZOS)
        sheet.alpha_composite(t, (30 + i * (tile + 30), 30))
    sheet.save(os.path.join(outdir, 'avatar_hand_options.png'))
    print('ok')
