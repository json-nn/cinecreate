package org.minechestplate.cinecreate.client.scene;

import com.simibubi.create.content.logistics.depot.EjectorBlock;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Scene;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.client.ClientConfig;

/**
 * The Weighted Ejector (DESIGN §7.12): the item is set on the plate as on a depot. After the plate snaps, the eyes stay
 * where they lean and only the head turns, following the item to where it lands (as far as a neck turns) and back. Only
 * with the "Watch launches" setting on and the target within {@link #WATCH_REACH} blocks.
 */
public class EjectorScene extends DepotScene {
    /** The snap of the plate: kick, and how long it fades (seconds). */
    static final double KICK = 0.40, KICK_TIME = 0.35;
    /** Watching: the eyes catching up with the item, the rest where it lands, the look back to the ejector (seconds). */
    static final double CATCH = 0.25, REST = 0.4, BACK = 0.6;
    /** How far the head turns from the lean (degrees: to either side, up, down). */
    static final float TURN = 110, UP = 70, DOWN = 60;
    /** A far landing is watched a little narrower (degrees off the field of view at {@link #FAR} blocks and beyond). */
    static final double SQUINT = 12, FAR = 16;
    static final int WATCH_REACH = 32;

    /** Development (SmokeTest): launches seen, watched to the landing, and the largest head turn (degrees). */
    public int launches, watched;
    public float turnedMost;

    private EjectorBlockEntity.State lastState;
    private double launchAt = -10;
    /** The launch being watched: its flight time (Create's ticks), or -1. */
    private double flight = -1;
    private boolean landed;

    public EjectorScene(BlockPos pos, Direction side) {
        super(pos, side);
    }

    @Nullable
    @Override
    protected Component notReady() {
        EjectorBlockEntity ejector = ejector();
        return ejector != null && ejector.getState() != EjectorBlockEntity.State.CHARGED ? Component.translatable("cinecreate.busy.ejector") : null;
    }

    @Nullable
    private EjectorBlockEntity ejector() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof EjectorBlockEntity ejector ? ejector : null;
    }

    private Direction facing() {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        return state != null && state.hasProperty(EjectorBlock.HORIZONTAL_FACING) ? state.getValue(EjectorBlock.HORIZONTAL_FACING) : side.getOpposite();
    }

    /**
     * How long Create's thrown item flies, in ticks: the first moment Create's path has covered the whole distance to the
     * target (its path is corrected to end exactly there).
     */
    private double flightTicks(EjectorBlockEntity ejector) {
        Direction facing = facing();
        Vec3 start = Vec3.atCenterOf(pos);
        BlockPos target = ejector.getTargetPosition();
        double distance = Math.abs((target.getX() - pos.getX()) * facing.getStepX() + (target.getZ() - pos.getZ()) * facing.getStepZ());
        for (double t = 0; t <= 200; t += 0.5) {
            Vec3 at = ejector.getLaunchedItemLocation((float) t).subtract(start);
            if (at.x * facing.getStepX() + at.z * facing.getStepZ() >= distance - 1e-3) {
                return Math.max(3, t);
            }
        }
        return 200;
    }

    /** Seconds since the launch being watched, or -1 when none is. */
    private double watching() {
        if (flight < 0) {
            return -1;
        }
        double t = Scene.clock() - launchAt;
        if (t > flight / 20 + REST + BACK) {
            flight = -1;
            return -1;
        }
        return t;
    }

    /** How much the gaze is on the item rather than on the plate (0..1). */
    private double onItem(double t) {
        double fly = flight / 20;
        return Anim.inOutCubic(Anim.window(t, 0, CATCH)) * (1 - Anim.inOutCubic(Anim.window(t, fly + REST, fly + REST + BACK)));
    }

    // ---- the launch ----------------------------------------------------------------------------------

    /** The plate snapping (Create's state leaving CHARGED) starts the watch. */
    @Override
    public void tick() {
        super.tick();
        EjectorBlockEntity ejector = ejector();
        if (ejector == null) {
            return;
        }
        EjectorBlockEntity.State state = ejector.getState();
        if (lastState == EjectorBlockEntity.State.CHARGED && state != EjectorBlockEntity.State.CHARGED) {
            launches++;
            launchAt = Scene.clock();
            landed = false;
            boolean near = ejector.getTargetPosition().distSqr(pos) <= (double) WATCH_REACH * WATCH_REACH;
            flight = ClientConfig.followLaunches() && near ? flightTicks(ejector) : -1;
        }
        lastState = state;
        double t = watching();
        if (t >= 0 && !landed && t >= flight / 20) {
            landed = true;
            watched++;
        }
    }

    @Override
    public double cameraKick() {
        double t = Scene.clock() - launchAt;
        return t >= 0 && t < KICK_TIME ? KICK * (1 - t / KICK_TIME) : 0;
    }

    @Override
    public CameraRig.Pose lean() {
        CameraRig.Pose home = super.lean();
        double t = watching();
        EjectorBlockEntity ejector = ejector();
        if (t < 0 || ejector == null) {
            return home;
        }
        double fly = flight / 20;
        Vec3 item = ejector.getLaunchedItemLocation((float) (Math.min(t, fly) * 20));
        Vec3 look = item.subtract(home.pos());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        // шея поворачивается только до упора: дальше взгляд останавливается
        float turn = Mth.clamp(Mth.wrapDegrees(yaw - home.yaw()), -TURN, TURN);
        pitch = Mth.clamp(pitch, -UP, DOWN);
        double k = onItem(t);
        turnedMost = Math.max(turnedMost, Math.max(Math.abs(turn), Math.abs(pitch - home.pitch())) * (float) k);
        return new CameraRig.Pose(home.pos(), home.yaw() + (float) (turn * k), (float) Mth.lerp(k, home.pitch(), pitch),
                (float) (home.roll() * (1 - k)));
    }

    @Override
    public double leanFov() {
        double t = watching();
        EjectorBlockEntity ejector = ejector();
        if (t < 0 || ejector == null) {
            return super.leanFov();
        }
        double far = Mth.clamp(Math.sqrt(ejector.getTargetPosition().distSqr(pos)) / FAR, 0, 1);
        return super.leanFov() - SQUINT * far * onItem(t);
    }

    @Override
    protected Component sceneHint() {
        return Component.translatable("cinecreate.hint.weighted_ejector");
    }

    /** Development (SmokeTest): whether a launch is being watched right now. */
    public boolean watchingNow() {
        return watching() >= 0;
    }
}
