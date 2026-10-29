package org.minechestplate.cinecreate.client.scene;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import dev.dyslexic.cinematic.client.CameraRig;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.bridge.Bridge;

/**
 * Create's Extendo Grip in the scenes (DESIGN §8.5): its reach is exactly Create's (the player's block interaction range,
 * Create's modifier included), and a block within it is worked from where the player stands.
 */
public final class Grip {
    private Grip() {}

    /** A bare arm's comfortable reach from the eyes. */
    static final double BARE = 1.9;
    /** CI's server lets a scene go on within 6 of the block's middle; a little less here. */
    static final double SERVER = Bridge.SCENE_REACH;
    /** The hand holding the grip: at most and at least this far from the shoulder toward the work. */
    static final double HOLD = 0.42, NEAR = 0.15;

    public static int held(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        return (AllItems.EXTENDO_GRIP.isIn(player.getMainHandItem()) ? 1 : 0) + (AllItems.EXTENDO_GRIP.isIn(player.getOffhandItem()) ? 1 : 0);
    }

    public static boolean inHand() {
        return held(Minecraft.getInstance().player) > 0;
    }

    /** Create's own rule for a grip's click from these feet, and within the server's limit. */
    public static boolean reaches(Vec3 feet, BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        return held(player) > 0 && within(feet, pos, player.blockInteractionRange());
    }

    /** For the refusal card's hint: whether one grip's modifier would let the player reach the block. */
    public static boolean wouldReach(Vec3 feet, BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        return player != null && held(player) == 0
                && within(feet, pos, player.blockInteractionRange() + ExtendoGripItem.singleRangeAttributeModifier.amount());
    }

    /** As Player.isWithinBlockInteractionRange, for eyes at these feet; plus the server's limit. */
    static boolean within(Vec3 feet, BlockPos pos, double range) {
        Vec3 eye = feet.add(0, Views.EYE, 0);
        return new AABB(pos).distanceToSqr(eye) < range * range && feet.distanceTo(Vec3.atCenterOf(pos)) <= SERVER;
    }

    /**
     * Create's rule alone, from the player's real eyes: for a crafter wall's windows (the server's limit is for the
     * crafter the scene was started at).
     */
    public static boolean reachesNow(BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        if (held(player) == 0) {
            return false;
        }
        double range = player.blockInteractionRange();
        return new AABB(pos).distanceToSqr(player.getEyePosition()) < range * range;
    }

    static Vec3 holdPoint(Vec3 shoulder, Vec3 tip) {
        Vec3 way = tip.subtract(shoulder);
        double length = way.length();
        if (length < 1e-4) {
            return shoulder;
        }
        double out = Math.max(NEAR, Math.min(HOLD, length - GripArm.FOLDED));
        return shoulder.add(way.scale(out / length));
    }

    /** Too close to the shoulder for the grip to reach from the fist: it stays folded. */
    static boolean folds(Vec3 shoulder, Vec3 at) {
        return at.distanceTo(shoulder) < NEAR + GripArm.FOLDED + 0.1;
    }

    /** Development (SmokeTest): the grip folded right up, from the fist to the claw's palm. */
    public static double folded() {
        return GripArm.FOLDED;
    }

    /** From the client's constructor: the grip's models must be registered before the game bakes its models. */
    public static void loadModels() {
        GripArm.load();
    }

    @Nullable
    static CameraRig.Pose camera() {
        return CameraRig.last();
    }

    /** CI's shoulder for this camera (as its Hands.shoulder): 0.38 to the side, 0.46 down, 0.08 forward. */
    static Vec3 shoulder(boolean right, Vec3 camera, float yaw) {
        if (!handShoulderMissing) {
            try {
                if (handShoulder == null) {
                    handShoulder = Class.forName("dev.dyslexic.cinematic.client.Hands").getDeclaredMethod("shoulder", boolean.class, Vec3.class, float.class);
                    handShoulder.setAccessible(true);
                }
                return (Vec3) handShoulder.invoke(null, right, camera, yaw);
            } catch (ReflectiveOperationException | RuntimeException e) {
                handShoulderMissing = true;
                CineCreate.LOGGER.warn("[cinecreate] Cinematic Interactions' shoulders are not readable; using its default arm style's", e);
            }
        }
        Vec3 forward = forward(yaw);
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        return camera.add(side.scale(right ? 0.38 : -0.38)).add(0, -0.46, 0).add(forward.scale(0.08));
    }

    @Nullable
    private static Method handShoulder;
    private static boolean handShoulderMissing;

    static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    static Vec3 bodyRest(boolean right, CameraRig.Pose camera) {
        return shoulder(right, camera.pos(), camera.yaw()).add(forward(camera.yaw()).scale(0.34)).add(0, -0.08, 0);
    }

    private static Method handPosition;
    private static boolean handPositionMissing;

    /**
     * Where CI draws a hand this frame (its spring follows the targets), read from its private Hands.position; null if
     * CI's internals changed, and the grip is then drawn from the target.
     */
    @Nullable
    static Vec3 hand(boolean right) {
        if (handPositionMissing) {
            return null;
        }
        try {
            if (handPosition == null) {
                handPosition = Class.forName("dev.dyslexic.cinematic.client.Hands").getDeclaredMethod("position", boolean.class);
                handPosition.setAccessible(true);
            }
            return (Vec3) handPosition.invoke(null, right);
        } catch (ReflectiveOperationException | RuntimeException e) {
            handPositionMissing = true;
            CineCreate.LOGGER.warn("[cinecreate] Cinematic Interactions' hand positions are not readable; the Extendo Grip is drawn from the hands' targets", e);
            return null;
        }
    }
}
