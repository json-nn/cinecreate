package org.minechestplate.cinecreate.client.scene;

import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.ClientStation;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.client.Notices;

/**
 * Where a scene may put the player and its camera (DESIGN §8.4): never into blocks. A scene whose camera stands away
 * from the player's head (the crafters' framing, the basin's look over its rim) must be reachable from the player's own
 * eyes, or the view would pass through a wall. And when the place a scene would walk the player to is taken by blocks,
 * the refusal card says which.
 */
final class Views {
    private Views() {}

    /** Eye heights above the feet, standing and crouching (as Cinematic Interactions has them). */
    static final double EYE = 1.62, CROUCHED_EYE = 1.27;

    /**
     * Whether the scene's view can be reached from the player's eyes, standing at these feet, without passing through a
     * block (the scene's own blocks excepted). When not, the reason is noted for the refusal card.
     */
    static boolean reachable(ClientStation station, Vec3 feet, Set<BlockPos> own) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) {
            return true;
        }
        CameraRig.Pose view = station.view();
        Vec3 eye = feet.add(0, station.crouchesFor(view.pos().y - feet.y) ? CROUCHED_EYE : EYE, 0);
        Vec3 from = eye;
        for (int tries = 0; tries < 4; tries++) {
            BlockHitResult hit = level.clip(new ClipContext(from, view.pos(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (!own.contains(hit.getBlockPos())) {
                Notices.why(station.pos, Component.translatable("cinecreate.notice.view_blocked",
                        level.getBlockState(hit.getBlockPos()).getBlock().getName()));
                return false;
            }
            // через свой же блок сцены луч проходит дальше
            from = hit.getLocation().add(view.pos().subtract(from).normalize().scale(0.05));
        }
        return true;
    }

    /** The first block between these two points (the scene's own blocks passed through), or null. */
    @Nullable
    static BlockPos blocking(Vec3 from, Vec3 to, Set<BlockPos> own) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        for (int tries = 0; tries < 4; tries++) {
            BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                return null;
            }
            if (!own.contains(hit.getBlockPos())) {
                return hit.getBlockPos();
            }
            from = hit.getLocation().add(to.subtract(from).normalize().scale(0.05));
        }
        return null;
    }

    /**
     * The place the scene walks the player to: if blocks take it (where the body would be), the refusal card will say
     * which, should Cinematic Interactions find no other place to stand.
     */
    static void noteStand(ClientStation station, Vec3 feet) {
        BlockPos taken = taken(feet);
        Minecraft mc = Minecraft.getInstance();
        if (taken != null && mc.level != null) {
            Notices.why(station.pos, Component.translatable("cinecreate.notice.stand_blocked",
                    mc.level.getBlockState(taken).getBlock().getName()));
        }
    }

    /** A block whose shape is where the player's body would be, standing at these feet, or null. */
    @Nullable
    static BlockPos taken(Vec3 feet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        double w = mc.player.getBbWidth() / 2 - 0.02;
        AABB body = new AABB(feet.x - w, feet.y + 0.05, feet.z - w, feet.x + w, feet.y + mc.player.getBbHeight(), feet.z + w);
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            var shape = mc.level.getBlockState(at).getCollisionShape(mc.level, at);
            if (!shape.isEmpty() && shape.bounds().move(at).intersects(body)) {
                return at.immutable();
            }
        }
        return null;
    }
}
