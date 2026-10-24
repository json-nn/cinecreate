"""Blender (headless) scene for the cinecreate avatar: a player's hand on a Create depot.

blender --background --factory-startup --python build.py -- <variant: rest|flat|place> <out.png> [size]
Coordinates are Minecraft's (x east, y up, z south, 16 units a block) until mc_to_blender() at the end.
"""
import json
import math
import os
import sys

import bpy
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'assets', 'assets')
args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
VARIANT = args[0] if args else 'rest'
OUT = args[1] if len(args) > 1 else os.path.join(HERE, f'render_{VARIANT}.png')
SIZE = int(args[2]) if len(args) > 2 else 2000

# ---- maths ------------------------------------------------------------------------------------------------

def rot(axis, deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def M4(R=np.eye(3), t=(0, 0, 0), s=1.0):
    M = np.eye(4)
    M[:3, :3] = np.asarray(R) * s
    M[:3, 3] = t
    return M


def around(R, pivot, to, s=1.0):
    pivot = np.asarray(pivot, float)
    M = np.eye(4)
    M[:3, :3] = np.asarray(R) * s
    M[:3, 3] = np.asarray(to, float) - M[:3, :3] @ pivot
    return M


def rotation_between(a, b):
    a = np.asarray(a, float) / np.linalg.norm(a)
    b = np.asarray(b, float) / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(a @ b)
    if np.linalg.norm(v) < 1e-9:
        return np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx * (1 / (1 + c))


def camera_basis(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    b = np.array([math.sin(y) * math.cos(p), math.sin(p), math.cos(y) * math.cos(p)])
    r = np.cross([0, 1, 0], b)
    r /= np.linalg.norm(r)
    u = np.cross(b, r)
    return r, u, b


def mc_to_blender(p):
    x, y, z = p
    return (x / 16.0, -z / 16.0, y / 16.0)


# ---- materials --------------------------------------------------------------------------------------------

_mats = {}


def material(image_path):
    """Cel-shaded: the light is cut into soft bands (cool in shadow, warm at the top), times the pixel texture."""
    if image_path in _mats:
        return _mats[image_path]
    m = bpy.data.materials.new(os.path.basename(image_path))
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(image_path, check_existing=True)
    tex.interpolation = 'Closest'
    tex.extension = 'REPEAT'
    diff = nt.nodes.new('ShaderNodeBsdfDiffuse')
    diff.inputs['Color'].default_value = (1, 1, 1, 1)
    s2r = nt.nodes.new('ShaderNodeShaderToRGB')
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    cr = ramp.color_ramp
    cr.interpolation = 'EASE'
    stops = [(0.00, (0.38, 0.44, 0.70)), (0.22, (0.55, 0.60, 0.82)), (0.45, (0.78, 0.80, 0.91)),
             (0.70, (0.97, 0.97, 0.98)), (1.00, (1.00, 0.97, 0.90))]
    cr.elements[0].position, cr.elements[0].color = stops[0][0], stops[0][1] + (1,)
    cr.elements[1].position, cr.elements[1].color = stops[-1][0], stops[-1][1] + (1,)
    for pos, col in stops[1:-1]:
        e = cr.elements.new(pos)
        e.color = col + (1,)
    bw = nt.nodes.new('ShaderNodeRGBToBW')
    mix = nt.nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    mix.blend_type = 'MULTIPLY'
    mix.inputs[0].default_value = 1.0
    emit = nt.nodes.new('ShaderNodeEmission')
    transp = nt.nodes.new('ShaderNodeBsdfTransparent')
    gt = nt.nodes.new('ShaderNodeMath')
    gt.operation = 'GREATER_THAN'
    gt.inputs[1].default_value = 0.5
    msh = nt.nodes.new('ShaderNodeMixShader')
    L = nt.links
    L.new(diff.outputs[0], s2r.inputs[0])
    L.new(s2r.outputs['Color'], bw.inputs[0])
    ao = nt.nodes.new('ShaderNodeAmbientOcclusion')
    ao.inputs['Distance'].default_value = 0.35
    aomap = nt.nodes.new('ShaderNodeMapRange')
    aomap.inputs['To Min'].default_value = 0.45
    aomap.inputs['To Max'].default_value = 1.0
    mul = nt.nodes.new('ShaderNodeMath')
    mul.operation = 'MULTIPLY'
    L.new(ao.outputs['AO'], aomap.inputs['Value'])
    L.new(bw.outputs[0], mul.inputs[0])
    L.new(aomap.outputs['Result'], mul.inputs[1])
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    grad = nt.nodes.new('ShaderNodeMapRange')
    grad.inputs['From Min'].default_value = 0.0
    grad.inputs['From Max'].default_value = 1.6
    grad.inputs['To Min'].default_value = 0.84
    grad.inputs['To Max'].default_value = 1.06
    mul2 = nt.nodes.new('ShaderNodeMath')
    mul2.operation = 'MULTIPLY'
    L.new(geo.outputs['Position'], sep.inputs[0])
    L.new(sep.outputs['Z'], grad.inputs['Value'])
    L.new(mul.outputs[0], mul2.inputs[0])
    L.new(grad.outputs['Result'], mul2.inputs[1])
    L.new(mul2.outputs[0], ramp.inputs[0])
    col_in = [i for i in mix.inputs if i.type == 'RGBA']
    col_out = [o for o in mix.outputs if o.type == 'RGBA'][0]
    L.new(tex.outputs['Color'], col_in[0])
    L.new(ramp.outputs['Color'], col_in[1])
    L.new(col_out, emit.inputs['Color'])
    L.new(tex.outputs['Alpha'], gt.inputs[0])
    L.new(gt.outputs[0], msh.inputs[0])
    L.new(transp.outputs[0], msh.inputs[1])
    L.new(emit.outputs[0], msh.inputs[2])
    L.new(msh.outputs[0], out.inputs['Surface'])
    try:
        m.surface_render_method = 'DITHERED'
    except Exception:
        pass
    _mats[image_path] = m
    return m


# ---- geometry ---------------------------------------------------------------------------------------------

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def quad(face, f, t):
    """Corners of a face seen from outside: top-left, top-right, bottom-right, bottom-left (Minecraft's uv order)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def default_uv(face, f, t):
    fx, fy, fz = f
    tx, ty, tz = t
    return {
        'north': [16 - tx, 16 - ty, 16 - fx, 16 - fy], 'south': [fx, 16 - ty, tx, 16 - fy],
        'east': [16 - tz, 16 - ty, 16 - fz, 16 - fy], 'west': [fz, 16 - ty, tz, 16 - fy],
        'up': [fx, fz, tx, tz], 'down': [fx, 16 - tz, tx, 16 - fz],
    }[face]


def pixel_uv(face, f, t):
    """One texel per unit on custom boxes, so their pixels match Create's."""
    d = np.array(t) - np.array(f)
    w, h = {'north': (d[0], d[1]), 'south': (d[0], d[1]), 'east': (d[2], d[1]), 'west': (d[2], d[1]),
            'up': (d[0], d[2]), 'down': (d[0], d[2])}[face]
    return [0, 0, w, h]


class Mesh:
    def __init__(self, name):
        self.name = name
        self.verts, self.faces, self.uvs, self.mats = [], [], [], []
        self.mat_index = {}

    def add_box(self, frm, to, faces, M=np.eye(4), elem_rot=None, uvmode='mc'):
        """faces: name -> (image path, uv or None, rotation)."""
        R_e, o_e = np.eye(3), np.zeros(3)
        if elem_rot and elem_rot.get('angle', 0):
            R_e = rot(elem_rot['axis'], elem_rot['angle'])
            o_e = np.array(elem_rot['origin'], float)
        for face, (img, uv, frot) in faces.items():
            corners = quad(face, frm, to)
            if uv is None:
                uv = default_uv(face, frm, to) if uvmode == 'mc' else pixel_uv(face, frm, to)
            u1, v1, u2, v2 = uv
            tuv = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            k = (frot or 0) // 90 % 4
            tuv = tuv[-k:] + tuv[:-k] if k else tuv
            idx = []
            for c in corners:
                p = R_e @ (np.array(c, float) - o_e) + o_e
                p = M[:3, :3] @ p + M[:3, 3]
                self.verts.append(mc_to_blender(p))
                idx.append(len(self.verts) - 1)
            # counter-clockwise from outside: TL, BL, BR, TR
            order = [0, 3, 2, 1]
            self.faces.append([idx[i] for i in order])
            self.uvs.append([(tuv[i][0] / 16.0, 1 - tuv[i][1] / 16.0) for i in order])
            if img not in self.mat_index:
                self.mat_index[img] = len(self.mats)
                self.mats.append(img)
            self.mat_index_last = self.mat_index[img]
            self.faces[-1] = (self.faces[-1], self.mat_index[img])

    def build(self):
        me = bpy.data.meshes.new(self.name)
        me.from_pydata(self.verts, [], [f for f, _ in self.faces])
        uvl = me.uv_layers.new()
        li = 0
        for pi, poly in enumerate(me.polygons):
            for k, loop in enumerate(poly.loop_indices):
                uvl.data[loop].uv = self.uvs[pi][k]
            poly.material_index = self.faces[pi][1]
        for img in self.mats:
            me.materials.append(material(img))
        obj = bpy.data.objects.new(self.name, me)
        bpy.context.scene.collection.objects.link(obj)
        return obj


def tex_path(ref):
    if os.path.isabs(ref) or ref.endswith('.png'):
        return ref
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    return os.path.join(ASSETS, ns, 'textures', path + '.png')


def add_model(mesh, rel, M=np.eye(4), overrides=None, keep=None):
    with open(os.path.join(ASSETS, 'create', 'models', rel + '.json')) as f:
        m = json.load(f)
    textures = dict(m.get('textures', {}))
    textures.update(overrides or {})

    def resolve(r):
        for _ in range(10):
            if not r.startswith('#'):
                return r
            r = textures.get(r[1:], r)
        return r

    for i, e in enumerate(m['elements']):
        if keep and not keep(e, i):
            continue
        faces = {}
        for fname, fd in e['faces'].items():
            r = resolve(fd['texture'])
            if r.startswith('#'):
                continue
            faces[fname] = (tex_path(r), fd.get('uv'), fd.get('rotation', 0))
        mesh.add_box(e['from'], e['to'], faces, M, e.get('rotation'))


TEX = {k: os.path.join(HERE, k + '.png') for k in ('skin', 'skin_shade', 'skin_tip', 'sleeve', 'cuff')}


def skin_box(mesh, frm, to, M, tip_face=None):
    faces = {f: (TEX['skin'], None, 0) for f in FACES}
    if tip_face:
        faces[tip_face] = (TEX['skin_tip'], None, 0)
    faces['down'] = (TEX['skin_shade'], None, 0)
    mesh.add_box(frm, to, faces, M, uvmode='px')


def cloth_box(mesh, frm, to, M, tex):
    mesh.add_box(frm, to, {f: (TEX[tex], None, 0) for f in FACES}, M, uvmode='px')


# ---- the scene ----------------------------------------------------------------------------------------------

YAW, PITCH = 40, 30
DEPOT_HALF_PX = 0.0
R_, U_, B_ = camera_basis(YAW, PITCH)
PLATE = 13.0
CENTER = np.array([8.0, PLATE, 8.0])
# the arm reaches in from the upper right of the picture, a little towards the viewer
ARM_DIR = R_ * 0.58 + U_ * 0.78 + B_ * 0.22
ARM_DIR /= np.linalg.norm(ARM_DIR)
# on the plate the fingers point away from the shoulder
FLAT_DIR = -np.array([ARM_DIR[0], 0, ARM_DIR[2]])
FLAT_DIR /= np.linalg.norm(FLAT_DIR)
HAND_YAW = math.degrees(math.atan2(FLAT_DIR[0], FLAT_DIR[2]))  # rotation about y taking +z to FLAT_DIR


def forearm(mesh, wrist, length=13.0, s=1.0, cuff_at=0.0):
    """Cuff and sleeve going up the arm from the wrist."""
    R = rotation_between((0, 1, 0), ARM_DIR)
    M = around(R, (0, 0, 0), wrist, s)
    cloth_box(mesh, (-2.35, cuff_at, -2.35), (2.35, cuff_at + 1.3, 2.35), M, 'cuff')
    cloth_box(mesh, (-2.2, cuff_at + 1.3, -2.2), (2.2, cuff_at + length, 2.2), M, 'sleeve')
    return M


def flat_hand(mesh, M, curl=0.0):
    """An open blocky hand: palm (wrist at z=0) and four fingers towards +z, thumb on the -x side. curl bends the
    finger ends down (degrees)."""
    skin_box(mesh, (-3.1, 0, 0), (3.1, 1.8, 6.4), M)
    widths = [(-3.05, -1.6, 9.4), (-1.5, -0.05, 10.4), (0.05, 1.5, 10.0), (1.6, 3.05, 8.9)]
    for x0, x1, zt in widths:
        if curl:
            skin_box(mesh, (x0, 0.1, 6.4), (x1, 1.6, 8.2), M)
            Mf = M @ around(rot('x', curl), (0, 0.85, 8.2), (0, 0.85, 8.2))
            skin_box(mesh, (x0, 0.1, 8.2), (x1, 1.6, zt), Mf, tip_face='south')
        else:
            skin_box(mesh, (x0, 0.1, 6.4), (x1, 1.6, zt), M, tip_face='south')
    Mt = M @ around(rot('y', 38), (3.1, 0.8, 2.0), (3.1, 0.8, 2.0))
    skin_box(mesh, (2.9, 0.1, 1.4), (4.5, 1.5, 5.6), Mt, tip_face='south')


def decal(name, center, radius, color, alpha, emission=1.0):
    """A round soft spot lying on the plate (radial falloff), drawn over what is under it."""
    me = bpy.data.meshes.new(name)
    c = np.array(center, float)
    pts = [c + np.array([dx, 0, dz]) * radius for dx, dz in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    me.from_pydata([mc_to_blender(p) for p in pts], [], [[0, 3, 2, 1]])
    uvl = me.uv_layers.new()
    for i, uv in enumerate(((0, 0), (0, 1), (1, 1), (1, 0))):
        uvl.data[i].uv = uv
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    tc = nt.nodes.new('ShaderNodeTexCoord')
    vm = nt.nodes.new('ShaderNodeVectorMath')
    vm.operation = 'SUBTRACT'
    vm.inputs[1].default_value = (0.5, 0.5, 0)
    ln = nt.nodes.new('ShaderNodeVectorMath')
    ln.operation = 'LENGTH'
    fall = nt.nodes.new('ShaderNodeMapRange')
    fall.inputs['From Min'].default_value = 0.5
    fall.inputs['From Max'].default_value = 0.08
    fall.interpolation_type = 'SMOOTHSTEP'
    k = nt.nodes.new('ShaderNodeMath')
    k.operation = 'MULTIPLY'
    k.inputs[1].default_value = alpha
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = tuple(color) + (1,)
    em.inputs['Strength'].default_value = emission
    tr = nt.nodes.new('ShaderNodeBsdfTransparent')
    mx = nt.nodes.new('ShaderNodeMixShader')
    L = nt.links
    L.new(tc.outputs['UV'], vm.inputs[0])
    L.new(vm.outputs[0], ln.inputs[0])
    L.new(ln.outputs['Value'], fall.inputs['Value'])
    L.new(fall.outputs['Result'], k.inputs[0])
    L.new(k.outputs[0], mx.inputs[0])
    L.new(tr.outputs[0], mx.inputs[1])
    L.new(em.outputs[0], mx.inputs[2])
    L.new(mx.outputs[0], out.inputs['Surface'])
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    me.materials.append(m)
    obj = bpy.data.objects.new(name, me)
    try:
        obj.visible_shadow = False
    except Exception:
        pass
    bpy.context.scene.collection.objects.link(obj)
    return obj


FRAME = []  # points the framing is fitted to (the sleeve may run off the picture)


def scene(variant):
    objs = Mesh('scene')
    add_model(objs, 'block/depot/block')
    glow = None
    if variant == 'rest':
        # the player's own arm, as Cinematic Interactions draws it: one block of an arm, its end on the plate
        s = 1.7
        R = rotation_between((0, 1, 0), ARM_DIR)
        M = around(R, (0, 0, 0), (0, 0, 0), s)
        corners = [M[:3, :3] @ np.array([x, 0, z]) for x in (-2, 2) for z in (-2, 2)]
        lift = -min(c[1] for c in corners)
        base = CENTER + np.array([0.4, lift, 0.6])
        M = around(R, (0, 0, 0), base, s)
        skin_box(objs, (-2, 0, -2), (2, 3.6, 2), M, tip_face='down')
        cloth_box(objs, (-2.3, 3.6, -2.3), (2.3, 4.6, 2.3), M, 'cuff')
        cloth_box(objs, (-2.15, 4.6, -2.15), (2.15, 22, 2.15), M, 'sleeve')
        glow = CENTER + np.array([0.4, 0.05, 0.6])
        FRAME.extend([M[:3, :3] @ np.array(p) + M[:3, 3] for p in ((0, 7, 0), (2.3, 6, 2.3), (-2.3, 6, -2.3))])
    elif variant == 'flat':
        s = 1.25
        palm_at = CENTER + np.array([0, 0.02, 0]) - FLAT_DIR * 5.0 * s
        Mh = around(rot('y', HAND_YAW), (0, 0, 0), palm_at, s)
        flat_hand(objs, Mh)
        wrist = palm_at + np.array([0, 1.2 * s, 0]) - FLAT_DIR * 0.4 * s
        skin_box(objs, (-2.6, -0.4, -2.0), (2.6, 2.2, 2.0), around(rotation_between((0, 1, 0), ARM_DIR), (0, 0, 0), wrist, s))
        Mf = forearm(objs, wrist + ARM_DIR * 1.6 * s, s=s, length=18)
        glow = CENTER + FLAT_DIR * 0.6
        FRAME.extend([Mf[:3, :3] @ np.array(p) + Mf[:3, 3] for p in ((0, 3, 0), (2.3, 3, 2.3))])
        FRAME.extend([Mh[:3, :3] @ np.array(p) + Mh[:3, 3] for p in ((-3, 0, 10.5), (3, 0, 10.5), (4.5, 0, 5))])
    elif variant == 'place':
        cube_s = 0.62  # a brass casing at item size, set on the plate
        Mc = around(rot('y', HAND_YAW), (8, 0, 8), CENTER + np.array([0, 0.01, 0]), cube_s)
        objs.add_box((0, 0, 0), (16, 16, 16), {f: (tex_path('create:block/brass_casing'), None, 0) for f in FACES}, Mc)
        s = 1.18
        half = 8 * cube_s
        palm_at = CENTER + np.array([0, 2 * half + 0.1, 0]) - FLAT_DIR * 5.2 * s
        Mh = around(rot('y', HAND_YAW), (0, 0, 0), palm_at, s)
        flat_hand(objs, Mh, curl=84)
        wrist = palm_at + np.array([0, 1.2 * s, 0]) - FLAT_DIR * 0.4 * s
        skin_box(objs, (-2.6, -0.4, -2.0), (2.6, 2.2, 2.0), around(rotation_between((0, 1, 0), ARM_DIR), (0, 0, 0), wrist, s))
        Mf = forearm(objs, wrist + ARM_DIR * 1.6 * s, s=s, length=18)
        glow = CENTER + FLAT_DIR * 0.2
        FRAME.extend([Mf[:3, :3] @ np.array(p) + Mf[:3, 3] for p in ((0, 3, 0), (2.3, 3, 2.3))])
        FRAME.extend([Mh[:3, :3] @ np.array(p) + Mh[:3, 3] for p in ((-3, 1, 10), (3, 1, 10))])
    obj = objs.build()
    FRAME.extend([np.array(p, float) for p in ((0, 0, 0), (16, 0, 0), (0, 0, 16), (16, 0, 16), (0, 13, 0), (16, 13, 16))])
    if glow is not None:
        decal('shadow', glow + np.array([1.2, 0.03, 1.2]), 5.2, (0.05, 0.06, 0.14), 0.55)
        if variant != 'place':
            decal('glow', glow + np.array([0, 0.06, 0]), 6.0 if variant == 'rest' else 7.4, (1.0, 0.78, 0.42), 0.55, emission=1.6)
    return obj, glow


def setup(obj):
    sc = bpy.context.scene
    for eng in ('BLENDER_EEVEE_NEXT', 'BLENDER_EEVEE'):
        try:
            sc.render.engine = eng
            break
        except TypeError:
            continue
    sc.render.resolution_x = sc.render.resolution_y = SIZE
    sc.render.film_transparent = True
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    ee = sc.eevee
    for k, v in (('taa_render_samples', 96), ('use_shadows', True), ('shadow_ray_count', 3), ('shadow_step_count', 12),
                 ('use_raytracing', True), ('use_fast_gi', True), ('fast_gi_distance', 0.6), ('shadow_resolution_scale', 1.0)):
        try:
            setattr(ee, k, v)
        except Exception as e:
            print('eevee', k, e)
    # light: a soft sun from the upper left, cool sky fill
    sun_d = bpy.data.lights.new('sun', 'SUN')
    sun_d.energy = 2.2
    sun_d.angle = math.radians(5)
    sun_d.use_shadow = True
    try:
        sun_d.use_shadow_jitter = True
        sun_d.shadow_maximum_resolution = 0.002
    except Exception:
        pass
    sun = bpy.data.objects.new('sun', sun_d)
    sc.collection.objects.link(sun)
    L = -R_ * 0.72 + U_ * 0.66 + B_ * 0.18
    Lb = np.array(mc_to_blender(L * 16))
    Lb /= np.linalg.norm(Lb)
    # a sun shines along its local -z: point -z away from the light direction
    from mathutils import Vector
    sun.rotation_euler = Vector(tuple(-Lb)).to_track_quat('-Z', 'Y').to_euler()
    rim_d = bpy.data.lights.new('rim', 'SUN')
    rim_d.energy = 0.9
    rim_d.color = (0.75, 0.88, 1.0)
    rim_d.use_shadow = False
    rim = bpy.data.objects.new('rim', rim_d)
    sc.collection.objects.link(rim)
    Lr = R_ * 0.55 + U_ * 0.35 - B_ * 0.75
    Lrb = np.array(mc_to_blender(Lr * 16))
    Lrb /= np.linalg.norm(Lrb)
    rim.rotation_euler = Vector(tuple(-Lrb)).to_track_quat('-Z', 'Y').to_euler()
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    bgn = world.node_tree.nodes.get('Background')
    bgn.inputs['Color'].default_value = (0.42, 0.5, 0.72, 1)
    bgn.inputs['Strength'].default_value = 0.30
    sc.world = world
    # camera: orthographic 3/4 view, the depot in the middle of the frame
    cam_d = bpy.data.cameras.new('cam')
    cam_d.type = 'ORTHO'
    cam = bpy.data.objects.new('cam', cam_d)
    sc.collection.objects.link(cam)
    sc.camera = cam
    rb, ub, bb = (np.array(mc_to_blender(v * 16)) for v in (R_, U_, B_))
    rb, ub, bb = rb / np.linalg.norm(rb), ub / np.linalg.norm(ub), bb / np.linalg.norm(bb)
    # framed on the depot alone, so it is the same size in every variant: centred across, a little below the middle
    depot = np.array([mc_to_blender((x, y, z)) for x in (0, 16) for y in (0, 13) for z in (0, 16)])
    sx, sy = depot @ rb, depot @ ub
    width = sx.max() - sx.min()
    cam_d.ortho_scale = width / 0.46
    cx = (sx.max() + sx.min()) / 2
    cy = (sy.max() + sy.min()) / 2 + 0.11 * cam_d.ortho_scale
    target = rb * cx + ub * cy + bb * (depot @ bb).mean()
    global DEPOT_HALF_PX
    DEPOT_HALF_PX = width / 2 / cam_d.ortho_scale * SIZE
    cam.location = Vector(tuple(target + bb * 40))
    cam.rotation_euler = Vector(tuple(-bb)).to_track_quat('-Z', 'Y').to_euler()
    cam_d.clip_end = 200
    # ink lines
    try:
        sc.render.use_freestyle = True
        sc.render.line_thickness_mode = 'ABSOLUTE'
        sc.render.line_thickness = SIZE / 430
        vl = sc.view_layers[0]
        fs = vl.freestyle_settings
        ls = fs.linesets[0] if len(fs.linesets) else fs.linesets.new('ink')
        if ls.linestyle is None:
            ls.linestyle = bpy.data.linestyles.new('ink')
        ls.select_by_visibility = True
        ls.select_by_edge_types = True
        ls.select_silhouette = True
        ls.select_border = True
        ls.select_crease = True
        ls.linestyle.color = (0.06, 0.07, 0.15)
        ls.linestyle.thickness = SIZE / 430
        vl.freestyle_settings.crease_angle = math.radians(120)
    except Exception as e:
        print('no freestyle:', e)
    return cam


if __name__ == '__main__':
    bpy.ops.wm.read_factory_settings(use_empty=True)
    obj, glow = scene(VARIANT)
    cam = setup(obj)
    sc = bpy.context.scene
    # where the effects go, in pixels, for the 2D pass afterwards
    from bpy_extras.object_utils import world_to_camera_view
    from mathutils import Vector
    bpy.context.view_layer.update()
    marks = {}
    for name, p in (('plate', CENTER), ('glow', glow if glow is not None else CENTER),
                    ('shoulder', CENTER + ARM_DIR * 24), ('top', CENTER + np.array([0, 8, 0]))):
        v = world_to_camera_view(sc, cam, Vector(mc_to_blender(p)))
        marks[name] = [v.x * SIZE, (1 - v.y) * SIZE]
    sc.render.filepath = OUT
    if not os.environ.get('MARKS_ONLY'):
        bpy.ops.render.render(write_still=True)
    with open(os.path.splitext(OUT)[0] + '.json', 'w') as f:
        json.dump({'marks': marks, 'hw': DEPOT_HALF_PX, 'engine': sc.render.engine, 'variant': VARIANT}, f)
    print('RENDERED', OUT, sc.render.engine)
