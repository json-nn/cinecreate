package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.minechestplate.cinecreate.registry.CineMenus;
import org.minechestplate.cinecreate.station.Composites;

/**
 * The depot (and the Weighted Ejector's): slot 0 is the stack lying on it, slots 1-8 what processing on it has left over
 * (Create's output buffer, take only). Putting down goes through {@link DepotBehaviour} as Create's own right-click does.
 */
public class DepotMenu extends BridgeMenu {
    public static final int MAIN = 0;
    /**
     * Button: Create's own click on the hand of a Deployer working this depot from above, with what the scene's hand
     * carries: they swap, as at the hand-off.
     */
    public static final int DEPLOYER_SWAP = 0;
    public static final int OUTPUTS = 8;
    public static final int SLOTS = 1 + OUTPUTS;
    /** Where on the depot a stack set down starts: just short of the middle, so it slides the last pixel. */
    public static final float SET_DOWN_AT = 0.44f;

    public DepotMenu(int id, Inventory inventory, BlockPos pos, DepotBehaviour depot) {
        super(CineMenus.DEPOT.get(), id, inventory, pos, SLOTS, 1);
        addSlot(new MainSlot(depot, Bridge.facing(inventory.player)));
        for (int i = 0; i < OUTPUTS; i++) {
            addSlot(new OutputSlot(depot, i));
        }
        addPlayerInventory(inventory, 8, 84);
    }

    private DepotMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.DEPOT.get(), id, inventory, pos, SLOTS, 1);
        SimpleContainer copy = new SimpleContainer(SLOTS);
        DepotBehaviour depot = BlockEntityBehaviour.get(inventory.player.level(), pos, DepotBehaviour.TYPE);
        addSlot(new Slot(copy, MAIN, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return depot == null || depot.isItemValid(stack);
            }
        });
        for (int i = 0; i < OUTPUTS; i++) {
            addSlot(new Slot(copy, 1 + i, outputX(i), 48) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addPlayerInventory(inventory, 8, 84);
    }

    public static DepotMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new DepotMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        DepotBehaviour depot = BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
        if (depot == null) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new DepotMenu(id, inventory, pos, depot),
                Component.translatable("cinecreate.menu.depot"));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        DeployerBlockEntity deployer = Composites.deployerOver(player.level(), pos);
        if (id != DEPLOYER_SWAP || deployer == null || !(player instanceof ServerPlayer server) || !player.mayBuild()) {
            return false;
        }
        return Bridge.useWithCarried(server, this, deployer.getBlockPos(), Direction.DOWN, true) != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static int outputX(int i) {
        return 17 + i * 18;
    }

    private static final class MainSlot extends Slot {
        private final DepotBehaviour depot;
        private final Direction from;

        MainSlot(DepotBehaviour depot, Direction from) {
            super(new SimpleContainer(1), 0, 80, 20);
            this.depot = depot;
            this.from = from;
        }

        @Override
        public ItemStack getItem() {
            return depot.getHeldItemStack();
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            boolean had = hasItem();
            if (stack.isEmpty()) {
                depot.removeHeldItem();
            } else {
                // as Create's own use puts it down: it comes in from the player's side
                TransportedItemStack transported = new TransportedItemStack(stack);
                transported.insertedFrom = from;
                transported.prevBeltPosition = SET_DOWN_AT;
                transported.beltPosition = SET_DOWN_AT;
                depot.setHeldItem(transported);
            }
            setChanged();
            Level level = depot.getWorld();
            if (level != null && !level.isClientSide) {
                if (!stack.isEmpty()) {
                    AllSoundEvents.DEPOT_SLIDE.playOnServer(level, depot.getPos());
                } else if (had) {
                    pickupSound(level);
                }
            }
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack held = getItem();
            ItemStack taken = held.split(amount);
            if (held.isEmpty()) {
                depot.removeHeldItem();
            }
            setChanged();
            Level level = depot.getWorld();
            if (level != null && !level.isClientSide && !taken.isEmpty()) {
                pickupSound(level);
            }
            return taken;
        }

        private void pickupSound(Level level) {
            level.playSound(null, depot.getPos(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
                    1f + level.getRandom().nextFloat());
        }

        @Override
        public void setChanged() {
            depot.blockEntity.notifyUpdate();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return depot.isItemValid(stack) && !launching(depot);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !launching(depot);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.getMaxStackSize();
        }
    }

    /** An ejector whose plate is not down: Create takes and gives nothing then. */
    static boolean launching(DepotBehaviour depot) {
        return depot.blockEntity instanceof EjectorBlockEntity ejector && ejector.getState() != EjectorBlockEntity.State.CHARGED;
    }

    private static final class OutputSlot extends Slot {
        private final DepotBehaviour depot;
        private final int buffer;

        OutputSlot(DepotBehaviour depot, int buffer) {
            super(new SimpleContainer(1), 0, outputX(buffer), 48);
            this.depot = depot;
            this.buffer = buffer;
        }

        @Override
        public ItemStack getItem() {
            return depot.itemHandler.getStackInSlot(1 + buffer);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            if (stack.isEmpty()) {
                depot.itemHandler.extractItem(1 + buffer, Integer.MAX_VALUE, false);
            }
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack taken = depot.itemHandler.extractItem(1 + buffer, amount, false);
            setChanged();
            return taken;
        }

        @Override
        public void setChanged() {
            depot.blockEntity.notifyUpdate();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !launching(depot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
