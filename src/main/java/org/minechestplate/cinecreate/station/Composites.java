package org.minechestplate.cinecreate.station;

import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Machines that work one of our blocks and turn its scene into theirs (DESIGN §6, "Composite"): the Mechanical Press
 * over a depot or a basin, the Mechanical Mixer over a basin, the Spout and a downward Deployer over a depot, and an
 * Encased Fan whose stream crosses a depot.
 */
public final class Composites {
    private Composites() {}

    /** The press, mixer, spout and deployer stand two blocks up from what they work; the head reaches down through the block between. */
    public static final int PRESS_HEIGHT = 2;
    /** How far a fan's stream is looked for (Create's default reach is 20 blocks). */
    private static final int FAN_REACH = 24;

    @Nullable
    public static MechanicalPressBlockEntity pressOver(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos.above(PRESS_HEIGHT)) instanceof MechanicalPressBlockEntity press ? press : null;
    }

    @Nullable
    public static MechanicalMixerBlockEntity mixerOver(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos.above(PRESS_HEIGHT)) instanceof MechanicalMixerBlockEntity mixer ? mixer : null;
    }

    @Nullable
    public static SpoutBlockEntity spoutOver(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos.above(PRESS_HEIGHT)) instanceof SpoutBlockEntity spout ? spout : null;
    }

    @Nullable
    public static DeployerBlockEntity deployerOver(BlockGetter level, BlockPos pos) {
        BlockPos at = pos.above(PRESS_HEIGHT);
        return level.getBlockEntity(at) instanceof DeployerBlockEntity deployer && facesDown(level.getBlockState(at)) ? deployer : null;
    }

    private static boolean facesDown(BlockState state) {
        return state.hasProperty(DeployerBlock.FACING) && state.getValue(DeployerBlock.FACING) == Direction.DOWN;
    }

    /**
     * A fan's stream over this block and what it does there, found as Create's {@code AirCurrent.findAffectedHandlers}
     * finds it: to the current's length rounded up (a depot stops the air at its face and is still worked), and a
     * horizontal current works the block under it too. The type is the block's own catalyst, or the current's one block
     * short of it.
     */
    public record FanStream(BlockPos fan, Direction from, @Nullable FanProcessingType type) {}

    @Nullable
    public static FanStream fanStream(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            for (int below = 0; below <= (direction.getAxis().isHorizontal() ? 1 : 0); below++) {
                // below = 1: the current runs one block higher, over this block
                BlockPos from = pos.above(below);
                for (int distance = 1; distance <= FAN_REACH; distance++) {
                    BlockPos at = from.relative(direction, distance);
                    if (!(level.getBlockEntity(at) instanceof EncasedFanBlockEntity fan)) {
                        continue;
                    }
                    AirCurrent current = fan.getAirCurrent();
                    if (current != null && current.direction == direction.getOpposite() && distance <= limit(current)) {
                        FanProcessingType own = level instanceof Level world ? FanProcessingType.getAt(world, pos) : null;
                        return new FanStream(at, direction, own != null ? own : current.getTypeAt(distance - 1));
                    }
                    break;
                }
            }
        }
        return null;
    }

    /** How many blocks the current works: its length, rounded up (Create's {@code AirCurrent.getLimit}). */
    private static int limit(AirCurrent current) {
        return (int) Math.ceil(current.maxDistance);
    }

    /**
     * For a click on a machine: the block of ours it works (a press: the depot or basin under it; a mixer: the basin; a
     * spout or a downward deployer: the depot), or null.
     */
    @Nullable
    public static BlockPos workedBy(BlockGetter level, BlockPos machine) {
        boolean press = level.getBlockEntity(machine) instanceof MechanicalPressBlockEntity;
        boolean mixer = level.getBlockEntity(machine) instanceof MechanicalMixerBlockEntity;
        boolean onDepot = level.getBlockEntity(machine) instanceof SpoutBlockEntity
                || level.getBlockEntity(machine) instanceof DeployerBlockEntity && facesDown(level.getBlockState(machine));
        if (!press && !mixer && !onDepot) {
            return null;
        }
        BlockPos below = machine.below(PRESS_HEIGHT);
        SceneBlock scene = SceneBlocks.of(level.getBlockState(below));
        return scene == SceneBlocks.BASIN && (press || mixer) || scene == SceneBlocks.DEPOT && (press || onDepot) ? below : null;
    }
}
