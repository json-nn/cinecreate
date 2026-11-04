package org.minechestplate.cinecreate.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3f;

/** Particle shapes CI's sparks lack, drawn as CI draws its own: its textures, additive glow. */
public final class Particles {
    private enum Kind { DROP, RIPPLE, WISP, PUFF, BUBBLE, CHIP }

    static final RandomSource RANDOM = RandomSource.create();
    static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath("cinematic", "textures/fx/ring.png");
    static final ResourceLocation RAY = ResourceLocation.fromNamespaceAndPath("cinematic", "textures/fx/ray.png");
    private static final int MOST = 800;
    /** A wind trail keeps a point this often (seconds), up to this many. */
    private static final double TRAIL_STEP = 0.022;
    private static final int TRAIL_POINTS = 9;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[][] QUAD_UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    private static final RenderType PUFFS = new RenderType("cinecreate_puffs", RenderType.entityTranslucent(EnchantFx.GLOW).format(),
            RenderType.entityTranslucent(EnchantFx.GLOW).mode(), 1536, false, true, () -> {
                RenderType.entityTranslucent(EnchantFx.GLOW).setupRenderState();
                RenderSystem.depthMask(false);
            }, () -> {
                RenderType.entityTranslucent(EnchantFx.GLOW).clearRenderState();
                RenderSystem.depthMask(true);
            }) {};

    private static final List<P> LIVE = new ArrayList<>();

    private Particles() {}

    public static void register(IEventBus bus) {
        bus.addListener(Particles::onRender);
        bus.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                LIVE.clear();
            }
        });
    }

    static boolean room() {
        return LIVE.size() < MOST && SceneFx.keep();
    }

    // ---- starting them -------------------------------------------------------------------------------

    /**
     * Meeting {@code floor} within {@code pool} of {@code poolAt} it leaves a ripple; beyond that it falls on and
     * fades.
     */
    static void drop(Vec3 at, Vec3 velocity, double life, double size, int color, double gravity, double floor, Vec3 poolAt, double pool) {
        P p = start(Kind.DROP, at, life, size, color);
        if (p != null) {
            p.v = velocity;
            p.gravity = gravity;
            p.floor = floor;
            p.pool = poolAt;
            p.end = pool;
        }
    }

    /** In the plane facing {@code normal}, growing from {@code from} to {@code to} wide. */
    static void ripple(Vec3 at, Vec3 normal, double from, double to, double life, int color, float brightness) {
        P p = start(Kind.RIPPLE, at, life, from, color);
        if (p != null) {
            p.end = to;
            p.normal = normal.normalize();
            p.bright = brightness;
        }
    }

    /** Its path turns about {@code curlAxis} by {@code curl} radians a second. */
    static void wisp(Vec3 at, Vec3 velocity, double life, double width, int color, float brightness, Vec3 curlAxis, double curl) {
        P p = start(Kind.WISP, at, life, width, color);
        if (p != null) {
            p.v = velocity;
            p.bright = brightness;
            p.axis = curlAxis.normalize();
            p.spin = curl;
            p.drag = 1.6;
        }
    }

    /**
     * Circles {@code center} at {@code omega} radians a second, its distance growing by {@code drift} of itself a
     * second (negative: inward); {@code close}: it may come near the eye.
     */
    static void orbit(Vec3 at, Vec3 center, Vec3 axis, double omega, double drift, double life, double width, int color, float brightness,
            boolean close) {
        P p = start(Kind.WISP, at, life, width, color);
        if (p != null) {
            p.close = close;
            p.center = center;
            p.axis = axis.normalize();
            p.spin = omega;
            p.drag = drift;
            p.v = Vec3.ZERO;
            p.bright = brightness;
        }
    }

    /** Grows from {@code size} to {@code end}, rising and fading. */
    static void puff(Vec3 at, Vec3 velocity, double life, double size, double end, int color, float alpha) {
        P p = start(Kind.PUFF, at, life, size, color);
        if (p != null) {
            p.v = velocity;
            p.end = end;
            p.bright = alpha;
            p.drag = 2.4;
            p.light = lightAt(at);
            p.spin = RANDOM.nextDouble() * Math.PI * 2;
        }
    }

    static void bubble(Vec3 at, double life, double size, int color) {
        P p = start(Kind.BUBBLE, at, life, size, color);
        if (p != null) {
            p.v = new Vec3(0, 0.02, 0);
        }
    }

    /** Bounces once on {@code floor} (NaN: none) and settles. */
    static void chip(Vec3 at, Vec3 velocity, double life, double size, ItemStack stack, double floor) {
        TextureAtlasSprite sprite = sprite(stack);
        if (sprite == null) {
            return;
        }
        P p = start(Kind.CHIP, at, life, size, 0xFFFFFF);
        if (p != null) {
            p.v = velocity;
            p.gravity = 7;
            p.floor = floor;
            p.drag = 0.8;
            p.sprite = sprite;
            p.u = RANDOM.nextFloat() * 0.75f;
            p.w = RANDOM.nextFloat() * 0.75f;
            p.axis = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
            p.spin = (RANDOM.nextDouble() - 0.5) * 24;
            p.light = lightAt(at);
        }
    }

    @Nullable
    private static P start(Kind kind, Vec3 at, double life, double size, int color) {
        if (!room()) {
            return null;
        }
        P p = new P();
        p.kind = kind;
        p.at = at;
        p.life = life;
        p.size = size;
        p.r = (color >> 16 & 0xFF) / 255f;
        p.g = (color >> 8 & 0xFF) / 255f;
        p.b = (color & 0xFF) / 255f;
        LIVE.add(p);
        STARTED[kind.ordinal()]++;
        return p;
    }

    @Nullable
    private static TextureAtlasSprite sprite(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (stack.isEmpty() || mc.level == null) {
            return null;
        }
        TextureAtlasSprite sprite = mc.getItemRenderer().getModel(stack, mc.level, null, 0).getParticleIcon();
        return sprite.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS) ? sprite : null;
    }

    private static int lightAt(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0xF000F0 : LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
    }

    // ---- each frame ----------------------------------------------------------------------------------

    /** After the game's own particles: drawn over liquids (a basin's is drawn late) and behind the hands. */
    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            return;
        }
        double dt = Scene.frameDt;
        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            P p = LIVE.get(i);
            p.age += dt;
            if (p.age >= p.life || !step(p, dt)) {
                int last = LIVE.size() - 1;
                LIVE.set(i, LIVE.get(last));
                LIVE.remove(last);
                if (p.kind == Kind.BUBBLE) {
                    pop(p);
                }
                continue;
            }
            draw(p, pose, buffers, camera);
        }
        buffers.endBatch();
    }

    /** False when it is done. */
    private static boolean step(P p, double dt) {
        if (p.center != null) {
            Vec3 arm = p.at.subtract(p.center);
            p.at = p.center.add(rotate(arm, p.axis, p.spin * dt).scale(Math.exp(p.drag * dt)));
        } else {
            double drag = Math.exp(-p.drag * dt);
            p.v = new Vec3(p.v.x * drag, p.v.y * drag - p.gravity * dt, p.v.z * drag);
            if (p.kind == Kind.WISP && p.spin != 0) {
                p.v = rotate(p.v, p.axis, p.spin * dt);
            }
            p.at = p.at.add(p.v.scale(dt));
        }
        if (!Double.isNaN(p.floor) && p.at.y <= p.floor && p.v.y < 0) {
            if (p.kind == Kind.DROP) {
                if (Math.hypot(p.at.x - p.pool.x, p.at.z - p.pool.z) > p.end) {
                    p.floor = Double.NaN;
                    return true;
                }
                ripple(new Vec3(p.at.x, p.floor + 0.004, p.at.z), UP, 0.01, 0.05 + p.size * 2, 0.35, color(p), 0.6f);
                return false;
            }
            p.at = new Vec3(p.at.x, p.floor, p.at.z);
            p.v = p.bounced ? Vec3.ZERO : new Vec3(p.v.x * 0.4, -p.v.y * 0.25, p.v.z * 0.4);
            p.spin = p.bounced ? 0 : p.spin * 0.4;
            p.gravity = p.bounced ? 0 : p.gravity;
            p.bounced = true;
        }
        if (p.kind == Kind.WISP) {
            p.sinceMark += dt;
            if (p.trail.isEmpty() || p.sinceMark >= TRAIL_STEP) {
                p.sinceMark = 0;
                p.trail.add(0, p.at);
                if (p.trail.size() > TRAIL_POINTS) {
                    p.trail.remove(p.trail.size() - 1);
                }
            }
        }
        p.turn += p.spin * dt;
        return true;
    }

    private static void pop(P p) {
        ripple(p.at, UP, p.size * 0.6, p.size * 2.6, 0.25, color(p), 0.7f);
    }

    private static int color(P p) {
        return Math.round(p.r * 255) << 16 | Math.round(p.g * 255) << 8 | Math.round(p.b * 255);
    }

    // ---- drawing -------------------------------------------------------------------------------------

    private static void draw(P p, PoseStack pose, MultiBufferSource buffers, Camera camera) {
        double k = p.age / p.life;
        Vec3 cam = camera.getPosition();
        switch (p.kind) {
            case DROP -> {
                float f = (float) ((1 - k * k) * near(p.at, cam, 0.25, 0.4));
                Vec3 tail = p.at.subtract(p.v.scale(0.035));
                ribbon(buffers.getBuffer(RenderType.eyes(EnchantFx.GLOW)), pose, cam, List.of(p.at, tail), p.size, p.r * f, p.g * f, p.b * f);
            }
            case RIPPLE -> {
                double eased = 1 - Math.pow(1 - k, 3);
                float f = (float) (p.bright * Math.pow(1 - k, 1.5));
                plane(buffers.getBuffer(RenderType.eyes(RING)), pose, cam, p.at, p.normal, p.size + (p.end - p.size) * eased, p.turn,
                        p.r * f, p.g * f, p.b * f, 0xF000F0, 1f);
            }
            case WISP -> {
                if (p.trail.size() < 2) {
                    return;
                }
                double near = p.close ? near(p.at, cam, 0.12, 0.2) : near(p.at, cam, 0.4, 0.8);
                float f = (float) (p.bright * near * Math.pow(Math.sin(Math.PI * Math.min(1, k * 1.15)), 0.7));
                List<Vec3> points = new ArrayList<>(p.trail.size() + 1);
                points.add(p.at);
                points.addAll(p.trail);
                ribbon(buffers.getBuffer(RenderType.eyes(RAY)), pose, cam, points, p.size, p.r * f, p.g * f, p.b * f);
            }
            case PUFF -> {
                double size = p.size + (p.end - p.size) * (1 - Math.pow(1 - k, 2));
                float alpha = (float) (p.bright * near(p.at, cam, 0.3, 0.5) * Math.sin(Math.PI * Math.min(1, k * 1.3 + 0.08)) * (1 - k));
                facing(buffers.getBuffer(PUFFS), pose, camera, p.at, size, p.spin, p.r, p.g, p.b, alpha, p.light);
            }
            case BUBBLE -> {
                double size = p.size * Math.sin(Math.PI * 0.5 * Math.min(1, k * 1.6));
                float f = 0.75f;
                facing(buffers.getBuffer(RenderType.eyes(RING)), pose, camera, p.at, size, 0, p.r * f, p.g * f, p.b * f, 1f, 0xF000F0);
            }
            case CHIP -> chip(buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), pose, cam, p,
                    (float) (Math.min(1, (1 - k) * 4) * near(p.at, cam, 0.35, 0.5)));
        }
    }

    /** 0 within {@code from} of the eye, 1 from {@code from + span}: what flies right up to the camera fades instead of filling the view. */
    private static double near(Vec3 at, Vec3 cam, double from, double span) {
        return Math.min(1, Math.max(0, (at.distanceTo(cam) - from) / span));
    }

    /** Head first; the texture runs head to tail. */
    private static void ribbon(VertexConsumer vc, PoseStack pose, Vec3 cam, List<Vec3> points, double width, float r, float g, float b) {
        int n = points.size();
        Vec3[] across = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 along = points.get(Math.min(n - 1, i + 1)).subtract(points.get(Math.max(0, i - 1)));
            if (along.lengthSqr() < 1e-10) {
                along = UP.scale(1e-4);
            }
            Vec3 side = along.cross(points.get(i).subtract(cam));
            double taper = 1 - 0.55 * i / (double) (n - 1);
            across[i] = side.lengthSqr() < 1e-12 ? Vec3.ZERO : side.normalize().scale(width * taper);
        }
        for (int i = 0; i < n - 1; i++) {
            float u0 = i / (float) (n - 1), u1 = (i + 1) / (float) (n - 1);
            Vec3 a = points.get(i), c = points.get(i + 1);
            Vec3[] q = {a.add(across[i]).subtract(cam), c.add(across[i + 1]).subtract(cam), c.subtract(across[i + 1]).subtract(cam),
                    a.subtract(across[i]).subtract(cam)};
            float[][] uv = {{u0, 0}, {u1, 0}, {u1, 1}, {u0, 1}};
            quad(vc, pose, q, uv, r, g, b, 1f, 0xF000F0, a.subtract(cam).scale(-1));
        }
    }

    private static void plane(VertexConsumer vc, PoseStack pose, Vec3 cam, Vec3 center, Vec3 normal, double radius, double turn,
            float r, float g, float b, int light, float alpha) {
        Vec3 u = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : UP.cross(normal).normalize();
        Vec3 v = normal.cross(u);
        u = rotate(u, normal, turn).scale(radius);
        v = rotate(v, normal, turn).scale(radius);
        Vec3 c = center.subtract(cam);
        quad(vc, pose, new Vec3[] {c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v)}, QUAD_UV,
                r, g, b, alpha, light, normal);
    }

    private static void facing(VertexConsumer vc, PoseStack pose, Camera camera, Vec3 center, double radius, double turn,
            float r, float g, float b, float alpha, int light) {
        Vector3f look = camera.getLookVector();
        plane(vc, pose, camera.getPosition(), center, new Vec3(-look.x(), -look.y(), -look.z()), radius, turn, r, g, b, light, alpha);
    }

    private static void chip(VertexConsumer vc, PoseStack pose, Vec3 cam, P p, float alpha) {
        Vec3 normal = rotate(p.axis.cross(UP).lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : p.axis.cross(UP).normalize(), p.axis, p.turn);
        Vec3 u = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : UP.cross(normal).normalize();
        Vec3 v = normal.cross(u);
        u = u.scale(p.size * alpha);
        v = v.scale(p.size * alpha);
        Vec3 c = p.at.subtract(cam);
        float u0 = p.sprite.getU(p.u), u1 = p.sprite.getU(p.u + 0.25f), v0 = p.sprite.getV(p.w), v1 = p.sprite.getV(p.w + 0.25f);
        quad(vc, pose, new Vec3[] {c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v)},
                new float[][] {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, 1f, 1f, 1f, 1f, p.light, normal);
    }

    private static void quad(VertexConsumer vc, PoseStack pose, Vec3[] c, float[][] uv, float r, float g, float b, float a, int light, Vec3 normal) {
        int ri = channel(r), gi = channel(g), bi = channel(b), ai = channel(a);
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;
        for (int[] order : new int[][] {{0, 1, 2, 3}, {3, 2, 1, 0}}) {
            for (int i : order) {
                vc.addVertex(pose.last(), (float) c[i].x, (float) c[i].y, (float) c[i].z)
                        .setColor(ri, gi, bi, ai)
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose.last(), nx, ny, nz);
            }
        }
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    /** Right-handed, about the unit vector k. */
    static Vec3 rotate(Vec3 v, Vec3 k, double radians) {
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return v.scale(cos).add(k.cross(v).scale(sin)).add(k.scale(k.dot(v) * (1 - cos)));
    }

    private static final int[] STARTED = new int[Kind.values().length];

    /** Development (SmokeTest). */
    public static int started(String kind) {
        return STARTED[Kind.valueOf(kind).ordinal()];
    }

    private static final class P {
        Kind kind;
        Vec3 at = Vec3.ZERO, v = Vec3.ZERO, normal = UP, axis = UP, pool = Vec3.ZERO;
        @Nullable
        Vec3 center;
        double age, life, size, end, gravity, drag, spin, turn, sinceMark, floor = Double.NaN;
        float r, g, b, bright = 1;
        int light = 0xF000F0;
        boolean bounced, close;
        float u, w;
        @Nullable
        TextureAtlasSprite sprite;
        final List<Vec3> trail = new ArrayList<>(TRAIL_POINTS + 1);
    }
}
