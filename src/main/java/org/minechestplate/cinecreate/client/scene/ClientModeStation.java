package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.station.StationKind;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.station.SceneBlock;
import org.minechestplate.cinecreate.station.SceneBlocks;

/**
 * Client mode: one of our blocks on a server without the add-on (DESIGN §8.6). With no menu to work through, the scene
 * is Create's own right-click played out: the hand reaches with the hotbar's item, and on the beat the game's own click
 * goes to the server.
 */
public class ClientModeStation extends ClientStation {
    /** A click: seconds, and the beat (Create's click is sent). A held crank turns every this many ticks. */
    static final double USE = 0.45, USE_BEAT = 0.2;
    static final int HOLD_EVERY = 4;

    private final boolean top, holds;
    private final Direction face;
    private final Vec3 work;
    private boolean hovered, beaten = true;
    private double usedAt = -10;
    private int holding = -1, heldTicks;

    public ClientModeStation(BlockPos pos, Direction side, BlockState state) {
        super(pos, side);
        SceneBlock block = SceneBlocks.of(state);
        top = block != null && block.usedOn().test(state, Direction.UP);
        holds = block == SceneBlocks.CRANK;
        Direction facing = state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING)
                : state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : side;
        face = top ? Direction.UP : block != null && block.usedOn().test(state, facing) ? facing : side;
        Minecraft mc = Minecraft.getInstance();
        double height = mc.level == null ? 1 : Math.max(0.2, state.getShape(mc.level, pos).max(Direction.Axis.Y));
        work = top ? Vec3.atBottomCenterOf(pos).add(0, height, 0) : Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
    }

    @Override
    public StationKind kind() {
        return StationKind.ADDON;
    }

    @Override
    public Vec3 center() {
        return work;
    }

    @Override
    public boolean workOnTop() {
        return top;
    }

    /** Never a walk: the click is the player's own, from where they stand. */
    @Override
    public List<Vec3> approach(LocalPlayer player) {
        return List.of();
    }

    /** Within the player's reach, as the game measures a click. */
    @Override
    public boolean workable(Vec3 feet) {
        LocalPlayer player = Minecraft.getInstance().player;
        double range = player == null ? 4.5 : player.blockInteractionRange();
        return new AABB(pos).distanceToSqr(feet.add(0, Views.EYE, 0)) < range * range;
    }

    @Override
    public CameraRig.Pose lean() {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 eye = player == null ? work.add(side().scale(1.2)).add(0, 0.6, 0) : player.getEyePosition();
        Vec3 look = work.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    public double leanFov() {
        return 64;
    }

    @Override
    public boolean showsInventory() {
        return false;
    }

    @Override
    public Vec3 rest(boolean right) {
        CameraRig.Pose camera = Grip.camera();
        return camera == null ? work : Grip.bodyRest(right, camera);
    }

    @Override
    public void updateCursor(boolean overPanel) {
        if (moving()) {
            return;
        }
        hovered = !overPanel && CineStation.underCursor(Picking.mouseGui(), work, 0.45);
        aim = hovered ? work.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.1)) : null;
    }

    /** Development (SmokeTest): points at the block as the cursor would. */
    public void hover() {
        hovered = true;
        aim = work.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.1));
    }

    @Override
    public void clearCursor() {
        if (!moving() && holding < 0) {
            hovered = false;
            aim = null;
        }
    }

    @Override
    public Vec3 handTarget(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        double t = Scene.clock() - usedAt;
        double press = t >= 0 && t <= USE ? Anim.hump(Anim.window(t, 0, USE)) : 0;
        return target.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.1 - 0.12 * press)).add(0, -0.02 * tap, 0);
    }

    @Override
    public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) {
        return hand;
    }

    private boolean moving() {
        return Scene.clock() - usedAt < USE;
    }

    @Override
    public void click(int button, boolean shift) {
        if (!hovered || moving()) {
            return;
        }
        usedAt = Scene.clock();
        beaten = false;
        if (holds) {
            holding = button;
            heldTicks = 0;
        }
    }

    @Override
    public void release(int button) {
        if (button == holding) {
            holding = -1;
        }
    }

    @Override
    public void beforeFrame() {
        if (!beaten && Scene.clock() - usedAt >= USE_BEAT) {
            beaten = true;
            use();
        }
    }

    @Override
    public void tick() {
        if (holding >= 0 && ++heldTicks % HOLD_EVERY == 0) {
            usedAt = Scene.clock();
            use();
        }
    }

    private void use() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && Scene.phase == Scene.Phase.ACTIVE) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(work, face, pos, false));
        }
    }

    @Override
    public boolean keyPressed(int key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            player.getInventory().selected = key - GLFW.GLFW_KEY_1;
            return true;
        }
        return false;
    }

    @Override
    public void scroll(double delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && delta != 0) {
            player.getInventory().selected = Math.floorMod(player.getInventory().selected - (int) Math.signum(delta), 9);
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        DevView.drawWorld(this, aim, List.of(), pose, buffers, camera);
        if (hovered) {
            float p = (float) (0.4 + 0.15 * Math.sin(Scene.clock() * 4));
            EnchantFx.disc(pose, buffers, camera, work.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.004)), 0.18, 0, EnchantFx.GLOW,
                    p, p * 0.75f, p * 0.45f);
        }
    }

    /** What the hand holds, drawn in it (there is no drawer: the hotbar's item is the one used). */
    @Override
    public void renderBody(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 hand = Grip.hand(true);
        if (player == null || hand == null || player.getMainHandItem().isEmpty()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        ItemPlacer.drawOriented(pose, buffers, camera, held, hand.add(0, -0.06, 0),
                Axis.YP.rotationDegrees((float) Scene.clock() * 20), ItemPlacer.isBlock(held) ? 0.16f : 0.22f, LightTexture.FULL_BRIGHT);
    }

    @Override
    public Component hint() {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        Component item = held.isEmpty() ? Component.translatable("cinecreate.hint.client_mode.empty_hand") : held.getHoverName();
        return Component.translatable(holds ? "cinecreate.hint.client_mode.hold" : "cinecreate.hint.client_mode", item);
    }
}
