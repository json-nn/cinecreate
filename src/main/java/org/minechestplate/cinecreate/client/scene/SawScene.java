package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.SawMenu;

/**
 * A Mechanical Saw facing up (DESIGN §7.8), the stonecutter of Create. The stone is laid on the table, and the shapes it
 * can be cut into hang in an arc behind the blade (CI's stonecutter dial). Choosing one sets the saw's filter: that shape
 * glows, the others dim. While the blade spins, one hand pushes the stone along in step with Create's own progress,
 * dust sprays in the stone's colour, and the other hand waits at the far edge where the pieces come out.
 */
public class SawScene extends CineStation<SawMenu> {
    /** The table (the saw's 12 px casing). */
    static final double TABLE = 12 / 16.0;
    /** The dial: as wide and as high as CI's stonecutter's; how big a shape is drawn. */
    static final double DIAL_WIDE = 0.72, DIAL_HIGH = 0.5;
    static final float SHAPE_SIZE = 0.15f;
    /** Laying down: seconds, the beat. Taking back: seconds, the beat. Touching a shape: seconds, the beat. */
    static final double LAY = 0.55, LAY_BEAT = 0.38, TAKE = 0.45, TAKE_BEAT = 0.22, CHOOSE = 0.3, CHOOSE_BEAT = 0.14;
    /** The fist over the table: carrying, bare. */
    static final double CARRY = 0.17, BARE = 0.11, HAND_Y = 0.062;
    /** Dust: sparks per tick per RPM, at most this many a tick (110 a second). */
    static final double DUST_PER_RPM = 5.5 / 256, DUST_MOST = 5.5;
    /** Half the table the cursor can touch. */
    static final double PLATE = 0.46;

    /** Development only ({@code SmokeTest}): what {@link #hoverTable}/{@link #hoverShape} pointed at stays pointed at. */
    public static boolean smokePin;
    /** Development: stacks laid down, shapes chosen, cuts Create finished (and the first piece of the last one), dust sparks. */
    public int laid, chosen, cuts, sparks;
    public ItemStack lastCut = ItemStack.EMPTY;

    private final Vec3 top;
    private boolean onTable;
    private int hoverShape = -1;
    private enum Move { LAY, TAKE, CHOOSE }
    private Move move = Move.LAY;
    /** The input the dial shows shapes for, and when that changed (the shapes pop in). */
    private ItemStack dialFor = ItemStack.EMPTY;
    private double shapesAt = -10;
    private boolean wasApplied;
    private double dust;

    public SawScene(BlockPos pos, Direction side) {
        super(pos, side, SawMenu.class, 0);
        top = Vec3.atBottomCenterOf(pos).add(0, TABLE, 0);
    }

    @Nullable
    private SawBlockEntity saw() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof SawBlockEntity saw ? saw : null;
    }

    public boolean turning() {
        SawBlockEntity saw = saw();
        return saw != null && saw.getSpeed() != 0;
    }

    /** The saw cuts: turning, with something on the table that Create has not finished yet. */
    public boolean cutting() {
        SawBlockEntity saw = saw();
        return saw != null && saw.getSpeed() != 0 && !saw.inventory.isEmpty() && !saw.inventory.appliedRecipe
                && saw.inventory.recipeDuration > 0;
    }

    /** Create's progress through the cut: 0 as it starts, 1 when the pieces fall apart. */
    public double progress() {
        SawBlockEntity saw = saw();
        if (saw == null || saw.inventory.recipeDuration <= 0 || saw.inventory.isEmpty()) {
            return 0;
        }
        return saw.inventory.appliedRecipe ? 1 : Anim.clamp01(1 - saw.inventory.remainingTime / saw.inventory.recipeDuration);
    }

    /** The way Create moves things across the table (it flips with the blade's direction). */
    private Vec3 along() {
        SawBlockEntity saw = saw();
        return saw == null ? side().scale(-1) : saw.getItemMovementVec();
    }

    /**
     * Where Create draws what is on the table: from the entry edge to the middle while it is cut, then on to the far
     * edge as the pieces leave (Create's renderer, without its frame interpolation).
     */
    private Vec3 piecePoint() {
        SawBlockEntity saw = saw();
        if (saw == null || saw.inventory.recipeDuration <= 0 || saw.getSpeed() == 0) {
            return top.add(0, 0.06, 0);
        }
        double offset = Math.max(0.125, Math.min(1, saw.inventory.remainingTime / saw.inventory.recipeDuration));
        if (!saw.inventory.appliedRecipe) {
            offset += 1;
        }
        offset /= 2;
        return top.add(along().scale(0.5 - offset)).add(0, 0.06, 0);
    }

    /** The hand that pushes is the one on the side things come in from; the other waits where they go out. */
    private boolean rightPushes() {
        return along().dot(right()) <= 0;
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return top;
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = new Vec3(top.x + side.getStepX() * 0.95, player.getY(), top.z + side.getStepZ() * 0.95);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public boolean worksAtAnAngle() {
        return true;
    }

    /** As CI's stonecutter. */
    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = top.add(side().scale(1.0)).add(0, 0.95, 0);
        Vec3 look = top.add(side().scale(-0.05)).add(0, 0.25, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 1.5f);
    }

    @Override
    public double leanFov() {
        return 64;
    }

    @Override
    public float bodyPitch() {
        return 50f;
    }

    /**
     * Hands on the near rim. While the saw cuts, one pushes the work along, just behind where Create draws it, and the
     * other waits at the far edge for the pieces.
     */
    @Override
    protected Vec3 restAt(boolean right) {
        Vec3 rim = top.add(side().scale(0.36)).add(right().scale(right ? 0.28 : -0.3)).add(0, HAND_Y, 0);
        SawBlockEntity saw = saw();
        if (saw == null || saw.inventory.isEmpty() || saw.getSpeed() == 0) {
            return rim;
        }
        if (right == rightPushes()) {
            return saw.inventory.appliedRecipe ? rim : piecePoint().subtract(along().scale(0.13)).add(0, 0.02, 0);
        }
        // у дальнего края, но на своей стороне: рука не тянется поперёк диска
        Vec3 aside = Math.abs(along().dot(right())) > 0.5 ? side().scale(0.15) : right().scale(right ? 0.22 : -0.22);
        return top.add(along().scale(0.62)).add(aside).add(0, 0.05, 0);
    }

    @Override
    public double cameraKick() {
        return cutting() ? 0.04 : 0;
    }

    // ---- the dial ------------------------------------------------------------------------------------

    /** The shapes on the dial, for what is on the table, or what is carried while the table is empty. */
    private List<ItemStack> shapes() {
        Minecraft mc = Minecraft.getInstance();
        SawMenu menu = menu();
        return mc.level == null || menu == null ? List.of() : SawMenu.shapes(mc.level, SawMenu.input(slot(SawMenu.TABLE), menu.getCarried()));
    }

    /** The shape the saw's filter is set to, or -1. */
    private int selected(List<ItemStack> shapes) {
        SawBlockEntity saw = saw();
        FilteringBehaviour filter = saw == null ? null : BlockEntityBehaviour.get(saw, FilteringBehaviour.TYPE);
        ItemStack chosen = filter == null ? ItemStack.EMPTY : filter.getFilter();
        if (chosen.isEmpty()) {
            return -1;
        }
        for (int k = 0; k < shapes.size(); k++) {
            if (ItemStack.isSameItem(shapes.get(k), chosen)) {
                return k;
            }
        }
        return -1;
    }

    private double step(int n) {
        return n <= 1 ? 0 : Math.min(22, 144.0 / (n - 1));
    }

    /** A shape's place on the dial: an arc behind the blade, as CI's stonecutter hangs its shapes. */
    private Vec3 shapePoint(int k, int n) {
        double a = Math.toRadians(90 + ((n - 1) / 2.0 - k) * step(n));
        double bob = 0.008 * Math.sin(Scene.clock() * 1.7 + k * 1.3);
        return top.add(side().scale(-0.06)).add(right().scale(Math.cos(a) * DIAL_WIDE)).add(0, 0.04 + Math.sin(a) * DIAL_HIGH + bob, 0);
    }

    /** Scrolling turns the dial: the filter steps to the next shape (past the last one: no filter). */
    @Override
    public void scroll(double delta) {
        List<ItemStack> shapes = shapes();
        if (shapes.isEmpty() || moving()) {
            return;
        }
        int now = selected(shapes);
        int next = now - (int) Math.signum(delta);
        if (next < -1) {
            next = shapes.size() - 1;
        } else if (next >= shapes.size()) {
            next = -1;
        }
        choose(next);
    }

    /** The filter to shape k (-1: none), through the menu. */
    private void choose(int k) {
        Minecraft mc = Minecraft.getInstance();
        SawMenu menu = menu();
        if (menu == null || mc.gameMode == null) {
            return;
        }
        mc.gameMode.handleInventoryButtonClick(menu.containerId, k < 0 ? SawMenu.ANY_SHAPE : SawMenu.SHAPE + k);
        chosen++;
        if (mc.level != null) {
            mc.level.playLocalSound(top.x, top.y, top.z, SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 0.3f, 1.4f, false);
        }
    }

    // ---- interaction ---------------------------------------------------------------------------------

    @Override
    public void updateCursor(boolean overPanel) {
        if (moving() || smokePin) {
            return;
        }
        onTable = false;
        hoverShape = -1;
        aim = null;
        if (overPanel || menu() == null) {
            return;
        }
        double[] mouse = Picking.mouseGui();
        List<ItemStack> shapes = shapes();
        for (int k = 0; k < shapes.size(); k++) {
            if (under(mouse, shapePoint(k, shapes.size()), 0.075)) {
                hoverShape(k);
                return;
            }
        }
        Vec3 hit = Picking.cursorOnPlane(top.y);
        if (hit != null && Math.abs(hit.x - top.x) <= PLATE && Math.abs(hit.z - top.z) <= PLATE) {
            hoverTable();
        }
    }

    /** Whether the cursor is within this far (in the world, at least 9 GUI pixels) of the point on the screen. */
    private static boolean under(double[] mouse, Vec3 center, double radius) {
        double[] at = Picking.toGui(center);
        double[] edge = Picking.toGui(center.add(0, radius, 0));
        if (at == null || edge == null) {
            return false;
        }
        double r = Math.max(9, Math.hypot(at[0] - edge[0], at[1] - edge[1]));
        return Math.hypot(mouse[0] - at[0], mouse[1] - at[1]) < r;
    }

    /** Development: points at the table as the cursor would. */
    public void hoverTable() {
        onTable = true;
        hoverShape = -1;
        aim = top.add(0, 0.02, 0);
    }

    /** Development: points at shape k on the dial as the cursor would. */
    public void hoverShape(int k) {
        int n = shapes().size();
        onTable = false;
        hoverShape = k;
        aim = k < n ? shapePoint(k, n).add(0, -0.1, 0).add(side().scale(0.12)) : null;
    }

    @Override
    public void clearCursor() {
        if (!moving() && !smokePin) {
            onTable = false;
            hoverShape = -1;
            aim = null;
        }
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        double t = moveTime();
        if (hoverShape >= 0 || t >= 0 && move == Move.CHOOSE) {
            // the fingertip reaches up to the shape
            double touch = t >= 0 && move == Move.CHOOSE ? Anim.hump(Anim.window(t, 0, CHOOSE)) : 0;
            return target.add(side().scale(-0.06 * touch)).add(0, -0.01 * tap, 0);
        }
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
     * On a shape: the saw's filter is set to it (touching the chosen one again clears it). On the table: something carried
     * is laid there (a left click the stack, a right click one); an empty hand takes back what lies there, as Create's
     * own empty-handed click does.
     */
    @Override
    protected void act(int button, boolean shift) {
        SawMenu menu = menu();
        if (menu == null) {
            return;
        }
        if (hoverShape >= 0) {
            List<ItemStack> shapes = shapes();
            int k = hoverShape;
            if (k < shapes.size()) {
                int pick = selected(shapes) == k ? -1 : k;
                move = Move.CHOOSE;
                startMove(CHOOSE, CHOOSE_BEAT, () -> choose(pick));
            }
            return;
        }
        if (!onTable) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (!slot(SawMenu.TABLE).isEmpty()) {
                move = Move.TAKE;
                startMove(TAKE, TAKE_BEAT, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    SawMenu open = menu();
                    if (open != null && mc.gameMode != null) {
                        mc.gameMode.handleInventoryButtonClick(open.containerId, SawMenu.TAKE_ALL);
                    }
                });
            }
            return;
        }
        if (!slot(SawMenu.TABLE).isEmpty() || !accepts(SawMenu.TABLE, carried)) {
            deny();
            return;
        }
        move = Move.LAY;
        startMove(LAY, LAY_BEAT, () -> {
            Minecraft mc = Minecraft.getInstance();
            SawMenu open = menu();
            if (open != null && mc.gameMode != null && mc.player != null) {
                mc.gameMode.handleInventoryMouseClick(open.containerId, SawMenu.TABLE, button == 1 ? 1 : 0, ClickType.PICKUP, mc.player);
            }
            laid++;
            Fx.motes(top.add(0, 0.03, 0), 0.08, 5, 0.62f, 0.6f, 0.56f, 0.05);
        });
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return top;
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        List<ItemStack> shapes = shapes();
        if (hoverShape >= 0 && hoverShape < shapes.size()) {
            return List.of(shapes.get(hoverShape).getHoverName());
        }
        ItemStack lying = slot(SawMenu.TABLE);
        return onTable && carried.isEmpty() && !lying.isEmpty() ? List.of(lying.getHoverName()) : null;
    }

    @Override
    public Component hint() {
        return Component.translatable(turning() ? "cinecreate.hint.mechanical_saw" : "cinecreate.hint.mechanical_saw.stopped");
    }

    // ---- the saw at work -----------------------------------------------------------------------------

    /** Client tick: dust from the blade in the stone's colour while it cuts; a finished cut is counted with its pieces. */
    @Override
    public void tick() {
        super.tick();
        SawBlockEntity saw = saw();
        if (saw == null) {
            return;
        }
        boolean applied = saw.inventory.appliedRecipe;
        if (applied && !wasApplied) {
            cuts++;
            for (int i = 1; i < saw.inventory.getSlots(); i++) {
                if (!saw.inventory.getStackInSlot(i).isEmpty()) {
                    lastCut = saw.inventory.getStackInSlot(i).copy();
                    break;
                }
            }
        }
        wasApplied = applied;
        if (!cutting()) {
            dust = 0;
            return;
        }
        int color = dustColor(saw.inventory.getStackInSlot(0));
        dust += Math.min(DUST_MOST, Math.abs(saw.getSpeed()) * DUST_PER_RPM);
        while (dust >= 1) {
            dust--;
            Vec3 spray = along().scale(0.6).add(Math.random() - 0.5, 1.2 + Math.random(), Math.random() - 0.5).normalize();
            Fx.spark(top.add(0, 0.12, 0), spray.scale(0.9 + Math.random() * 0.8), 0.25 + Math.random() * 0.25,
                    0.008f + (float) Math.random() * 0.006f, Colors.red(color), Colors.green(color), Colors.blue(color), 3.5, 0.03);
            sparks++;
        }
    }

    /** The dust's colour: a block's map colour, else a sawdust tan. */
    private static int dustColor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block) {
            int color = block.getBlock().defaultMapColor().col;
            if (color != 0) {
                return color;
            }
        }
        return 0xC8B08A;
    }

    // ---- drawing -------------------------------------------------------------------------------------

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        ItemStack input = menu() == null ? ItemStack.EMPTY : SawMenu.input(slot(SawMenu.TABLE), menu().getCarried());
        if (!ItemStack.isSameItem(input, dialFor)) {
            dialFor = input.copy();
            shapesAt = Scene.clock();
        }
    }

    /** The dial is drawn in {@link #renderWorld} (CI calls this one twice a frame for add-on stations). */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

    /**
     * The dial: each shape turning slowly on its glow, the hovered one bigger, the chosen one ringed; with a filter set,
     * the others dim. A warm glow on the table under the cursor; red when the saw will not take what is carried.
     */
    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        SawMenu menu = menu();
        if (menu == null) {
            return;
        }
        double clock = Scene.clock();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (onTable && !moving()) {
            ItemStack carried = menu.getCarried();
            boolean ok = carried.isEmpty() ? !slot(SawMenu.TABLE).isEmpty() : slot(SawMenu.TABLE).isEmpty() && accepts(SawMenu.TABLE, carried);
            float p = (float) (0.45 + 0.15 * Math.sin(clock * 4));
            EnchantFx.disc(pose, buffers, camera, top.add(0, 0.004, 0), 0.16, 0, EnchantFx.GLOW,
                    p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
        }
        List<ItemStack> shapes = shapes();
        int n = shapes.size();
        int chosenShape = selected(shapes);
        double appear = Anim.outBack(Anim.window(clock - shapesAt, 0, 0.4), 1.4);
        for (int k = 0; k < n; k++) {
            Vec3 at = shapePoint(k, n);
            boolean hovered = k == hoverShape;
            boolean picked = k == chosenShape;
            double dim = chosenShape >= 0 && !picked && !hovered ? 0.4 : 1;
            float g = (float) ((hovered ? 0.34 : picked ? 0.3 : 0.15) * appear * dim);
            EnchantFx.billboard(pose, buffers, cam, at, (hovered ? 0.095 : 0.075) * appear, 0, EnchantFx.GLOW, g, g * 0.95f, g * 0.85f);
            if (picked) {
                float s = (float) (0.4 * appear);
                EnchantFx.billboard(pose, buffers, cam, at, 0.085 * appear, clock * 0.8, EnchantFx.CIRCLE, s, s * 0.85f, s * 0.4f);
            }
            ItemStack shape = shapes.get(k);
            float size = (float) (SHAPE_SIZE * appear * (hovered ? 1.35 : 1) * (0.75 + 0.25 * dim));
            float spin = (float) (clock * (hovered ? 90 : 25) + k * 40);
            boolean block = ItemPlacer.isBlock(shape);
            ItemPlacer.drawOriented(pose, buffers, camera, shape, at, Axis.YP.rotationDegrees(spin), block ? size * 0.85f : size * 1.2f,
                    LightTexture.FULL_BRIGHT);
        }
    }

    /** Development: the saw's filter as the client sees it. */
    public ItemStack filter() {
        SawBlockEntity saw = saw();
        FilteringBehaviour filter = saw == null ? null : BlockEntityBehaviour.get(saw, FilteringBehaviour.TYPE);
        return filter == null ? ItemStack.EMPTY : filter.getFilter();
    }

    /** Development: the shapes on the dial right now. */
    public List<ItemStack> dial() {
        return shapes();
    }
}
