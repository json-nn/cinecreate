package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * An Item Drain: slot 0 is what lies on its grate. It goes in through the drain's own side input from the side away
 * from the player, as a belt or a funnel would put it: Create rolls it to the middle, drains it, and rolls it on off the
 * edge toward the player. No button: Create's own click on a drain only empties into it, and its tank feeds pipes, not
 * a bucket.
 */
public class DrainMenu extends BridgeMenu {
    public static final int MAIN = 0;

    /** Server: the slot over the drain, fed from the side away from the player. */
    private DrainMenu(int id, Inventory inventory, BlockPos pos, ItemDrainBlockEntity drain) {
        super(CineMenus.DRAIN.get(), id, inventory, pos, 1, 1);
        Level level = inventory.player.level();
        Direction far = Bridge.facing(inventory.player);
        addSlot(new GrateSlot(drain, level.getCapability(Capabilities.ItemHandler.BLOCK, pos, far)));
        addPlayerInventory(inventory, 8, 84);
    }

    private DrainMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.DRAIN.get(), id, inventory, pos, 1, 1);
        Level level = inventory.player.level();
        addSlot(new Slot(new SimpleContainer(1), 0, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain && drain.getHeldItemStack().isEmpty();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return oneAtATime(level, stack) ? 1 : stack.getMaxStackSize();
            }
        });
        addPlayerInventory(inventory, 8, 84);
    }

    public static DrainMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new DrainMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new DrainMenu(id, inventory, pos, drain),
                Component.translatable("cinecreate.menu.item_drain"));
    }

    /** The drain takes a filled container one at a time (it drains each before the next). */
    static boolean oneAtATime(Level level, ItemStack stack) {
        return GenericItemEmptying.canItemBeEmptied(level, stack);
    }

    private static final class GrateSlot extends Slot {
        private final ItemDrainBlockEntity drain;
        @Nullable
        private final IItemHandler input;

        GrateSlot(ItemDrainBlockEntity drain, @Nullable IItemHandler input) {
            super(new SimpleContainer(1), 0, 80, 20);
            this.drain = drain;
            this.input = input;
        }

        @Override
        public ItemStack getItem() {
            return drain.getHeldItemStack();
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return input != null && input.insertItem(0, stack, true).getCount() < stack.getCount();
        }

        @Override
        public void set(ItemStack stack) {
            if (input == null) {
                return;
            }
            if (stack.isEmpty()) {
                input.extractItem(0, Integer.MAX_VALUE, false);
            } else {
                input.insertItem(0, stack, false);
            }
        }

        @Override
        public ItemStack remove(int amount) {
            return input == null ? ItemStack.EMPTY : input.extractItem(0, amount, false);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return oneAtATime(drain.getLevel(), stack) ? 1 : stack.getMaxStackSize();
        }
    }
}
