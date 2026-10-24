package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.minechestplate.cinecreate.registry.CineMenus;
import org.minechestplate.cinecreate.station.CrafterGroup;
import org.minechestplate.cinecreate.station.Crafters;

/**
 * A group of Mechanical Crafters ({@link CrafterGroup}): one slot per crafter, in the group's reading order, holding
 * what that crafter's window holds (one item). Putting in goes through the crafter's own inventory, as Create's click
 * and funnels put items in, so Create decides what fits (nothing while the crafters are at work, nothing in a covered
 * window) and starts the recipe once every window is filled. The Slot Cover is an action, not an item: buttons
 * {@link #COVER} and {@link #UNCOVER} plus the cell, done through Create's own click on that crafter's front.
 */
public class CrafterMenu extends BridgeMenu {
    /** Buttons: put the carried Slot Cover on cell {@code COVER + i}, take the cover off cell {@code UNCOVER + i}. */
    public static final int COVER = 0, UNCOVER = 32;

    private final CrafterGroup group;

    private CrafterMenu(int id, Inventory inventory, BlockPos pos, CrafterGroup group, boolean server) {
        super(CineMenus.CRAFTER.get(), id, inventory, pos, group.cells().size(), group.cells().size());
        this.group = group;
        Level level = inventory.player.level();
        SimpleContainer copy = new SimpleContainer(group.cells().size());
        for (int i = 0; i < group.cells().size(); i++) {
            BlockPos cell = group.cells().get(i);
            int x = 8 + group.column(cell) * 18, y = 18 + group.row(cell) * 18;
            addSlot(server ? new WindowSlot(level, cell, x, y) : new Slot(copy, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // клиент гадает по своей копии крафтера; решает сервер
                    return crafter(level, cell) instanceof MechanicalCrafterBlockEntity crafter && Crafters.takes(crafter, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addPlayerInventory(inventory, 8, 120);
    }

    public static CrafterMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new CrafterMenu(id, inventory, pos, CrafterGroup.read(buf), false);
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        CrafterGroup group = CrafterGroup.of(level, pos);
        return group == null ? null : new Provider(pos, group);
    }

    /** Opens the menu over the group, and sends the group along for the client's copy. */
    private record Provider(BlockPos pos, CrafterGroup group) implements MenuProvider, ExtraData {
        @Override
        public Component getDisplayName() {
            return Component.translatable("cinecreate.menu.mechanical_crafter");
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new CrafterMenu(id, inventory, pos, group, true);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            group.write(buf);
        }
    }

    /** The group as the server found it (the client's copy has the same cells). */
    public CrafterGroup group() {
        return group;
    }

    /** The slot of the crafter at this position, or -1 when it is not in the group. */
    public int slotOf(BlockPos cell) {
        return group.cells().indexOf(cell);
    }

    @Nullable
    private static MechanicalCrafterBlockEntity crafter(Level level, BlockPos cell) {
        return level.getBlockEntity(cell) instanceof MechanicalCrafterBlockEntity crafter ? crafter : null;
    }

    /**
     * Server: a cover button. Done as Create's own click on that crafter's front does it: with the carried Slot Cover
     * it covers the empty window (and uses the cover up, but not in creative), with an empty hand it takes the cover off
     * into the inventory. Only on an idle crafter: Create's click on a crafter at work would throw the whole grid out.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        boolean cover = id < UNCOVER;
        int cell = cover ? id - COVER : id - UNCOVER;
        if (id < 0 || cell >= group.cells().size() || !player.mayBuild()) {
            return false;
        }
        BlockPos at = group.cells().get(cell);
        Level level = player.level();
        MechanicalCrafterBlockEntity crafter = crafter(level, at);
        if (crafter == null || !Crafters.idle(crafter)) {
            return false;
        }
        BlockState state = level.getBlockState(at);
        Direction front = group.facing();
        BlockHitResult hit = new BlockHitResult(group.face(at), front, at, false);
        if (cover) {
            ItemStack carried = getCarried();
            if (!Crafters.isCover(carried) || !Crafters.coverable(crafter)) {
                return false;
            }
            // Create сам кладёт крышку и уменьшает стопку, которую ему дали: это и есть стопка в руке сцены
            state.useItemOn(carried, level, player, InteractionHand.MAIN_HAND, hit);
            setCarried(carried);
            return Crafters.covered(crafter);
        }
        if (!Crafters.covered(crafter)) {
            return false;
        }
        state.useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND, hit);
        return !Crafters.covered(crafter);
    }

    /** Server: a crafter's window, read and written on the crafter itself. */
    private static final class WindowSlot extends Slot {
        private final Level level;
        private final BlockPos cell;

        WindowSlot(Level level, BlockPos cell, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.level = level;
            this.cell = cell;
        }

        @Override
        public ItemStack getItem() {
            MechanicalCrafterBlockEntity crafter = crafter(level, cell);
            return crafter == null ? ItemStack.EMPTY : Crafters.held(crafter);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            MechanicalCrafterBlockEntity crafter = crafter(level, cell);
            return crafter != null && Crafters.takes(crafter, stack);
        }

        @Override
        public void set(ItemStack stack) {
            MechanicalCrafterBlockEntity crafter = crafter(level, cell);
            if (crafter == null) {
                return;
            }
            if (stack.isEmpty()) {
                // всегда через инвентарь: перенос Shift-кликом уменьшает саму стопку крафтера, и без этого никто не узнает
                crafter.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            } else {
                // как воронка или клик Create: через инвентарь крафтера (его звук, и рецепт начнётся, когда всё готово)
                crafter.getInventory().insertItem(0, stack, false);
            }
        }

        @Override
        public ItemStack remove(int amount) {
            MechanicalCrafterBlockEntity crafter = crafter(level, cell);
            ItemStack held = crafter == null ? ItemStack.EMPTY : Crafters.held(crafter).copy();
            if (held.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            crafter.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            level.playSound(null, cell, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1f + level.getRandom().nextFloat());
            return held;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
