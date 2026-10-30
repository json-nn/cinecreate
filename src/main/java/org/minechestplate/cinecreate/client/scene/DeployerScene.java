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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.DeployerMenu;

/**
 * The Deployer's hand-off (DESIGN §7.10): the player holds the item out, the claw takes it as the fingers let go, and
 * the hand draws back with what the deployer held (Create's own swap). Facing up or down, it is worked from any side.
 */
public class DeployerScene extends CineStation<DeployerMenu> {
    /** The hand-off: seconds, and the beat (the claw closes). */
    static final double HANDOFF = 0.7, HANDOFF_BEAT = 0.30;
    /** Where the claw is when the deployer rests: this far out from the block's middle. */
    static final double CLAW = 0.62;

    /** Development (SmokeTest): the claw counts as pointed at wherever the cursor is. */
    public static boolean smokePin;
    /** Development (SmokeTest): hand-offs. */
    public int swaps;

    private boolean hovered;
    private final Vec3 facing;

    public DeployerScene(BlockPos pos, Direction side) {
        super(pos, side, DeployerMenu.class, 0);
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        Direction toward = state != null && state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : side;
        facing = Vec3.atLowerCornerOf(toward.getNormal());
    }

    /** The claw, where Create draws it this frame (it reaches out when the deployer works). */
    private Vec3 claw() {
        Minecraft mc = Minecraft.getInstance();
        float reach = mc.level != null && mc.level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer
                ? deployer.getHandOffset(mc.getTimer().getGameTimeDeltaPartialTick(false)) : 0;
        return Vec3.atCenterOf(pos).add(facing.scale(CLAW + reach));
    }

    @Override
    public boolean spotOnFace(int spot) {
        return true;
    }

    @Nullable
    @Override
    protected Component notReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer
                && arm.moving(deployer.getHandOffset(mc.getTimer().getGameTimeDeltaPartialTick(false))) ? Component.translatable("cinecreate.busy.deployer") : null;
    }

    private final Motion arm = new Motion();

    @Override
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinecreate.layout.spot.claw"));
    }

    @Override
    public Vec3 spotBase(int spot) {
        return center();
    }

    /** Where the hand meets the claw, out as far as Create's claw is this frame (moved as the editor moved the hand). */
    private Vec3 handOff() {
        return spot(0).add(claw().subtract(center()));
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return Vec3.atCenterOf(pos).add(facing.scale(CLAW));
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = center().add(side().scale(facing.y == 0 ? 1.3 - CLAW : 1.0));
        stand = new Vec3(stand.x, player.getY(), stand.z);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    /** Facing down, its hand hangs over the player (a deployer one block over the head): reached up to, a little higher than a face. */
    @Override
    protected boolean bareWorkable(Vec3 feet) {
        if (facing.y >= 0) {
            return super.bareWorkable(feet);
        }
        Vec3 claw = center();
        double high = claw.y - feet.y;
        return high >= 0.6 && high <= 2.1 && Math.hypot(claw.x - feet.x, claw.z - feet.z) <= 1.5;
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
        if (underCursor(Picking.mouseGui(), claw(), 0.35)) {
            hover();
        }
    }

    /** Development (SmokeTest): points at the claw as the cursor would. */
    public void hover() {
        hovered = true;
        aim = spot(0);
    }

    @Override
    public void clearCursor() {
        if (!moving() && !smokePin) {
            hovered = false;
            aim = null;
        }
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        Vec3 held = handOff().add(side().scale(0.28));
        double t = moveTime();
        if (t < 0) {
            return held.add(0, -0.02 * tap, 0);
        }
        double reach = Anim.inOutCubic(Anim.window(t, 0, HANDOFF_BEAT)) - Anim.inOutCubic(Anim.window(t, HANDOFF_BEAT + 0.05, HANDOFF));
        // кулак останавливается у ладони клешни, не входя в неё
        return held.subtract(side().scale(0.12 * reach));
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
    protected void sceneAct(int button, boolean shift) {
        DeployerMenu menu = menu();
        if (!hovered || menu == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ItemStack theirs = client.level != null && client.level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer
                ? DeployingScene.held(deployer) : null;
        if (menu.getCarried().isEmpty() && theirs != null && theirs.isEmpty()) {
            deny();
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
    protected Component sceneHint() {
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
