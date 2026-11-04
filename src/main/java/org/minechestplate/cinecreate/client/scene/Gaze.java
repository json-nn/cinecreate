package org.minechestplate.cinecreate.client.scene;

import dev.dyslexic.cinematic.client.CameraRig;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A client-mode scene's camera (DESIGN §8.6). CI keeps it within 0.35 of the eyes but keeps the angles worked out for
 * the scene's own camera; here it turns to what that camera looks at, the frame narrowed or widened to show the work at
 * that camera's size.
 */
final class Gaze {
    private Gaze() {}

    /** How far the camera leans from the eyes, and how high a tethered one may rise (CI: 0.35 and 0.16). */
    static final double LEAN = 0.3, RISE = 0.16;
    static final double MIN_FOV = 50, MAX_FOV = 80;

    static CameraRig.Pose from(CameraRig.Pose own, Vec3 center, boolean free) {
        Vec3 eye = eye();
        if (eye == null) {
            return own;
        }
        Vec3 camera = camera(own, eye, free);
        Vec3 look = focus(own, center).subtract(camera);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(camera, yaw, pitch, own.roll());
    }

    static double fov(CameraRig.Pose own, Vec3 center, boolean free, double fov) {
        Vec3 eye = eye();
        if (eye == null) {
            return fov;
        }
        Vec3 focus = focus(own, center);
        double scale = focus.distanceTo(own.pos()) / Math.max(1e-3, focus.distanceTo(camera(own, eye, free)));
        return Mth.clamp(2 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fov / 2)) * scale)), MIN_FOV, MAX_FOV);
    }

    /** What the scene's camera looks at, as CI takes it (Reach.lookPoint): along its view, as far off as the scene's centre. */
    private static Vec3 focus(CameraRig.Pose own, Vec3 center) {
        double far = Math.max(0.3, center.distanceTo(own.pos()));
        return own.pos().add(Vec3.directionFromRotation(own.pitch(), own.yaw()).scale(far));
    }

    private static Vec3 camera(CameraRig.Pose own, Vec3 eye, boolean free) {
        Vec3 toward = own.pos().subtract(eye);
        if (!free && toward.y > RISE) {
            toward = new Vec3(toward.x, RISE, toward.z);
        }
        return toward.length() > LEAN ? eye.add(toward.normalize().scale(LEAN)) : eye.add(toward);
    }

    @Nullable
    private static Vec3 eye() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
    }
}
