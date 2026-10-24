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

/**
 * Create's Extendo Grip in the scenes (DESIGN §8.5): held in either hand it lengthens the player's reach exactly as it
 * does in Create (the player's block interaction range, Create's modifier included), and the scenes use it: they are
 * worked from where the player stands when the block is within that reach, and the grip's hand does the work at the end
 * of its lattice.
 */
public final class Grip {
    private Grip() {}

    /** How far a bare arm works from the eyes, comfortably (the scenes' own reach is about this). */
    static final double BARE = 1.9;
    /** The server lets a scene go on this far from the block's middle (CI's and our menus' limit is 6), less a margin. */
    static final double SERVER = 5.9;
    /** The grip held: the hand this far from the shoulder toward the work at most, and at least. */
    static final double HOLD = 0.42, NEAR = 0.15;

    /** How many grips the player holds (main hand, off hand): 0, 1 or 2. */
    public static int held(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        return (AllItems.EXTENDO_GRIP.isIn(player.getMainHandItem()) ? 1 : 0) + (AllItems.EXTENDO_GRIP.isIn(player.getOffhandItem()) ? 1 : 0);
    }

    /** The local player holds a grip. */
    public static boolean inHand() {
        return held(Minecraft.getInstance().player) > 0;
    }

    /**
     * Whether, standing at these feet with the grip in hand, the player reaches the block: exactly as far as Create's
     * grip lets a click reach, the player's own block interaction range (Create's modifier included, and any other)
     * from the eyes to the block, as the game measures it. And no farther than the server lets a scene go.
     */
    public static boolean reaches(Vec3 feet, BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        return held(player) > 0 && within(feet, pos, player.blockInteractionRange());
    }

    /** Whether a grip would let the player reach the block from these feet (the refusal card's hint): one grip's modifier on top. */
    public static boolean wouldReach(Vec3 feet, BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        return player != null && held(player) == 0
                && within(feet, pos, player.blockInteractionRange() + ExtendoGripItem.singleRangeAttributeModifier.amount());
    }

    /** As {@code Player.isWithinBlockInteractionRange}, for eyes standing at these feet; and the server's limit. */
    static boolean within(Vec3 feet, BlockPos pos, double range) {
        Vec3 eye = feet.add(0, Views.EYE, 0);
        return new AABB(pos).distanceToSqr(eye) < range * range && feet.distanceTo(Vec3.atCenterOf(pos)) <= SERVER;
    }

    /**
     * Whether the player, where they stand now, reaches the block with the grip: Create's rule alone, from their real
     * eyes (a window of a crafter wall: the server's limit is for the crafter the scene was started at).
     */
    public static boolean reachesNow(BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        if (held(player) == 0) {
            return false;
        }
        double range = player.blockInteractionRange();
        return new AABB(pos).distanceToSqr(player.getEyePosition()) < range * range;
    }

    /** Where the hand holds the grip on its way to the work: a little out from the shoulder, toward it. */
    static Vec3 holdPoint(Vec3 shoulder, Vec3 tip) {
        Vec3 way = tip.subtract(shoulder);
        double length = way.length();
        if (length < 1e-4) {
            return shoulder;
        }
        double out = Math.max(NEAR, Math.min(HOLD, length - GripArm.FOLDED));
        return shoulder.add(way.scale(out / length));
    }

    /** Whether a hand's place is too close to the shoulder for the grip to reach it from the fist: the grip stays folded. */
    static boolean folds(Vec3 shoulder, Vec3 at) {
        return at.distanceTo(shoulder) < NEAR + GripArm.FOLDED + 0.1;
    }

    /** Development: the grip folded right up, from the fist to the claw's palm (blocks). */
    public static double folded() {
        return GripArm.FOLDED;
    }

    /** Creates the grip's models (Create's handle and cog) before the game loads its models: from the client's constructor. */
    public static void loadModels() {
        GripArm.load();
    }

    /** The camera as drawn last (CI's), or null before the first frame. */
    @Nullable
    static CameraRig.Pose camera() {
        return CameraRig.last();
    }

    /** CI's shoulder for this camera: 0.38 to the side, 0.46 down, 0.08 forward (as {@code Hands.shoulder}). */
    static Vec3 shoulder(boolean right, Vec3 camera, float yaw) {
        Vec3 forward = forward(yaw);
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        return camera.add(side.scale(right ? 0.38 : -0.38)).add(0, -0.46, 0).add(forward.scale(0.08));
    }

    static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** A hand held in front of the body, as when the work is out of a bare arm's reach. */
    static Vec3 bodyRest(boolean right, CameraRig.Pose camera) {
        return shoulder(right, camera.pos(), camera.yaw()).add(forward(camera.yaw()).scale(0.34)).add(0, -0.08, 0);
    }

    private static Method handPosition;
    private static boolean handPositionMissing;

    /**
     * Where CI draws a hand this frame (its spring follows the targets the scene gives it), or null if CI's internals
     * changed: the grip is then drawn from the target instead.
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
