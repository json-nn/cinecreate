package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.MillstoneMenu;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.fx.SceneFx;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;

/**
 * The Millstone (DESIGN §7.9): what is carried is poured into the mouth on top, flour dust puffs from the gap between
 * the stones while they grind, and an empty hand gathers what has been ground.
 */
public class MillstoneScene extends CineStation<MillstoneMenu> {
    /** Pouring: seconds, the beat (the stack goes in). Gathering: seconds, the beat. */
    static final double POUR = 0.7, POUR_BEAT = 0.25, TAKE = 0.5, TAKE_BEAT = 0.25;
    /** The fist over the mouth: carrying, bare. */
    static final double CARRY = 0.2, BARE = 0.14;
    /** Pieces trickling in: how many, from when to when (seconds into the pour), and their size. */
    static final int PIECES = 4;
    static final double PIECES_FROM = 0.2, PIECES_TO = 0.55;
    static final float PIECE_SIZE = 0.09f;
    /** The gap between the stones: this far below the top, this far out from the axis. */
    static final double GAP_DOWN = 10 / 16.0, GAP_OUT = 6.5 / 16;
    /** Flour dust, and how much of it: motes per tick per RPM (at most this many a tick). */
    static final int DUST = 0xEEE6D2;
    static final double DUST_PER_RPM = 1 / 160.0, DUST_MOST = 2;
    /** The faint hum of the stones while they grind. */
    static final double HUM = 0.05;
    /** Half the top the cursor can touch. */
    static final double MOUTH = 0.42;

    /** Development (SmokeTest): the mouth counts as pointed at wherever the cursor is. */
    public static boolean smokePin;
    /** Development (SmokeTest): pours, gatherings, dust motes puffed. */
    public int pours, gathers, puffs;

    private final Vec3 top;
    private boolean hovered;
    /** The move under way pours (else it gathers); what was poured, for the pieces trickling in. */
    private boolean pouring;
    private ItemStack poured = ItemStack.EMPTY;
    private double dust;
    private long drawnFrame = -1;

    public MillstoneScene(BlockPos pos, Direction side) {
        super(pos, side, MillstoneMenu.class, 0);
        top = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
    }

    @Nullable
    private MillstoneBlockEntity millstone() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof MillstoneBlockEntity millstone ? millstone : null;
    }

    public boolean turning() {
        MillstoneBlockEntity millstone = millstone();
        return millstone != null && millstone.getSpeed() != 0;
    }

    /** The stones grind: turning, with something in the mouth and Create's countdown running. */
    public boolean grinding() {
        MillstoneBlockEntity millstone = millstone();
        return millstone != null && millstone.getSpeed() != 0 && millstone.timer > 0 && !millstone.inputInv.getStackInSlot(0).isEmpty();
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return top;
    }

    @Override
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinecreate.layout.spot.mouth"));
    }

    @Override
    public Vec3 spotBase(int spot) {
        return top;
    }

    /** Where the hand pours (the layout's mouth spot). */
    private Vec3 mouth() {
        return spotBase(0);
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
    public CameraRig.Pose lean() {
        Vec3 eye = top.add(side().scale(0.8)).add(0, 0.7, 0);
        Vec3 look = top.add(0, -0.2, 0).subtract(eye);
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

    @Override
    protected Vec3 restAt(boolean right) {
        return top.add(side().scale(0.36)).add(right().scale(right ? 0.28 : -0.28)).add(0, 0.05, 0);
    }

    @Override
    public double cameraKick() {
        return grinding() ? HUM : 0;
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
        Vec3 mouth = mouth();
        Vec3 hit = Picking.cursorOnPlane(mouth.y);
        if (hit != null && inSpot(0, hit, mouth, MOUTH)) {
            hover();
        }
    }

    /** Development (SmokeTest): points at the mouth as the cursor would. */
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

    /** Where an empty hand gathers: by the gap where the ground output comes out, or the mouth when nothing is ground yet. */
    private Vec3 gatherPoint() {
        MillstoneBlockEntity millstone = millstone();
        boolean ground = false;
        for (int i = 0; millstone != null && i < millstone.outputInv.getSlots(); i++) {
            ground |= !millstone.outputInv.getStackInSlot(i).isEmpty();
        }
        return ground || millstone == null ? top.add(right().scale(0.5)).add(side().scale(0.12)).add(0, -0.3, 0) : spot(0).add(0, BARE, 0);
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        double t = moveTime();
        if (t < 0) {
            return target.add(0, (carrying ? CARRY : BARE) - 0.02 * tap, 0);
        }
        if (pouring) {
            double away = Anim.outCubic(Anim.window(t, PIECES_TO, POUR));
            return spot(0).add(side().scale(0.05)).add(0, CARRY - 0.04 * Anim.hump(Anim.window(t, 0, POUR_BEAT)) + 0.06 * away, 0);
        }
        double out = Anim.inOutCubic(Anim.window(t, 0, TAKE_BEAT)) - Anim.inOutCubic(Anim.window(t, TAKE_BEAT + 0.05, TAKE));
        return Anim.lerp(spot(0).add(0, BARE, 0), gatherPoint(), out);
    }

    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        return hand.add(0, -0.07, 0);
    }

    @Override
    public float heldItemTilt() {
        double t = moveTime();
        return t >= 0 && pouring ? (float) (18 + 60 * Anim.inOutCubic(Anim.window(t, 0, POUR_BEAT))) : 18f;
    }

    /** Create's own input decides what the stones take (a left click pours the stack, a right click one). */
    @Override
    protected void sceneAct(int button, boolean shift) {
        MillstoneMenu menu = menu();
        if (!hovered || menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (holdsAnything()) {
                pouring = false;
                startMove(TAKE, TAKE_BEAT, () -> {
                    buttonClick();
                    gathers++;
                    SceneFx.motes(gatherPoint(), 0.06, 5, Colors.red(DUST), Colors.green(DUST), Colors.blue(DUST), 0.06);
                });
            }
            return;
        }
        if (!accepts(MillstoneMenu.MOUTH, carried)) {
            deny();
            return;
        }
        pouring = true;
        poured = carried.copyWithCount(1);
        startMove(POUR, POUR_BEAT, () -> {
            click(button);
            pours++;
            SceneFx.dust(mouth(), SceneFx.FLOUR, 2, 0.06, 0.15);
        });
    }

    private boolean holdsAnything() {
        for (int i = 0; i < MillstoneMenu.SLOTS; i++) {
            if (!slot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void click(int button) {
        Minecraft mc = Minecraft.getInstance();
        MillstoneMenu menu = menu();
        if (menu != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, MillstoneMenu.MOUTH, button == 1 ? 1 : 0, ClickType.PICKUP, mc.player);
        }
    }

    private void buttonClick() {
        Minecraft mc = Minecraft.getInstance();
        MillstoneMenu menu = menu();
        if (menu != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, MillstoneMenu.TAKE_ALL);
        }
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return mouth();
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        ItemStack in = slot(MillstoneMenu.MOUTH);
        return hovered && carried.isEmpty() && !in.isEmpty() ? List.of(in.getHoverName()) : null;
    }

    @Override
    public SceneKind sceneKind() {
        return SceneKinds.MILLSTONE;
    }

    @Override
    protected Component sceneHint() {
        return Component.translatable(turning() ? "cinecreate.hint.millstone" : "cinecreate.hint.millstone.stopped");
    }

    // ---- the stones at work --------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        MillstoneBlockEntity millstone = millstone();
        if (millstone == null || !grinding()) {
            dust = 0;
            return;
        }
        dust += Math.min(DUST_MOST, Math.abs(millstone.getSpeed()) * DUST_PER_RPM);
        float r = Colors.red(DUST), g = Colors.green(DUST), b = Colors.blue(DUST);
        while (dust >= 1) {
            dust--;
            double a = Math.random() * Math.PI * 2;
            Vec3 at = top.add(Math.cos(a) * GAP_OUT, -GAP_DOWN + 0.02, Math.sin(a) * GAP_OUT);
            SceneFx.motes(at, 0.02, 1, r, g, b, 0.04);
            puffs++;
            Vec3 out = new Vec3(Math.cos(a), -0.2, Math.sin(a));
            if (Math.random() < 0.3) {
                SceneFx.dust(at, SceneFx.FLOUR, 1, 0.06, 0.08);
            }
            if (Math.random() < 0.35) {
                SceneFx.chips(at, millstone.inputInv.getStackInSlot(0), 1, out, 0.6, pos.getY());
            }
        }
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /** The pieces of a pour trickling into the mouth; drawn once a frame (CI calls this twice). */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        long frame = FrameCounter.frame();
        if (frame == drawnFrame) {
            return;
        }
        drawnFrame = frame;
        double t = moveTime();
        if (!pouring || t < 0 || poured.isEmpty()) {
            return;
        }
        Vec3 from = mouth().add(side().scale(0.05)).add(0, CARRY - 0.08, 0);
        Vec3 to = mouth().add(0, -0.2, 0);
        double each = (PIECES_TO - PIECES_FROM) / (PIECES + 1);
        for (int i = 0; i < PIECES; i++) {
            double start = PIECES_FROM + i * each;
            double k = Anim.window(t, start, start + 2 * each);
            if (k <= 0 || k >= 1) {
                continue;
            }
            double fall = k * k;
            Vec3 at = Anim.lerp(from, to, fall).add(right().scale(0.03 * Math.sin(i * 2.1)));
            float size = (float) (PIECE_SIZE * (1 - 0.6 * fall));
            ItemPlacer.drawOriented(pose, buffers, camera.getPosition(), poured, at, Axis.YP.rotationDegrees((float) (i * 70 + k * 200)), size, light);
        }
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        MillstoneMenu menu = menu();
        if (!hovered || moving() || menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        boolean ok = carried.isEmpty() ? holdsAnything() : accepts(MillstoneMenu.MOUTH, carried);
        ok = ok && notReady() == null;
        float p = (float) (0.45 + 0.15 * Math.sin(Scene.clock() * 4));
        EnchantFx.disc(pose, buffers, camera, mouth().add(0, 0.004, 0), 0.14, 0, EnchantFx.GLOW,
                p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
    }

    /** Development (SmokeTest): the input and the outputs, as the client's millstone has them. */
    @Nullable
    public ItemStack[] contents() {
        MillstoneBlockEntity millstone = millstone();
        if (millstone == null) {
            return null;
        }
        ItemStack[] all = new ItemStack[1 + millstone.outputInv.getSlots()];
        all[0] = millstone.inputInv.getStackInSlot(0);
        for (int i = 0; i < millstone.outputInv.getSlots(); i++) {
            all[1 + i] = millstone.outputInv.getStackInSlot(i);
        }
        return all;
    }
}
