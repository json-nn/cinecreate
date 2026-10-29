package org.minechestplate.cinecreate.station;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.minechestplate.cinecreate.bridge.BridgeMenu;

/**
 * A Create block that gets a Cinematic Interactions scene.
 *
 * @param sides  horizontal sides the player may work from
 * @param usedOn faces whose right-click starts the scene; any other face keeps Create's use (and block placing)
 * @param menu   opens the bridge menu (null when the block is not ready)
 * @param use    Create's own right-click, for when the scene falls back to it
 * @param kindAt which kind plays at a position; null where the block gets no scene at all (a crafter group too big to
 *               frame): there every click stays Create's
 * @param bypass held items whose click stays Create's, besides the {@code scene_bypass} tag (a dye recolours a valve)
 */
public record SceneBlock(ResourceLocation sceneId, ResourceLocation blockId, Class<? extends BridgeMenu> menuClass,
                         Function<BlockState, List<Direction>> sides, BiPredicate<BlockState, Direction> usedOn,
                         MenuFactory menu, CreateUse use, List<SceneKind> kinds, KindFactory kindAt,
                         Predicate<ItemStack> bypass) {

    @FunctionalInterface
    public interface MenuFactory {
        @Nullable
        MenuProvider create(Level level, BlockPos pos);
    }

    @FunctionalInterface
    public interface CreateUse {
        void use(ServerPlayer player, BlockState state, BlockPos pos);
    }

    @FunctionalInterface
    public interface KindFactory {
        @Nullable
        SceneKind at(BlockGetter level, BlockPos pos);
    }
}
