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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.SawMenu;
import org.minechestplate.cinecreate.client.layout.Layouts;

/**
 * A Mechanical Saw facing up (DESIGN §7.8), the stonecutter of Create: the shapes the stone on the table can be cut into
 * hang on an arc behind the blade, as CI's stonecutter dial, and choosing one sets the saw's filter. While it cuts, one
 * hand pushes the stone in step with Create's progress and the other waits where the pieces come out.
 */
public class SawScene extends CineStation<SawMenu> {
    /** The table (the saw's 12 px casing). */
    static final double TABLE = 12 / 16.0;
    /** The dial, as CI's stonecutter's: as wide and as high, how big a shape is drawn, how many shapes it holds at once. */
    static final double DIAL_WIDE = 0.72, DIAL_HIGH = 0.5;
    static final float SHAPE_SIZE = 0.15f;
    public static final int DIAL = 9;
    /** Laying down: seconds, the beat. Taking back: seconds, the beat. Touching a shape: seconds, the beat. */
    static final double LAY = 0.55, LAY_BEAT = 0.38, TAKE = 0.45, TAKE_BEAT = 0.22, CHOOSE = 0.3, CHOOSE_BEAT = 0.14;
    /** The fist over the table: carrying, bare; and the resting hands' height. */
    static final double CARRY = 0.17, BARE = 0.11, HAND_Y = 0.062;
    /** Dust: sparks per tick per RPM, at most this many a tick (110 a second). */
    static final double DUST_PER_RPM = 5.5 / 256, DUST_MOST = 5.5;
    /** Half the table the cursor can touch. */
    static final double PLATE = 0.46;

    /** Development (SmokeTest): what hoverTable or hoverShape pointed at stays pointed at. */
    public static boolean smokePin;
    /** Development (SmokeTest): stacks laid, shapes chosen, cuts finished (and the last one's first piece), dust sparks. */
    public int laid, chosen, cuts, sparks;
    public ItemStack lastCut = ItemStack.EMPTY;

    private final Vec3 top;
    private boolean onTable;
    private int hoverShape = -1, lastHover = -1;
    /** How far the wheel has turned the dial (shapes along), and how far it eases to. */
    private double turned;
    private int turnTo;
    /** The shapes for an input, worked out once for it (it goes through every cutting recipe). */
    private List<ItemStack> shapes = List.of();
    private ItemStack shapesFor = ItemStack.EMPTY;
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

    @Override
    public void devLines(List<String> lines) {
        lines.add(String.format(java.util.Locale.ROOT, "dial: %d shapes of %s · turned %.2f → %d · hovered %d", shapes.size(),
                shapesFor.isEmpty() ? "nothing" : shapesFor.getHoverName().getString(), turned, turnTo, hoverShape));
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
     * Where Create's renderer draws what is on the table (without its frame interpolation): from the entry edge to the
     * middle while it is cut, then on to the far edge.
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
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinematic.spatial_editor.stone"), Component.translatable("cinematic.spatial_editor.shapes"));
    }

    /** The table where the stone is laid; the middle of the dial's arc. */
    @Override
    public Vec3 spotBase(int spot) {
        return spot == 0 ? top : top.add(side().scale(-0.06)).add(0, 0.04, 0);
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

    /** While the saw cuts, one hand pushes just behind where Create draws the work; the other waits at the far edge. */
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

    /** All the shapes, for what is on the table, or for what is carried while the table is empty. */
    private List<ItemStack> shapes() {
        Minecraft mc = Minecraft.getInstance();
        SawMenu menu = menu();
        if (mc.level == null || menu == null) {
            return List.of();
        }
        ItemStack input = SawMenu.input(slot(SawMenu.TABLE), menu.getCarried());
        if (!ItemStack.isSameItemSameComponents(input, shapesFor)) {
            // по всем рецептам — только когда сменилось то, что пилят; выбранная форма — на диске
            shapesFor = input.copy();
            shapes = SawMenu.shapes(mc.level, input);
            turnTo = Mth.clamp(selected(shapes) - DIAL / 2, 0, Math.max(0, shapes.size() - DIAL));
            turned = turnTo;
        }
        return shapes;
    }

    private int lastTurn() {
        return Math.max(0, shapes().size() - DIAL);
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

    private int visible() {
        return Math.min(DIAL, shapes().size());
    }

    private double step() {
        int n = visible();
        return n <= 1 ? 0 : Math.min(22, 144.0 / (n - 1));
    }

    /** A shape's angle on the arc (90° straight up), as the dial is turned. */
    private double angle(int k) {
        return 90 + ((visible() - 1) / 2.0 - (k - turned)) * step();
    }

    /** How far a shape is on the dial: those turned past its ends fade away (CI's). */
    private double onDial(int k) {
        double a = angle(k);
        return Anim.smoother(Anim.window(a, 4, 18)) * Anim.smoother(Anim.window(176 - a, 0, 14));
    }

    private Vec3 shapePoint(int k) {
        return onArc(angle(k), 0.008 * Math.sin(Scene.clock() * 1.7 + k * 1.3));
    }

    /** Just past an end of the arc, where "more this way" shows. */
    private Vec3 dialEnd(boolean left) {
        return onArc(left ? 176 : 4, 0.03);
    }

    /** The arc's point at this angle, raised by lift: the layout moves the arc and stretches it (width across, depth up). */
    private Vec3 onArc(double degrees, double lift) {
        Layouts.Profile layout = Layouts.current(layoutId());
        double wide = DIAL_WIDE * layout.whole().width() * layout.region(1).width() / 10000;
        double high = DIAL_HIGH * layout.whole().depth() * layout.region(1).depth() / 10000;
        double a = Math.toRadians(degrees);
        return spot(1).add(right().scale(Math.cos(a) * wide)).add(0, Math.sin(a) * high + lift, 0);
    }

    /** The wheel turns the dial, a shape at a time, when there are more shapes than it holds (CI's). */
    @Override
    public void scroll(double delta) {
        int next = Mth.clamp(turnTo - (int) Math.signum(delta), 0, lastTurn());
        if (next != turnTo && !moving()) {
            turnTo = next;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.level.playLocalSound(top.x, top.y, top.z, SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 0.3f, 1.4f, false);
            }
        }
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
        double radius = 0.075 * spotSize(1);
        for (int k = 0; k < shapes.size(); k++) {
            if (onDial(k) > 0.5 && underCursor(mouse, shapePoint(k), radius)) {
                hoverShape(k);
                return;
            }
        }
        Vec3 table = spot(0);
        double half = PLATE * spotArea(0);
        Vec3 hit = Picking.cursorOnPlane(table.y);
        if (hit != null && Math.abs(hit.x - table.x) <= half && Math.abs(hit.z - table.z) <= half) {
            hoverTable();
        }
    }

    /** Development (SmokeTest): points at the table as the cursor would. */
    public void hoverTable() {
        onTable = true;
        hoverShape = -1;
        aim = spot(0).add(0, 0.02, 0);
    }

    /** Points at shape k as the cursor would (development, SmokeTest: a shape off the dial is turned onto it first). */
    public void hoverShape(int k) {
        int n = shapes().size();
        if (k < n && onDial(k) <= 0.5) {
            turnTo = Mth.clamp(k - DIAL / 2, 0, lastTurn());
            turned = turnTo;
        }
        onTable = false;
        hoverShape = k;
        aim = k < n ? shapePoint(k).add(0, -0.1, 0).add(side().scale(0.12)) : null;
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
     * On a shape: sets the saw's filter (the chosen one again clears it). On the table: lays what is carried (a left
     * click the stack, a right click one), or an empty hand takes it back, as Create's own empty-handed click does.
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
            Fx.motes(spot(0).add(0, 0.03, 0), 0.08, 5, 0.62f, 0.6f, 0.56f, 0.05);
        });
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return spot(0);
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        List<ItemStack> shapes = shapes();
        if (hoverShape >= 0 && hoverShape < shapes.size()) {
            ItemStack shape = shapes.get(hoverShape);
            Component name = shape.getCount() > 1 ? shape.getHoverName().copy().append(" ×" + shape.getCount()) : shape.getHoverName();
            boolean chosenNow = selected(shapes) == hoverShape;
            return List.of(name, Component.translatable(chosenNow ? "cinecreate.saw.unchoose" : "cinecreate.saw.choose")
                    .withStyle(chosenNow ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        }
        ItemStack lying = slot(SawMenu.TABLE);
        return onTable && carried.isEmpty() && !lying.isEmpty() ? List.of(lying.getHoverName()) : null;
    }

    @Override
    public Component hint() {
        return Component.translatable(turning() ? "cinecreate.hint.mechanical_saw" : "cinecreate.hint.mechanical_saw.stopped");
    }

    // ---- the saw at work -----------------------------------------------------------------------------

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
        turned = Anim.approach(turned, turnTo, 10, Scene.frameDt);
        if (hoverShape != lastHover && hoverShape >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.level.playLocalSound(top.x, top.y, top.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.3f, 1.6f + hoverShape % 3 * 0.1f, false);
            }
        }
        lastHover = hoverShape;
        ItemStack input = menu() == null ? ItemStack.EMPTY : SawMenu.input(slot(SawMenu.TABLE), menu().getCarried());
        if (!ItemStack.isSameItem(input, dialFor)) {
            dialFor = input.copy();
            shapesAt = Scene.clock();
        }
    }

    /** The dial is drawn in renderWorld: CI calls this one twice a frame for add-on stations. */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

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
            EnchantFx.disc(pose, buffers, camera, spot(0).add(0, 0.004, 0), 0.16 * spotSize(0), 0, EnchantFx.GLOW,
                    p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
        }
        List<ItemStack> shapes = shapes();
        int chosenShape = selected(shapes);
        double appear = Anim.outBack(Anim.window(clock - shapesAt, 0, 0.4), 1.4);
        float grow = spotSize(1);
        for (int k = 0; k < shapes.size(); k++) {
            double on = onDial(k) * appear;
            if (on < 0.02) {
                continue;
            }
            Vec3 at = shapePoint(k);
            boolean hovered = k == hoverShape;
            boolean picked = k == chosenShape;
            double dim = chosenShape >= 0 && !picked && !hovered ? 0.4 : 1;
            float g = (float) ((hovered ? 0.34 : picked ? 0.3 : 0.15) * on * dim);
            EnchantFx.billboard(pose, buffers, cam, at, (hovered ? 0.095 : 0.075) * on * grow, 0, EnchantFx.GLOW, g, g * 0.95f, g * 0.85f);
            if (picked) {
                float r = (float) (0.4 * on);
                EnchantFx.billboard(pose, buffers, cam, at, 0.085 * on * grow, clock * 0.8, EnchantFx.CIRCLE, r, r * 0.85f, r * 0.4f);
            }
            ItemStack shape = shapes.get(k);
            float size = (float) (SHAPE_SIZE * grow * on * (hovered ? 1.35 : 1) * (0.75 + 0.25 * dim));
            float spin = (float) (clock * (hovered ? 90 : 25) + k * 40);
            boolean block = ItemPlacer.isBlock(shape);
            ItemPlacer.drawOriented(pose, buffers, camera, shape, at, Axis.YP.rotationDegrees(spin), block ? size * 0.85f : size * 1.2f,
                    LightTexture.FULL_BRIGHT);
            if (shape.getCount() > 1) {
                label(pose, buffers, cam, "×" + shape.getCount(), at.add(0, -size * 0.85 - 0.02, 0), (float) on);
            }
        }
        if (shapes.size() > DIAL) {
            if (turnTo > 0) {
                label(pose, buffers, cam, "‹", dialEnd(true), 1);
            }
            if (turnTo < lastTurn()) {
                label(pose, buffers, cam, "›", dialEnd(false), 1);
            }
        }
    }

    /** Text facing the camera, as CI's stonecutter writes its counts; no backing, which would hide the glyphs. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Camera camera, String text, Vec3 at, float alpha) {
        Font font = Minecraft.getInstance().font;
        Vec3 eye = camera.getPosition();
        pose.pushPose();
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
        pose.mulPose(camera.rotation());
        pose.scale(0.0065f, -0.0065f, 0.0065f);
        int a = Math.max(8, (int) (alpha * 255));
        font.drawInBatch(text, -font.width(text) / 2f, -4, a << 24 | 0xF2EAD8, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Development (SmokeTest): how far the dial is turned, and how far it can be. */
    public int[] dialTurn() {
        return new int[] {turnTo, lastTurn()};
    }

    /** Development (SmokeTest): the saw's filter as the client sees it. */
    public ItemStack filter() {
        SawBlockEntity saw = saw();
        FilteringBehaviour filter = saw == null ? null : BlockEntityBehaviour.get(saw, FilteringBehaviour.TYPE);
        return filter == null ? ItemStack.EMPTY : filter.getFilter();
    }

    /** Development (SmokeTest): the shapes on the dial right now. */
    public List<ItemStack> dial() {
        return shapes();
    }
}
