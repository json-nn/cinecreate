package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.bridge.CrafterMenu;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.Notices;
import org.minechestplate.cinecreate.station.CrafterGroup;
import org.minechestplate.cinecreate.station.Crafters;

/**
 * Mechanical Crafters (DESIGN §7.3). The crafters stand as a wall of windows, seen from the player's own eyes: the
 * camera never leaves the head (after M7, the user's note: large walls looked like flying).
 * <ul>
 * <li>By hand, a group up to 3×3 within the player's height: the player steps back until the whole group is in frame
 * (crouching for a low one). Within arm's reach the hand carries an item in and pushes it into the slot; a farther
 * window gets a short toss.</li>
 * <li>With an Extendo Grip in hand, any group up to 10×10, and only so: the player stands where they reach the clicked
 * crafter, and the wall is seen a section at a time (up to 3×3 windows); the head turns from section to section (keys,
 * the wheel, or the cursor resting at a screen edge), and a small map shows where on the wall it looks. The grip's hand
 * puts items into every window within Create's own reach of the grip; the others are shaded and refused (stand closer,
 * or higher).</li>
 * </ul>
 * An empty hand takes an item back out, a Slot Cover in hand covers an empty window, and an empty hand takes the cover
 * off again. Faint lights run across each front the way its items will travel. When the last window is filled and the
 * crafters turn, the view widens a little and watches: the items hop from crafter to crafter (Create draws that), and
 * when the result forms, the output window flashes.
 */
public class CrafterScene extends CineStation<CrafterMenu> {
    /** A hand reaches this far from its shoulder; a farther window gets a toss. */
    static final double REACH = 1.15;
    /** Putting in by hand: seconds, the beat (in the slot), how far in front of the window the fist carries it, the push. */
    static final double INSERT = 0.45, INSERT_BEAT = 0.33, CARRY = 0.16, PUSH = 0.10;
    /** A toss: seconds, when it leaves the hand, the beat (it lands), the arc's rise, and more per block of flight. */
    static final double TOSS = 0.5, RELEASE = 0.10, TOSS_BEAT = 0.40, ARC = 0.12, ARC_PER_BLOCK = 0.05;
    /** Taking out: seconds, the beat (the fingers close on it), and how long it then flies to a hand that could not reach. */
    static final double TAKE = 0.45, TAKE_BEAT = 0.22, FETCH = 0.2;
    /** The watch: how much wider the view opens, and how long either way takes. */
    static final double WIDEN = 4, WATCH_TIME = 0.8;
    /** The largest group worked by hand (a side, in windows); and how far from the eyes its top row may be above them. */
    static final int BARE_MOST = 3;
    static final double BARE_ABOVE = 1.5;
    /** A crafter by itself is stood close to, so the hand reaches it. */
    static final double LONE_BACK = 1.0;
    /** With the grip: a section is up to this many windows a side; the player stands this far before the clicked crafter. */
    static final int SECTION = 3;
    static final double GRIP_STAND = 2.6;
    /** The head's turn from one section to the next (seconds); the cursor resting at an edge turns it after this long. */
    static final double TURN = 0.35, DWELL = 0.35;
    /** The edges that turn the head: a share of the screen's width at the sides, GUI pixels below the drawer and above the hint line. */
    static final double EDGE = 0.06, EDGE_ROWS = 14;
    /** The narrowest field of view (a section close by). */
    static final double MIN_FOV = 50;
    /** The wall's map: its middle this far down the screen, on the right (CI's field guide button is at the middle). */
    static final double MAP_AT = 0.68;
    /** The frame keeps this much room round the group, more per size. */
    static final double MARGIN = 0.1, MARGIN_PER_SIZE = 0.06;
    /**
     * Field of view for one crafter, wider per extra row or column up to the widest a framing opens (beyond it the camera
     * stands farther back), and at most this wide when a wall behind presses in.
     */
    static final double FOV = 62, FOV_PER_SIZE = 4, FRAMING_FOV = 84, MAX_FOV = 90;
    /**
     * Cinematic Interactions' inventory drawer along the top of the screen and its hint line along the bottom, in GUI
     * pixels: the windows (with this much room round each) stay clear of them, only the group's edges may go under.
     */
    static final double DRAWER = 46, HINT_LINE = 24, WINDOW_ROOM = 0.05;
    /** An item just put in is drawn until Create draws it (one sync), at most this long; it settles in over the first part. */
    static final double GHOST = 0.6, SETTLE = 0.12;
    /** Held items stand nearly upright, facing the player. */
    static final float HELD_TILT = 80;
    /** Half a window, for its glow. */
    static final double WINDOW = 0.32;
    /** The lights that run the way items travel: how bright at most, and how far out in front of the fronts. */
    static final double LIGHT = 0.45, LIGHT_OUT = 0.13;
    /** The flash when the result forms. */
    static final double FLASH = 0.06, FLASH_DECAY = 9;
    static final int FLASH_COLOR = 0xFFF2C8;

    /** Development only ({@code SmokeTest}): the window the cursor is held on (-1: the cursor decides). */
    public static int smokePin = -1;
    /** Development: windows filled by hand and by toss, things taken out, clicks refused, results formed. */
    public int inserts, tosses, takes, denials, crafts;

    private final CrafterGroup group;
    /** The middle of the group's front. */
    private final Vec3 middle;
    /** By hand: how far back the player stands to have the whole group in view. */
    private final double distance;
    /**
     * How far above what is looked at the view's middle is, as a share of the half-height it sees there: the windows
     * sit in the band between the drawer and the hint line, not in the middle of the screen. The band's share of the
     * screen's height, and the screen's shape.
     */
    private final double rise, band, aspect;
    /** Worked with an Extendo Grip (decided when the scene starts; DESIGN §7.3, after M7). */
    private final boolean gripMode;
    /** With the grip: each section's first column and first row, and the section looked at. */
    private final int[] sectionColumns, sectionRows;
    private int sectionColumn, sectionRow;
    /** The head's turn between sections: the look it turns from, and since when. */
    private float fromYaw, fromPitch;
    private double fromFov, turnAt = -10;
    /** The look as last drawn (where a turn starts from). */
    private float lastYaw, lastPitch;
    private double lastFov = 70;
    /** The cursor resting at a screen edge: which way, since when. */
    private int dwellX, dwellY;
    private double dwellAt = -10;

    private int hovered = -1;

    private enum Move { PUT, TAKE }

    /** The move under way (see {@code moving()}): what, at which window, and how. */
    private Move move = Move.PUT;
    private int moveCell = -1;
    private boolean far, covering, uncovering, lastOne, released;
    private ItemStack flying = ItemStack.EMPTY;
    @Nullable
    private Vec3 releaseFrom;

    /** Any crafter of the group at work (Create's phase is not idle), and the camera's way there and back (0..1). */
    private boolean watching;
    private double watch;
    /** Each crafter's phase and countdown at the last tick. */
    private final String[] phases;
    private final int[] countDowns;
    private double craftedAt = -10;

    private ItemStack ghost = ItemStack.EMPTY;
    private int ghostCell = -1;
    private double ghostAt = -10;
    private long drawnFrame = -1;

    public CrafterScene(BlockPos pos, Direction side) {
        super(pos, side, CrafterMenu.class, 0);
        Minecraft mc = Minecraft.getInstance();
        CrafterGroup found = mc.level == null ? null : CrafterGroup.of(mc.level, pos);
        group = found != null && found.facing() == side ? found : CrafterGroup.single(pos, side);
        middle = group.centre();
        phases = new String[group.cells().size()];
        countDowns = new int[group.cells().size()];
        gripMode = Grip.inHand();
        Window window = mc.getWindow();
        aspect = window.getWidth() / (double) Math.max(1, window.getHeight());
        double gui = Math.max(1, window.getGuiScaledHeight());
        double top = showsInventory() ? DRAWER / gui : 0, bottom = HINT_LINE / gui;
        band = 1 - top - bottom;
        rise = top - bottom;
        // руками: вся группа в кадре; игрок отходит, сколько нужно (и сколько позволяет стена позади)
        int size = size();
        double wide = Math.min(FRAMING_FOV, FOV + FOV_PER_SIZE * (size - 1));
        double back = frameTan(group.height(), group.width()) / Math.tan(Math.toRadians(wide / 2));
        if (size == 1) {
            back = Math.min(back, LONE_BACK);
        }
        distance = Math.max(0.9, Math.min(back, room(mc, back)));
        // с захватом: стена по частям, начиная с той, где кликнутый сборщик
        sectionColumns = starts(group.width());
        sectionRows = starts(group.height());
        int clicked = Math.max(0, group.cells().indexOf(pos));
        sectionColumn = nearest(sectionColumns, clicked % group.width(), group.width());
        sectionRow = nearest(sectionRows, clicked / group.width(), group.height());
    }

    /** The sections' first indices along a side of this many windows: up to {@link #SECTION} each, spread evenly, overlapping. */
    private static int[] starts(int windows) {
        if (windows <= SECTION) {
            return new int[] {0};
        }
        int count = (int) Math.ceil(windows / (double) SECTION);
        int[] starts = new int[count];
        for (int i = 0; i < count; i++) {
            starts[i] = (int) Math.round(i * (windows - SECTION) / (double) (count - 1));
        }
        return starts;
    }

    /** The section whose middle is nearest this window. */
    private static int nearest(int[] starts, int window, int windows) {
        int best = 0;
        for (int i = 1; i < starts.length; i++) {
            double middle = starts[i] + (Math.min(SECTION, windows) - 1) / 2.0;
            double was = starts[best] + (Math.min(SECTION, windows) - 1) / 2.0;
            if (Math.abs(middle - window) < Math.abs(was - window)) {
                best = i;
            }
        }
        return best;
    }

    /**
     * The tangent of half the field of view that frames this many rows and columns of windows (and a margin round them)
     * from a block away, with the windows in the band between the drawer and the hint line: divide by the distance.
     */
    private double frameTan(int rows, int columns) {
        double margin = MARGIN + MARGIN_PER_SIZE * (Math.max(rows, columns) - 1);
        double edges = Math.max(rows / 2.0 + margin, (columns / 2.0 + margin) / aspect);
        double windows = (rows - 1) / 2.0 + WINDOW + WINDOW_ROOM;
        return Math.max(edges, windows / band);
    }

    /** The field of view that frames them from this far. */
    private double fovFor(int rows, int columns, double far) {
        return Mth.clamp(2 * Math.toDegrees(Math.atan(frameTan(rows, columns) / Math.max(0.3, far))), MIN_FOV, MAX_FOV);
    }

    /** How far back from the group the camera can stand before a block is in the way (at most {@code wanted}). */
    private double room(Minecraft mc, double wanted) {
        if (mc.level == null || mc.player == null) {
            return wanted;
        }
        Vec3 from = middle.add(side().scale(0.6));
        Vec3 to = middle.add(side().scale(wanted + 0.25));
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? wanted : Math.max(0, hit.getLocation().distanceTo(middle) - 0.25);
    }

    public CrafterGroup group() {
        return group;
    }

    private int size() {
        return Math.max(group.width(), group.height());
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return middle;
    }

    /** The fronts are a wall: worked as a face, not a tabletop. */
    @Override
    public boolean workOnTop() {
        return false;
    }

    /** The view is the player's own: never away from the head (no free camera). */
    @Override
    public boolean freeCamera() {
        return false;
    }

    /** Low groups are worked crouching. */
    @Override
    public boolean crouches() {
        return true;
    }

    /** Small enough to be worked by hand. */
    private boolean small() {
        return group.width() <= BARE_MOST && group.height() <= BARE_MOST;
    }

    /**
     * By hand: a group up to 3×3, in front of it, no more than a step beyond its sides, from its bottom row up to a
     * little above the eyes. A bigger or higher one is the grip's (its card says so).
     */
    @Override
    protected boolean bareWorkable(Vec3 feet) {
        if (!small()) {
            return refuse("too_big");
        }
        Vec3 off = feet.subtract(middle);
        double along = off.dot(side()), aside = Math.abs(off.dot(right()));
        // своя причина для карточки отказа: CI судит по высоте точки работы и сказал бы «слишком далеко»
        if (along < 0.3 || along > distance + 1.4 || aside > group.width() / 2.0 + 1) {
            // за стеной (она и не даёт отойти): карточка назовёт её
            BlockPos wall = along < 0.3 ? null : Views.blocking(feet.add(0, Views.EYE, 0), middle.add(side().scale(0.05)), new HashSet<>(group.cells()));
            if (wall != null) {
                Notices.why(pos, Component.translatable("cinecreate.notice.covered", Minecraft.getInstance().level.getBlockState(wall).getBlock().getName()));
                return false;
            }
            return refuse(along < 0.3 ? "wrong_side" : "too_far");
        }
        double bottom = middle.y - group.height() / 2.0, top = middle.y + group.height() / 2.0;
        if (bottom - feet.y < -0.5) {
            return refuse("too_low");
        }
        if (top - (feet.y + Views.EYE) > BARE_ABOVE) {
            return refuse("too_high");
        }
        return true;
    }

    /**
     * With the grip: in front of the wall (a little beyond its sides too), not standing above its bottom row, and the
     * clicked crafter within Create's reach of the grip from these feet.
     */
    @Override
    protected boolean gripWorkable(Vec3 feet) {
        Vec3 off = feet.subtract(middle);
        double along = off.dot(side()), aside = Math.abs(off.dot(right()));
        if (along < 0.3 || aside > group.width() / 2.0 + 2.5) {
            return refuse(along < 0.3 ? "wrong_side" : "too_far");
        }
        if (middle.y - group.height() / 2.0 - feet.y < -1.5) {
            return refuse("too_low");
        }
        return Grip.reaches(feet, pos) || refuse("too_far");
    }

    /** Started with the grip, the scene needs it throughout (it is put away: the scene starts again, by hand if it can). */
    @Override
    protected boolean needsGrip(Vec3 feet) {
        return gripMode;
    }

    private boolean refuse(String why) {
        Notices.why(pos, Component.translatable("cinecreate.notice." + why));
        return false;
    }

    /** By hand, the middle and the group's corners (all in view); with the grip, the first section's. */
    @Override
    public List<Vec3> workSamples() {
        Vec3 out = side().scale(0.04);
        int c0 = gripMode ? sectionColumns[sectionColumn] : 0, r0 = gripMode ? sectionRows[sectionRow] : 0;
        int cs = gripMode ? Math.min(SECTION, group.width()) : group.width(), rs = gripMode ? Math.min(SECTION, group.height()) : group.height();
        double left = c0 - 0.2, right = c0 + cs - 0.8, top = r0 - 0.2, bottom = r0 + rs - 0.8;
        return List.of(sectionMiddle().add(out), group.point(left, top).add(out), group.point(right, top).add(out),
                group.point(left, bottom).add(out), group.point(right, bottom).add(out));
    }

    /** By hand, back far enough to see the whole group; with the grip, before the clicked crafter. */
    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = gripMode ? group.window(pos).add(side().scale(GRIP_STAND)) : middle.add(side().scale(distance));
        stand = new Vec3(stand.x, player.getY(), stand.z);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    /** The player's eyes, crouched for a low group. */
    private Vec3 eye() {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 feet = player == null ? middle.add(side().scale(distance)) : player.position();
        double top = middle.y + group.height() / 2.0;
        boolean low = !gripMode && top < feet.y + Views.EYE - 0.5;
        return feet.add(0, low ? Views.CROUCHED_EYE : Views.EYE, 0);
    }

    /** The middle of the section looked at (by hand: of the whole group). */
    private Vec3 sectionMiddle() {
        if (!gripMode) {
            return middle;
        }
        int columns = Math.min(SECTION, group.width()), rows = Math.min(SECTION, group.height());
        return group.point(sectionColumns[sectionColumn] + (columns - 1) / 2.0, sectionRows[sectionRow] + (rows - 1) / 2.0);
    }

    /**
     * From the player's eyes: by hand at the whole group, with the grip at the section looked at, a little above its
     * middle so it sits between the drawer and the hint line. A turn between sections swings the head over.
     */
    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = eye();
        Vec3 at = sectionMiddle();
        double far = Math.max(0.3, gripMode ? eye.distanceTo(at) : eye.subtract(middle).dot(side()));
        int rows = gripMode ? Math.min(SECTION, group.height()) : group.height(), columns = gripMode ? Math.min(SECTION, group.width()) : group.width();
        double fov = fovFor(rows, columns, far);
        Vec3 look = at.add(0, rise * far * Math.tan(Math.toRadians(fov / 2)), 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        double k = Anim.inOutCubic(Anim.clamp01((Scene.clock() - turnAt) / TURN));
        if (k < 1) {
            yaw = fromYaw + Mth.wrapDegrees(yaw - fromYaw) * (float) k;
            pitch = (float) Mth.lerp(k, fromPitch, pitch);
            fov = Mth.lerp(k, fromFov, fov);
        }
        lastYaw = yaw;
        lastPitch = pitch;
        lastFov = fov;
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    public double leanFov() {
        return lastFov + WIDEN * Anim.inOutCubic(watch);
    }

    @Override
    public float bodyPitch() {
        return 10f;
    }

    /** Hands low before the body, ready; they sink while the crafters work. */
    @Override
    protected Vec3 restAt(boolean right) {
        return eye().add(forward().scale(0.5)).add(right().scale(right ? 0.24 : -0.24))
                .add(0, -0.42 - 0.12 * Anim.inOutCubic(watch), 0);
    }

    @Override
    public double maxReach() {
        return REACH + 0.25;
    }

    // ---- the grip's sections ------------------------------------------------------------------------------

    /** Turns the head to the next section that way (columns as seen from the front; rows from the top). */
    public boolean turn(int dx, int dy) {
        int column = Mth.clamp(sectionColumn + dx, 0, sectionColumns.length - 1), row = Mth.clamp(sectionRow + dy, 0, sectionRows.length - 1);
        if (!gripMode || moving() || column == sectionColumn && row == sectionRow) {
            return false;
        }
        fromYaw = lastYaw;
        fromPitch = lastPitch;
        fromFov = lastFov;
        turnAt = Scene.clock();
        sectionColumn = column;
        sectionRow = row;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS, 0.12f, 1.4f, false);
        }
        return true;
    }

    /** W/A/S/D and the arrows turn the head (with the grip). */
    @Override
    public boolean keyPressed(int key) {
        if (!gripMode) {
            return false;
        }
        return switch (key) {
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> turn(0, -1) || true;
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> turn(0, 1) || true;
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> turn(-1, 0) || true;
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> turn(1, 0) || true;
            default -> false;
        };
    }

    /** The wheel turns the head up and down (with Shift: along the wall). */
    @Override
    public void scroll(double delta) {
        if (gripMode && delta != 0) {
            int step = delta > 0 ? -1 : 1;
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                turn(step, 0);
            } else {
                turn(0, step);
            }
        }
    }

    /** Whether the player, where they stand, reaches this window with the grip (Create's reach; by hand everything is, by toss). */
    private boolean inReach(int cell) {
        return !gripMode || Grip.reachesNow(group.cells().get(cell));
    }

    /** The section the window is in, by the nearest middle (several sections overlap). */
    private boolean inSection(int cell) {
        int column = cell % group.width(), row = cell / group.width();
        int c0 = sectionColumns[sectionColumn], r0 = sectionRows[sectionRow];
        return column >= c0 && column < c0 + SECTION && row >= r0 && row < r0 + SECTION;
    }

    @Override
    public BlockPos lightPos() {
        return BlockPos.containing(middle.add(side().scale(0.5)));
    }

    @Override
    public SoundEvent touchSound() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? super.touchSound() : mc.level.getBlockState(pos).getSoundType(mc.level, pos, mc.player).getStepSound();
    }

    // ---- interaction ---------------------------------------------------------------------------------

    /** The window under the cursor: the front it points at, read off the plane of the fronts. */
    @Override
    public void updateCursor(boolean overPanel) {
        if (moving()) {
            return;
        }
        hovered = -1;
        aim = null;
        if (watching) {
            return;
        }
        if (smokePin >= 0) {
            hover(smokePin);
            return;
        }
        if (overPanel) {
            return;
        }
        Vec3 hit = Picking.cursorOnPlane(middle, side());
        if (hit == null) {
            return;
        }
        Vec3 off = hit.subtract(group.point(0, 0));
        int cell = group.index((int) Math.round(off.dot(right())), (int) Math.round(-off.y));
        if (cell >= 0) {
            hover(cell);
        }
    }

    /** Development: points at a window as the cursor would. */
    public void hover(int cell) {
        hovered = cell;
        aim = group.window(group.cells().get(cell));
    }

    /** The cursor leaving does not stop a move half way, nor take the hand off a pinned window. */
    @Override
    public void clearCursor() {
        if (!moving() && smokePin < 0) {
            hovered = -1;
            aim = null;
        }
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        Vec3 toward = target.subtract(shoulder);
        Vec3 dir = toward.normalize();
        // окно руке не достать: она держит вещь наготове, в его сторону
        Vec3 ready = shoulder.add(dir.scale(REACH * 0.75));
        double t = moveTime();
        if (t < 0) {
            // с захватом его рука доходит до окна (вне досягаемости окно не отвечает)
            return gripMode || toward.length() <= REACH ? target.add(side().scale(CARRY - 0.02 * tap)) : ready;
        }
        if (move == Move.PUT) {
            if (!far) {
                double in = Anim.inOutCubic(Anim.window(t, 0.25, 0.37)) - Anim.outCubic(Anim.window(t, 0.37, INSERT));
                return target.add(side().scale(CARRY - PUSH * in));
            }
            // замах назад, бросок вперёд, рука успокаивается
            double back = Anim.outCubic(Anim.window(t, 0, RELEASE * 0.8)) * (1 - Anim.window(t, RELEASE * 0.8, RELEASE));
            double flick = Anim.hump(Anim.window(t, RELEASE * 0.8, RELEASE + 0.16));
            return ready.add(dir.scale(-0.06 * back + 0.12 * flick)).add(0, 0.03 * back, 0);
        }
        // взять: к окну, пальцы сжимаются, назад; издали ладонь только тянется навстречу вещи
        double reach = Anim.inOutCubic(Anim.window(t, 0, TAKE_BEAT)) - Anim.outCubic(Anim.window(t, TAKE_BEAT, TAKE));
        return far ? ready.add(dir.scale(0.05 * reach)) : target.add(side().scale(CARRY - (CARRY - 0.08) * reach));
    }

    /**
     * Held in front of the fist, toward the windows. During a toss the last item of a stack flies along the arc itself
     * (with more in hand, the scene draws the one flying); a taken item comes out of its window to the hand.
     */
    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        Vec3 held = hand.add(side().scale(-0.07)).add(0, -0.02, 0);
        double t = moveTime();
        if (t < 0 || moveCell < 0) {
            return held;
        }
        if (move == Move.PUT && far) {
            if (t < RELEASE) {
                releaseFrom = held;
                return held;
            }
            return lastOne ? flight(t) : held;
        }
        if (move == Move.TAKE && t >= TAKE_BEAT) {
            double k = Anim.inOutCubic(Anim.window(t, TAKE_BEAT, TAKE_BEAT + (far ? FETCH : 0.08)));
            Vec3 from = group.window(group.cells().get(moveCell));
            return Anim.lerp(from, held, k).add(0, far ? arc(from, held) * Anim.hump(k) : 0, 0);
        }
        return held;
    }

    @Override
    public float heldItemTilt() {
        double t = moveTime();
        return t >= RELEASE && move == Move.PUT && far && lastOne ? spin(t) : HELD_TILT;
    }

    /** Where a tossed item is: from the hand to its window along an arc. */
    private Vec3 flight(double t) {
        Vec3 to = group.window(group.cells().get(moveCell));
        Vec3 from = releaseFrom != null ? releaseFrom : to;
        double k = Anim.window(t, RELEASE, TOSS_BEAT);
        return Anim.lerp(from, to, k).add(0, arc(from, to) * Anim.hump(k), 0);
    }

    /** A tossed item tumbles a turn and a half on the way, and lands upright. */
    private static float spin(double t) {
        return (float) (HELD_TILT + 540 * Anim.window(t, RELEASE, TOSS_BEAT));
    }

    private static double arc(Vec3 from, Vec3 to) {
        return ARC + ARC_PER_BLOCK * from.distanceTo(to);
    }

    /** While the crafters work every click is refused (Create's own click would throw the whole grid out). */
    @Override
    public void click(int button, boolean shift) {
        if (moving() || menu() == null) {
            return;
        }
        if (watching) {
            refuse();
            return;
        }
        act(button, shift);
    }

    /**
     * A window, as Create's own click on it: something in hand goes in (a Slot Cover covers instead), an empty hand
     * takes out what is there, or the cover. A window that is full, covered or refuses the item: the hands shake.
     */
    @Override
    protected void act(int button, boolean shift) {
        int index = hovered < 0 ? -1 : slotIndex(hovered);
        MechanicalCrafterBlockEntity crafter = hovered < 0 ? null : crafter(hovered);
        CrafterMenu menu = menu();
        if (index < 0 || crafter == null || menu == null) {
            return;
        }
        if (!inReach(hovered)) {
            // захват туда не достаёт: как и клик в Create, отсюда никак
            refuse();
            Notices.about(pos, Component.translatable("cinecreate.notice.out_of_reach"));
            return;
        }
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            if (!answers(hovered)) {
                refuse();
                return;
            }
            put(index, Crafters.isCover(carried), carried);
        } else if (!slot(index).isEmpty()) {
            take(index, shift, false);
        } else if (Crafters.covered(crafter)) {
            take(index, false, true);
        }
    }

    /** Whether a click on this window would do something with what the hand holds (or does not). */
    private boolean answers(int cell) {
        int index = slotIndex(cell);
        MechanicalCrafterBlockEntity crafter = crafter(cell);
        CrafterMenu menu = menu();
        if (index < 0 || crafter == null || menu == null || watching) {
            return false;
        }
        ItemStack carried = menu.getCarried(), there = slot(index);
        if (carried.isEmpty()) {
            return !there.isEmpty() || Crafters.covered(crafter);
        }
        if (Crafters.isCover(carried)) {
            return there.isEmpty() && Crafters.coverable(crafter);
        }
        return there.isEmpty() && !Crafters.covered(crafter) && accepts(index, carried);
    }

    private void put(int index, boolean cover, ItemStack carried) {
        move = Move.PUT;
        moveCell = hovered;
        covering = cover;
        lastOne = carried.getCount() == 1;
        far = beyondReach(moveCell);
        released = false;
        releaseFrom = null;
        flying = carried.copyWithCount(1);
        int cell = moveCell;
        startMove(far ? TOSS : INSERT, far ? TOSS_BEAT : INSERT_BEAT, () -> {
            if (covering) {
                buttonClick(CrafterMenu.COVER + index);
                coverUsedUp();
            } else {
                click(index, 0, ClickType.PICKUP);
                // у клиента вещь уже в слоте (клик предсказан), у Create — когда сервер пришлёт
                ghost = slot(index).copy();
                ghostCell = cell;
                ghostAt = Scene.clock();
            }
            if (far) {
                tosses++;
            } else {
                inserts++;
            }
            Fx.motes(group.window(group.cells().get(cell)), 0.06, 4, 0.62f, 0.6f, 0.56f, 0.04);
        });
    }

    private void take(int index, boolean shift, boolean cover) {
        move = Move.TAKE;
        moveCell = hovered;
        uncovering = cover;
        far = beyondReach(moveCell);
        startMove(TAKE, TAKE_BEAT, () -> {
            if (uncovering) {
                buttonClick(CrafterMenu.UNCOVER + index);
            } else {
                click(index, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            }
            takes++;
        });
    }

    private void refuse() {
        denials++;
        deny();
    }

    /** The cover goes on in the server's answer; until then the hand already lets go of it (not in creative). */
    private void coverUsedUp() {
        Minecraft mc = Minecraft.getInstance();
        CrafterMenu menu = menu();
        if (menu != null && mc.player != null && !mc.player.isCreative()) {
            ItemStack carried = menu.getCarried().copy();
            carried.shrink(1);
            menu.setCarried(carried);
        }
    }

    /**
     * Whether the working hand cannot reach this window (Cinematic Interactions works with the left hand left of the
     * middle): then it is tossed to. Never with the grip: its hand puts items in, and windows out of its reach refuse.
     */
    private boolean beyondReach(int cell) {
        Vec3 window = group.window(group.cells().get(cell));
        return !gripMode && window.distanceTo(shoulder(lateral(window) >= -0.1)) > REACH;
    }

    /** Where Cinematic Interactions hangs an arm: off the camera, as its hands do it. */
    private static Vec3 shoulder(boolean right) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 across = new Vec3(-forward.z, 0, forward.x);
        return camera.getPosition().add(across.scale(right ? 0.38 : -0.38)).add(0, -0.46, 0).add(forward.scale(0.08));
    }

    /** A click on the menu right now (the move's beat: {@code slotClick} waits for the move to end). */
    private void click(int slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        CrafterMenu menu = menu();
        if (menu != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
        }
    }

    private void buttonClick(int id) {
        Minecraft mc = Minecraft.getInstance();
        CrafterMenu menu = menu();
        if (menu != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private int slotIndex(int cell) {
        CrafterMenu menu = menu();
        return menu == null ? -1 : menu.slotOf(group.cells().get(cell));
    }

    @Nullable
    private MechanicalCrafterBlockEntity crafter(int cell) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(group.cells().get(cell)) instanceof MechanicalCrafterBlockEntity crafter
                ? crafter : null;
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return middle;
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        if (hovered < 0 || !carried.isEmpty() || moving() || watching) {
            return null;
        }
        int index = slotIndex(hovered);
        MechanicalCrafterBlockEntity crafter = crafter(hovered);
        if (index < 0 || crafter == null) {
            return null;
        }
        ItemStack there = slot(index);
        if (!there.isEmpty()) {
            return List.of(there.getHoverName());
        }
        return Crafters.covered(crafter)
                ? List.of(Component.translatable("cinecreate.crafter.covered").withStyle(ChatFormatting.GRAY)) : null;
    }

    @Override
    public Component hint() {
        if (watching) {
            return Component.translatable("cinecreate.hint.mechanical_crafter.crafting");
        }
        return Component.translatable(turning() ? "cinecreate.hint.mechanical_crafter" : "cinecreate.hint.mechanical_crafter.stopped");
    }

    private boolean turning() {
        for (int i = 0; i < group.cells().size(); i++) {
            MechanicalCrafterBlockEntity crafter = crafter(i);
            if (crafter != null && crafter.getSpeed() != 0) {
                return true;
            }
        }
        return false;
    }

    // ---- the watch -----------------------------------------------------------------------------------

    /**
     * Client tick: whether the crafters are at work (Create's phase), and the moment the result forms: Create's
     * crafting countdown passing its middle, when the server plays {@code crafter_craft}.
     */
    @Override
    public void tick() {
        super.tick();
        boolean busy = false;
        for (int i = 0; i < group.cells().size(); i++) {
            MechanicalCrafterBlockEntity crafter = crafter(i);
            String phase = crafter == null ? null : Crafters.phase(crafter);
            int count = crafter == null ? 0 : Crafters.countDown(crafter);
            if (Crafters.CRAFTING.equals(phase) && Crafters.CRAFTING.equals(phases[i])
                    && countDowns[i] >= Crafters.RESULT_AT && count < Crafters.RESULT_AT) {
                crafted(i);
            }
            phases[i] = phase;
            countDowns[i] = count;
            busy |= phase != null && !Crafters.IDLE.equals(phase);
        }
        watching = busy;
    }

    public boolean watching() {
        return watching;
    }

    private void crafted(int cell) {
        crafts++;
        craftedAt = Scene.clock();
        Fx.motes(group.window(group.cells().get(cell)), 0.15, 16, 1f, 0.95f, 0.78f, 0.25);
    }

    /** Once a frame: the camera's way back and forth for the watch, and the soft whoosh of a toss leaving the hand. */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        watch = Mth.clamp(watch + (watching ? 1 : -1) * Scene.frameDt / WATCH_TIME, 0, 1);
        dwell();
        if (move == Move.PUT && far && !released && moveTime() >= RELEASE) {
            released = true;
            Minecraft mc = Minecraft.getInstance();
            Vec3 at = mc.gameRenderer.getMainCamera().getPosition();
            if (mc.level != null) {
                mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.2f, 1.5f, false);
            }
        }
    }

    /** The cursor resting at a screen edge (with the grip) turns the head that way after a moment, and on while it stays. */
    private void dwell() {
        int dx = 0, dy = 0;
        if (gripMode && !moving() && !watching && smokePin < 0) {
            Window window = Minecraft.getInstance().getWindow();
            double[] mouse = Picking.mouseGui();
            double width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
            double top = showsInventory() ? DRAWER : 0, bottom = height - HINT_LINE;
            dx = mouse[0] < width * EDGE ? -1 : mouse[0] > width * (1 - EDGE) ? 1 : 0;
            dy = mouse[1] > top && mouse[1] < top + EDGE_ROWS ? -1 : mouse[1] < bottom && mouse[1] > bottom - EDGE_ROWS ? 1 : 0;
            if (!canTurn(dx, 0)) {
                dx = 0;
            }
            if (!canTurn(0, dy)) {
                dy = 0;
            }
        }
        if (dx == 0 && dy == 0 || dx != dwellX || dy != dwellY) {
            dwellX = dx;
            dwellY = dy;
            dwellAt = Scene.clock();
            return;
        }
        if (Scene.clock() - dwellAt >= DWELL && turn(dx, dy)) {
            dwellAt = Scene.clock() + TURN;
        }
    }

    private boolean canTurn(int dx, int dy) {
        int column = sectionColumn + dx, row = sectionRow + dy;
        return column >= 0 && column < sectionColumns.length && row >= 0 && row < sectionRows.length;
    }

    @Override
    public double flash() {
        return FLASH * Math.exp(-(Scene.clock() - craftedAt) * FLASH_DECAY);
    }

    @Override
    public int flashColor() {
        return FLASH_COLOR;
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /**
     * A tossed item while the rest of the stack stays in hand, and an item just put in until Create draws it (the
     * client's slot has it at once, Create's crafter once the server sends it). Drawn once a frame.
     */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        long frame = FrameCounter.frame();
        if (frame == drawnFrame) {
            return;
        }
        drawnFrame = frame;
        Vec3 cam = camera.getPosition();
        double t = moveTime();
        if (t >= RELEASE && t < TOSS_BEAT && move == Move.PUT && far && !lastOne && !flying.isEmpty()) {
            ItemPlacer.renderOne(pose, buffers, cam, flying, flight(t), side, 77L, 1f, spin(t), light);
        }
        drawGhost(pose, buffers, cam);
    }

    /** As Create's renderer draws a window's item, settling in from the size it had in hand. */
    private void drawGhost(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        if (ghost.isEmpty() || ghostCell < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MechanicalCrafterBlockEntity crafter = crafter(ghostCell);
        double age = Scene.clock() - ghostAt;
        if (mc.level == null || crafter == null || !Crafters.held(crafter).isEmpty() || age > GHOST) {
            ghost = ItemStack.EMPTY;
            return;
        }
        BlockPos cell = group.cells().get(ghostCell);
        Vec3 at = group.window(cell).subtract(cam);
        float size = (float) (0.5 * (0.45 + 0.55 * Anim.outBack(Anim.clamp01(age / SETTLE), 1.6)));
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.scale(size, size, size);
        pose.mulPose(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(group.facing())));
        pose.translate(0, 0, -1 / 256f);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        int light = LevelRenderer.getLightColor(mc.level, cell.relative(group.facing()));
        mc.getItemRenderer().renderStatic(ghost, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
    }

    /**
     * Faint lights run across each front the way its items will travel (fading out while the crafters work), and the
     * window under the cursor glows: warm when the click will do something, red when it will be refused.
     */
    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double clock = Scene.clock();
        Vec3 out = side(), across = right(), up = new Vec3(0, 1, 0);
        float fade = (float) (1 - Anim.inOutCubic(watch));
        for (int i = 0; fade > 0.01f && i < group.cells().size(); i++) {
            BlockPos cell = group.cells().get(i);
            BlockState state = mc.level.getBlockState(cell);
            if (!AllBlocks.MECHANICAL_CRAFTER.has(state)) {
                continue;
            }
            Vec3 toward = Vec3.atLowerCornerOf(MechanicalCrafterBlock.getTargetDirection(state).getNormal());
            double run = (clock * 0.9 + i * 0.17) % 1.0;
            float glow = (float) (LIGHT * Anim.hump(run)) * fade;
            // перед лентами и стрелками Create, иначе их модели закрывают огонёк
            Vec3 at = group.face(cell).add(out.scale(LIGHT_OUT)).add(toward.scale(0.1 + 0.3 * run));
            Fx.facePatch(pose, buffers, camera, at, across, up, 0.045, glow, glow * 0.78f, glow * 0.48f);
        }
        // с захватом: окна вне досягаемости притушены
        for (int i = 0; gripMode && i < group.cells().size(); i++) {
            if (!inReach(i)) {
                Fx.facePatch(pose, buffers, camera, group.window(group.cells().get(i)).add(out.scale(0.012)), across, up, WINDOW * 0.6,
                        0.16f, 0.03f, 0.03f);
            }
        }
        if (hovered < 0 || moving() || watching || menu() == null) {
            return;
        }
        boolean ok = answers(hovered) && inReach(hovered);
        float p = (float) (0.22 + 0.06 * Math.sin(clock * 5));
        Fx.facePatch(pose, buffers, camera, group.window(group.cells().get(hovered)).add(out.scale(0.01)), across, up, WINDOW,
                p, ok ? p * 0.85f : p * 0.25f, ok ? p * 0.6f : p * 0.2f);
    }

    /**
     * With the grip, on a wall of more than one section: a small map at the right edge (each window a dot: filled brass,
     * covered grey, empty dark, out of reach red; the section looked at framed), and a faint arrow at each edge the head
     * can turn to, filling while the cursor rests there.
     */
    @Override
    public void renderScreen(GuiGraphics g, Font font, float alpha) {
        if (!gripMode || sectionColumns.length * sectionRows.length <= 1 || alpha < 0.05f) {
            return;
        }
        int dot = 4, gap = 1, step = dot + gap;
        int w = group.width() * step + gap, h = group.height() * step + gap;
        // ниже середины правого края: там у CI кнопка справочника
        int x0 = g.guiWidth() - w - 8, y0 = Math.min((int) (g.guiHeight() * MAP_AT) - h / 2, g.guiHeight() - (int) HINT_LINE - h - 6);
        g.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, argb(0.55f * alpha, 14, 13, 18));
        for (int i = 0; i < group.cells().size(); i++) {
            int x = x0 + gap + (i % group.width()) * step, y = y0 + gap + (i / group.width()) * step;
            MechanicalCrafterBlockEntity crafter = crafter(i);
            int index = slotIndex(i);
            int color = !inReach(i) ? 0x7A2A2A : index >= 0 && !slot(index).isEmpty() ? 0xE2B25C
                    : crafter != null && Crafters.covered(crafter) ? 0x8A8A8A : 0x3A3A44;
            g.fill(x, y, x + dot, y + dot, argb(0.9f * alpha, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF));
        }
        int c0 = sectionColumns[sectionColumn], r0 = sectionRows[sectionRow];
        int sx = x0 + c0 * step, sy = y0 + r0 * step;
        int sw = Math.min(SECTION, group.width()) * step + gap, sh = Math.min(SECTION, group.height()) * step + gap;
        g.renderOutline(sx, sy, sw, sh, argb(alpha, 245, 238, 220));
        double filling = dwellX != 0 || dwellY != 0 ? Anim.clamp01((Scene.clock() - dwellAt) / DWELL) : 0;
        arrow(g, -1, 0, alpha, filling);
        arrow(g, 1, 0, alpha, filling);
        arrow(g, 0, -1, alpha, filling);
        arrow(g, 0, 1, alpha, filling);
    }

    /** A faint arrow at the edge the head can turn to; brighter as the cursor rests there. */
    private void arrow(GuiGraphics g, int dx, int dy, float alpha, double filling) {
        if (!canTurn(dx, dy)) {
            return;
        }
        boolean resting = dx == dwellX && dy == dwellY;
        float a = (float) (alpha * (0.25 + (resting ? 0.6 * filling : 0)));
        int color = argb(a, 245, 238, 220);
        int cx = dx < 0 ? 6 : dx > 0 ? g.guiWidth() - 6 : g.guiWidth() / 2;
        double top = showsInventory() ? DRAWER : 0;
        int cy = dy < 0 ? (int) top + 6 : dy > 0 ? g.guiHeight() - (int) HINT_LINE - 6 : g.guiHeight() / 2;
        for (int i = 0; i < 4; i++) {
            if (dx != 0) {
                int x = cx - dx * i;
                g.fill(x, cy - i, x + 1, cy + i + 1, color);
            } else {
                int y = cy - dy * i;
                g.fill(cx - i, y, cx + i + 1, y + 1, color);
            }
        }
    }

    private static int argb(float a, int r, int g, int b) {
        return Mth.clamp(Math.round(a * 255), 0, 255) << 24 | r << 16 | g << 8 | b;
    }

    /** Development: how the group is seen. */
    public String framing() {
        return gripMode ? String.format(Locale.ROOT, "with the grip, section %d,%d of %d×%d, field of view %.0f°", sectionColumn, sectionRow,
                sectionColumns.length, sectionRows.length, lastFov)
                : String.format(Locale.ROOT, "by hand, %.2f back, field of view %.0f°", distance, lastFov);
    }

    /** Development: worked with the grip. */
    public boolean gripMode() {
        return gripMode;
    }

    /** Development: the section looked at, as column and row, and how many there are each way. */
    public int[] section() {
        return new int[] {sectionColumn, sectionRow, sectionColumns.length, sectionRows.length};
    }

    /** Development: the look as last drawn (degrees; negative pitch looks up). */
    public float lookPitch() {
        return lastPitch;
    }

    /** Development: a window of this section within the grip's reach, or out of it (-1: none). */
    public int windowIn(boolean reachable) {
        for (int i = group.cells().size() - 1; i >= 0; i--) {
            if (inSection(i) && inReach(i) == reachable) {
                return i;
            }
        }
        return -1;
    }

    /** Development: a tossed item is in the air right now. */
    public boolean inFlight() {
        double t = moveTime();
        return move == Move.PUT && far && t >= RELEASE + 0.05 && t <= TOSS_BEAT - 0.05;
    }

    /**
     * Development ({@code SmokeTest}): what the last frame drawn did not show well (empty: nothing). Every window must
     * be between the drawer and the hint line, and the group's outer corners on the screen.
     */
    public String outOfFrame() {
        Window window = Minecraft.getInstance().getWindow();
        double width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
        double top = showsInventory() ? DRAWER : 0, bottom = height - HINT_LINE;
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < group.cells().size(); i++) {
            if (gripMode && !inSection(i)) {
                continue;
            }
            BlockPos cell = group.cells().get(i);
            double[] gui = Picking.toGui(group.window(cell));
            if (gui == null || gui[0] < 0 || gui[0] > width || gui[1] < top || gui[1] > bottom) {
                missing.add("window " + cell.toShortString() + where(gui));
            }
        }
        double right = group.width() - 0.5, low = group.height() - 0.5;
        for (Vec3 corner : gripMode ? List.<Vec3>of() : List.of(group.point(-0.5, -0.5), group.point(right, -0.5), group.point(-0.5, low), group.point(right, low))) {
            double[] gui = Picking.toGui(corner);
            if (gui == null || gui[0] < 0 || gui[0] > width || gui[1] < 0 || gui[1] > height) {
                missing.add("corner" + where(gui));
            }
        }
        return String.join(", ", missing);
    }

    private static String where(@Nullable double[] gui) {
        return gui == null ? " behind the camera" : String.format(Locale.ROOT, " at %.0f, %.0f", gui[0], gui[1]);
    }
}
