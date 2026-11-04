package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.minechestplate.cinecreate.bridge.DrainMenu;
import org.minechestplate.cinecreate.client.fx.SceneFx;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;

/**
 * The Item Drain (DESIGN §7.13). A filled container is laid on the far side of the grate; Create rolls it to the middle,
 * drains it and rolls it off the near edge, where the hand catches it. Create's drain gives its liquid to pipes only: an
 * empty bucket rolls through like anything else.
 */
public class DrainScene extends CineStation<DrainMenu> {
    /** The grate (what lies on it is drawn this high); where a container is laid, on the far side. */
    static final double GRATE = 15 / 16.0, LAY_AT = 0.25;
    /** Laying down: seconds, the beat. Taking back: seconds, the beat. */
    static final double LAY = 0.55, LAY_BEAT = 0.38, TAKE = 0.45, TAKE_BEAT = 0.22;
    /** The fist over the grate: carrying, bare; the resting hands on the rim. */
    static final double CARRY = 0.17, BARE = 0.11, HAND_Y = 0.062;
    /** How long the catching hand waits at the near edge after the container has gone. */
    static final double CATCH = 0.5;

    /** Development (SmokeTest): the grate counts as pointed at wherever the cursor is. */
    public static boolean smokePin;
    /** Development (SmokeTest): containers laid down, drained, caught. */
    public int laid, drained, caught;

    private final Vec3 top;
    private boolean hovered;

    private enum Move { LAY, TAKE }

    private Move move = Move.LAY;
    private ItemStack lastLying = ItemStack.EMPTY;
    private boolean draining;
    private int drainColor = 0xFFFFFF;
    private double caughtAt = -10;
    /** When the hand last took from the grate: Create's sync of that comes later, and is not a catch. */
    private double tookAt = -10;
    static final double TOOK_SYNC = 1.0;

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
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinecreate.layout.spot.grate"));
    }

    @Override
    public Vec3 spotBase(int spot) {
        return top.add(side().scale(-LAY_AT));
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

    @Override
    protected CameraRig.Pose sceneLean() {
        Vec3 eye = top.add(side().scale(0.72)).add(0, 0.62, 0);
        Vec3 look = top.add(side().scale(-0.04)).add(0, 0.04, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    protected double sceneFov() {
        return 64;
    }

    @Override
    public float bodyPitch() {
        return 45f;
    }

    /** Hands on the near rim; the right one reaches for the near edge when a drained container rolls off. */
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
        Vec3 middle = top.add(layAt().subtract(spotBase(0)));
        Vec3 hit = Picking.cursorOnPlane(middle.y);
        if (hit != null && inSpot(0, hit, middle, 0.46)) {
            hover();
        }
    }

    /** Development (SmokeTest): points at the grate as the cursor would. */
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

    /** Where a container is laid (the layout's grate spot). */
    private Vec3 layAt() {
        return spotBase(0);
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

    /** Lays what is carried (one container at a time, as the drain takes them); an empty hand takes back. */
    @Override
    protected void sceneAct(int button, boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        DrainMenu menu = menu();
        if (!hovered || menu == null || mc.level == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (!slot(DrainMenu.MAIN).isEmpty()) {
                move(Move.TAKE, () -> {
                    tookAt = Scene.clock();
                    click(shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
                });
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
            SceneFx.motes(layAt().add(0, 0.02, 0), 0.08, 5, 0.62f, 0.6f, 0.56f, 0.05);
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
    public SceneKind sceneKind() {
        return SceneKinds.DRAIN;
    }

    @Override
    protected Component sceneHint() {
        return Component.translatable("cinecreate.hint.item_drain");
    }

    // ---- the drain at work ---------------------------------------------------------------------------

    /** A filled container on the grate is drained (the liquid spirals in, in its colour); one rolling off is caught. */
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
            if (!draining) {
                SceneFx.splash(top, 0.35, color, 0.5);
            }
            drainColor = color;
            double turn = Scene.clock() * 9;
            for (int i = 0; i < 2; i++) {
                double a = turn + i * Math.PI;
                Vec3 at = top.add(Math.cos(a) * 0.12, 0.05, Math.sin(a) * 0.12);
                SceneFx.spark(at, new Vec3(-Math.sin(a) * 0.4, -0.5, Math.cos(a) * 0.4), 0.3, 0.012f,
                        Colors.red(color), Colors.green(color), Colors.blue(color), 0.5, 0.03);
            }
        }
        if (draining && !full && !now.isEmpty()) {
            drained++;
        }
        if (!lastLying.isEmpty() && now.isEmpty() && moveTime() < 0 && Scene.clock() - tookAt > TOOK_SYNC) {
            caught++;
            caughtAt = Scene.clock();
            SceneFx.motes(top.add(side().scale(0.45)), 0.08, 6, 0.85f, 0.85f, 0.85f, 0.08);
        }
        draining = full;
        lastLying = now.copy();
    }

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        if (draining) {
            SceneFx.drain(top, 0.3, drainColor);
        }
        DrainMenu menu = menu();
        if (!hovered || moving() || menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        boolean ok = carried.isEmpty() ? !slot(DrainMenu.MAIN).isEmpty() : slot(DrainMenu.MAIN).isEmpty() && accepts(DrainMenu.MAIN, carried);
        ok = ok && notReady() == null;
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
