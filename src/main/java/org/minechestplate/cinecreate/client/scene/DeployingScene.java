package org.minechestplate.cinecreate.client.scene;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Scene;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.station.Composites;

/**
 * Application: a Deployer facing down onto the depot (DESIGN §7.10). The work piece goes on the plate first; when the
 * deployer punches down, the scene answers like the press, smaller: a jolt and a spray of sparks off the work. When what
 * lies there changes (an item applied, a step of a sequenced assembly), it glows for a moment.
 */
public class DeployingScene extends DepotScene {
    /** The punch: camera jolt and how fast it dies away; the hand's reach past which a peak counts as a punch. */
    static final double KICK = 0.25, KICK_DECAY = 13;
    static final float PUNCH_REACH = 0.5f;

    /** Development: punches seen, and changes of the work seen. */
    public int punches, applied;

    private float lastOffset;
    private boolean extending;
    private double kickAt = -10;
    private ItemStack lastLying = ItemStack.EMPTY;

    public DeployingScene(BlockPos pos, Direction side) {
        super(pos, side);
    }

    @Override
    public CameraRig.Pose lean() {
        return machineLean();
    }

    @Override
    public double leanFov() {
        return 66;
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

    /**
     * Once a frame: Create's hand as drawn this frame. It reaches down and draws back; the frame it turns back from
     * far enough down is the punch.
     */
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
        kickAt = Scene.clock();
        Vec3 at = top.add(0, 0.12, 0);
        Fx.hotSparks(at, new Vec3(0, 1, 0), 0.7, 12, 1.2);
    }

    /** Client tick: the work changed under the deployer. */
    @Override
    public void tick() {
        super.tick();
        ItemStack now = lying();
        if (!lastLying.isEmpty() && !now.isEmpty() && !ItemStack.isSameItem(lastLying, now)) {
            applied++;
            Fx.motes(spot.add(0, 0.08, 0), 0.12, 12, 1f, 0.95f, 0.78f, 0.2);
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
