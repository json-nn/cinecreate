package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.minechestplate.cinecreate.bridge.BasinMenu;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.CiNotice;
import org.minechestplate.cinecreate.client.fx.SceneFx;
import org.minechestplate.cinecreate.station.Composites;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;

/**
 * The Basin (DESIGN §7.6), the brewing stand of Create: items are dropped in over the rim, buckets and bottles pour or
 * fill, an empty hand scoops everything out. A Mechanical Mixer above makes it a watch; a Mechanical Press above jolts
 * the camera on every stroke.
 */
public class BasinScene extends CineStation<BasinMenu> {
    /** The basin's floor and rim, up from its bottom (walls 2 px thick). */
    static final double FLOOR = 2 / 16.0, RIM = 1.0;
    /** Dropping in: seconds, the beat (the hand lets go), how long the fall takes. */
    static final double DROP = 0.5, DROP_BEAT = 0.22, FALL = 0.15;
    /** Pouring or filling: seconds, the beat, how far the container tips, and when the stream runs. */
    static final double POUR = 0.9, POUR_BEAT = 0.35, TILT = 100, STREAM_FROM = 0.30, STREAM_TO = 0.75;
    /** Taking everything out: seconds, the beat (the fingers close in the basin). */
    static final double TAKE = 0.45, TAKE_BEAT = 0.22;
    /** The fist over the opening: carrying, bare; how deep it reaches in to take or to fill. */
    static final double CARRY = 0.2, BARE = 0.15, DIP = 0.45;
    /** The camera from the middle of the inside, out and up: about 70° down, else the near wall hides the floor. */
    static final double CAMERA_OUT = 0.55, CAMERA_UP = 1.25;
    /** The watch over a mixer: how much higher and farther back the camera goes (still seeing the floor), and over how long. */
    static final double RISE = 0.3, BACK = 0.1, WATCH_TIME = 0.6;
    /** A dropped item is drawn until Create draws it (one sync), at most this long. */
    static final double GHOST = 0.6;
    /** Higher than this over the feet, the basin is hung from by an Extendo Grip hooked on its rim (DESIGN §8.5). */
    static final double HANG = 1.9;
    /** Letting go of the hooked grip: the view drops this fast (blocks per second squared), at most this long. */
    static final double FALL_PULL = 22, FALL_MOST = 0.6;
    /** Hanging, the view sways a little: up and down (blocks), and a roll (degrees). */
    static final double SWAY_BOB = 0.015, SWAY_ROLL = 1.2;
    /** The flash when outputs appear; the jolt of a press stroke. */
    static final double FLASH = 0.05, FLASH_DECAY = 10, KICK = 0.25, KICK_DECAY = 14;
    static final int WARM = 0xFFF2C8, KINDLED = 0xFF8A2A, SEETHING = 0x3AA0FF;
    /** The pour's stream: sparks a second (as 2 a frame at 60 fps), and those owed since the last frame. */
    static final double STREAM_RATE = 120;
    private double streamSparks;
    /** Steam over a mixing basin: a puff every this many ticks. */
    static final int STEAM_EVERY = 3;
    /** The mixer's head offset (Create's, down is 1 + 7/16) past which the whisk is in the basin; the inside's half-width. */
    static final float STIRRING = 1.1f;
    static final double INNER = 0.34;

    /** Development (SmokeTest): the basin counts as pointed at wherever the cursor is. */
    public static boolean smokePin;
    /** Development (SmokeTest): drops, pours and fills, takes, clicks refused, outputs seen appearing, press strokes. */
    public int drops, pours, takes, denials, outputs, strokes;

    /** The middle of the inside, and the middle of the opening at the rim. */
    private final Vec3 inside, top;
    private boolean hovered;

    private enum Move { DROP, POUR, FILL, TAKE }

    private Move move = Move.DROP;
    private FluidStack pouring = FluidStack.EMPTY;
    /** Where the held item was drawn last frame (the dropped one falls from there). */
    @Nullable
    private Vec3 held;
    /** The item falling in, where from, when it left the hand, how many of it the basin had before, and whether it landed. */
    private ItemStack falling = ItemStack.EMPTY;
    private Vec3 fallFrom = Vec3.ZERO;
    private double fellAt = -10;
    private int hadBefore;
    private boolean landed;

    /** A mixer above is at work, and the camera's way there and back (0..1). */
    private boolean mixing;
    private double watch;
    private int lastOutputs = -1, steamTicks;
    private double flashAt = -10, kickAt = -10;
    private int flashColor = WARM;
    /** The press stroke as in the press scene: the cycle seen last, and whether this cycle's stroke was shown. */
    private boolean primed, struck;
    private int lastTicks = -1;
    private long drawnFrame = -1;

    public BasinScene(BlockPos pos, Direction side) {
        super(pos, side, BasinMenu.class, BasinMenu.INPUTS);
        inside = Vec3.atCenterOf(pos);
        top = Vec3.atBottomCenterOf(pos).add(0, RIM, 0);
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return inside;
    }

    @Override
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinecreate.layout.spot.drop"));
    }

    @Override
    public Vec3 spotBase(int spot) {
        return top;
    }

    private Vec3 dropAt() {
        return top;
    }

    /** Where what is dropped or poured in lands: on the contents. */
    private Vec3 landing() {
        return surface();
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = new Vec3(inside.x + side.getStepX() * 0.86, player.getY(), inside.z + side.getStepZ() * 0.86);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public boolean worksAtAnAngle() {
        return true;
    }

    /**
     * A basin often stands on a Blaze Burner, its inside 1.5 above the feet: higher than CI lets a tabletop be, but the
     * player still leans over its rim.
     */
    @Override
    protected boolean bareWorkable(Vec3 feet) {
        double high = inside.y - feet.y;
        return high >= -0.6 && high <= 1.9 && Math.hypot(inside.x - feet.x, inside.z - feet.z) <= 1.5
                && Views.reachable(this, feet, Set.of(pos));
    }

    /**
     * With an Extendo Grip: a basin too high to lean over is hung from by the rim (close enough to climb); any other
     * within the grip's reach is worked from where the player stands.
     */
    @Override
    protected boolean gripWorkable(Vec3 feet) {
        double high = inside.y - feet.y;
        if (high > HANG) {
            return Grip.reaches(feet, pos) && Math.hypot(inside.x - feet.x, inside.z - feet.z) <= 1.5
                    && Views.reachable(this, feet, Set.of(pos));
        }
        // ближе 1,5 вид всё равно над краем (свободная камера): его, как и без захвата, не ставить за блоком
        boolean close = Math.hypot(inside.x - feet.x, inside.z - feet.z) <= 1.5;
        return high >= -0.6 && Grip.reaches(feet, pos) && (!close || Views.reachable(this, feet, Set.of(pos)));
    }

    private boolean hanging() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Grip.inHand() && inside.y - player.getY() > HANG;
    }

    /** Development (SmokeTest): hanging from the rim by a grip right now. */
    public boolean hangingNow() {
        return hanging();
    }

    /** Worked from afar with a grip: the view stays at the player's eyes. */
    private boolean fromFar() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Grip.inHand() && !hanging() && Math.hypot(inside.x - player.getX(), inside.z - player.getZ()) > 1.5;
    }

    @Override
    protected boolean hooked() {
        return hanging() || letGoAt >= 0;
    }

    /** On the rim on the player's side, to the left: the grip is in the left hand, and the right one works. */
    @Override
    protected Vec3 hookPoint() {
        return top.add(side().scale(0.44)).add(right().scale(-0.34)).add(0, 0.06, 0);
    }

    /** Hanging from the rim, the grip gone: the player drops (the view falls back to their eyes; no harm done). */
    @Override
    protected void gripLost() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && inside.y - player.getY() > HANG) {
            letGoAt = Scene.clock();
            hungFrom = lean().pos();
            return;
        }
        super.gripLost();
    }

    /** Over the rim (on a burner, higher than the player's head); from afar, at the eyes. */
    @Override
    public boolean freeCamera() {
        return !fromFar();
    }

    @Override
    protected CameraRig.Pose sceneLean() {
        double k = Anim.inOutCubic(watch);
        Vec3 eye = inside.add(side().scale(CAMERA_OUT + BACK * k)).add(0, CAMERA_UP + RISE * k, 0);
        if (k > 0) {
            // подъём к смесителю — не в соседний блок: камера встаёт за 0.1 до него
            Vec3 start = inside.add(side().scale(CAMERA_OUT)).add(0, CAMERA_UP, 0);
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                BlockHitResult hit = mc.level.clip(new ClipContext(start, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() != HitResult.Type.MISS) {
                    double room = Math.max(0, hit.getLocation().distanceTo(start) - 0.1);
                    eye = start.add(eye.subtract(start).normalize().scale(room));
                }
            }
        }
        if (letGoAt >= 0 && hungFrom != null) {
            // отпустил захват: вид падает к глазам
            double t = Scene.clock() - letGoAt;
            eye = new Vec3(hungFrom.x, hungFrom.y - 0.5 * FALL_PULL * t * t, hungFrom.z);
        }
        float roll = -1;
        if (hanging() && letGoAt < 0) {
            // висит на захвате: чуть покачивается
            double t = Scene.clock();
            eye = eye.add(0, SWAY_BOB * Math.sin(t * 2.1), 0);
            roll += (float) (SWAY_ROLL * Math.sin(t * 1.3));
        }
        Vec3 look = inside.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, roll);
    }

    @Override
    protected double sceneFov() {
        return 64;
    }

    @Override
    public float bodyPitch() {
        return 50f;
    }

    /** The falling view has come down to the player's eyes (or fallen long enough): the scene ends with a thud. */
    private void land() {
        Minecraft mc = Minecraft.getInstance();
        double t = Scene.clock() - letGoAt;
        if (mc.player == null || hungFrom.y - 0.5 * FALL_PULL * t * t > mc.player.getEyeY() && t < FALL_MOST) {
            return;
        }
        letGoAt = -1;
        if (mc.level == null) {
            return;
        }
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.PLAYER_SMALL_FALL,
                SoundSource.PLAYERS, 0.8f, 1f, false);
        CiNotice.show(Component.translatable("cinecreate.notice.grip_dropped"));
        Scene.abort();
    }

    /** When the grip was let go of while hanging, and where the view fell from; -1: not falling. */
    private double letGoAt = -1;
    @Nullable
    private Vec3 hungFrom;

    @Override
    protected Vec3 restAt(boolean right) {
        return top.add(side().scale(0.44)).add(right().scale(right ? 0.28 : -0.28)).add(0, 0.04, 0);
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
        Vec3 at = dropAt();
        Vec3 hit = Picking.cursorOnPlane(at.y);
        if (hit != null && inSpot(0, hit, at, 0.5)) {
            hover();
        }
    }

    /** Development (SmokeTest): points at the basin as the cursor would. */
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
        double t = moveTime();
        if (t < 0) {
            return target.add(0, (carrying ? CARRY : BARE) - 0.02 * tap, 0);
        }
        return switch (move) {
            case DROP -> target.add(0, CARRY + 0.08 * Anim.outCubic(Anim.window(t, DROP_BEAT, DROP)), 0);
            case POUR -> target.add(side().scale(0.12)).add(0, CARRY + 0.05, 0);
            case FILL -> target.add(0, CARRY - (CARRY + DIP) * dip(t, POUR_BEAT, POUR), 0);
            case TAKE -> target.add(0, BARE - (BARE + DIP) * dip(t, TAKE_BEAT, TAKE), 0);
        };
    }

    /** 0..1..0: down by the beat, back up by the end. */
    private static double dip(double t, double beat, double length) {
        return Anim.inOutCubic(Anim.window(t, 0, beat)) - Anim.outCubic(Anim.window(t, beat + 0.05, length));
    }

    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        held = hand.add(0, -0.07, 0);
        return held;
    }

    @Override
    public float heldItemTilt() {
        double t = moveTime();
        if (t < 0 || move != Move.POUR) {
            return 18f;
        }
        double tip = Anim.inOutCubic(Anim.window(t, 0, POUR_BEAT)) - Anim.inOutCubic(Anim.window(t, STREAM_TO, POUR));
        return (float) (18 + (TILT - 18) * tip);
    }

    /**
     * As Create's own click: a fluid container pours (or, empty, fills up), anything else is dropped in (the left
     * button the whole stack, the right one item), an empty hand takes everything out.
     */
    @Override
    protected void sceneAct(int button, boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        BasinMenu menu = menu();
        if (!hovered || menu == null || mc.level == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (holdsItems()) {
                take();
            }
            return;
        }
        if (GenericItemEmptying.canItemBeEmptied(mc.level, carried)) {
            FluidStack fluid = GenericItemEmptying.emptyItem(mc.level, carried, true).getFirst();
            IFluidHandler tanks = mc.level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            if (tanks == null || tanks.fill(fluid, IFluidHandler.FluidAction.SIMULATE) != fluid.getAmount()) {
                refuse();
                return;
            }
            pour(fluid);
            return;
        }
        if (GenericItemFilling.canItemBeFilled(mc.level, carried) || carried.is(Items.SPONGE)) {
            if (!canFill(carried)) {
                refuse();
                return;
            }
            fill();
            return;
        }
        int slot = slotFor(carried, -1);
        if (slot < 0) {
            refuse();
            return;
        }
        drop(slot, button);
    }

    private void drop(int slot, int button) {
        move = Move.DROP;
        startMove(DROP, DROP_BEAT, () -> {
            ItemStack before = slot(slot).copy();
            click(slot, button == 1 ? 1 : 0, ClickType.PICKUP);
            ItemStack now = slot(slot);
            if (now.getCount() > before.getCount()) {
                falling = now.copyWithCount(1);
                hadBefore = createHas(falling);
                fallFrom = held != null ? held : dropAt().add(0, CARRY, 0);
                fellAt = Scene.clock();
                landed = false;
                drops++;
            }
        });
    }

    private void pour(FluidStack fluid) {
        move = Move.POUR;
        pouring = fluid;
        startMove(POUR, POUR_BEAT, () -> {
            buttonClick(BasinMenu.USE);
            pours++;
        });
    }

    private void fill() {
        move = Move.FILL;
        pouring = FluidStack.EMPTY;
        startMove(POUR, POUR_BEAT, () -> {
            buttonClick(BasinMenu.USE);
            pours++;
            splash(landing(), 6);
        });
    }

    private void take() {
        move = Move.TAKE;
        startMove(TAKE, TAKE_BEAT, () -> {
            buttonClick(BasinMenu.USE);
            takes++;
        });
    }

    private void refuse() {
        denials++;
        deny();
    }

    private boolean holdsItems() {
        for (int i = 0; i < BasinMenu.SLOTS; i++) {
            if (!slot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A click on the menu right now, at the move's beat (CI's slotClick would wait for the move to end). */
    private void click(int slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        BasinMenu menu = menu();
        if (menu != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
        }
    }

    private void buttonClick(int id) {
        Minecraft mc = Minecraft.getInstance();
        BasinMenu menu = menu();
        if (menu != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return surface();
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        if (!hovered || !carried.isEmpty() || moving()) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < BasinMenu.SLOTS; i++) {
            ItemStack there = slot(i);
            if (!there.isEmpty()) {
                lines.add(Component.literal(there.getCount() + " × ").append(there.getHoverName()));
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    @Override
    public SceneKind sceneKind() {
        return SceneKinds.BASIN;
    }

    @Override
    protected Component sceneHint() {
        if (mixing) {
            return Component.translatable("cinecreate.hint.basin.mixing");
        }
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = mc.level != null && Composites.pressOver(mc.level, pos) != null;
        return Component.translatable(pressed ? "cinecreate.hint.basin.pressing" : "cinecreate.hint.basin");
    }

    // ---- what the basin does -------------------------------------------------------------------------

    @Nullable
    private BasinBlockEntity basin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof BasinBlockEntity basin ? basin : null;
    }

    private float fluidUnits() {
        BasinBlockEntity basin = basin();
        return basin == null ? 0 : basin.getTotalFluidUnits(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
    }

    /** Where the contents float: Create's renderer puts items there, on the liquid or just above the floor. */
    public Vec3 surface() {
        float units = fluidUnits();
        double liquid = units < 1 ? 0 : FLOOR + 12 / 16.0 * Mth.clamp(units / 2000, 0, 1);
        return Vec3.atBottomCenterOf(pos).add(0, 0.2 + Mth.clamp(liquid - 0.3, 0.125, 0.6), 0);
    }

    /** How many of this item the client's basin holds (Create draws it from there). */
    private int createHas(ItemStack stack) {
        BasinBlockEntity basin = basin();
        int count = 0;
        for (int i = 0; basin != null && i < basin.getInputInventory().getSlots(); i++) {
            ItemStack there = basin.getInputInventory().getStackInSlot(i);
            count += ItemStack.isSameItemSameComponents(there, stack) ? there.getCount() : 0;
        }
        return count;
    }

    /** Everything processing has made so far: items, and fluid in tenths of a bucket. */
    private int outputCount() {
        BasinBlockEntity basin = basin();
        if (basin == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) {
            count += basin.getOutputInventory().getStackInSlot(i).getCount();
        }
        SmartFluidTankBehaviour tank = basin.getTanks().getSecond();
        IFluidHandler fluids = tank == null ? null : tank.getCapability();
        for (int i = 0; fluids != null && i < fluids.getTanks(); i++) {
            count += fluids.getFluidInTank(i).getAmount() / 100;
        }
        return count;
    }

    @Nullable
    private MechanicalMixerBlockEntity mixer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : Composites.mixerOver(mc.level, pos);
    }

    @Override
    public void tick() {
        super.tick();
        if (letGoAt >= 0 && hungFrom != null) {
            land();
            return;
        }
        MechanicalMixerBlockEntity mixer = mixer();
        mixing = mixer != null && mixer.running;
        if (mixing && ++steamTicks % STEAM_EVERY == 0) {
            Vec3 at = surface();
            SceneFx.steam(at.add(0, 0.05, 0), 0.22, 1);
            int tint = basinTint();
            SceneFx.motes(at, 0.22, 2, red(tint), green(tint), blue(tint), 0.06);
        }
        int now = outputCount();
        if (lastOutputs >= 0 && now > lastOutputs) {
            outputs++;
            flashAt = Scene.clock();
            flashColor = fluidUnits() >= 1 ? basinTint() : WARM;
            SceneFx.motes(surface(), 0.18, 12, 1f, 0.95f, 0.78f, 0.3);
        }
        lastOutputs = now;
    }

    @Override
    public void beforeFrame() {
        super.beforeFrame();
        watch = Mth.clamp(watch + (mixing ? 1 : -1) * Scene.frameDt / WATCH_TIME, 0, 1);
        double t = moveTime();
        if (move == Move.POUR && t >= STREAM_FROM && t <= STREAM_TO && !pouring.isEmpty() && held != null) {
            int tint = tint(pouring);
            Vec3 mouth = held.add(side().scale(-0.06));
            streamSparks += STREAM_RATE * Scene.frameDt;
            for (; streamSparks >= 1; streamSparks--) {
                SceneFx.spark(mouth, new Vec3(0, -1.6, 0).add(landing().subtract(mouth).multiply(1, 0, 1)), 0.22, 0.016f,
                        red(tint), green(tint), blue(tint), 3.0, 0.05);
            }
            Vec3 liquid = liquidTop();
            Vec3 lands = landing();
            SceneFx.pour(mouth, new Vec3(lands.x, liquid == null ? pos.getY() + FLOOR : liquid.y, lands.z), tint, 0.6);
        }
        pressStroke();
        if (!falling.isEmpty() && !landed && Scene.clock() - fellAt >= FALL) {
            landed = true;
            splash(landing(), 5);
        }
    }

    /** A press above: each stroke, timed as the press scene times its impact. */
    private void pressStroke() {
        Minecraft mc = Minecraft.getInstance();
        MechanicalPressBlockEntity press = mc.level == null ? null : Composites.pressOver(mc.level, pos);
        PressingBehaviour pressing = press == null ? null : press.getPressingBehaviour();
        boolean running = pressing != null && pressing.running;
        int ticks = running ? Math.abs(pressing.runningTicks) : -1;
        if (!primed) {
            primed = true;
            struck = ticks >= PressingBehaviour.CYCLE / 2;
        } else if (ticks < lastTicks) {
            struck = false;
        }
        lastTicks = ticks;
        float drawnAt = running ? Mth.lerp(mc.getTimer().getGameTimeDeltaPartialTick(false), pressing.prevRunningTicks, ticks) : 0;
        if (drawnAt >= PressingBehaviour.CYCLE / 2f && !struck) {
            struck = true;
            strokes++;
            kickAt = Scene.clock();
            Vec3 at = surface();
            if (fluidUnits() >= 1) {
                splash(at, 10);
            } else {
                SceneFx.motes(at, 0.25, 14, 0.62f, 0.6f, 0.56f, 0.12);
                SceneFx.shock(at, new Vec3(0, 1, 0), 0.3, SceneFx.DUST, 0.8);
                SceneFx.chips(at, firstInput(), 6, new Vec3(0, 1, 0), 1.2, pos.getY() + FLOOR);
            }
        }
    }

    private void splash(Vec3 at, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 liquid = liquidTop();
        if (liquid != null) {
            int tint = basinTint();
            SceneFx.motes(at, 0.15, count, red(tint), green(tint), blue(tint), 0.18);
            SceneFx.splash(new Vec3(at.x, liquid.y, at.z), INNER, tint, count / 8.0);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.2f, 1.6f, false);
        } else {
            AllSoundEvents.DEPOT_PLOP.playAt(mc.level, at, 0.3f, 1f, false);
        }
    }

    /** The liquid's surface as Create draws it (its level eased the same way), or null for none. */
    @Nullable
    private Vec3 liquidTop() {
        float units = fluidUnits();
        if (units < 1) {
            return null;
        }
        float level = Mth.clamp(units / 2000, 0, 1);
        level = 1 - (1 - level) * (1 - level);
        return Vec3.atBottomCenterOf(pos).add(0, FLOOR + 12 / 16.0 * level, 0);
    }

    private ItemStack firstInput() {
        BasinBlockEntity basin = basin();
        for (int i = 0; basin != null && i < basin.getInputInventory().getSlots(); i++) {
            if (!basin.getInputInventory().getStackInSlot(i).isEmpty()) {
                return basin.getInputInventory().getStackInSlot(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Once a frame: churns with the whisk down (boils over a fire), tumbles a dry mix, simmers a heated liquid at rest.
     */
    private void agitate() {
        Vec3 liquid = liquidTop();
        HeatLevel heat = heat();
        boolean heated = heat.isAtLeast(HeatLevel.KINDLED);
        MechanicalMixerBlockEntity mixer = mixer();
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (mixing && mixer != null && mixer.getRenderedHeadOffset(pt) > STIRRING) {
            double omega = Math.toRadians(mixer.getRenderedHeadRotationSpeed(pt) * 12);
            if (liquid != null) {
                SceneFx.churn(liquid, INNER, basinTint(), omega, heated);
                return;
            }
            Vec3 floor = Vec3.atBottomCenterOf(pos).add(0, FLOOR, 0);
            for (int n = SceneFx.rate(10); n > 0; n--) {
                double a = Math.random() * Math.PI * 2, r = 0.08 + Math.random() * 0.18;
                Vec3 arm = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 tangent = new Vec3(0, 1, 0).cross(arm).scale(Math.signum(omega));
                SceneFx.chips(floor.add(arm.scale(r)).add(0, 0.06, 0), firstInput(), 1, tangent.add(0, 0.8, 0), 1.0, floor.y);
            }
            if (SceneFx.rate(3) > 0) {
                SceneFx.dust(floor.add(0, 0.1, 0), SceneFx.DUST, 1, 0.08, 0.15);
            }
        } else if (liquid != null && heated) {
            SceneFx.simmer(liquid, INNER, basinTint(), heat == HeatLevel.SEETHING);
        }
    }

    /** The colour of the basin's liquid (the first one in it), or a warm white. */
    private int basinTint() {
        BasinBlockEntity basin = basin();
        if (basin != null) {
            for (SmartFluidTankBehaviour tanks : basin.getTanks()) {
                for (int i = 0; tanks != null && i < tanks.getCapability().getTanks(); i++) {
                    FluidStack fluid = tanks.getCapability().getFluidInTank(i);
                    if (!fluid.isEmpty()) {
                        return tint(fluid);
                    }
                }
            }
        }
        return WARM;
    }

    /** Without its alpha: CI's flash adds its own, and an opaque tint fills the screen. */
    private static int tint(FluidStack fluid) {
        return IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid) & 0xFFFFFF;
    }

    private static float red(int color) {
        return (color >> 16 & 0xFF) / 255f;
    }

    private static float green(int color) {
        return (color >> 8 & 0xFF) / 255f;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255f;
    }

    @Override
    protected double sceneKick() {
        return KICK * Math.exp(-(Scene.clock() - kickAt) * KICK_DECAY);
    }

    @Override
    protected double sceneFlash() {
        return FLASH * Math.exp(-(Scene.clock() - flashAt) * FLASH_DECAY);
    }

    @Override
    public int flashColor() {
        return flashColor;
    }

    public boolean mixing() {
        return mixing;
    }

    public HeatLevel heat() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? HeatLevel.NONE : BasinBlockEntity.getHeatLevelOf(mc.level.getBlockState(pos.below()));
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /** The item just dropped, until Create draws it; drawn once a frame (CI calls this twice). */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        long frame = FrameCounter.frame();
        if (frame == drawnFrame) {
            return;
        }
        drawnFrame = frame;
        if (falling.isEmpty()) {
            return;
        }
        double age = Scene.clock() - fellAt;
        if (age > GHOST || landed && createHas(falling) > hadBefore) {
            falling = ItemStack.EMPTY;
            return;
        }
        double k = Anim.clamp01(age / FALL);
        Vec3 at = Anim.lerp(fallFrom, landing(), k * k);
        ItemPlacer.renderOne(pose, buffers, camera.getPosition(), falling, at, side, 31L, 1f, (float) (18 * (1 - k)), light);
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        agitate();
        HeatLevel heat = heat();
        double clock = Scene.clock();
        if (heat.isAtLeast(HeatLevel.FADING)) {
            int color = heat == HeatLevel.SEETHING ? SEETHING : KINDLED;
            float p = (float) ((heat == HeatLevel.FADING ? 0.18 : 0.32) + 0.06 * Math.sin(clock * 3));
            Fx.facePatch(pose, buffers, camera, Vec3.atBottomCenterOf(pos).add(0, FLOOR + 0.01, 0), right(), forward(), 0.36,
                    p * red(color), p * green(color), p * blue(color));
        }
        if (!hovered || moving()) {
            return;
        }
        float p = (float) (0.45 + 0.15 * Math.sin(clock * 4));
        boolean ok = !refusesCarried() && notReady() == null;
        EnchantFx.disc(pose, buffers, camera, landing().add(0, 0.01, 0), 0.22, 0, EnchantFx.GLOW,
                p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
    }

    /** What the hand carries would be refused (checked as act decides). */
    private boolean refusesCarried() {
        Minecraft mc = Minecraft.getInstance();
        BasinMenu menu = menu();
        if (menu == null || mc.level == null) {
            return false;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return !holdsItems();
        }
        if (GenericItemEmptying.canItemBeEmptied(mc.level, carried)) {
            return false;
        }
        if (GenericItemFilling.canItemBeFilled(mc.level, carried) || carried.is(Items.SPONGE)) {
            return !canFill(carried);
        }
        return slotFor(carried, -1) < 0;
    }

    /** Whether Create would fill this from the basin: one of its tanks holds at least what the container takes (a sponge: any fluid). */
    private boolean canFill(ItemStack carried) {
        Minecraft mc = Minecraft.getInstance();
        IFluidHandler tanks = mc.level == null ? null : mc.level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (tanks == null) {
            return false;
        }
        for (int i = 0; i < tanks.getTanks(); i++) {
            FluidStack fluid = tanks.getFluidInTank(i);
            if (fluid.isEmpty()) {
                continue;
            }
            int needed = carried.is(Items.SPONGE) ? 1 : GenericItemFilling.getRequiredAmountForItem(mc.level, carried, fluid.copy());
            if (needed > 0 && fluid.getAmount() >= needed) {
                return true;
            }
        }
        return false;
    }
}
