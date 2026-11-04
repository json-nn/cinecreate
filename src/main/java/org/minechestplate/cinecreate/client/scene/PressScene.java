package org.minechestplate.cinecreate.client.scene;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.fx.SceneFx;
import org.minechestplate.cinecreate.station.Composites;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;

/**
 * Pressing (DESIGN §7.2): under a Mechanical Press the depot is the anvil. Create drives the head and the scene only
 * listens: the hands flinch as it comes down, the impact kicks the camera with sparks and a warm flash, and the result
 * rises in motes with a chime. Without rotation the fingers drum on the rim.
 */
public class PressScene extends DepotScene {
    /** The impact at full strength: camera kick and flash, and how fast each dies away. */
    static final double KICK = 0.45, KICK_DECAY = 13, FLASH = 0.10, FLASH_DECAY = 16;
    /** CI's anvil flash colour, hot metal. */
    static final int HOT_METAL = 0xFFE6B8;
    /** At most one kick (and flash) this often, however fast the press runs. */
    static final double KICK_GAP = 0.35;
    /** A press cycle at least this long gets full-strength effects; faster ones get proportionally weaker ones. */
    static final double FULL_CYCLE = 1.0;
    /** The hands flinch back this far, over this long, while the head comes down. */
    static final double FLINCH = 0.12, FLINCH_TIME = 0.22;
    /** The head's offset (0 up, 1 on the plate, as Create draws it) past which it counts as coming down. */
    static final float HEAD_MOVING = 0.05f;
    static final double RIM = 0.34;
    /** How long after an impact the server may take to show the result. */
    static final double RESULT_WAIT = 1.5;
    static final double MIN_RPM = 1;

    /** Development (SmokeTest): the latest impacts, and how many results showed. */
    public final List<Impact> impacts = new ArrayList<>();
    /** Only the latest impacts are kept: a press watched for an hour adds thousands. */
    static final int IMPACTS_KEPT = 64;
    public int results;

    /** An impact: the frame it landed on, the scene clock then, and whether it kicked the camera. */
    public record Impact(long frame, double at, boolean kicked) {}

    private record Contents(Item held, int count, int outputs) {}

    /** struck: this cycle's impact has landed; lastTicks: Create's cycle counter last frame (-1: not running). */
    private boolean primed, struck;
    private int lastTicks = -1;
    private double kickAt = -10, strength = 1;
    /** 0..1, straight; eased when it moves the hands. */
    private double flinch;
    /**
     * Changes the press made that no impact has shown yet: Create's server runs a few ticks ahead, so at speed a change
     * can come before the impact the client draws for it, or after. Each impact shows one, until RESULT_WAIT after it.
     */
    @Nullable
    private Contents seen;
    private int unshown;
    private double resultBy = -10;

    public PressScene(BlockPos pos, Direction side) {
        super(pos, side);
    }

    // ---- staging -------------------------------------------------------------------------------------

    /** Pulled back and a little lower than at the bare depot, so the head is in the frame too. */
    @Override
    protected CameraRig.Pose sceneLean() {
        Vec3 eye = top.add(side().scale(1.05)).add(0, 0.55, 0);
        Vec3 look = top.add(0, 0.45, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, -1.5f);
    }

    @Override
    protected double sceneFov() {
        return 66;
    }

    @Override
    public float bodyPitch() {
        return 30f;
    }

    @Override
    protected Vec3 restAt(boolean right) {
        Vec3 rim = top.add(side().scale(0.34)).add(right().scale(right ? RIM : -RIM)).add(0, HAND_Y, 0);
        return rim.add(flinchOffset()).add(0, drum(right), 0);
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        return super.reach(target, shoulder, carrying, tap).add(flinchOffset());
    }

    private Vec3 flinchOffset() {
        return side().scale(FLINCH * Anim.inOutCubic(flinch));
    }

    /** Idle fingers tapping the rim, the two hands out of step. */
    private double drum(boolean right) {
        if (!stopped()) {
            return 0;
        }
        double beat = Math.max(0, Math.sin(Scene.clock() * 9 + (right ? 0 : 2.2)));
        return 0.012 * Math.pow(beat, 6);
    }

    @Override
    public SceneKind sceneKind() {
        return SceneKinds.PRESSING;
    }

    @Override
    protected Component sceneHint() {
        if (press() == null) {
            return super.sceneHint();
        }
        return Component.translatable(stopped() ? "cinecreate.hint.pressing.stopped" : "cinecreate.hint.pressing");
    }

    // ---- the machine ---------------------------------------------------------------------------------

    /**
     * Once a frame: where Create draws the head this frame (its own interpolation). The impact lands on the frame the
     * head reaches the plate or, when no frame showed it there (a hitch, a fast press at a low frame rate), the next one.
     */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        PressingBehaviour pressing = pressing();
        boolean running = pressing != null && pressing.running;
        // на клиенте счётчик цикла стоит на -CYCLE/2, пока сервер не подтвердит удар: важна величина
        int ticks = running ? Math.abs(pressing.runningTicks) : -1;
        if (!primed) {
            // сцена началась посреди цикла: удар, который уже был, не повторяем
            primed = true;
            struck = ticks >= PressingBehaviour.CYCLE / 2;
        } else if (ticks < lastTicks) {
            // счётчик пошёл заново (или пресс встал): новый цикл
            struck = false;
        }
        lastTicks = ticks;
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float head = running ? pressing.getRenderedHeadOffset(pt) : 0;
        float drawnAt = running ? Mth.lerp(pt, pressing.prevRunningTicks, ticks) : 0;
        if (drawnAt >= PressingBehaviour.CYCLE / 2f && !struck) {
            struck = true;
            impact(pressing);
        }
        boolean coming = !struck && head > HEAD_MOVING;
        flinch = Anim.clamp01(flinch + (coming ? Scene.frameDt : -Scene.frameDt) / FLINCH_TIME);
        noteChanges(running);
        watchResult();
    }

    private final Motion headMotion = new Motion();

    @Nullable
    @Override
    protected Component notReady() {
        PressingBehaviour pressing = pressing();
        float head = pressing == null || !pressing.running ? 0 : pressing.getRenderedHeadOffset(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        return headMotion.moving(head) && head > 0.3f ? Component.translatable("cinecreate.busy.press") : null;
    }

    private void impact(PressingBehaviour pressing) {
        double now = Scene.clock();
        double cycle = PressingBehaviour.CYCLE / (double) Math.max(1, pressing.getRunningTickSpeed()) / 20.0;
        strength = Math.min(1, cycle / FULL_CYCLE);
        boolean kick = now - kickAt >= KICK_GAP;
        if (kick) {
            kickAt = now;
        }
        impacts.add(new Impact(FrameCounter.frame(), now, kick));
        if (impacts.size() > IMPACTS_KEPT) {
            impacts.remove(0);
        }
        resultBy = now + RESULT_WAIT;
        Vec3 at = top.add(0, 0.03, 0);
        SceneFx.blowSparks(at, (int) Math.round(28 * strength), 2.2);
        SceneFx.hotSparks(at, side().scale(-1).add(0, 0.7, 0), 0.5, (int) Math.round(10 * strength), 2.4);
        SceneFx.shock(top.add(0, 0.015, 0), new Vec3(0, 1, 0), 0.45, HOT_METAL, strength);
        SceneFx.chips(top.add(0, 0.06, 0), slot(DepotMenu.MAIN), (int) Math.round(6 * strength), new Vec3(0, 1, 0), 1.4, top.y);
        // удар пресса играет сервер (звук Create); здесь — только тихий звон металла
        play(SoundEvents.ANVIL_PLACE, 0.2f * (float) strength, 1.7f);
    }

    /** A change of what lies on the depot while the press runs is the press's (the player's own moves are not). */
    private void noteChanges(boolean running) {
        Contents now = contents();
        if (seen != null && !now.equals(seen) && running && !moving()) {
            unshown++;
        }
        seen = now;
        if (!running) {
            unshown = 0;
        }
    }

    /** After an impact: once the stack shows as pressed (it may already), motes rise off it with a chime. */
    private void watchResult() {
        if (resultBy < 0) {
            return;
        }
        if (unshown > 0) {
            unshown--;
            results++;
            SceneFx.motes(top.add(0, 0.05, 0), 0.1, 10, 1.0f, 0.75f, 0.4f, 0.2);
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35f, 1.3f);
        } else if (Scene.clock() <= resultBy) {
            return;
        }
        resultBy = -10;
    }

    @Override
    protected double sceneKick() {
        return KICK * strength * Math.exp(-(Scene.clock() - kickAt) * KICK_DECAY);
    }

    @Override
    protected double sceneFlash() {
        return FLASH * strength * Math.exp(-(Scene.clock() - kickAt) * FLASH_DECAY);
    }

    @Override
    public int flashColor() {
        return HOT_METAL;
    }

    /** No rotation (or overstressed): the head will not move. */
    public boolean stopped() {
        MechanicalPressBlockEntity press = press();
        return press != null && Math.abs(press.getSpeed()) < MIN_RPM;
    }

    @Nullable
    private MechanicalPressBlockEntity press() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : Composites.pressOver(mc.level, pos);
    }

    @Nullable
    private PressingBehaviour pressing() {
        MechanicalPressBlockEntity press = press();
        return press == null ? null : press.getPressingBehaviour();
    }

    private Contents contents() {
        int outputs = 0;
        for (int i = 1; i <= DepotMenu.OUTPUTS; i++) {
            outputs += slot(i).getCount();
        }
        return new Contents(slot(DepotMenu.MAIN).getItem(), slot(DepotMenu.MAIN).getCount(), outputs);
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(top.x, top.y, top.z, sound, SoundSource.BLOCKS, volume, pitch, false);
        }
    }
}
