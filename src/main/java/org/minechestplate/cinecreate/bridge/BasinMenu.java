package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * A basin: slots 0-8 are what lies in it (Create's input inventory, which takes a new item only into its first free
 * slot and never the same item twice: exactly what an item thrown in gets), slots 9-17 what processing made (take
 * only). Fluids are not slots: button {@link #USE} is Create's own click with what the hand carries, so a bucket or a
 * bottle pours in or fills up by Create's rules, and an empty hand takes everything out into the inventory.
 */
public class BasinMenu extends BridgeMenu {
    public static final int INPUTS = 9, OUTPUTS = 9, SLOTS = INPUTS + OUTPUTS;
    /** Button: Create's click on the basin with what the hand carries (a fluid container, or nothing). */
    public static final int USE = 0;

    /** Server: slots over the basin itself. */
    private BasinMenu(int id, Inventory inventory, BlockPos pos, BasinBlockEntity basin) {
        super(CineMenus.BASIN.get(), id, inventory, pos, SLOTS, INPUTS);
        for (int i = 0; i < INPUTS; i++) {
            addSlot(new HandlerSlot(basin.getInputInventory(), i, x(i), 20, true));
        }
        for (int i = 0; i < OUTPUTS; i++) {
            addSlot(new HandlerSlot(basin.getOutputInventory(), i, x(i), 48, false));
        }
        addPlayerInventory(inventory, 8, 84);
    }

    /** Client: a plain copy the server keeps in sync; what goes in is guessed from the client's basin. */
    private BasinMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.BASIN.get(), id, inventory, pos, SLOTS, INPUTS);
        SimpleContainer copy = new SimpleContainer(SLOTS);
        Level level = inventory.player.level();
        for (int i = 0; i < SLOTS; i++) {
            int slot = i;
            addSlot(new Slot(copy, i, x(i % INPUTS), i < INPUTS ? 20 : 48) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return slot < INPUTS && level.getBlockEntity(pos) instanceof BasinBlockEntity basin
                            && takes(basin.getInputInventory(), slot, stack);
                }
            });
        }
        addPlayerInventory(inventory, 8, 84);
    }

    public static BasinMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new BasinMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new BasinMenu(id, inventory, pos, basin),
                Component.translatable("cinecreate.menu.basin"));
    }

    private static int x(int i) {
        return 8 + i * 18;
    }

    /** Whether the basin's inventory would take some of this into that slot (Create's insertion, tried without doing it). */
    static boolean takes(IItemHandlerModifiable inventory, int slot, ItemStack stack) {
        return !stack.isEmpty() && inventory.insertItem(slot, stack, true).getCount() < stack.getCount();
    }

    /** Server: Create's click on the basin's opening with what the hand carries (never with an item to drop in). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != USE || !(player instanceof ServerPlayer server) || !(player.level().getBlockEntity(pos) instanceof BasinBlockEntity)
                || !player.mayBuild()) {
            return false;
        }
        return Bridge.useWithCarried(server, this, pos, Direction.UP) != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Server: a slot of one of the basin's inventories, read and written there. */
    private static final class HandlerSlot extends Slot {
        private final IItemHandlerModifiable inventory;
        private final int index;
        private final boolean input;

        HandlerSlot(IItemHandlerModifiable inventory, int index, int x, int y, boolean input) {
            super(new SimpleContainer(1), 0, x, y);
            this.inventory = inventory;
            this.index = index;
            this.input = input;
        }

        @Override
        public ItemStack getItem() {
            return inventory.getStackInSlot(index);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return input && takes(inventory, index, stack);
        }

        @Override
        public void set(ItemStack stack) {
            inventory.setStackInSlot(index, stack);
        }

        @Override
        public ItemStack remove(int amount) {
            return inventory.extractItem(index, amount, false);
        }

        /** A stack grown where it lies (a quick move merging into it) is set again, so Create hears of it. */
        @Override
        public void setChanged() {
            inventory.setStackInSlot(index, inventory.getStackInSlot(index));
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(stack.getMaxStackSize(), inventory.getSlotLimit(index));
        }
    }
}
