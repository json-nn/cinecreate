package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.bridge.CrankMenu;

/**
 * The Hand Crank (DESIGN §7.5). Both hands on the grip, one behind the other. Holding the left button cranks: the
 * hands ride the grip round with Create's own drawn handle, the camera bobs gently with each turn, and the player
 * tires (Create's hunger cost). Letting go lets the crank coast to a stop, the hands still on it. The right button
 * cranks the other way.
 */
public class CrankScene extends CineStation<CrankMenu> {
    /**
     * The handle's grip in Create's model, from the crank's centre: 7 px out from the axis, reaching from 1 px behind
     * the centre to 5 px in front. The right fist holds its outer end, the left one the inner end, by the arm.
     */
    static final float GRIP_OUT = 7 / 16f, RIGHT_FIST = 3.5f / 16, LEFT_FIST = 0.5f / 16;
    /**
     * How far the camera stands from the crank (a wall crank: in front and a little above) and how wide it sees: the
     * handle is a block long, but CI's arms hang from the camera, and from farther back they reach out like poles.
     */
    static final double CAMERA = 1.0, CAMERA_UP = 0.3, FOV = 70;
    /** At full speed the camera bobs this much with each turn and rolls this many degrees; more when tired. */
    static final double BOB = 0.006, ROLL = 1.0, TIRED_BOB = 1.6;
    /** Food below this: tired. */
    static final int TIRED_FOOD = 6;
    /** Create's crank at full speed, in degrees per tick. */
    static final double FULL_SPEED = CrankMenu.RPM * 360 / 60.0 / 20.0;

    /** Development: turns sent. */
    public int turns;
    /** Development only ({@code SmokeTest}): marks where the scene puts the grip, to compare with Create's drawn handle. */
    public static boolean smokeMarks;

    private final Direction facing;
    private final Vec3 hub;
    /** Create's handle model turned to face the crank's way, and the positive direction of its axis. */
    private final Quaternionf model;
    private final Vector3f axis;
    /** The button held (0 cranks on, 1 back; -1: none), held by the smoke test rather than the mouse, ticks since a turn. */
    private int held = -1;
    private boolean heldByCode;
    private int sinceTurn;
    /** The handle as Create draws it this frame: its angle in degrees, and its speed as a share of full speed. */
    private double angle, speed;

    public CrankScene(BlockPos pos, Direction side) {
        super(pos, side, CrankMenu.class, 0);
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        facing = state != null && state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : side;
        hub = Vec3.atCenterOf(pos);
        model = new Quaternionf().rotateTo(0, 0, -1, facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis());
        axis = new Vector3f(positive.getStepX(), positive.getStepY(), positive.getStepZ());
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return hub;
    }

    /** The crank's "up" as the player sees it: up on a wall, away from the player on a floor or a ceiling. */
    private Vec3 up() {
        return facing.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : side().scale(-1);
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = hub.add(side().scale(0.86));
        stand = new Vec3(stand.x, player.getY(), stand.z);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public boolean worksAtAnAngle() {
        return !facing.getAxis().isHorizontal();
    }

    /** A crank on a wall or a ceiling is worked as a face (CI lets those sit higher above the feet than a surface). */
    @Override
    public boolean workOnTop() {
        return facing == Direction.UP;
    }

    /** In front of the crank, a little above it (looking down onto one on the floor, up at one on a ceiling); it bobs with each turn. */
    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = switch (facing) {
            case UP -> hub.add(side().scale(CAMERA * Math.cos(Math.toRadians(60)))).add(0, CAMERA * Math.sin(Math.toRadians(60)), 0);
            case DOWN -> hub.add(side().scale(CAMERA * Math.cos(Math.toRadians(35)))).add(0, -CAMERA * Math.sin(Math.toRadians(35)), 0);
            default -> hub.add(side().scale(CAMERA)).add(0, CAMERA_UP, 0);
        };
        double sway = Math.sin(Math.toRadians(angle)) * Math.min(1, Math.abs(speed)) * (tired() ? TIRED_BOB : 1);
        eye = eye.add(0, BOB * sway, 0);
        Vec3 look = hub.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, (float) (ROLL * sway));
    }

    @Override
    public double leanFov() {
        return FOV;
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

    /** Both hands on the grip: the right at its outer end, the left behind it, by the arm. */
    @Override
    protected Vec3[] bothHands() {
        return new Vec3[] {grip(RIGHT_FIST), grip(LEFT_FIST)};
    }

    /** The hands hold on: they follow the knob closely, not lazily like hands at rest. */
    @Override
    public double handsStiffness() {
        return 60;
    }

    @Override
    protected Vec3 restAt(boolean right) {
        return grip(right ? RIGHT_FIST : LEFT_FIST);
    }

    /**
     * A point on the grip of Create's handle, this far in front of the crank's centre, turned as Create's visual turns
     * the handle: the model turned to face the crank's way, then about the axis's positive direction by the drawn angle.
     */
    private Vec3 grip(float front) {
        Vector3f at = new Vector3f(-GRIP_OUT, 0, -front);
        model.transform(at);
        new Quaternionf().rotateAxis((float) Math.toRadians(angle), axis).transform(at);
        return hub.add(at.x, at.y, at.z);
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
        HandCrankBlockEntity crank = crank();
        if (crank != null && crank.isOverStressed()) {
            return Component.translatable("cinecreate.hint.hand_crank.overstressed");
        }
        return Component.translatable(tired() ? "cinecreate.hint.hand_crank.tired" : "cinecreate.hint.hand_crank");
    }

    /** Hungry players tire: Create charges food for every turn. */
    public boolean tired() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !player.getAbilities().invulnerable && player.getFoodData().getFoodLevel() < TIRED_FOOD;
    }

    // ---- cranking ------------------------------------------------------------------------------------

    /** A press starts cranking (left on, right back); it goes on while the button stays down. */
    @Override
    public void click(int button, boolean shift) {
        if (menu() == null || button != 0 && button != 1) {
            return;
        }
        held = button;
        heldByCode = false;
        turn();
    }

    @Override
    public void release(int button) {
        if (button == held && !heldByCode) {
            held = -1;
        }
    }

    /** Development ({@code SmokeTest}): holds a button down as the mouse would, or lets go (-1). */
    public void hold(int button) {
        held = button;
        heldByCode = button >= 0;
        if (button >= 0) {
            turn();
        }
    }

    /** Client tick: while the button is held, a turn as often as holding Create's use repeats. */
    @Override
    public void tick() {
        super.tick();
        if (held >= 0 && !heldByCode && !mouseDown(held)) {
            // отпустили где-то, куда сцена не смотрит
            held = -1;
        }
        if (held >= 0 && ++sinceTurn >= CrankMenu.TURN_EVERY) {
            turn();
        }
    }

    public boolean cranking() {
        return held >= 0;
    }

    /**
     * One turn: the button to the server, and the same turn on the client's crank right away, as Create's own click
     * does on both sides (the client's crank then plays Create's cranking sound).
     */
    private void turn() {
        sinceTurn = 0;
        boolean back = held == 1;
        Minecraft mc = Minecraft.getInstance();
        CrankMenu menu = menu();
        HandCrankBlockEntity crank = crank();
        if (menu == null || mc.gameMode == null || crank == null) {
            return;
        }
        mc.gameMode.handleInventoryButtonClick(menu.containerId, back ? CrankMenu.TURN_BACK : CrankMenu.TURN);
        crank.turn(back);
        turns++;
    }

    private static boolean mouseDown(int button) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, button == 0 ? GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    /** Once a frame: the handle's angle and speed as Create draws them this frame. */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        HandCrankBlockEntity crank = crank();
        if (crank == null) {
            return;
        }
        angle = crank.getIndependentAngle(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        speed = crank.chasingAngularVelocity / FULL_SPEED;
    }

    @Override
    protected void act(int button, boolean shift) {
    }

    @Nullable
    private HandCrankBlockEntity crank() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof HandCrankBlockEntity crank ? crank : null;
    }

    // ---- drawing -------------------------------------------------------------------------------------

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        if (smokeMarks) {
            // чуть впереди наружного конца рукояти: кулак его закрывает
            Fx.facePatch(pose, buffers, camera, grip(6.5f / 16), right(), up(), 0.03, 1f, 0.1f, 0.1f);
        }
    }
}
