package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.AllBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * A Blaze Burner: no item slots. What the hand carries goes in by Create's own click ({@link #FEED} plus the side the
 * player stands at): fuel kindles the blaze, a blaze cake makes it seethe, flint and steel lights an empty burner, and
 * what is left (a lava bucket's bucket) comes back to the hand, as Create's click leaves it.
 */
public class BurnerMenu extends BridgeMenu {
    /** Button {@code FEED + side}: Create's click on that side of the burner with what the hand carries. */
    public static final int FEED = 0;

    private BurnerMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.BURNER.get(), id, inventory, pos, 0, 0);
        addPlayerInventory(inventory, 8, 84);
    }

    public static BurnerMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new BurnerMenu(id, inventory, buf.readBlockPos());
    }

    /** Also an empty burner (no blaze yet, and no block entity): flint and steel lights it. */
    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!AllBlocks.BLAZE_BURNER.has(level.getBlockState(pos))) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new BurnerMenu(id, inventory, pos),
                Component.translatable("cinecreate.menu.blaze_burner"));
    }

    /** Server: Create's click with the carried item, on the side the player works from. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int side = id - FEED;
        if (side < 0 || side >= Direction.values().length || getCarried().isEmpty() || !(player instanceof ServerPlayer server)
                || !AllBlocks.BLAZE_BURNER.has(player.level().getBlockState(pos)) || !player.mayBuild()) {
            return false;
        }
        return Bridge.useWithCarried(server, this, pos, Direction.from3DDataValue(side))
                != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
