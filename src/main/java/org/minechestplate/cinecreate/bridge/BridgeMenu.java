package org.minechestplate.cinecreate.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * A windowless menu over a Create block: its first slots stand for the block's own inventory, the rest are the player's
 * 36 inventory slots (Cinematic Interactions moves items between them with ordinary slot clicks and quick moves). The
 * server copy reads and writes the block itself, so Create's own rules decide what fits; the client copy is a plain
 * container the server keeps in sync.
 */
public abstract class BridgeMenu extends AbstractContainerMenu {
    /** Same reach as a Cinematic Interactions session (6 blocks). */
    private static final double REACH_SQR = 6.0 * 6.0;

    protected final BlockPos pos;
    private final Block block;
    /** Slots of the block, before the player's inventory. */
    protected final int machineSlots;
    /** The first slots that take items in; the others only give out (outputs). */
    protected final int inputSlots;

    protected BridgeMenu(MenuType<?> type, int id, Inventory inventory, BlockPos pos, int machineSlots, int inputSlots) {
        super(type, id);
        this.pos = pos.immutable();
        this.block = inventory.player.level().getBlockState(pos).getBlock();
        this.machineSlots = machineSlots;
        this.inputSlots = inputSlots;
    }

    /**
     * A menu provider whose client copy needs more than the block's position to build the same slots (a crafter
     * group's cells). {@link Bridge#open} writes it after the position.
     */
    public interface ExtraData {
        void write(RegistryFriendlyByteBuf buf);
    }

    public BlockPos pos() {
        return pos;
    }

    public int machineSlots() {
        return machineSlots;
    }

    /** Main inventory then hotbar, as in every vanilla menu (call after the block's slots). */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    /** Menu index of a player inventory slot (0..35, hotbar first, as {@link Inventory} counts them). */
    public int inventoryIndex(int inventorySlot) {
        return inventorySlot < 9 ? machineSlots + 27 + inventorySlot : machineSlots + inventorySlot - 9;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(block) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= REACH_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        // into the block only through its inputs: vanilla merging does not ask outputs whether they take anything
        boolean moved = index < machineSlots
                ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, inputSlots, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return before;
    }
}
