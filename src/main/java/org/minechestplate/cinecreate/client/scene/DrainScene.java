package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.Fx;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.minechestplate.cinecreate.bridge.DrainMenu;

/**
 * The Item Drain (DESIGN §7.13). The filled container is laid on the far side of the grate; Create rolls it to the
 * middle and drains it, and the liquid spirals down into the grate in its colour. Then it rolls on toward the player and
 * off the edge, where the hand catches it. An empty hand takes back what lies there. (Create's drain gives its liquid to
 * pipes only: an empty bucket is laid down and rolls through like anything else.)
 */
public class DrainScene extends CineStation<DrainMenu> {
    /** The grate (what lies on it is drawn this high); where a container is laid, on the far side. */
    static final double GRATE = 15 / 16.0, LAY_AT = 0.25;
    /** Laying down: seconds, the beat. Taking back: seconds, the beat. */
    static final double LAY = 0.55, LAY_BEAT = 0.38, TAKE = 0.45, TAKE_BEAT = 0.22;
    /** The fist over the grate: carrying, bare; reaching for the near edge to catch. */
    static final double CARRY = 0.17, BARE = 0.11, HAND_Y = 0.062;
    /** How long the catching hand waits at the near edge after the container has gone. */
    static final double CATCH = 0.5;

    /** Development only ({@code SmokeTest}): the grate counts as pointed at while the cursor is somewhere else. */
    public static boolean smokePin;
    /** Development: containers laid down, drained (seen spiralling), caught. */
    public int laid, drained, caught;

    private final Vec3 top;
    private boolean hovered;

    private enum Move { LAY, TAKE }

    private Move move = Move.LAY;
    /** What lay on the grate last tick, and whether it was being drained; when the catching hand reached out. */
    private ItemStack lastLying = ItemStack.EMPTY;
    private boolean draining;
    private double caughtAt = -10;

    public DrainScene(BlockPos pos, Direction side) {
        super(pos, side, DrainMenu.class, 1);
        top = Vec3.atBottomCenterOf(pos).add(0, GRATE, 0);
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return top;
    }

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

    /** Leaning over the grate, as at the depot. */
    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = top.add(side().scale(0.72)).add(0, 0.62, 0);
        Vec3 look = top.add(side().scale(-0.04)).add(0, 0.04, 0).subtract(eye);
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
        return 45f;
    }

    /** Hands on the near rim; the right one reaches for the near edge while a drained container rolls off. */
    @Override
    protected Vec3 restAt(boolean right) {
        Vec3 rim = top.add(side().scale(0.34)).add(right().scale(right ? 0.3 : -0.3)).add(0, HAND_Y, 0);
        double reach = right ? Anim.hump(Anim.clamp01((Scene.clock() - caughtAt) / CATCH)) : 0;
        return rim.add(side().scale(0.2 * reach)).add(0, -0.05 * reach, 0);
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
        Vec3 hit = Picking.cursorOnPlane(top.y);
        if (hit != null && Math.abs(hit.x - top.x) <= 0.46 && Math.abs(hit.z - top.z) <= 0.46) {
            hover();
        }
    }

    /** Development: points at the grate as the cursor would. */
    public void hover() {
        hovered = true;
        aim = layAt();
    }

    @Override
    public void clearCursor() {
        if (!moving() && !smokePin) {
            hovered = false;
            aim = null;
        }
    }

    /** On the far side of the grate, where a container laid down goes in. */
    private Vec3 layAt() {
        return top.add(side().scale(-LAY_AT));
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        double t = moveTime();
        double lift;
        if (t < 0) {
            lift = carrying ? CARRY : BARE;
        } else if (move == Move.LAY) {
            double down = Anim.inOutCubic(Anim.window(t, 0.15, LAY_BEAT));
            double away = Anim.outCubic(Anim.window(t, LAY_BEAT, LAY));
            lift = CARRY + (0.08 - CARRY) * down + (BARE - 0.08) * away;
        } else {
            double down = Anim.inOutCubic(Anim.window(t, 0, TAKE_BEAT - 0.02));
            double up = Anim.outCubic(Anim.window(t, TAKE_BEAT, TAKE));
            lift = BARE + (0.07 - BARE) * down + (CARRY - 0.07) * up;
        }
        return target.add(0, lift - 0.02 * tap, 0);
    }

    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        return hand.add(0, -0.07, 0);
    }

    /**
     * Something in hand is laid on the grate (a filled container one at a time, as the drain takes them). An empty hand
     * takes back what lies there.
     */
    @Override
    protected void act(int button, boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        DrainMenu menu = menu();
        if (!hovered || menu == null || mc.level == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (!slot(DrainMenu.MAIN).isEmpty()) {
                move(Move.TAKE, () -> click(shift ? ClickType.QUICK_MOVE : ClickType.PICKUP));
            }
            return;
        }
        if (!slot(DrainMenu.MAIN).isEmpty() || !accepts(DrainMenu.MAIN, carried)) {
            deny();
            return;
        }
        move(Move.LAY, () -> {
            click(ClickType.PICKUP);
            laid++;
            Fx.motes(layAt().add(0, 0.02, 0), 0.08, 5, 0.62f, 0.6f, 0.56f, 0.05);
        });
    }

    private void move(Move kind, Runnable beat) {
        move = kind;
        startMove(kind == Move.LAY ? LAY : TAKE, kind == Move.LAY ? LAY_BEAT : TAKE_BEAT, beat);
    }

    private void click(ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        DrainMenu menu = menu();
        if (menu != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, DrainMenu.MAIN, 0, type, mc.player);
        }
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return top;
    }

    @Override
    public Component hint() {
        return Component.translatable("cinecreate.hint.item_drain");
    }

    // ---- the drain at work ---------------------------------------------------------------------------

    /**
     * Client tick: a filled container in the middle of the grate is being drained (the liquid spirals into the grate in
     * its colour); one that rolls off the near edge is caught.
     */
    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain)) {
            return;
        }
        ItemStack now = drain.getHeldItemStack();
        boolean full = !now.isEmpty() && GenericItemEmptying.canItemBeEmptied(mc.level, now);
        if (full) {
            FluidStack fluid = GenericItemEmptying.emptyItem(mc.level, now, true).getFirst();
            int color = Colors.of(fluid);
            double turn = Scene.clock() * 9;
            for (int i = 0; i < 2; i++) {
                double a = turn + i * Math.PI;
                Vec3 at = top.add(Math.cos(a) * 0.12, 0.05, Math.sin(a) * 0.12);
                Fx.spark(at, new Vec3(-Math.sin(a) * 0.4, -0.5, Math.cos(a) * 0.4), 0.3, 0.012f,
                        Colors.red(color), Colors.green(color), Colors.blue(color), 0.5, 0.03);
            }
        }
        if (draining && !full && !now.isEmpty()) {
            drained++;
        }
        if (!lastLying.isEmpty() && now.isEmpty() && moveTime() < 0) {
            caught++;
            caughtAt = Scene.clock();
            Fx.motes(top.add(side().scale(0.45)), 0.08, 6, 0.85f, 0.85f, 0.85f, 0.08);
        }
        draining = full;
        lastLying = now.copy();
    }

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

    /** A warm glow where the container goes; red when the drain will not take what is carried. */
    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        DrainMenu menu = menu();
        if (!hovered || moving() || menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        boolean ok = carried.isEmpty() ? !slot(DrainMenu.MAIN).isEmpty() : slot(DrainMenu.MAIN).isEmpty() && accepts(DrainMenu.MAIN, carried);
        float p = (float) (0.45 + 0.15 * Math.sin(Scene.clock() * 4));
        EnchantFx.disc(pose, buffers, camera, layAt().add(0, 0.004, 0), 0.14, 0, EnchantFx.GLOW,
                p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
    }

    /** Development: what lies on the grate, as the client's drain has it. */
    @Nullable
    public ItemStack onGrate() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain ? drain.getHeldItemStack() : null;
    }
}
