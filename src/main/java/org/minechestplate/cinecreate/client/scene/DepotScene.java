package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.station.SceneBlocks;

/**
 * The depot (DESIGN §7.1): lay the held stack on the plate or take what lies there, through {@link DepotMenu}, so what
 * fits is Create's decision. The base of every depot composite: a machine working the depot makes the scene its own.
 */
public class DepotScene extends CineStation<DepotMenu> {
    /** Setting a stack down: seconds, and the beat (the click, the frame it touches the plate). */
    static final double SET_DOWN = 0.55, SET_DOWN_BEAT = 0.38;
    /** Taking it: seconds, and the beat (the fingers close on it). */
    static final double TAKE = 0.45, TAKE_BEAT = 0.20;
    /** How far a held item hangs under the fist. */
    static final double HOLD = 0.07;
    /** The fist over the plate: carrying, lifted before setting down, setting down, touching bare, at rest. */
    static final double CARRY = 0.17, LIFT = 0.06, SET = 0.085, TOUCH = 0.075, BARE = 0.11;
    static final double HAND_Y = 0.062;
    /** A stack just set down is drawn by the scene until Create's own renderer has it (one sync), at most this long. */
    static final double GHOST = 0.6;
    /** Half the plate the cursor can touch. */
    static final double PLATE = 0.46;

    /** Development (SmokeTest): the plate counts as touched wherever the cursor is. */
    public static boolean smokePin;

    protected final Vec3 top;
    /** Where a stack set down lies: short of the middle, where the server sets it ({@link DepotMenu#SET_DOWN_AT}). */
    private final Vec3 setAt;
    protected boolean hovered;
    private boolean setting;
    private ItemStack ghost = ItemStack.EMPTY;
    private double ghostAt = -10;
    private long drawnFrame = -1;

    public DepotScene(BlockPos pos, Direction side) {
        super(pos, side, DepotMenu.class, 0);
        top = Vec3.atBottomCenterOf(pos).add(0, SceneBlocks.DEPOT_TOP, 0);
        setAt = top.add(side().scale(0.5 - DepotMenu.SET_DOWN_AT));
    }

    @Override
    public Vec3 center() {
        return top;
    }

    @Override
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinecreate.layout.spot.plate"));
    }

    @Override
    public Vec3 spotBase(int spot) {
        return setAt;
    }

    /** Where a stack is laid and taken. */
    protected final Vec3 plate() {
        return setAt;
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = new Vec3(top.x + side.getStepX() * 0.86, player.getY(), top.z + side.getStepZ() * 0.86);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public boolean worksAtAnAngle() {
        return true;
    }

    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = top.add(side().scale(0.72)).add(0, 0.62, 0);
        Vec3 look = top.add(side().scale(-0.04)).add(0, 0.06, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    public double leanFov() {
        return 64;
    }

    /** With a machine over the depot: pulled back and a little lower, so the machine's head is in the frame too. */
    protected CameraRig.Pose machineLean() {
        Vec3 eye = top.add(side().scale(1.05)).add(0, 0.55, 0);
        Vec3 look = top.add(0, 0.45, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, -1.5f);
    }

    protected ItemStack lying() {
        return slot(DepotMenu.MAIN);
    }

    @Override
    public float bodyPitch() {
        return 45f;
    }

    @Override
    protected Vec3 restAt(boolean right) {
        return top.add(side().scale(0.34)).add(right().scale(right ? 0.3 : -0.3)).add(0, HAND_Y, 0);
    }

    @Override
    public SoundEvent touchSound() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? super.touchSound() : mc.level.getBlockState(pos).getSoundType(mc.level, pos, mc.player).getStepSound();
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
        Vec3 middle = top.add(plate().subtract(setAt));
        Vec3 hit = Picking.cursorOnPlane(middle.y + 0.01);
        if (hit != null && inSpot(0, hit, middle, PLATE)) {
            hover();
        }
    }

    /** Development (SmokeTest): touches the plate as the cursor would. */
    public void hover() {
        hovered = true;
        aim = spot(0);
    }

    /** The cursor leaving does not stop a move half way, nor take the hand off a pinned plate. */
    @Override
    public void clearCursor() {
        if (!moving() && !smokePin) {
            hovered = false;
            aim = null;
        }
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        double t = moveTime();
        double lift;
        if (t < 0) {
            lift = carrying ? CARRY : BARE;
        } else if (setting) {
            double high = CARRY + Anim.outCubic(Anim.window(t, 0, 0.2)) * LIFT;
            double down = Anim.inOutCubic(Anim.window(t, 0.2, SET_DOWN_BEAT));
            double away = Anim.outCubic(Anim.window(t, SET_DOWN_BEAT, SET_DOWN));
            lift = high + (SET - high) * down + (BARE - SET) * away;
        } else {
            double down = Anim.inOutCubic(Anim.window(t, 0, TAKE_BEAT - 0.02));
            double up = Anim.outCubic(Anim.window(t, TAKE_BEAT, TAKE));
            lift = BARE + (TOUCH - BARE) * down + (CARRY - TOUCH) * up;
        }
        return target.add(0, lift - 0.02 * tap, 0);
    }

    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        return hand.add(0, -HOLD, 0);
    }

    @Override
    public float heldItemTilt() {
        double t = moveTime();
        return t >= 0 && setting ? (float) (18 * (1 - Anim.inOutCubic(Anim.window(t, 0.05, SET_DOWN_BEAT)))) : 18f;
    }

    @Override
    protected void sceneAct(int button, boolean shift) {
        if (!hovered) {
            return;
        }
        DepotMenu menu = menu();
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            if (!accepts(DepotMenu.MAIN, carried)) {
                deny();
                return;
            }
            setDown(button);
        } else if (holdsAnything()) {
            take(shift);
        }
    }

    private void setDown(int button) {
        setting = true;
        startMove(SET_DOWN, SET_DOWN_BEAT, () -> {
            click(DepotMenu.MAIN, button, ClickType.PICKUP);
            ghost = slot(DepotMenu.MAIN).copy();
            ghostAt = Scene.clock();
            Fx.motes(plate().add(0, 0.02, 0), 0.08, 6, 0.62f, 0.6f, 0.56f, 0.05);
        });
    }

    private void take(boolean shift) {
        setting = false;
        startMove(TAKE, TAKE_BEAT, () -> {
            if (!slot(DepotMenu.MAIN).isEmpty()) {
                click(DepotMenu.MAIN, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            }
            // what processing left goes to the inventory, as with Create's own empty-handed use
            for (int i = 1; i <= DepotMenu.OUTPUTS; i++) {
                if (!slot(i).isEmpty()) {
                    click(i, 0, ClickType.QUICK_MOVE);
                }
            }
            ghost = ItemStack.EMPTY;
        });
    }

    private boolean holdsAnything() {
        for (int i = 0; i < DepotMenu.SLOTS; i++) {
            if (!slot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A click on the menu at once: on the beat, {@code slotClick} would wait for the move to end. */
    private void click(int slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        DepotMenu menu = menu();
        if (menu != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
        }
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return plate();
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        ItemStack lying = slot(DepotMenu.MAIN);
        return hovered && carried.isEmpty() && !lying.isEmpty() && notReady() == null ? List.of(lying.getHoverName()) : null;
    }

    @Override
    protected Component sceneHint() {
        return Component.translatable("cinecreate.hint.depot");
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /**
     * The stack just set down, until Create draws it: the client's slot has it at once (the click is predicted), Create's
     * depot only once the server has sent it. CI calls this twice a frame for add-on stations: it draws once.
     */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        long frame = FrameCounter.frame();
        if (frame == drawnFrame) {
            return;
        }
        drawnFrame = frame;
        if (ghost.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        DepotBehaviour depot = mc.level == null ? null : BlockEntityBehaviour.get(mc.level, pos, DepotBehaviour.TYPE);
        boolean shown = depot != null && ItemStack.isSameItemSameComponents(depot.getHeldItemStack(), ghost);
        if (shown || Scene.clock() - ghostAt > GHOST) {
            ghost = ItemStack.EMPTY;
            return;
        }
        ItemPlacer.render(pose, buffers, camera.getPosition(), ghost, plate(), side, 31L, 1f, 0f, light);
    }

    /** A warm glow on the plate under the cursor; red when the depot will not take what is carried. */
    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        if (!hovered || moving()) {
            return;
        }
        ItemStack carried = menu() == null ? ItemStack.EMPTY : menu().getCarried();
        boolean ok = carried.isEmpty() ? holdsAnything() : accepts(DepotMenu.MAIN, carried);
        ok = ok && notReady() == null;
        float p = (float) (0.45 + 0.15 * Math.sin(Scene.clock() * 4));
        EnchantFx.disc(pose, buffers, camera, plate().add(0, 0.004, 0), 0.16, 0, EnchantFx.GLOW,
                p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
    }
}
