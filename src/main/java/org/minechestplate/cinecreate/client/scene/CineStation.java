package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.Hands;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.station.StationKind;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.CiNotice;
import org.minechestplate.cinecreate.client.layout.LayoutPanel;
import org.minechestplate.cinecreate.client.layout.LayoutScene;
import org.minechestplate.cinecreate.client.layout.Layouts;

/**
 * The base of our scenes: each says where its hands go, where the player stands and what it draws, and this adds
 * Create's Extendo Grip to all of them (DESIGN §8.5): the hand holding it stays by the body and its claw goes where the
 * scene sends the hand.
 */
public abstract class CineStation<M extends AbstractContainerMenu> extends AddonMenuStation<M> implements LayoutScene {
    /** The grip's claw this frame for the working hand, at rest, and for both hands at once. */
    @Nullable
    private Vec3 tip, restTip, bothTip;
    /** Where the hand holding the grip was sent for each of them. */
    @Nullable
    private Vec3 workHold, restHold, bothHold;
    /** Null: the claw points along the grip. */
    @Nullable
    private Vec3 restDir;
    private boolean tipRight = true;
    private long tipFrame = -10;
    /** The grip this frame; out: the claw from the fist, easing toward where it is sent. */
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
    /** Where CI would walk the player, as last asked (for the developer view). */
    private List<Vec3> standSpots = List.of();

    protected CineStation(BlockPos pos, Direction side, Class<M> menuType, int inputs) {
        super(pos, side, menuType, inputs);
    }

    // ---- what each scene says --------------------------------------------------------------------------

    /** CI's approach, without a grip. */
    protected abstract List<Vec3> stand(LocalPlayer player);

    /** CI's workable, with bare hands. */
    protected boolean bareWorkable(Vec3 feet) {
        return super.workable(feet);
    }

    /** CI's handTarget. */
    protected abstract Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap);

    /** CI's heldItemPoint. */
    protected abstract Vec3 carry(Vec3 hand, Vec3 shoulder);

    /** CI's rest. */
    protected abstract Vec3 restAt(boolean right);

    /** CI's handsOverride. */
    @Nullable
    protected Vec3[] bothHands() {
        return null;
    }

    /** CI's renderWorld. */
    protected abstract void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light);

    // ---- the grip --------------------------------------------------------------------------------------

    /** With a grip in hand: the block within Create's reach of it (a scene may add its own rules). */
    protected boolean gripWorkable(Vec3 feet) {
        return Grip.reaches(feet, pos);
    }

    /** Whether the scene, under way from these feet with the grip in hand, needs it (put away, the scene starts again). */
    protected boolean needsGrip(Vec3 feet) {
        return !bareWorkable(feet);
    }

    /** The grip held as a hook (the basin's rim): on the resting hand, its claw at hookPoint; the working hand is bare. */
    protected boolean hooked() {
        return false;
    }

    /** Where a hooked grip holds on: the claw's palm. */
    protected Vec3 hookPoint() {
        return center();
    }

    /** Which way a hooked grip's claw points: over the edge, away from the player and down. */
    protected Vec3 hookReach() {
        return forward().scale(0.55).add(0, -0.83, 0).normalize();
    }

    /** The fist holding a hooked grip: below the hook on the player's side. */
    protected Vec3 hookHold() {
        return hookPoint().subtract(forward().scale(0.18)).add(0, -0.36, 0);
    }

    /** The scene was started out of a bare arm's reach, and the grip has left the hand. */
    protected void gripLost() {
        CiNotice.show(Component.translatable("cinecreate.notice.grip_gone"));
        BlockPos at = pos;
        // заново, без захвата: CI сам проложит путь (или покажет, почему не может)
        Scene.leave();
        Scene.begin(at, StationKind.ADDON);
    }

    public boolean fromAfar() {
        return fromAfar;
    }

    /**
     * A seated player cannot be walked up (CI's walk would make no headway and give up): the work is done from the seat
     * when it can be, else CI refuses and the card says to get up first.
     */
    @Override
    public final List<Vec3> approach(LocalPlayer player) {
        if (stuck(player) != null || Grip.inHand() && gripWorkable(player.position())) {
            return List.of();
        }
        standSpots = stand(player);
        return standSpots;
    }

    /**
     * Only where the player is when they cannot or need not walk (seated, or with a grip that reaches from there): CI
     * walks whenever its view check from here fails, even when the work could be done here.
     */
    @Override
    public final boolean workable(Vec3 feet) {
        LocalPlayer player = Minecraft.getInstance().player;
        String stuck = player == null ? null : stuck(player);
        if (player != null && Math.hypot(feet.x - player.getX(), feet.z - player.getZ()) > 0.1
                && (stuck != null || Grip.inHand() && gripWorkable(player.position()))) {
            return false;
        }
        boolean workable = bareWorkable(feet) || Grip.inHand() && gripWorkable(feet);
        if (!workable && stuck != null) {
            CiNotice.why(pos, Component.translatable("cinecreate.notice." + stuck));
        }
        return workable;
    }

    /** Why CI cannot walk this player anywhere (seated or flying), or null. */
    @Nullable
    private static String stuck(LocalPlayer player) {
        return player.isPassenger() ? "seated" : player.getAbilities().flying ? "flying" : null;
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
        Vec3 want = notReady() != null ? restAt(rightOf(shoulder)) : reach(target, shoulder, carrying, tap);
        want = want.add(right().scale(denyShake()));
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

    /** When the working hand holds the grip, what it carries is in the claw. */
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
        DevView.drawWorld(this, aim, standSpots, pose, buffers, camera);
        LayoutPanel.renderWorld(this, pose, buffers, camera);
        if (notReady() != null) {
            float pulse = (float) (0.55 + 0.25 * Math.sin(Scene.clock() * 6));
            EnchantFx.billboard(pose, buffers, Minecraft.getInstance().gameRenderer.getMainCamera(), busyAt(), 0.13, Scene.clock() * 0.6,
                    EnchantFx.CIRCLE, pulse, pulse * 0.22f, pulse * 0.18f);
        }
    }

    // ---- where the hands work (developer mode's editor); a scene with none has no editor ----

    @Nullable
    private String layoutId;

    @Override
    public String layoutId() {
        if (layoutId == null) {
            layoutId = getClass().getSimpleName().replace("Scene", "").toLowerCase(Locale.ROOT);
        }
        return layoutId;
    }

    @Override
    public Component layoutTitle() {
        return Component.translatable("cinecreate.layout." + layoutId());
    }

    @Override
    public List<Component> spotNames() {
        return List.of();
    }

    @Override
    public Vec3 spotBase(int spot) {
        return center();
    }

    @Override
    public Vec3 layoutAcross() {
        return new Vec3(right().x, 0, right().z).normalize();
    }

    @Override
    public Vec3 layoutTowards() {
        return new Vec3(side().x, 0, side().z).normalize();
    }

    @Override
    public final Component hint() {
        if (LayoutPanel.isOpen()) {
            return LayoutPanel.hint();
        }
        Component busy = notReady();
        return busy != null ? busy.copy().withStyle(ChatFormatting.GOLD) : sceneHint();
    }

    /**
     * Why the scene's zone cannot be worked right now (the machine is mid-move: a raised plate, an arm out), or null.
     * While busy, a click is refused, the hand stays back, the glow turns red and a ring marks the zone.
     */
    @Nullable
    protected Component notReady() {
        return null;
    }

    @Override
    protected final void act(int button, boolean shift) {
        if (notReady() != null) {
            deny();
            return;
        }
        sceneAct(button, shift);
    }

    protected abstract void sceneAct(int button, boolean shift);

    /** Where the busy ring is drawn: the first hand spot, or the work. */
    protected Vec3 busyAt() {
        return spotNames().isEmpty() ? center() : spotBase(0);
    }

    protected abstract Component sceneHint();

    protected final Vec3 spot(int spot) {
        return Layouts.point(this, spot);
    }

    /** How far the editor moved the hand's spot from the scene's own. */
    protected final Vec3 handShift(int spot) {
        return Layouts.point(this, spot).subtract(spotBase(spot));
    }

    /** Whether a point lies in a square of this half-size about a centre, on the spot's surface. */
    protected final boolean inSpot(int spot, Vec3 at, Vec3 centre, double half) {
        Vec3[] axes = Layouts.axes(this, spot);
        Vec3 rel = at.subtract(centre);
        return Math.abs(rel.dot(axes[0])) <= half && Math.abs(rel.dot(axes[1])) <= half;
    }

    /** The scene's own state, as lines for the developer view. */
    public void devLines(List<String> lines) {}

    /**
     * The grip is drawn with the arms: CI draws them in every phase but the scene's world only while it is under way,
     * so the grip comes up and goes down with its hand instead of popping in.
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

    /** The fist (where CI draws that hand), the claw, its direction (null: along the grip), and whether it carries. */
    private record Drawn(Vec3 hand, Vec3 claw, @Nullable Vec3 clawDir, GripArm.Claw pose, boolean carries) {}

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

    /** Whether the cursor is within this radius (in the world, at least 9 GUI pixels) of the point on the screen. */
    protected static boolean underCursor(double[] mouse, Vec3 center, double radius) {
        double[] at = Picking.toGui(center);
        double[] edge = Picking.toGui(center.add(0, radius, 0));
        if (at == null || edge == null) {
            return false;
        }
        DevView.target(center, radius);
        double r = Math.max(9, Math.hypot(at[0] - edge[0], at[1] - edge[1]));
        return Math.hypot(mouse[0] - at[0], mouse[1] - at[1]) < r;
    }

    /** Development (SmokeTest): the claw's distance from the fist this frame, or -1 when no grip is drawn. */
    public double gripLength() {
        Drawn grip = drawn();
        return grip == null ? -1 : grip.claw().distanceTo(grip.hand());
    }

    /** Development (SmokeTest): the fist and the claw as drawn this frame. */
    @Nullable
    public Vec3[] gripDrawn() {
        Drawn grip = drawn();
        return grip == null ? null : new Vec3[] {grip.hand(), grip.claw()};
    }

    /** The working hand was sent somewhere this frame or the last. */
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
