package org.minechestplate.cinecreate.client.scene;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.lang.reflect.Field;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.station.Composites;

/**
 * Application: a Deployer facing down onto the depot (DESIGN §7.10). Each punch is answered like a smaller press, and a
 * change of the work glows. The deployer's own hand is a target too: reaching up to it swaps what the hand carries with
 * what the deployer holds (Create's click on its hand), so what it applies is changed without leaving.
 */
public class DeployingScene extends DepotScene {
    /** The punch: camera jolt and how fast it dies away; the hand's reach past which a peak counts as a punch. */
    static final double KICK = 0.25, KICK_DECAY = 13;
    static final float PUNCH_REACH = 0.5f;
    /** Handing the deployer something: seconds, the beat (the swap); how far below the deployer's middle its palm is at rest. */
    static final double HAND_OFF = 0.6, HAND_OFF_BEAT = 0.3, PALM = 0.55;

    /** Development (SmokeTest): the deployer's hand counts as pointed at wherever the cursor is. */
    public static boolean smokeHand;
    /** Development (SmokeTest): punches, changes of the work, swaps with the deployer's hand. */
    public int punches, applied, swaps;
    private boolean punchedSinceChange;

    private float lastOffset;
    private boolean extending;
    private double kickAt = -10;
    private ItemStack lastLying = ItemStack.EMPTY;
    /** The deployer's hand under the cursor, and whether the move under way reaches up to it. */
    private boolean onHand, handing;

    public DeployingScene(BlockPos pos, Direction side) {
        super(pos, side);
    }

    /** Back a little more than the press's pose and looking higher: the deployer's hand hangs clear of the drawer. */
    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = top.add(side().scale(1.25)).add(0, 0.5, 0);
        Vec3 look = top.add(0, 0.62, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, -1.5f);
    }

    @Override
    public double leanFov() {
        return 70;
    }

    @Nullable
    private Vec3 palm() {
        DeployerBlockEntity deployer = deployer();
        if (deployer == null) {
            return null;
        }
        float offset = deployer.getHandOffset(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        return Vec3.atCenterOf(deployer.getBlockPos()).add(0, -(PALM + offset), 0);
    }

    /** The deployer's hand wins over the plate: it hangs over it. */
    @Override
    public void updateCursor(boolean overPanel) {
        if (moving()) {
            return;
        }
        onHand = false;
        if (smokeHand) {
            hoverHand();
            return;
        }
        super.updateCursor(overPanel);
        Vec3 palm = palm();
        if (overPanel || smokePin || palm == null) {
            return;
        }
        if (underCursor(Picking.mouseGui(), palm, 0.2)) {
            hoverHand();
        }
    }

    /** Points at the deployer's hand (SmokeTest calls it too). */
    public void hoverHand() {
        Vec3 palm = palm();
        if (palm == null) {
            return;
        }
        hovered = false;
        onHand = true;
        aim = palm.add(side().scale(0.14)).add(0, -0.06, 0);
    }

    @Override
    public void clearCursor() {
        if (!moving() && !smokeHand) {
            onHand = false;
        }
        super.clearCursor();
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        if (onHand || handing && moveTime() >= 0) {
            // ладонь к руке активатора: подаёт и забирает
            double t = moveTime();
            double in = t < 0 ? 0 : Anim.hump(Anim.window(t, 0, HAND_OFF));
            return target.add(side().scale(-0.1 * in)).add(0, -0.02 * tap, 0);
        }
        return super.reach(target, shoulder, carrying, tap);
    }

    @Override
    protected void act(int button, boolean shift) {
        if (!onHand) {
            handing = false;
            super.act(button, shift);
            return;
        }
        DepotMenu menu = menu();
        ItemStack held = held();
        if (menu == null || menu.getCarried().isEmpty() && held != null && held.isEmpty()) {
            deny();
            return;
        }
        handing = true;
        startMove(HAND_OFF, HAND_OFF_BEAT, () -> {
            Minecraft mc = Minecraft.getInstance();
            DepotMenu open = menu();
            if (open != null && mc.gameMode != null) {
                mc.gameMode.handleInventoryButtonClick(open.containerId, DepotMenu.DEPLOYER_SWAP);
                swaps++;
            }
        });
    }

    @Nullable
    @Override
    public List<Component> tooltip(ItemStack carried) {
        if (!onHand) {
            return super.tooltip(carried);
        }
        ItemStack held = held();
        if (held == null) {
            return null;
        }
        return List.of(held.isEmpty() ? Component.translatable("cinecreate.deploying.empty")
                : Component.translatable("cinecreate.deploying.holds", held.getHoverName()));
    }

    private static Field heldItem;
    private static boolean heldItemMissing;

    /** What the deployer holds, as Create syncs it for drawing (a protected field: read by reflection), or null if unreadable. */
    @Nullable
    private ItemStack held() {
        DeployerBlockEntity deployer = deployer();
        if (deployer == null || heldItemMissing) {
            return null;
        }
        try {
            if (heldItem == null) {
                heldItem = DeployerBlockEntity.class.getDeclaredField("heldItem");
                heldItem.setAccessible(true);
            }
            return (ItemStack) heldItem.get(deployer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            heldItemMissing = true;
            CineCreate.LOGGER.warn("[cinecreate] the Deployer's held item is not readable; its tooltip is left out", e);
            return null;
        }
    }

    @Override
    public float bodyPitch() {
        return 30f;
    }

    @Nullable
    private DeployerBlockEntity deployer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : Composites.deployerOver(mc.level, pos);
    }

    /** Once a frame, from Create's drawn hand: the frame it turns back from far enough down is the punch. */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        DeployerBlockEntity deployer = deployer();
        float offset = deployer == null ? 0 : deployer.getHandOffset(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        if (offset > lastOffset + 1e-4f) {
            extending = true;
        } else if (offset < lastOffset - 1e-4f && extending) {
            extending = false;
            if (lastOffset > PUNCH_REACH) {
                punch();
            }
        }
        lastOffset = offset;
    }

    private void punch() {
        punches++;
        punchedSinceChange = true;
        kickAt = Scene.clock();
        Vec3 at = top.add(0, 0.12, 0);
        Fx.hotSparks(at, new Vec3(0, 1, 0), 0.7, 12, 1.2);
    }

    @Override
    public void tick() {
        super.tick();
        ItemStack now = lying();
        if (!lastLying.isEmpty() && !now.isEmpty() && !ItemStack.isSameItem(lastLying, now)) {
            // при редких кадрах удар целиком проходит между ними: тогда он виден по изменившейся работе
            if (!punchedSinceChange) {
                punch();
            }
            punchedSinceChange = false;
            applied++;
            Fx.motes(plate().add(0, 0.08, 0), 0.12, 12, 1f, 0.95f, 0.78f, 0.2);
        }
        lastLying = now.copy();
    }

    @Override
    public double cameraKick() {
        return KICK * Math.exp(-(Scene.clock() - kickAt) * KICK_DECAY);
    }

    @Override
    public Component hint() {
        return deployer() == null ? super.hint() : Component.translatable("cinecreate.hint.deploying");
    }
}
