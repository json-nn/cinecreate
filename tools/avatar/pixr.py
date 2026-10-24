"""Tiny pixel-art ray caster for Minecraft block models (Blockbench JSON elements) plus a pixel-art post pass."""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, 'assets', 'assets')

FACE_NORMALS = {
    'west': (-1, 0, 0), 'east': (1, 0, 0), 'down': (0, -1, 0),
    'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1),
}
FACE_ID = {name: i for i, name in enumerate(FACE_NORMALS)}

_tex_cache = {}


def tex(ref):
    """A texture as an RGBA uint8 array: 'create:block/x', or an array already."""
    if isinstance(ref, np.ndarray):
        return ref
    if ref in _tex_cache:
        return _tex_cache[ref]
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    p = os.path.join(ASSETS, ns, 'textures', path + '.png')
    a = np.array(Image.open(p).convert('RGBA'))
    if a.shape[0] > a.shape[1]:  # animated strip: first frame
        a = a[:a.shape[1]]
    _tex_cache[ref] = a
    return a


def grade(ref, sat=1.0, warm=(0, 0, 0), gain=1.0):
    """A copy of a texture with its colours pushed (saturation, warmth, brightness), e.g. brass towards gold."""
    a = tex(ref).astype(float)
    rgb = a[..., :3]
    g = rgb.mean(-1, keepdims=True)
    rgb = (g + (rgb - g) * sat) * gain + np.array(warm)
    a[..., :3] = np.clip(rgb, 0, 255)
    return a.astype(np.uint8)


def solid(rgb, a=255):
    return np.array([[list(rgb) + [a]]], dtype=np.uint8)


def rot_axis(axis, deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Box:
    def __init__(self, frm, to, faces, rotation=None, name='', tag=None):
        self.frm = np.array(frm, dtype=float)
        self.to = np.array(to, dtype=float)
        self.faces = faces  # name -> dict(tex, uv, rot)
        self.name = name
        self.tag = tag
        if rotation and rotation.get('angle', 0):
            self.R = rot_axis(rotation['axis'], rotation['angle'])
            self.origin = np.array(rotation['origin'], dtype=float)
        else:
            self.R = np.eye(3)
            self.origin = np.zeros(3)

    def moved(self, d):
        b = Box(self.frm + d, self.to + d, self.faces, None, self.name, self.tag)
        b.R, b.origin = self.R, self.origin + d
        return b


def load_model(rel, overrides=None, tag=None, keep=None):
    """Elements of assets/create/models/<rel>.json; keep(element_dict, index) -> bool filters them."""
    with open(os.path.join(ASSETS, 'create', 'models', rel + '.json')) as f:
        m = json.load(f)
    textures = dict(m.get('textures', {}))
    if overrides:
        textures.update(overrides)

    def resolve(r):
        for _ in range(10):
            if not isinstance(r, str) or not r.startswith('#'):
                return r
            r = textures.get(r[1:], r)
        return r

    boxes = []
    for i, e in enumerate(m.get('elements', [])):
        if keep is not None and not keep(e, i):
            continue
        faces = {}
        for fname, f in e['faces'].items():
            r = resolve(f['texture'])
            if isinstance(r, str) and r.startswith('#'):
                continue
            faces[fname] = dict(tex=tex(r) if isinstance(r, str) else r, uv=f.get('uv'), rot=f.get('rotation', 0))
        boxes.append(Box(e['from'], e['to'], faces, e.get('rotation'), e.get('name', ''), tag))
    return boxes


def box(frm, to, texmap, tag=None, rotation=None, uvs=None):
    """A box with a texture (or a dict of textures) per face; uvs: optional dict of explicit uv per face."""
    faces = {}
    for name in FACE_NORMALS:
        t = texmap.get(name) if isinstance(texmap, dict) else texmap
        if t is None:
            continue
        faces[name] = dict(tex=tex(t), uv=(uvs or {}).get(name), rot=0)
    return Box(frm, to, faces, rotation, tag=tag)


def sprite_boxes(texture, z0=7.5, z1=8.5, tag=None, side_shade=0.72):
    """An item sprite extruded like Minecraft draws items: one 1x1 box per opaque texel."""
    t = tex(texture)
    h, w = t.shape[:2]
    out = []
    for y in range(h):
        for x in range(w):
            px = t[y, x]
            if px[3] < 128:
                continue
            front = solid(px[:3])
            side = solid((px[:3] * side_shade).astype(np.uint8))
            fx = x * 16 / w
            fy = (h - 1 - y) * 16 / h
            out.append(box([fx, fy, z0], [fx + 16 / w, fy + 16 / h, z1],
                           {'south': front, 'north': front, 'east': side, 'west': side, 'up': side, 'down': side}, tag))
    return out


def affine(R=np.eye(3), t=(0, 0, 0), s=1.0):
    M = np.eye(4)
    M[:3, :3] = np.asarray(R) * s
    M[:3, 3] = t
    return M


def around(R, pivot, to):
    """Rotate about a model-space pivot, then put that pivot at a world point."""
    pivot = np.asarray(pivot, dtype=float)
    M = np.eye(4)
    M[:3, :3] = R
    M[:3, 3] = np.asarray(to, dtype=float) - R @ pivot
    return M


class Instance:
    def __init__(self, boxes, M, oid):
        self.boxes = boxes
        self.M = M
        self.oid = oid


def camera(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    b = np.array([math.sin(y) * math.cos(p), math.sin(p), math.cos(y) * math.cos(p)])
    r = np.cross([0, 1, 0], b)
    r /= np.linalg.norm(r)
    u = np.cross(b, r)
    return np.stack([r, u, b])  # rows: right, up, back (towards the viewer)


def face_coords(face, P, frm, to):
    """(s, t) in [0, 1] across a face, s to the right and t downwards as Minecraft maps uvs."""
    d = np.maximum(to - frm, 1e-6)
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    if face == 'north':
        return (to[0] - x) / d[0], (to[1] - y) / d[1]
    if face == 'south':
        return (x - frm[0]) / d[0], (to[1] - y) / d[1]
    if face == 'east':
        return (to[2] - z) / d[2], (to[1] - y) / d[1]
    if face == 'west':
        return (z - frm[2]) / d[2], (to[1] - y) / d[1]
    if face == 'up':
        return (x - frm[0]) / d[0], (z - frm[2]) / d[2]
    return (x - frm[0]) / d[0], (to[2] - z) / d[2]  # down


def default_uv(face, frm, to):
    fx, fy, fz = frm
    tx, ty, tz = to
    return {
        'north': [16 - tx, 16 - ty, 16 - fx, 16 - fy], 'south': [fx, 16 - ty, tx, 16 - fy],
        'east': [16 - tz, 16 - ty, 16 - fz, 16 - fy], 'west': [fz, 16 - ty, tz, 16 - fy],
        'up': [fx, fz, tx, tz], 'down': [fx, 16 - tz, tx, 16 - fz],
    }[face]


def sample(face_def, face, P, frm, to):
    s, t = face_coords(face, P, frm, to)
    s, t = np.clip(s, 0, 0.99999), np.clip(t, 0, 0.99999)
    rot = face_def.get('rot', 0) % 360
    if rot == 90:
        s, t = t, 1 - s
    elif rot == 180:
        s, t = 1 - s, 1 - t
    elif rot == 270:
        s, t = 1 - t, s
    uv = face_def['uv'] or default_uv(face, frm, to)
    u = uv[0] + s * (uv[2] - uv[0])
    v = uv[1] + t * (uv[3] - uv[1])
    img = face_def['tex']
    h, w = img.shape[:2]
    if h == 1 and w == 1:
        return np.broadcast_to(img[0, 0], P.shape[:-1] + (4,))
    ix = np.clip((u * w / 16).astype(int), 0, w - 1)
    iy = np.clip((v * h / 16).astype(int), 0, h - 1)
    return img[iy, ix]


def _local(inst, b):
    Minv = np.linalg.inv(inst.M)
    A = b.R.T @ Minv[:3, :3]
    c = b.R.T @ (Minv[:3, 3] - b.origin) + b.origin
    return A, c


def _slab(O, D, frm, to):
    with np.errstate(divide='ignore', invalid='ignore'):
        Dsafe = np.where(np.abs(D) < 1e-9, 1e-9, D)
        t1 = (frm - O) / Dsafe
        t2 = (to - O) / Dsafe
    tlo = np.minimum(t1, t2)
    thi = np.maximum(t1, t2)
    tmin = tlo.max(-1)
    tmax = thi.min(-1)
    axis = tlo.argmax(-1)
    return tmin, tmax, axis


def trace(instances, Ow, Dw, want_color=True, eps=1e-4):
    """Nearest opaque hit for rays (any leading shape). Returns t, rgb, world normal, object id, box key."""
    shape = Ow.shape[:-1]
    best = np.full(shape, np.inf)
    rgb = np.zeros(shape + (3,))
    nrm = np.zeros(shape + (3,))
    oid = np.full(shape, -1)
    key = np.full(shape, -1)
    glow = np.zeros(shape, bool)
    Dw = np.broadcast_to(Dw, Ow.shape)
    k = 0
    for inst in instances:
        for b in inst.boxes:
            k += 1
            A, c = _local(inst, b)
            O = Ow @ A.T + c
            D = Dw @ A.T
            tmin, tmax, axis = _slab(O, D, b.frm, b.to)
            hit = (tmax >= tmin) & (tmax > eps) & (tmin > eps) & (tmin < best)
            if not hit.any():
                continue
            P = O + D * tmin[..., None]
            Dax = np.take_along_axis(D, axis[..., None], -1)[..., 0]
            names = np.where(axis == 0, np.where(Dax > 0, 0, 1),
                             np.where(axis == 1, np.where(Dax > 0, 2, 3), np.where(Dax > 0, 4, 5)))
            face_names = ['west', 'east', 'down', 'up', 'north', 'south']
            for fi, fname in enumerate(face_names):
                m = hit & (names == fi)
                if not m.any():
                    continue
                fd = b.faces.get(fname)
                if fd is None:
                    continue
                col = sample(fd, fname, P[m], b.frm, b.to)
                opaque = col[..., 3] >= 128
                idx = np.nonzero(m)
                sel = tuple(ix[opaque] for ix in idx)
                if len(sel[0]) == 0:
                    continue
                best[sel] = tmin[sel]
                if want_color:
                    rgb[sel] = col[opaque][:, :3]
                n_local = np.array(FACE_NORMALS[fname], dtype=float)
                n_world = inst.M[:3, :3] @ (b.R @ n_local)
                n_world /= np.linalg.norm(n_world)
                nrm[sel] = n_world
                oid[sel] = inst.oid
                key[sel] = k * 8 + fi
                glow[sel] = b.tag == 'glow'
    trace.glow = glow
    return best, rgb, nrm, oid, key


BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16


def bayer(h, w, ox=0, oy=0):
    return np.tile(BAYER4, (h // 4 + 2, w // 4 + 2))[oy:oy + h, ox:ox + w]


def render(instances, cam, size, scale, center, light_c=(-0.55, 0.78, 0.45), samples=8, cone=0.12,
           levels=(0.64, 0.78, 0.9, 1.0, 1.1), amb=0.6, seed=1, saturation=1.18):
    """Objects layer at size x size: RGBA array plus id buffers."""
    H = W = size
    xs = (np.arange(W) + 0.5 - center[0]) / scale
    ys = -(np.arange(H) + 0.5 - center[1]) / scale
    X, Y = np.meshgrid(xs, ys)
    Oc = np.stack([X, Y, np.full_like(X, 4000.0)], -1)
    Ow = Oc @ cam
    Dw = np.array([0, 0, -1.0]) @ cam
    t, rgb, nrm, oid, key = trace(instances, Ow, Dw)
    glow = trace.glow
    hit = np.isfinite(t)

    L = np.array(light_c, dtype=float)
    L /= np.linalg.norm(L)
    Lw = L @ cam
    P = Ow + Dw * np.where(hit, t, 0)[..., None]
    rng = np.random.default_rng(seed)
    lit = np.zeros((H, W))
    hp = np.nonzero(hit)
    origins = P[hp] + nrm[hp] * 0.08
    facing = (nrm[hp] @ Lw) > 0
    for _ in range(samples):
        j = Lw + rng.normal(0, cone, 3)
        j /= np.linalg.norm(j)
        ts, *_ = trace(instances, origins, j, want_color=False, eps=0.02)
        lit[hp] += (~np.isfinite(ts)) & facing
    lit /= samples
    ndl = np.clip(nrm @ Lw, 0, 1)
    shade = amb + (1 - amb) * ndl * lit
    # stepped light with ordered dithering between the steps
    lv = np.array(levels)
    pos = np.interp(shade, lv, np.arange(len(lv)))
    q = np.floor(pos + bayer(H, W)).astype(int)
    q = np.clip(q, 0, len(lv) - 1)
    f = lv[q]
    f = np.where(glow, 1.0, f)
    out = rgb * f[..., None]
    # cool shadows, warm light (as an artist would tint them)
    cool = np.clip(1 - f, 0, 1)[..., None]
    out = out * (1 - 0.35 * cool) + np.array([40, 60, 120]) * 0.35 * cool
    warm = np.clip(f - 1, 0, 1)[..., None]
    out = out + np.array([40, 28, 0]) * warm
    grey = out.mean(-1, keepdims=True)
    out = grey + (out - grey) * saturation
    out = np.clip(out, 0, 255)
    rgba = np.zeros((H, W, 4), dtype=np.uint8)
    rgba[..., :3] = out.astype(np.uint8)
    rgba[..., 3] = np.where(hit, 255, 0)
    return dict(rgba=rgba, t=t, oid=oid, key=key, nrm=nrm, hit=hit, shade=f, glow=glow)


def contour(layer, depth_jump=2.5, dark=0.62):
    """Dark 1 px lines where a nearer surface hides a farther one (pixel-art contour lines)."""
    rgba = layer['rgba'].copy()
    t, hit, oid, key = layer['t'], layer['hit'], layer['oid'], layer['key']
    H, W = t.shape
    line = np.zeros((H, W), bool)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        tn = np.roll(np.roll(t, dy, 0), dx, 1)
        hn = np.roll(np.roll(hit, dy, 0), dx, 1)
        on = np.roll(np.roll(oid, dy, 0), dx, 1)
        behind = hit & hn & (tn < t - depth_jump) & ((on != oid) | (tn < t - depth_jump * 1.6))
        line |= behind
    c = rgba[..., :3].astype(float)
    c[line] = c[line] * dark + np.array([20, 24, 40]) * (1 - dark)
    rgba[..., :3] = c.astype(np.uint8)
    layer = dict(layer)
    layer['rgba'] = rgba
    return layer


def rim(layer, boost=1.14):
    """A 1 px light rim on lit convex edges (top-left of each face), as hand-drawn blocks have."""
    rgba = layer['rgba'].copy()
    t, hit, key, f = layer['t'], layer['hit'], layer['key'], layer['shade']
    up = np.roll(key, 1, 0)
    left = np.roll(key, 1, 1)
    tu = np.roll(t, 1, 0)
    tl = np.roll(t, 1, 1)
    with np.errstate(invalid='ignore'):
        edge = hit & (((up != key) & (np.abs(tu - t) < 1.5)) | ((left != key) & (np.abs(tl - t) < 1.5))) & (f >= 0.88)
    c = rgba[..., :3].astype(float)
    c[edge] = np.clip(c[edge] * boost + 10, 0, 255)
    rgba[..., :3] = c.astype(np.uint8)
    layer = dict(layer)
    layer['rgba'] = rgba
    return layer


def quantize(rgba, colors=34):
    """Limit the palette (crisp, no dithering) like a pixel artist's swatch."""
    a = rgba[..., 3] > 0
    img = Image.fromarray(rgba[..., :3])
    q = img.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGB')
    out = rgba.copy()
    out[..., :3] = np.array(q)
    out[..., 3] = np.where(a, 255, 0)
    return out


def dilate(mask, cross=True):
    m = mask.copy()
    m[1:] |= mask[:-1]
    m[:-1] |= mask[1:]
    m[:, 1:] |= mask[:, :-1]
    m[:, :-1] |= mask[:, 1:]
    if not cross:
        m[1:, 1:] |= mask[:-1, :-1]
        m[:-1, :-1] |= mask[1:, 1:]
        m[1:, :-1] |= mask[:-1, 1:]
        m[:-1, 1:] |= mask[1:, :-1]
    return m


def over(dst, src):
    """Alpha-over for uint8 RGBA arrays with 0/255 alpha."""
    out = dst.copy()
    m = src[..., 3] > 0
    out[m] = src[m]
    return out


def outline(rgba, color=(255, 255, 255), width=1):
    mask = rgba[..., 3] > 0
    grown = mask
    for _ in range(width):
        grown = dilate(grown)
    ring = grown & ~mask
    out = rgba.copy()
    out[ring] = list(color) + [255]
    return out


# ---- 2D pixel effects ---------------------------------------------------------------------------------------

def arc(size, cx, cy, r, a0, a1, width, colors):
    """A swoosh: an arc from angle a0 to a1 (degrees, screen space, 0 = right, 90 = down), thicker in the middle."""
    out = np.zeros((size, size, 4), np.uint8)
    yy, xx = np.mgrid[0:size, 0:size]
    dx, dy = xx + 0.5 - cx, yy + 0.5 - cy
    d = np.hypot(dx, dy)
    ang = (np.degrees(np.arctan2(dy, dx)) - a0) % 360
    span = (a1 - a0) % 360
    u = ang / span
    inside = (ang <= span)
    wloc = width * np.sin(np.clip(u, 0, 1) * math.pi) ** 0.6
    band = inside & (np.abs(d - r) <= wloc / 2 + 0.25) & (wloc > 0.6)
    # core colour towards the leading end
    for i, col in enumerate(colors):
        lo = i / len(colors)
        m = band & (u >= lo)
        out[m] = list(col) + [255]
    return out


def sparkle(size, cx, cy, arm, color, core=(255, 255, 255)):
    out = np.zeros((size, size, 4), np.uint8)
    for k in range(-arm, arm + 1):
        for x, y in ((cx + k, cy), (cx, cy + k)):
            if 0 <= x < size and 0 <= y < size:
                out[y, x] = list(color) + [255]
    if arm >= 3:
        for x, y in ((cx - 1, cy - 1), (cx + 1, cy - 1), (cx - 1, cy + 1), (cx + 1, cy + 1)):
            out[y, x] = list(color) + [255]
    out[cy, cx] = list(core) + [255]
    return out


def sparks(size, cx, cy, n, length, spread, seed=3, colors=((255, 255, 255), (255, 235, 140), (251, 190, 90), (240, 140, 60))):
    """Hot sparks flying out of an impact: short streaks, white at the tip and cooling to orange."""
    out = np.zeros((size, size, 4), np.uint8)
    rng = np.random.default_rng(seed)
    for _ in range(n):
        a = math.radians(rng.uniform(*spread))
        ln = rng.uniform(length * 0.45, length)
        r0 = rng.uniform(3, 9)
        steps = int(ln) + 1
        for k in range(steps):
            r = r0 + k
            x = int(round(cx + math.cos(a) * r))
            y = int(round(cy + math.sin(a) * r * 0.75))
            if 0 <= x < size and 0 <= y < size:
                ci = min(len(colors) - 1, int((1 - k / max(1, steps - 1)) * (len(colors) - 1) + 0.5))
                out[y, x] = list(colors[len(colors) - 1 - ci]) + [255]
    return out


def ellipse_arc(size, cx, cy, rx, ry, a0, a1, color, width=1):
    out = np.zeros((size, size, 4), np.uint8)
    for k in range(int(abs(a1 - a0) * 3) + 1):
        a = math.radians(a0 + (a1 - a0) * k / (abs(a1 - a0) * 3))
        for w in range(width):
            x = int(round(cx + math.cos(a) * (rx + w)))
            y = int(round(cy + math.sin(a) * (ry + w * ry / rx)))
            if 0 <= x < size and 0 <= y < size:
                out[y, x] = list(color) + [255]
    return out


def dots(size, pts, color):
    out = np.zeros((size, size, 4), np.uint8)
    for x, y in pts:
        out[y, x] = list(color) + [255]
    return out


# ---- background -----------------------------------------------------------------------------------------------

def background(path, size=200, colors=6):
    """The attached Create background on the pixel grid: its white ring kept pure white, the blues reduced to a few
    swatches with ordered dithering."""
    src = Image.open(path).convert('RGBA').resize((size, size), Image.Resampling.BOX)
    a = np.array(src).astype(float)
    opaque = a[..., 3] > 127
    rgb = a[..., :3]
    white = opaque & (rgb.min(-1) > 200)
    blue = opaque & ~white
    sw = Image.fromarray(rgb[blue].reshape(-1, 1, 3).astype(np.uint8))
    q = sw.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    pal = np.array(q.getpalette()[:colors * 3]).reshape(-1, 3).astype(float)
    jitter = (bayer(size, size) - 0.5)[..., None] * 16
    v = rgb + jitter
    d = ((v[..., None, :] - pal[None, None]) ** 2).sum(-1)
    out = np.zeros((size, size, 4), np.uint8)
    out[..., :3] = pal[d.argmin(-1)].astype(np.uint8)
    out[..., 3] = np.where(blue, 255, 0)
    out[white] = [255, 255, 255, 255]
    return out


def save(rgba, path, scale=10):
    img = Image.fromarray(rgba, 'RGBA')
    img.resize((img.width * scale, img.height * scale), Image.Resampling.NEAREST).save(path)
