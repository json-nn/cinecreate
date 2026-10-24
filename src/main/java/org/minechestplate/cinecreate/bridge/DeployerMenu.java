package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * A Deployer, for the hand-off: no item slots. Button {@link #SWAP} is Create's own click on the deployer's hand, done
 * with what the scene's hand carries: the deployer takes it, and the hand gets back what the deployer held.
 */
public class DeployerMenu extends BridgeMenu {
    public static final int SWAP = 0;

    private DeployerMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.DEPLOYER.get(), id, inventory, pos, 0, 0);
        addPlayerInventory(inventory, 8, 84);
    }

    public static DeployerMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new DeployerMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DeployerBlockEntity)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new DeployerMenu(id, inventory, pos),
                Component.translatable("cinecreate.menu.deployer"));
    }

    /** Server: Create's click on the deployer's front (its hand), with the carried item; it swaps them. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        BlockState state = player.level().getBlockState(pos);
        if (id != SWAP || !(player instanceof ServerPlayer server) || !(player.level().getBlockEntity(pos) instanceof DeployerBlockEntity)
                || !state.hasProperty(DeployerBlock.FACING) || !player.mayBuild()) {
            return false;
        }
        return Bridge.useWithCarried(server, this, pos, state.getValue(DeployerBlock.FACING))
                != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
