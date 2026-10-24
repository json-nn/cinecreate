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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.minechestplate.cinecreate.bridge.BasinMenu;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.Notices;
import org.minechestplate.cinecreate.station.Composites;

/**
 * The Basin (DESIGN §7.6), the brewing stand of Create. Leaning over the rim, the player drops ingredients in one by
 * one: each falls and settles, with a splash on liquid or a plop on the dry floor. A bucket or a bottle tipped over the
 * rim pours its fluid in (empty, it dips in and fills up); an empty hand scoops everything out. Heat from a Blaze
 * Burner below glows on the floor, orange, or blue when it seethes. A Mechanical Mixer above makes it a watch: the
 * camera rises and backs off to show the whisk, steam and bubbles come up, and outputs flash in as they appear. Under
 * a Mechanical Press every stroke puffs dust (or splashes the liquid) and jolts the camera.
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
    /**
     * The camera from the middle of the inside: out toward the player, and up. Steep, about 70° down: from less steep
     * the near wall hides the floor and what lies on it.
     */
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
    /** Steam over a mixing basin: a puff every this many ticks. */
    static final int STEAM_EVERY = 3;

    /** Development only ({@code SmokeTest}): the basin counts as pointed at while the cursor is somewhere else. */
    public static boolean smokePin;
    /** Development: things dropped in, pours and fills, takes, clicks refused, outputs seen appearing, press strokes. */
    public int drops, pours, takes, denials, outputs, strokes;

    /** The middle of the inside, and the middle of the opening at the rim. */
    private final Vec3 inside, top;
    private boolean hovered;

    private enum Move { DROP, POUR, FILL, TAKE }

    private Move move = Move.DROP;
    /** The fluid being poured (for the stream's colour). */
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
     * player still leans over its rim. Below that and within the usual step of it.
     */
    @Override
    protected boolean bareWorkable(Vec3 feet) {
        double high = inside.y - feet.y;
        return high >= -0.6 && high <= 1.9 && Math.hypot(inside.x - feet.x, inside.z - feet.z) <= 1.5
                && Views.reachable(this, feet, Set.of(pos));
    }

    /**
     * With an Extendo Grip: a basin too high to lean over is hung from, the grip hooked on its rim (close enough to
     * climb, within Create's reach of the grip); any other within that reach is worked from where the player stands.
     */
    @Override
    protected boolean gripWorkable(Vec3 feet) {
        double high = inside.y - feet.y;
        if (high > HANG) {
            return Grip.reaches(feet, pos) && Math.hypot(inside.x - feet.x, inside.z - feet.z) <= 1.5
                    && Views.reachable(this, feet, Set.of(pos));
        }
        return high >= -0.6 && Grip.reaches(feet, pos);
    }

    /** Hanging from the rim by a grip (the basin out of a bare arm's reach above). */
    private boolean hanging() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Grip.inHand() && inside.y - player.getY() > HANG;
    }

    /** Development: hanging from the rim by a grip right now. */
    public boolean hangingNow() {
        return hanging();
    }

    /** Working it from afar with a grip: from where the player stands, the view at their eyes. */
    private boolean fromFar() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Grip.inHand() && !hanging() && Math.hypot(inside.x - player.getX(), inside.z - player.getZ()) > 1.5;
    }

    @Override
    protected boolean hooked() {
        return hanging() || letGoAt >= 0;
    }

    /**
     * The grip hooks the rim on the player's side, to the left (it is in the left hand; the right works): the claw's
     * palm on the wall, its fingers over into the basin, the fist below the rim outside.
     */
    @Override
    protected Vec3 hookPoint() {
        return top.add(side().scale(0.44)).add(right().scale(-0.34)).add(0, 0.06, 0);
    }

    /** Hanging from the rim, the grip gone: the player drops (the view falls back to their eyes; no harm done). */
    @Override
    protected void gripLost() {
        if (inside.y - Minecraft.getInstance().player.getY() > HANG) {
            letGoAt = Scene.clock();
            hungFrom = lean().pos();
            return;
        }
        super.gripLost();
    }

    /** Over the rim, looking down into the basin (on a burner, higher than the player's head: the camera rises to it); from afar, at the eyes. */
    @Override
    public boolean freeCamera() {
        return !fromFar();
    }

    /** Leaning over the rim, looking down into the basin; over a mixer it rises and backs off to show the whisk. */
    @Override
    public CameraRig.Pose lean() {
        double k = Anim.inOutCubic(watch);
        Vec3 eye = inside.add(side().scale(CAMERA_OUT + BACK * k)).add(0, CAMERA_UP + RISE * k, 0);
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
    public double leanFov() {
        return 64;
    }

    @Override
    public float bodyPitch() {
        return 50f;
    }

    /** The falling view has come down to the player's eyes (or fallen long enough): the scene ends there, with a thud. */
    private void land() {
        Minecraft mc = Minecraft.getInstance();
        double t = Scene.clock() - letGoAt;
        if (mc.player == null || hungFrom.y - 0.5 * FALL_PULL * t * t > mc.player.getEyeY() && t < FALL_MOST) {
            return;
        }
        letGoAt = -1;
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.PLAYER_SMALL_FALL,
                SoundSource.PLAYERS, 0.8f, 1f, false);
        Notices.about(pos, Component.translatable("cinecreate.notice.grip_dropped"));
        Scene.abort();
    }

    /** When the grip was let go of while hanging (the fall), and where the view fell from; -1: not falling. */
    private double letGoAt = -1;
    @Nullable
    private Vec3 hungFrom;

    /** Fists on the near rim, either side. */
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

    /** The basin has one opening: the cursor over it points the hand there. */
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
        if (hit != null && Math.abs(hit.x - top.x) <= 0.5 && Math.abs(hit.z - top.z) <= 0.5) {
            hover();
        }
    }

    /** Development: points at the basin as the cursor would. */
    public void hover() {
        hovered = true;
        aim = top;
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
            // над серединой, отпустить, рука поднимается
            case DROP -> target.add(0, CARRY + 0.08 * Anim.outCubic(Anim.window(t, DROP_BEAT, DROP)), 0);
            // над ближним краем, сосуд наклоняется
            case POUR -> target.add(side().scale(0.12)).add(0, CARRY + 0.05, 0);
            // зачерпнуть: опустить сосуд внутрь и поднять
            case FILL -> target.add(0, CARRY - (CARRY + DIP) * dip(t, POUR_BEAT, POUR), 0);
            // взять: рука внутрь, пальцы сжимаются, наверх
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

    /** Held at a slant; a bucket or a bottle tips over the rim to pour, and rights itself after. */
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
     * button the whole stack, the right one item), an empty hand takes everything out. What the basin cannot take: the
     * hands shake.
     */
    @Override
    protected void act(int button, boolean shift) {
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
            if (fluidUnits() < 1) {
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
                fallFrom = held != null ? held : top.add(0, CARRY, 0);
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
            splash(surface(), 6);
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

    /** A click on the menu right now (the move's beat: {@code slotClick} waits for the move to end). */
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
    public Component hint() {
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

    /** How much fluid the basin holds, as Create draws it. */
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

    /** Client tick: steam and bubbles over a mixing basin, and outputs appearing (they rise and flash in). */
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
            Fx.steam(at.add(0, 0.05, 0), 0.22, 1);
            int tint = basinTint();
            Fx.motes(at, 0.22, 2, red(tint), green(tint), blue(tint), 0.06);
        }
        int now = outputCount();
        if (lastOutputs >= 0 && now > lastOutputs) {
            outputs++;
            flashAt = Scene.clock();
            flashColor = fluidUnits() >= 1 ? basinTint() : WARM;
            Fx.motes(surface(), 0.18, 12, 1f, 0.95f, 0.78f, 0.3);
        }
        lastOutputs = now;
    }

    /** Once a frame: the camera's way to the watch, the pour's stream, a press stroke, and the dropped item landing. */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        watch = Mth.clamp(watch + (mixing ? 1 : -1) * Scene.frameDt / WATCH_TIME, 0, 1);
        double t = moveTime();
        if (move == Move.POUR && t >= STREAM_FROM && t <= STREAM_TO && !pouring.isEmpty() && held != null) {
            int tint = tint(pouring);
            Vec3 mouth = held.add(side().scale(-0.06));
            for (int i = 0; i < 2; i++) {
                Fx.spark(mouth, new Vec3(0, -1.6, 0).add(inside.subtract(mouth).multiply(1, 0, 1)), 0.22, 0.016f,
                        red(tint), green(tint), blue(tint), 3.0, 0.05);
            }
        }
        pressStroke();
        if (!falling.isEmpty() && !landed && Scene.clock() - fellAt >= FALL) {
            landed = true;
            splash(surface(), 5);
        }
    }

    /** A press above: each stroke, timed as the press scene times its impact (the head on the work). */
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
                Fx.motes(at, 0.25, 14, 0.62f, 0.6f, 0.56f, 0.12);
            }
        }
    }

    /** Liquid splashes up in its colour; on the dry floor, a plop. */
    private void splash(Vec3 at, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (fluidUnits() >= 1) {
            int tint = basinTint();
            Fx.motes(at, 0.15, count, red(tint), green(tint), blue(tint), 0.18);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.2f, 1.6f, false);
        } else {
            AllSoundEvents.DEPOT_PLOP.playAt(mc.level, at, 0.3f, 1f, false);
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

    private static int tint(FluidStack fluid) {
        return IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
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
    public double cameraKick() {
        return KICK * Math.exp(-(Scene.clock() - kickAt) * KICK_DECAY);
    }

    @Override
    public double flash() {
        return FLASH * Math.exp(-(Scene.clock() - flashAt) * FLASH_DECAY);
    }

    @Override
    public int flashColor() {
        return flashColor;
    }

    public boolean mixing() {
        return mixing;
    }

    /** The heat a Blaze Burner below gives, as Create reads it. */
    public HeatLevel heat() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? HeatLevel.NONE : BasinBlockEntity.getHeatLevelOf(mc.level.getBlockState(pos.below()));
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /** The item just dropped, falling, then lying on the contents until Create draws it. Drawn once a frame. */
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
        Vec3 at = Anim.lerp(fallFrom, surface(), k * k);
        ItemPlacer.renderOne(pose, buffers, camera.getPosition(), falling, at, side, 31L, 1f, (float) (18 * (1 - k)), light);
    }

    /**
     * Heat from a burner below glows on the floor, orange, or blue when it seethes; the contents glow warm under the
     * cursor, or red when what is carried will not go in.
     */
    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
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
        boolean ok = !refusesCarried();
        EnchantFx.disc(pose, buffers, camera, surface().add(0, 0.01, 0), 0.22, 0, EnchantFx.GLOW,
                p, ok ? p * 0.75f : p * 0.2f, ok ? p * 0.45f : p * 0.2f);
    }

    /** What the hand carries would be refused (checked as {@link #act} decides). */
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
        if (GenericItemEmptying.canItemBeEmptied(mc.level, carried) || GenericItemFilling.canItemBeFilled(mc.level, carried)) {
            return false;
        }
        return slotFor(carried, -1) < 0;
    }
}
