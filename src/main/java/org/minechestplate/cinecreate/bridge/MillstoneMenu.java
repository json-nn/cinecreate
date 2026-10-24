package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * A Millstone: slot 0 is its mouth (what goes in passes Create's own test: only what it can mill), slots 1-9 what it has
 * ground (take only). Button {@link #TAKE_ALL} is Create's own empty-handed click: the ground output into the
 * inventory, or, with none, what is still in the mouth.
 */
public class MillstoneMenu extends BridgeMenu {
    public static final int MOUTH = 0, OUTPUTS = 9, SLOTS = 1 + OUTPUTS;
    public static final int TAKE_ALL = 0;

    /** Server: slots over the millstone. */
    private MillstoneMenu(int id, Inventory inventory, BlockPos pos, MillstoneBlockEntity millstone) {
        super(CineMenus.MILLSTONE.get(), id, inventory, pos, SLOTS, 1);
        addSlot(new MouthSlot(millstone));
        for (int i = 0; i < OUTPUTS; i++) {
            addSlot(new OutputSlot(millstone, i));
        }
        addPlayerInventory(inventory, 8, 84);
    }

    /** Client: a plain copy the server keeps in sync. */
    private MillstoneMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.MILLSTONE.get(), id, inventory, pos, SLOTS, 1);
        SimpleContainer copy = new SimpleContainer(SLOTS);
        Level level = inventory.player.level();
        for (int i = 0; i < SLOTS; i++) {
            int slot = i;
            addSlot(new Slot(copy, i, 8 + i * 16, 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return slot == MOUTH && level.getBlockEntity(pos) instanceof MillstoneBlockEntity millstone
                            && millstone.capability.insertItem(0, stack, true).getCount() < stack.getCount();
                }
            });
        }
        addPlayerInventory(inventory, 8, 84);
    }

    public static MillstoneMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new MillstoneMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MillstoneBlockEntity millstone)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new MillstoneMenu(id, inventory, pos, millstone),
                Component.translatable("cinecreate.menu.millstone"));
    }

    /** Server: Create's empty-handed click (only with nothing carried). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != TAKE_ALL || !getCarried().isEmpty() || !(player instanceof ServerPlayer server)
                || !(player.level().getBlockEntity(pos) instanceof MillstoneBlockEntity) || !player.mayBuild()) {
            return false;
        }
        return Bridge.useWithCarried(server, this, pos, Direction.UP) != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Server: the mouth, filled through Create's own input (which takes only what the millstone can mill). */
    private static final class MouthSlot extends Slot {
        private final MillstoneBlockEntity millstone;

        MouthSlot(MillstoneBlockEntity millstone) {
            super(new SimpleContainer(1), 0, 8, 20);
            this.millstone = millstone;
        }

        @Override
        public ItemStack getItem() {
            return millstone.inputInv.getStackInSlot(0);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            IItemHandler input = millstone.capability;
            return input.insertItem(0, stack, true).getCount() < stack.getCount();
        }

        @Override
        public void set(ItemStack stack) {
            if (stack.isEmpty()) {
                millstone.inputInv.setStackInSlot(0, ItemStack.EMPTY);
            } else if (getItem().isEmpty()) {
                millstone.capability.insertItem(0, stack, false);
            } else {
                millstone.inputInv.setStackInSlot(0, stack);
            }
            millstone.sendData();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack taken = millstone.inputInv.extractItem(0, amount, false);
            millstone.sendData();
            return taken;
        }

        @Override
        public void setChanged() {
            millstone.inputInv.setStackInSlot(0, millstone.inputInv.getStackInSlot(0));
            millstone.sendData();
        }
    }

    /** Server: what the millstone has ground, taken out only. */
    private static final class OutputSlot extends Slot {
        private final MillstoneBlockEntity millstone;
        private final int index;

        OutputSlot(MillstoneBlockEntity millstone, int index) {
            super(new SimpleContainer(1), 0, 8 + (1 + index) * 16, 20);
            this.millstone = millstone;
            this.index = index;
        }

        @Override
        public ItemStack getItem() {
            return millstone.outputInv.getStackInSlot(index);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void set(ItemStack stack) {
            millstone.outputInv.setStackInSlot(index, stack);
            millstone.sendData();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack taken = millstone.outputInv.extractItem(index, amount, false);
            millstone.sendData();
            return taken;
        }
    }
}
