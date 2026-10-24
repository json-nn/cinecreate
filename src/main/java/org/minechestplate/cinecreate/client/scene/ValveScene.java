package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.ValveMenu;

/**
 * The Valve Handle. The player takes the wheel with both hands, at ten and two. A click turns it by its set angle: the
 * hands ride the rim with Create's own wheel, long turns go hand over hand, and when the wheel stops a soft clunk runs
 * through the camera. The right button turns it back, and the scroll wheel sets the angle; the number floats over the
 * hub. A wheel that is still turning, or a network that something else drives or that is overstressed, will not
 * budge: the hands shake and the hint says why. (DESIGN §7.4)
 */
public class ValveScene extends CineStation<ValveMenu> {
    /** The rim's middle sits 6.5 px out from the face the valve is fixed to; the block's centre is at 8 px. */
    static final double RIM_DEPTH = 1.5 / 16, GRIP_RADIUS = 6.5 / 16, GRIP_FRONT = 0.04;
    /** Ten and two: the grips, in degrees from the wheel's top toward the player's right. */
    static final double RIGHT_GRIP = 60, LEFT_GRIP = -60;
    /** Bracing for a turn: the grips tighten this much inward, over this long; the beat comes when it is done. */
    static final double BRACE = 0.01, BRACE_TIME = 0.08;
    /** A hand lets go after this much travel and grips again back at its place, lifting off the rim. */
    static final double REGRIP_AFTER = 90, REGRIP_TIME = 0.15, REGRIP_LIFT = 0.03;
    /** How far the camera stands from the wheel's centre. */
    static final double CAMERA = 1.2;
    /** The clunk when the wheel stops. */
    static final double CLUNK = 0.25, CLUNK_DECAY = 14;
    /** Create's valve: 32 RPM, as degrees per tick. */
    static final double DEGREES_PER_TICK = 32 * 360 / 60.0 / 20.0;
    /** The server's answer to a turn may take this long before the wheel moves; no movement means it refused. */
    static final double ANSWER_WAIT = 0.6;
    /** A set angle is sent once the scroll wheel has rested this long. */
    static final double ANGLE_SEND_DELAY = 0.25;

    /** Development ({@code SmokeTest}): turns finished (clunks), and clicks refused. */
    public int clunks, denials;

    private final Direction facing;
    /** The wheel's centre, and the positive direction of its axis (Create turns the wheel about it). */
    private final Vec3 hub, axis;
    private final Hand right = new Hand(RIGHT_GRIP), left = new Hand(LEFT_GRIP);

    /** The turn under way: from the click until the wheel has stopped. */
    private boolean turning, started;
    private double clickedAt = -10, clunkAt = -10, turnLength = 0.5;
    private int turnDirection = 1;
    /** The angle being set with the scroll wheel, not sent yet; when the readout last changed. */
    @Nullable
    private Integer pendingAngle;
    private double scrolledAt = -10, readoutAt = -10;
    private double wheelAngle;
    private boolean primed;

    public ValveScene(BlockPos pos, Direction side) {
        super(pos, side, ValveMenu.class, 0);
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        facing = state != null && state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : side;
        hub = Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(RIM_DEPTH));
        axis = Vec3.atLowerCornerOf(Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()).getNormal());
    }

    /** One hand on the rim: where on the wheel it holds, and the change of grip under way. */
    private final class Hand {
        final double place;
        /** The wheel's angle when this hand took hold. */
        double heldAt;
        double regripAt = -10;
        Vec3 releasedFrom = Vec3.ZERO;

        Hand(double place) {
            this.place = place;
        }

        double travel() {
            return wheelAngle - heldAt;
        }

        boolean regripping() {
            return Scene.clock() - regripAt < REGRIP_TIME;
        }

        void regrip() {
            releasedFrom = point();
            regripAt = Scene.clock();
            heldAt = wheelAngle;
        }

        /** Where it holds now: its place on the rim, carried round by the wheel since it took hold. */
        Vec3 grip() {
            Vec3 offset = up().scale(Math.cos(Math.toRadians(place))).add(right().scale(Math.sin(Math.toRadians(place))))
                    .scale(GRIP_RADIUS);
            offset = rotate(offset, axis, Math.toRadians(travel()));
            Vec3 inward = offset.normalize().scale(-BRACE * brace());
            return hub.add(offset).add(inward).add(front().scale(GRIP_FRONT));
        }

        /** The grip, or on the way to it after letting go (a small lift off the rim, settling with a little overshoot). */
        Vec3 point() {
            double k = (Scene.clock() - regripAt) / REGRIP_TIME;
            if (k >= 1) {
                return grip();
            }
            Vec3 to = grip();
            return releasedFrom.add(to.subtract(releasedFrom).scale(Anim.outBack(Anim.clamp01(k), 1.4)))
                    .add(front().scale(REGRIP_LIFT * Anim.hump(Anim.clamp01(k))));
        }
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return hub;
    }

    /** Out from the wheel's face, toward the player (the side the wheel faces). */
    private Vec3 front() {
        return Vec3.atLowerCornerOf(facing.getNormal());
    }

    /** The wheel's top as the player sees it: up on a wall, away from the player on a floor or a ceiling. */
    private Vec3 up() {
        return facing.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : side().scale(-1);
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = Vec3.atCenterOf(pos).add(side().scale(0.86));
        stand = new Vec3(stand.x, player.getY(), stand.z);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public boolean worksAtAnAngle() {
        return !facing.getAxis().isHorizontal();
    }

    /** A wheel on a wall or a ceiling is worked as a face (CI lets those sit higher above the feet than a surface). */
    @Override
    public boolean workOnTop() {
        return facing == Direction.UP;
    }

    /**
     * In front of the wheel, far enough back that the whole wheel and both hands fit (the 0.8 of DESIGN §7.4 filled the
     * frame); looking down onto one on the floor, up at one on the ceiling. Leans into a turn.
     */
    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = switch (facing) {
            case UP -> hub.add(side().scale(CAMERA * Math.cos(Math.toRadians(60)))).add(0, CAMERA * Math.sin(Math.toRadians(60)), 0);
            case DOWN -> hub.add(side().scale(CAMERA * Math.cos(Math.toRadians(35)))).add(0, -CAMERA * Math.sin(Math.toRadians(35)), 0);
            default -> hub.add(side().scale(CAMERA)).add(0, 0.25, 0);
        };
        eye = eye.add(0, -0.004 * brace(), 0);
        Vec3 look = hub.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        float roll = (float) (2.5 * Anim.hump(turnProgress()) * turnDirection);
        return new CameraRig.Pose(eye, yaw, pitch, roll);
    }

    @Override
    public double leanFov() {
        return 62;
    }

    @Override
    public float bodyPitch() {
        return switch (facing) {
            case UP -> 50f;
            case DOWN -> -25f;
            default -> 15f;
        };
    }

    @Override
    public boolean showsInventory() {
        return false;
    }

    @Override
    protected Vec3[] bothHands() {
        return new Vec3[] {right.point(), left.point()};
    }

    /** The hands hold on: they follow the rim closely, not lazily like hands at rest. */
    @Override
    public double handsStiffness() {
        return 60;
    }

    @Override
    protected Vec3 restAt(boolean isRight) {
        return (isRight ? right : left).point();
    }

    @Override
    public void updateCursor(boolean overPanel) {
        aim = null;
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        return target;
    }

    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        return hand;
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return hub;
    }

    @Override
    public Component hint() {
        ValveHandleBlockEntity valve = valve();
        if (valve != null && valve.isOverStressed()) {
            return Component.translatable("cinecreate.hint.valve_handle.overstressed");
        }
        if (valve != null && valve.inUse == 0 && valve.getTheoreticalSpeed() != 0) {
            return Component.translatable("cinecreate.hint.valve_handle.driven");
        }
        return Component.translatable("cinecreate.hint.valve_handle", angleText(shownAngle()));
    }

    // ---- interaction ---------------------------------------------------------------------------------

    /** Every click is the wheel's: refused while it turns (CI would just drop it), so the hands can say no. */
    @Override
    public void click(int button, boolean shift) {
        if (menu() == null) {
            return;
        }
        ValveHandleBlockEntity valve = valve();
        if (turning || moving() || valve == null || valve.inUse > 0 || valve.getTheoreticalSpeed() != 0 || valve.isOverStressed()) {
            refuse();
            return;
        }
        act(button, shift);
    }

    @Override
    protected void act(int button, boolean shift) {
        boolean back = button == 1;
        int angle = shownAngle();
        // как у Create: колесо проворачивается на 90° (за 135° — на 180°), а сеть — ровно на заданный угол
        turnLength = (Math.ceil(Math.abs(angle) / DEGREES_PER_TICK) + 2) / 20.0;
        turnDirection = (angle < 0 ^ back) ? -1 : 1;
        turning = true;
        started = false;
        clickedAt = Scene.clock();
        sendAngle();
        startMove(BRACE_TIME + turnLength, BRACE_TIME, () -> buttonClick(back ? ValveMenu.TURN_BACK : ValveMenu.TURN));
    }

    private void refuse() {
        denials++;
        deny();
    }

    /** The scroll wheel sets the angle: 5° a notch, Shift 1°, Ctrl 15°; -180..180, never 0. */
    @Override
    public void scroll(double delta) {
        if (delta == 0) {
            return;
        }
        int step = Screen.hasShiftDown() ? 1 : Screen.hasControlDown() ? 15 : 5;
        int from = shownAngle();
        int to = Math.max(-ValveMenu.MAX_ANGLE, Math.min(ValveMenu.MAX_ANGLE, from + (delta > 0 ? step : -step)));
        if (to == 0) {
            to = delta > 0 ? step : -step;
        }
        if (to == from) {
            return;
        }
        pendingAngle = to;
        scrolledAt = Scene.clock();
        readoutAt = Scene.clock();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            AllSoundEvents.SCROLL_VALUE.playAt(mc.level, pos, 0.25f, 1f, true);
        }
    }

    /** Client tick: a set angle goes to the server once the scroll wheel rests. */
    @Override
    public void tick() {
        super.tick();
        if (pendingAngle != null && Scene.clock() - scrolledAt >= ANGLE_SEND_DELAY) {
            sendAngle();
        }
    }

    private void sendAngle() {
        if (pendingAngle != null) {
            buttonClick(ValveMenu.angleButton(pendingAngle));
            pendingAngle = null;
        }
    }

    private void buttonClick(int id) {
        Minecraft mc = Minecraft.getInstance();
        ValveMenu menu = menu();
        if (menu != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** The angle the next turn uses: being set, or Create's own. */
    public int shownAngle() {
        if (pendingAngle != null) {
            return pendingAngle;
        }
        ValveHandleBlockEntity valve = valve();
        return valve == null ? 45 : valve.angleInput.getValue();
    }

    /** As Create's value box shows it: the direction (⟳ is Create's first row, the negative angles) and the size. */
    static Component angleText(int angle) {
        return Component.literal((angle < 0 ? "⟳ " : "⟲ ") + Math.abs(angle) + "°");
    }

    // ---- the wheel -----------------------------------------------------------------------------------

    /**
     * Once a frame: the wheel's angle as Create draws it this frame, the hands carried round with it and changing
     * grip, and the turn's end (the clunk) or the server's refusal.
     */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        ValveHandleBlockEntity valve = valve();
        if (valve == null) {
            return;
        }
        wheelAngle = valve.getIndependentAngle(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        if (!primed) {
            // руки берутся за колесо там, где оно стоит сейчас
            primed = true;
            right.heldAt = left.heldAt = wheelAngle;
        }
        if (turning) {
            if (valve.inUse > 0) {
                started = true;
            } else if (started) {
                // колесо встало: мягкий стук через камеру
                turning = false;
                clunks++;
                clunkAt = Scene.clock();
                play();
            } else if (Scene.clock() - clickedAt > BRACE_TIME + ANSWER_WAIT) {
                // сервер не повернул (колесо ещё остывало, или сеть уже крутит что-то другое)
                turning = false;
                refuse();
            }
        }
        regrip(valve);
    }

    /**
     * Hand over hand: a hand that the wheel has carried a quarter turn lets go and takes hold again back at its place,
     * the trailing one first and one at a time; when the wheel has stopped, both go back to ten and two.
     */
    private void regrip(ValveHandleBlockEntity valve) {
        if (right.regripping() || left.regripping()) {
            return;
        }
        boolean still = valve.inUse == 0 && !turning;
        Hand trailing = trailing();
        Hand leading = trailing == right ? left : right;
        for (Hand hand : List.of(trailing, leading)) {
            double travel = Math.abs(hand.travel());
            if (travel >= REGRIP_AFTER || still && travel > 1) {
                hand.regrip();
                return;
            }
        }
    }

    /** The hand behind the other in the direction the wheel turns. */
    private Hand trailing() {
        Vec3 a = right.grip().subtract(hub), b = left.grip().subtract(hub);
        boolean rightToLeftIsPositive = a.cross(b).dot(axis) > 0;
        boolean turningPositive = right.travel() >= 0;
        return rightToLeftIsPositive == turningPositive ? right : left;
    }

    /** 0..1: braced for and through a turn; lets go once the wheel has stopped. */
    private double brace() {
        double since = Scene.clock() - clickedAt;
        if (turning) {
            return Anim.outCubic(Anim.clamp01(since / BRACE_TIME));
        }
        return 1 - Anim.clamp01((Scene.clock() - clunkAt) / 0.12);
    }

    private double turnProgress() {
        if (!turning) {
            return 0;
        }
        return Anim.clamp01((Scene.clock() - clickedAt - BRACE_TIME) / Math.max(0.05, turnLength));
    }

    @Override
    public double cameraKick() {
        return CLUNK * Math.exp(-(Scene.clock() - clunkAt) * CLUNK_DECAY);
    }

    private void play() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(hub.x, hub.y, hub.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.3f, 1.4f, false);
        }
    }

    @Nullable
    private ValveHandleBlockEntity valve() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof ValveHandleBlockEntity valve ? valve : null;
    }

    /** Turns {@code v} by {@code radians} about the unit vector {@code k} (right-handed, as Create turns the wheel). */
    static Vec3 rotate(Vec3 v, Vec3 k, double radians) {
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return v.scale(cos).add(k.cross(v).scale(sin)).add(k.scale(k.dot(v) * (1 - cos)));
    }

    // ---- drawing -------------------------------------------------------------------------------------

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

    /** The angle over the hub, facing the camera; it pops when it changes. */
    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Component text = angleText(shownAngle());
        double pop = Anim.outBack(Anim.clamp01((Scene.clock() - readoutAt) / 0.25), 1.6);
        float scale = (float) (0.006 * (0.7 + 0.3 * pop));
        Vec3 at = hub.add(side().scale(0.06)).add(up().scale(0.18));
        pose.pushPose();
        pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, 0, 0xFFFFE296, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
