package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Hands;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.station.StationKind;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.Notices;

/**
 * Our scenes' common ground: each scene says where its hands go, where it lets the player stand and what it draws, and
 * this adds Create's Extendo Grip to all of them (DESIGN §8.5). With a grip in hand:
 * <ul>
 * <li>a scene is worked from where the player stands, when the work is within the grip's reach ({@link Grip});</li>
 * <li>the hand holding the grip stays by the body, and the grip's claw, at the end of its lattice, goes where the
 * scene sends the hand: it sets down, takes, turns a crank or a valve's wheel ({@link GripArm}). Close to the work,
 * the grip is folded, never shorter than {@link GripArm#FOLDED}: at rest, the fist where the bare hand would be and the
 * claw ahead of it;</li>
 * <li>the claw is drawn from the fist as the hand is drawn (Cinematic Interactions' springs): it comes up with the hand
 * as the scene begins and goes down with it as it ends, and the lattice eases out and in as the work moves;</li>
 * <li>if the grip leaves the hand while the scene needed it (the work out of a bare arm's reach), the scene starts again
 * without it: the player walks up if there is a way, or a card says why not ({@link #gripLost}).</li>
 * </ul>
 * Without a grip, everything is the scene's own.
 */
public abstract class CineStation<M extends AbstractContainerMenu> extends AddonMenuStation<M> {
    /** The grip's claw this frame for the working hand (set where the scene sends it), at rest, and for both hands at once. */
    @Nullable
    private Vec3 tip, restTip, bothTip;
    /** Where the hand holding the grip was sent for each of them. */
    @Nullable
    private Vec3 workHold, restHold, bothHold;
    /** Which way the claw points at rest (a hook over an edge), or null: along the grip. */
    @Nullable
    private Vec3 restDir;
    private boolean tipRight = true;
    private long tipFrame = -10;
    /** The grip as drawn this frame, and the claw from the fist as it eases toward where it is sent. */
    @Nullable
    private Drawn drawn;
    private long drawnFrame = -1;
    @Nullable
    private Vec3 out;
    private boolean outRight;
    /** How fast the lattice eases out and in (per second). */
    private static final double EASE = 14;
    /** Whether the scene was started out of a bare arm's reach, thanks to the grip (decided once it is under way). */
    private boolean decided, fromAfar;

    protected CineStation(BlockPos pos, Direction side, Class<M> menuType, int inputs) {
        super(pos, side, menuType, inputs);
    }

    // ---- what each scene says --------------------------------------------------------------------------

    /** Where the player is walked to, without a grip (Cinematic Interactions' {@code approach}). */
    protected abstract List<Vec3> stand(LocalPlayer player);

    /** Whether the work can be done from these feet with bare hands (CI's {@code workable}). */
    protected boolean bareWorkable(Vec3 feet) {
        return super.workable(feet);
    }

    /** Where the working hand goes, for the cursor's target (CI's {@code handTarget}). */
    protected abstract Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap);

    /** Where the carried stack is drawn, by the hand (CI's {@code heldItemPoint}). */
    protected abstract Vec3 carry(Vec3 hand, Vec3 shoulder);

    /** Where a hand rests (CI's {@code rest}). */
    protected abstract Vec3 restAt(boolean right);

    /** Both hands placed by the scene itself (CI's {@code handsOverride}), or null. */
    @Nullable
    protected Vec3[] bothHands() {
        return null;
    }

    /** The scene's drawing in the world (CI's {@code renderWorld}). */
    protected abstract void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light);

    // ---- the grip --------------------------------------------------------------------------------------

    /**
     * Whether, with a grip in hand, the work can be done from these feet: the block within Create's own reach of the grip
     * (a scene may add its own rules).
     */
    protected boolean gripWorkable(Vec3 feet) {
        return Grip.reaches(feet, pos);
    }

    /** Whether the scene, under way from these feet with the grip in hand, needs it (put away, the scene starts again). */
    protected boolean needsGrip(Vec3 feet) {
        return !bareWorkable(feet);
    }

    /**
     * The grip held as a hook rather than a tool (the basin hung from by its rim): it is then on the resting hand, its
     * tip at {@link #hookPoint()}, and the working hand is bare.
     */
    protected boolean hooked() {
        return false;
    }

    /** Where a hooked grip holds on: the claw's palm. */
    protected Vec3 hookPoint() {
        return center();
    }

    /** Which way a hooked grip's claw points: over the edge it holds, away from the player and down. */
    protected Vec3 hookReach() {
        return forward().scale(0.55).add(0, -0.83, 0).normalize();
    }

    /** Where the hand holding a hooked grip is: below the hook on the player's side, the grip folded up to it. */
    protected Vec3 hookHold() {
        return hookPoint().subtract(forward().scale(0.18)).add(0, -0.36, 0);
    }

    /** The scene was started out of a bare arm's reach, and the grip has left the hand. */
    protected void gripLost() {
        Notices.about(pos, Component.translatable("cinecreate.notice.grip_gone"));
        BlockPos at = pos;
        // заново, без захвата: CI сам проложит путь (или покажет, почему не может)
        Scene.leave();
        Scene.begin(at, StationKind.ADDON);
    }

    /** Whether this scene is being worked out of a bare arm's reach, thanks to the grip. */
    public boolean fromAfar() {
        return fromAfar;
    }

    @Override
    public final List<Vec3> approach(LocalPlayer player) {
        // с захватом — оттуда, где стоит, если он дотягивается
        if (Grip.inHand() && gripWorkable(player.position())) {
            return List.of();
        }
        return stand(player);
    }

    @Override
    public final boolean workable(Vec3 feet) {
        return bareWorkable(feet) || Grip.inHand() && gripWorkable(feet);
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = Minecraft.getInstance().player;
        if (Scene.phase != Scene.Phase.ACTIVE || player == null) {
            return;
        }
        if (!decided) {
            decided = true;
            fromAfar = Grip.inHand() && needsGrip(player.position());
        }
        if (fromAfar && !Grip.inHand()) {
            fromAfar = false;
            gripLost();
        }
    }

    @Override
    public final Vec3 handTarget(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        Vec3 want = reach(target, shoulder, carrying, tap);
        if (!Grip.inHand() || hooked()) {
            tip = null;
            return want;
        }
        tip = want;
        tipRight = rightOf(shoulder);
        tipFrame = FrameCounter.frame();
        workHold = Grip.holdPoint(shoulder, want);
        return workHold;
    }

    /** What the working hand carries is in the claw, when that hand holds the grip: the scene carries it there. */
    @Override
    public final Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) {
        Drawn grip = drawn();
        return carry(grip != null && grip.carries() ? grip.claw() : hand, shoulder);
    }

    @Override
    public final Vec3 rest(boolean right) {
        Vec3 want = restAt(right);
        CameraRig.Pose camera = Grip.camera();
        if (!Grip.inHand() || camera == null) {
            return want;
        }
        Vec3 shoulder = Grip.shoulder(right, camera.pos(), camera.yaw());
        // крюк — в левой (свободной от работы) руке; инструмент — в той, что работает
        boolean holdsGrip = hooked() ? !right : right == tipRight;
        if (holdsGrip) {
            if (hooked()) {
                // крюк: кулак ниже края, клешня перехватила край
                restTip = hookPoint();
                restDir = hookReach();
                restHold = hookHold();
            } else if (Grip.folds(shoulder, want)) {
                // место под рукой: кулак там же, где был бы голый, сложенный захват — вперёд
                restTip = want.add(Grip.forward(camera.yaw()).scale(GripArm.FOLDED));
                restDir = null;
                restHold = want;
            } else {
                restTip = want;
                restDir = null;
                restHold = Grip.holdPoint(shoulder, want);
            }
            return restHold;
        }
        // свободная рука: если до её места не дотянуться, она остаётся у тела
        return want.distanceTo(shoulder) > Grip.BARE - 0.6 && fromAfarNow() ? Grip.bodyRest(right, camera) : want;
    }

    @Nullable
    @Override
    public final Vec3[] handsOverride() {
        Vec3[] both = bothHands();
        CameraRig.Pose camera = Grip.camera();
        if (both == null || !Grip.inHand() || camera == null) {
            bothTip = null;
            return both;
        }
        // обе руки на рукояти захвата, его клешня — там, где была бы правая
        bothTip = both[0];
        Vec3 right = Grip.holdPoint(Grip.shoulder(true, camera.pos(), camera.yaw()), both[0]);
        bothHold = right;
        Vec3 along = both[0].subtract(right).normalize();
        Vec3 left = right.subtract(along.scale(0.14)).add(Grip.forward(camera.yaw()).cross(new Vec3(0, 1, 0)).scale(0.04));
        return new Vec3[] {right, left};
    }

    @Override
    public final void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        drawWorld(pose, buffers, camera, light);
    }

    /**
     * The grip, with the arms: CI draws them in every phase, the scene's world only while it is under way. So the grip
     * comes up with the hand holding it and goes down with it, never popping in at the end of the arm.
     */
    @Override
    public final void renderBody(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        super.renderBody(pose, buffers, camera, light);
        Drawn grip = drawn();
        if (grip == null) {
            return;
        }
        CameraRig.Pose at = Grip.camera();
        GripArm.draw(pose, buffers, camera, grip.hand(), grip.claw(), grip.clawDir(), grip.pose(),
                at != null ? Grip.forward(at.yaw()) : forward(), light);
    }

    /**
     * The grip as drawn: the fist holding it (where Cinematic Interactions draws that hand), its claw, the claw's own
     * direction (null: along the grip), its pose, and whether what the hand carries is in the claw.
     */
    private record Drawn(Vec3 hand, Vec3 claw, @Nullable Vec3 clawDir, GripArm.Claw pose, boolean carries) {}

    /** The grip this frame (worked out once a frame), or null when none is drawn. */
    @Nullable
    private Drawn drawn() {
        long frame = FrameCounter.frame();
        if (drawnFrame == frame) {
            return drawn;
        }
        drawnFrame = frame;
        drawn = null;
        if (!Grip.inHand() || Scene.phase == Scene.Phase.NONE) {
            out = null;
            return null;
        }
        Vec3 hold, claw, dir = null;
        boolean right, carries = false;
        GripArm.Claw pose;
        boolean carrying = Hands.itemInHand();
        if (bothTip != null && bothHold != null) {
            hold = bothHold;
            claw = bothTip;
            right = true;
            pose = GripArm.Claw.HOLDING;
        } else if (working() && tip != null && workHold != null && !hooked()) {
            hold = workHold;
            claw = tip;
            right = tipRight;
            pose = carrying ? GripArm.Claw.HOLDING : GripArm.Claw.POINTING;
            carries = true;
        } else if (restTip != null && restHold != null) {
            hold = restHold;
            claw = restTip;
            dir = restDir;
            right = hooked() != tipRight;
            pose = hooked() || carrying ? GripArm.Claw.HOLDING : GripArm.Claw.PUNCHING;
            carries = !hooked();
        } else {
            return null;
        }
        Vec3 want = claw.subtract(hold);
        if (dir == null && want.length() < GripArm.FOLDED) {
            // сложен до упора: клешня не ближе к кулаку
            Vec3 way = want.lengthSqr() < 1e-8 ? forward() : want.normalize();
            want = way.scale(GripArm.FOLDED);
        }
        // клешня — от кулака, как его рисует CI: поднимается и опускается вместе с рукой, решётка выдвигается плавно
        out = out == null || outRight != right ? want : out.lerp(want, 1 - Math.exp(-EASE * Scene.frameDt));
        if (dir == null && out.length() < GripArm.FOLDED) {
            // поворачиваясь, решётка не срезает угол: не короче сложенной
            out = out.lengthSqr() < 1e-8 ? want : out.normalize().scale(GripArm.FOLDED);
        }
        outRight = right;
        Vec3 hand = Grip.hand(right);
        if (hand == null) {
            hand = hold;
        }
        drawn = new Drawn(hand, hand.add(out), dir, pose, carries);
        return drawn;
    }

    /** Development: how far the grip's claw is drawn from the fist holding it this frame, or -1 when no grip is drawn. */
    public double gripLength() {
        Drawn grip = drawn();
        return grip == null ? -1 : grip.claw().distanceTo(grip.hand());
    }

    /** Development: the fist holding the grip and its claw as drawn this frame, or null when no grip is drawn. */
    @Nullable
    public Vec3[] gripDrawn() {
        Drawn grip = drawn();
        return grip == null ? null : new Vec3[] {grip.hand(), grip.claw()};
    }

    /** The working hand was sent somewhere this frame or the last (the cursor points at the work). */
    private boolean working() {
        return aim != null && tipFrame >= FrameCounter.frame() - 1;
    }

    private boolean rightOf(Vec3 shoulder) {
        CameraRig.Pose camera = Grip.camera();
        return camera == null || shoulder.subtract(camera.pos()).dot(right()) >= 0;
    }

    /** Out of a bare arm's reach of the work, as things stand now (before the scene's decision is made, too). */
    private boolean fromAfarNow() {
        LocalPlayer player = Minecraft.getInstance().player;
        return fromAfar || player != null && !decided && !bareWorkable(player.position());
    }
}
