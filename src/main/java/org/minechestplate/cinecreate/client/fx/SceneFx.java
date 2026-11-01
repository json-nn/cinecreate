package org.minechestplate.cinecreate.client.fx;

import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Scene;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.client.ClientConfig;
import org.minechestplate.cinecreate.client.scene.CineStation;

/**
 * The scenes' effects, made of CI's sparks and our {@link Particles}: liquids (splashes, stirring, pouring), air (gusts,
 * trails, a fan's stream, things turning), dust, chips of the work itself, hard hits and fire. Calls marked "once a
 * frame" keep a steady rate whatever the frame rate. Everything here, CI's sparks included, starts only as far as the
 * player's particle settings let it (all on or off, each block, the amount, the game's own setting).
 */
public final class SceneFx {
    public static final int WIND = 0xDCE8F2, BRASS = 0xF2C25A, STEEL = 0xC8D2DC, DUST = 0xCBBFA8, FLOUR = 0xF2ECDD,
            STEAM = 0xE8EEF2, EMBER = 0xFF8A2A, CAKE = 0x6FC8FF;

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final RandomSource RANDOM = Particles.RANDOM;

    private SceneFx() {}

    /** How much of the playing scene's particles start, 0..1 ({@link ClientConfig#particleShare}). */
    static double share() {
        return ClientConfig.particleShare(Scene.station instanceof CineStation<?> scene ? scene.sceneKind() : null);
    }

    /** Whether one more starts, by {@link #share()}. */
    static boolean keep() {
        double share = share();
        return share >= 1 || share > 0 && RANDOM.nextDouble() < share;
    }

    /** {@code count} cut down to the share. */
    private static int scaled(int count) {
        double n = count * share();
        int whole = (int) n;
        return whole + (RANDOM.nextDouble() < n - whole ? 1 : 0);
    }

    // ---- CI's sparks, as far as the settings let them --------------------------------------------------

    public static void motes(Vec3 at, double radius, int count, float r, float g, float b, double rise) {
        int n = scaled(count);
        if (n > 0) {
            Fx.motes(at, radius, n, r, g, b, rise);
        }
    }

    public static void spark(Vec3 at, Vec3 velocity, double life, float size, float r, float g, float b, double gravity, double streak) {
        if (keep()) {
            Fx.spark(at, velocity, life, size, r, g, b, gravity, streak);
        }
    }

    public static void hotSparks(Vec3 at, Vec3 direction, double spread, int count, double speed) {
        int n = scaled(count);
        if (n > 0) {
            Fx.hotSparks(at, direction, spread, n, speed);
        }
    }

    public static void blowSparks(Vec3 at, int count, double speed) {
        int n = scaled(count);
        if (n > 0) {
            Fx.blowSparks(at, n, speed);
        }
    }

    public static void steam(Vec3 at, double radius, int count) {
        int n = scaled(count);
        if (n > 0) {
            Fx.steam(at, radius, n);
        }
    }

    /** How many to start this frame for {@code perSecond}. */
    public static int rate(double perSecond) {
        double n = perSecond * Scene.frameDt;
        int whole = (int) n;
        return whole + (RANDOM.nextDouble() < n - whole ? 1 : 0);
    }

    // ---- liquids -------------------------------------------------------------------------------------

    /** Something landing in liquid {@code radius} wide: drops thrown up and out, rings on the surface. */
    public static void splash(Vec3 surface, double radius, int color, double strength) {
        int drops = (int) Math.round(12 * strength);
        for (int i = 0; i < drops; i++) {
            double a = angle(), out = 0.25 + RANDOM.nextDouble() * 0.45;
            Vec3 v = new Vec3(Math.cos(a) * out, 1.1 + RANDOM.nextDouble(), Math.sin(a) * out).scale(Math.sqrt(strength));
            Particles.drop(surface.add(Math.cos(a) * 0.03, 0.01, Math.sin(a) * 0.03), v, 0.9, 0.011 + RANDOM.nextDouble() * 0.008,
                    color, 7, surface.y, surface, radius);
        }
        Vec3 ring = surface.add(0, 0.004, 0);
        Particles.ripple(ring, UP, 0.03, 0.2 + 0.12 * strength, 0.6, color, 0.9f);
        Particles.ripple(ring, UP, 0.02, 0.1 + 0.06 * strength, 0.45, 0xFFFFFF, 0.35f);
    }

    /**
     * Liquid stirred by a whisk turning at {@code omega} (radians a second, signed): rings, drops flung off the whisk, the
     * liquid's glow circling, bubbles. Once a frame.
     */
    public static void churn(Vec3 surface, double radius, int color, double omega, boolean boiling) {
        double spin = Math.signum(omega) * Math.min(Math.abs(omega), 14);
        for (int n = rate(9); n > 0; n--) {
            Particles.ripple(around(surface, radius * 0.6).add(0, 0.004, 0), UP, 0.02, 0.09 + RANDOM.nextDouble() * 0.08, 0.7, color, 0.55f);
        }
        for (int n = rate(16); n > 0; n--) {
            double a = angle(), r = 0.12 + RANDOM.nextDouble() * 0.08;
            Vec3 arm = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 tangent = UP.cross(arm).scale(Math.signum(spin) * Math.min(0.5, Math.abs(spin) * r * 0.9));
            Particles.drop(surface.add(arm.scale(r)).add(0, 0.01, 0), tangent.add(0, 0.7 + RANDOM.nextDouble() * 0.5, 0), 1.0,
                    0.009 + RANDOM.nextDouble() * 0.007, color, 7, surface.y, surface, radius);
        }
        for (int n = rate(10); n > 0; n--) {
            double a = angle(), r = 0.18 + RANDOM.nextDouble() * (radius * 0.9 - 0.18);
            Vec3 at = surface.add(Math.cos(a) * r, 0.02 + RANDOM.nextDouble() * 0.04, Math.sin(a) * r);
            Particles.orbit(at, surface, UP, spin * 0.8, 0.35, 0.5 + RANDOM.nextDouble() * 0.3, 0.012, color, 0.55f, false);
        }
        for (int n = rate(boiling ? 10 : 3); n > 0; n--) {
            Particles.bubble(around(surface, radius * 0.85).add(0, 0.006, 0), 0.35 + RANDOM.nextDouble() * 0.35,
                    0.015 + RANDOM.nextDouble() * 0.02, color);
        }
    }

    /** A heated liquid at rest: bubbles, and now and then a breath of steam (more for a hotter fire). Once a frame. */
    public static void simmer(Vec3 surface, double radius, int color, boolean hot) {
        for (int n = rate(hot ? 9 : 4); n > 0; n--) {
            Particles.bubble(around(surface, radius).add(0, 0.006, 0), 0.4 + RANDOM.nextDouble() * 0.4, 0.014 + RANDOM.nextDouble() * 0.02, color);
        }
        for (int n = rate(hot ? 3 : 1.2); n > 0; n--) {
            Particles.puff(around(surface, radius).add(0, 0.04, 0), new Vec3(0, 0.25, 0), 1.6, 0.05, 0.16, STEAM, 0.22f);
        }
    }

    /** A thin stream falling from {@code from} to {@code to}: drops along it, rings and spray where it lands. Once a frame. */
    public static void pour(Vec3 from, Vec3 to, int color, double strength) {
        double fall = Math.max(0.05, from.y - to.y);
        double time = Math.sqrt(2 * fall / 9);
        Vec3 drift = new Vec3((to.x - from.x) / time, 0, (to.z - from.z) / time);
        for (int n = rate(30 * strength); n > 0; n--) {
            Vec3 at = from.add(jitter(0.015));
            Particles.drop(at, drift.add(0, -0.4 - RANDOM.nextDouble() * 0.4, 0), 1.2, 0.01 + RANDOM.nextDouble() * 0.005, color, 9, to.y,
                    to, 0.3);
        }
        for (int n = rate(7 * strength); n > 0; n--) {
            Particles.ripple(to.add(0, 0.004, 0), UP, 0.02, 0.08 + RANDOM.nextDouble() * 0.05, 0.4, color, 0.5f);
        }
        for (int n = rate(10 * strength); n > 0; n--) {
            double a = angle(), out = 0.3 + RANDOM.nextDouble() * 0.3;
            Particles.drop(to.add(0, 0.01, 0), new Vec3(Math.cos(a) * out, 0.5 + RANDOM.nextDouble() * 0.4, Math.sin(a) * out), 0.5, 0.008,
                    color, 7, to.y, to, 0.3);
        }
    }

    /** Liquid running into a grate: its glow spiralling in, small rings, now and then a bubble. Once a frame. */
    public static void drain(Vec3 grate, double radius, int color) {
        for (int n = rate(12); n > 0; n--) {
            double a = angle(), r = radius * (0.6 + RANDOM.nextDouble() * 0.4);
            Particles.orbit(grate.add(Math.cos(a) * r, 0.01, Math.sin(a) * r), grate, UP, 7, -1.2, 0.5 + RANDOM.nextDouble() * 0.2, 0.012,
                    color, 0.55f, false);
        }
        for (int n = rate(5); n > 0; n--) {
            Particles.ripple(around(grate, radius * 0.5).add(0, 0.004, 0), UP, 0.02, 0.07, 0.4, color, 0.45f);
        }
        for (int n = rate(2); n > 0; n--) {
            Particles.bubble(around(grate, radius * 0.4).add(0, 0.006, 0), 0.4, 0.015, color);
        }
    }

    // ---- air -----------------------------------------------------------------------------------------

    /** The air a launch throws: streaks shooting along {@code direction}, a ring of air round the plate, a little dust. */
    public static void gust(Vec3 at, Vec3 direction, double strength) {
        Vec3 dir = direction.normalize(), side = perpendicular(dir), other = dir.cross(side);
        for (int i = 0; i < Math.round(10 * strength); i++) {
            double a = angle(), spread = 0.2 + RANDOM.nextDouble() * 0.4;
            Vec3 out = side.scale(Math.cos(a)).add(other.scale(Math.sin(a)));
            Vec3 v = dir.scale(2.4 + RANDOM.nextDouble() * 1.8).add(out.scale(spread * 1.6));
            Particles.wisp(at.add(out.scale(0.08)), v, 0.35 + RANDOM.nextDouble() * 0.25, 0.012, WIND, 0.45f, dir.cross(out),
                    (RANDOM.nextDouble() - 0.5) * 10);
        }
        for (int i = 0; i < Math.round(10 * strength); i++) {
            double a = angle();
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Particles.wisp(at.add(out.scale(0.15)), out.scale(1.6 + RANDOM.nextDouble()).add(0, 0.3, 0), 0.3 + RANDOM.nextDouble() * 0.15,
                    0.014, WIND, 0.5f, UP, (RANDOM.nextDouble() - 0.5) * 12);
        }
        Particles.ripple(at.add(dir.scale(0.1)), dir, 0.05, 0.45 * strength, 0.3, WIND, 0.7f);
        dust(at, DUST, (int) Math.round(4 * strength), 0.08, 0.3);
    }

    /** The air off something flying at {@code velocity} (blocks a second): streaks spiralling after it. Once a frame. */
    public static void trail(Vec3 at, Vec3 velocity, double strength) {
        double speed = velocity.length();
        if (speed < 0.5) {
            return;
        }
        Vec3 dir = velocity.scale(1 / speed), side = perpendicular(dir), other = dir.cross(side);
        for (int n = rate(26 * strength); n > 0; n--) {
            double a = angle();
            Vec3 out = side.scale(Math.cos(a)).add(other.scale(Math.sin(a)));
            Vec3 from = at.add(out.scale(0.12 + RANDOM.nextDouble() * 0.1)).subtract(dir.scale(0.05));
            Particles.wisp(from, dir.scale(speed * 0.35).add(out.scale(0.4)), 0.3 + RANDOM.nextDouble() * 0.2, 0.014, WIND, 0.5f, dir,
                    (RANDOM.nextBoolean() ? -1 : 1) * (6 + RANDOM.nextDouble() * 6));
        }
    }

    /** A fan's air flowing past {@code at}: streaks along {@code flow} through a window {@code width} wide. Once a frame. */
    public static void breeze(Vec3 at, Vec3 flow, double width, int color, float brightness) {
        Vec3 dir = flow.normalize(), side = perpendicular(dir), other = dir.cross(side);
        for (int n = rate(18); n > 0; n--) {
            Vec3 from = at.subtract(dir.scale(0.55)).add(side.scale((RANDOM.nextDouble() - 0.5) * 2 * width))
                    .add(other.scale((RANDOM.nextDouble() - 0.5) * 1.2 * width));
            Particles.wisp(from, dir.scale(2.4 + RANDOM.nextDouble() * 1.4), 0.35 + RANDOM.nextDouble() * 0.2, 0.013, color, brightness, side,
                    (RANDOM.nextDouble() - 0.5) * 7);
        }
    }

    /** Air dragged round by something turning at {@code omega} (radians a second): streaks circling its axis. Once a frame. */
    public static void swirl(Vec3 center, Vec3 axis, double radius, double omega, int color, double strength) {
        if (Math.abs(omega) < 0.5) {
            return;
        }
        Vec3 k = axis.normalize(), side = perpendicular(k), other = k.cross(side);
        for (int n = rate(14 * strength); n > 0; n--) {
            double a = angle();
            Vec3 arm = side.scale(Math.cos(a)).add(other.scale(Math.sin(a))).scale(radius * (0.9 + RANDOM.nextDouble() * 0.25));
            Particles.orbit(center.add(arm).add(k.scale((RANDOM.nextDouble() - 0.5) * 0.06)), center, k,
                    omega * (0.8 + RANDOM.nextDouble() * 0.3), 0.35, 0.35 + RANDOM.nextDouble() * 0.25, 0.012, color, 0.5f, true);
        }
    }

    // ---- solids --------------------------------------------------------------------------------------

    /** Soft clouds (sawdust, flour, stone dust), lit like the place they are in. */
    public static void dust(Vec3 at, int color, int count, double size, double rise) {
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3((RANDOM.nextDouble() - 0.5) * 0.6, rise * (0.5 + RANDOM.nextDouble()), (RANDOM.nextDouble() - 0.5) * 0.6);
            Particles.puff(at.add(jitter(size * 0.6)), v, 0.8 + RANDOM.nextDouble() * 0.7, size * 0.5, size * (1.4 + RANDOM.nextDouble() * 0.8),
                    color, 0.38f);
        }
    }

    /** Bits of the work itself thrown off (cut, ground, crushed), tumbling down to {@code floor}. */
    public static void chips(Vec3 at, ItemStack stack, int count, Vec3 direction, double speed, double floor) {
        Vec3 dir = direction.normalize();
        for (int i = 0; i < count; i++) {
            Vec3 v = dir.add((RANDOM.nextDouble() - 0.5) * 1.2, RANDOM.nextDouble() * 0.5, (RANDOM.nextDouble() - 0.5) * 1.2).normalize()
                    .scale(speed * (0.5 + RANDOM.nextDouble() * 0.8));
            Particles.chip(at.add(jitter(0.03)), v, 1.1 + RANDOM.nextDouble() * 0.6, 0.016 + RANDOM.nextDouble() * 0.014, stack, floor);
        }
    }

    /** A hard hit on a surface facing {@code normal}: a ring racing out, and on a floor, dust kicked up round it. */
    public static void shock(Vec3 at, Vec3 normal, double radius, int color, double strength) {
        Particles.ripple(at, normal, radius * 0.15, radius, 0.35, color, (float) (0.85 * strength));
        Particles.ripple(at, normal, radius * 0.1, radius * 0.6, 0.25, 0xFFFFFF, (float) (0.45 * strength));
        if (normal.y < 0.9) {
            return;
        }
        for (int i = 0; i < Math.round(6 * strength); i++) {
            double a = angle(), r = radius * (0.5 + RANDOM.nextDouble() * 0.3);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Particles.puff(at.add(out.scale(r)), out.scale(0.5).add(0, 0.15, 0), 0.7 + RANDOM.nextDouble() * 0.4, 0.04, 0.12, DUST, 0.3f);
        }
    }

    // ---- fire and metal ------------------------------------------------------------------------------

    /** Embers rising off a fire (blue over a cake), and the heat waving above it. Once a frame. */
    public static void embers(Vec3 at, double radius, boolean cake, double perSecond) {
        int color = cake ? CAKE : EMBER;
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        for (int n = rate(perSecond); n > 0; n--) {
            Vec3 v = new Vec3((RANDOM.nextDouble() - 0.5) * 0.3, 0.5 + RANDOM.nextDouble() * 0.5, (RANDOM.nextDouble() - 0.5) * 0.3);
            spark(around(at, radius), v, 0.6 + RANDOM.nextDouble() * 0.6, 0.008f + RANDOM.nextFloat() * 0.008f, r, g, b, -0.3, 0);
        }
        for (int n = rate(perSecond * 0.25); n > 0; n--) {
            double a = angle();
            Particles.wisp(around(at, radius), new Vec3(0, 0.6 + RANDOM.nextDouble() * 0.3, 0), 0.6, 0.02, color, 0.25f,
                    new Vec3(Math.cos(a), 0, Math.sin(a)), (RANDOM.nextDouble() - 0.5) * 8);
        }
    }

    /** Brass-hot sparks thrown along {@code direction} (a crafter finishing, a wheel hitting its stop). */
    public static void brassSparks(Vec3 at, Vec3 direction, int count, double speed) {
        Vec3 dir = direction.normalize();
        for (int i = 0; i < count; i++) {
            Vec3 v = dir.add(jitter(0.7)).normalize().scale(speed * (0.5 + RANDOM.nextDouble() * 0.7));
            spark(at, v, 0.25 + RANDOM.nextDouble() * 0.25, 0.008f, 1f, 0.8f, 0.4f, 4.0, 0.04);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private static double angle() {
        return RANDOM.nextDouble() * Math.PI * 2;
    }

    private static Vec3 jitter(double amount) {
        return new Vec3((RANDOM.nextDouble() - 0.5) * 2 * amount, (RANDOM.nextDouble() - 0.5) * 2 * amount, (RANDOM.nextDouble() - 0.5) * 2 * amount);
    }

    /** A random point on the level disc of this radius round {@code center}. */
    private static Vec3 around(Vec3 center, double radius) {
        double a = angle(), r = radius * Math.sqrt(RANDOM.nextDouble());
        return center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    private static Vec3 perpendicular(Vec3 dir) {
        return (Math.abs(dir.y) < 0.9 ? UP : new Vec3(1, 0, 0)).cross(dir).normalize();
    }
}
