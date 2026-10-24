package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Picking;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.DeployerMenu;

/**
 * The Deployer's hand-off (DESIGN §7.10). Facing the deployer's hand, the player holds the item out; the claw takes it
 * just as the fingers let go, a handshake between person and machine, and the hand draws back with whatever the
 * deployer held before (Create's own swap).
 */
public class DeployerScene extends CineStation<DeployerMenu> {
    /** The hand-off: seconds, and the beat (the claw closes). */
    static final double HANDOFF = 0.7, HANDOFF_BEAT = 0.30;
    /** Where the claw is when the deployer rests: this far out from the block's middle. */
    static final double CLAW = 0.62;

    /** Development only ({@code SmokeTest}): the claw counts as pointed at while the cursor is somewhere else. */
    public static boolean smokePin;
    /** Development: hand-offs. */
    public int swaps;

    private boolean hovered;

    public DeployerScene(BlockPos pos, Direction side) {
        super(pos, side, DeployerMenu.class, 0);
    }

    /** The claw, where Create draws it this frame (it reaches out when the deployer works). */
    private Vec3 claw() {
        Minecraft mc = Minecraft.getInstance();
        float reach = mc.level != null && mc.level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer
                ? deployer.getHandOffset(mc.getTimer().getGameTimeDeltaPartialTick(false)) : 0;
        return Vec3.atCenterOf(pos).add(side().scale(CLAW + reach));
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return Vec3.atCenterOf(pos).add(side().scale(CLAW));
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = Vec3.atCenterOf(pos).add(side().scale(1.3));
        stand = new Vec3(stand.x, player.getY(), stand.z);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    /** The deployer's hand is in front of its face: worked as a face, not a tabletop. */
    @Override
    public boolean workOnTop() {
        return false;
    }

    @Override
    public CameraRig.Pose lean() {
        Vec3 at = center();
        Vec3 eye = at.add(side().scale(1.05)).add(0, 0.2, 0);
        Vec3 look = at.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    public double leanFov() {
        return 64;
    }

    @Override
    public float bodyPitch() {
        return 15f;
    }

    /** Hands low, before the deployer. */
    @Override
    protected Vec3 restAt(boolean right) {
        return center().add(side().scale(0.4)).add(right().scale(right ? 0.25 : -0.25)).add(0, -0.25, 0);
    }

    // ---- interaction ---------------------------------------------------------------------------------

    @Override
    public void updateCursor(boolean overPanel) {
        if (moving()) {
            return;
        }
        if (smokePin) {
            hover();
            return;
        }
        hovered = false;
        aim = null;
        if (overPanel) {
            return;
        }
        Vec3 face = Vec3.atCenterOf(pos).add(side().scale(0.5));
        Vec3 hit = Picking.cursorOnPlane(face, side());
        if (hit != null && Math.abs(hit.subtract(face).dot(right())) <= 0.5 && Math.abs(hit.y - face.y) <= 0.5) {
            hover();
        }
    }

    /** Development: points at the claw as the cursor would. */
    public void hover() {
        hovered = true;
        aim = center();
    }

    @Override
    public void clearCursor() {
        if (!moving() && !smokePin) {
            hovered = false;
            aim = null;
        }
    }

    /** Held out before the claw; in the hand-off it reaches the claw by the beat and draws back. */
    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        Vec3 held = claw().add(side().scale(0.28));
        double t = moveTime();
        if (t < 0) {
            return held.add(0, -0.02 * tap, 0);
        }
        double reach = Anim.inOutCubic(Anim.window(t, 0, HANDOFF_BEAT)) - Anim.inOutCubic(Anim.window(t, HANDOFF_BEAT + 0.05, HANDOFF));
        return held.subtract(side().scale(0.2 * reach));
    }

    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        return hand.add(side().scale(-0.06)).add(0, -0.03, 0);
    }

    @Override
    public float heldItemTilt() {
        return 70f;
    }

    /** The hand-off, as Create's own click on the deployer's hand: whatever each holds changes hands. */
    @Override
    protected void act(int button, boolean shift) {
        DeployerMenu menu = menu();
        if (!hovered || menu == null) {
            return;
        }
        startMove(HANDOFF, HANDOFF_BEAT, () -> {
            Minecraft mc = Minecraft.getInstance();
            DeployerMenu open = menu();
            if (open != null && mc.gameMode != null) {
                mc.gameMode.handleInventoryButtonClick(open.containerId, DeployerMenu.SWAP);
            }
            swaps++;
            Fx.motes(claw(), 0.05, 4, 0.85f, 0.85f, 0.85f, 0.05);
        });
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return center();
    }

    @Override
    public Component hint() {
        return Component.translatable("cinecreate.hint.deployer");
    }

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
    }

    /** Development: whether the deployer's arm is out (it works). */
    @Nullable
    public Boolean reaching() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer
                ? deployer.getHandOffset(0) > 0.05f : null;
    }
}
