package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * A Mechanical Saw facing up: slot 0 is what lies on its table (in through the saw's own inventory, as an item dropped on
 * it gets in). The shapes it can be cut into are {@link #shapes}: Create's cutting recipes for it (and stonecutting,
 * when the server allows that on the saw), by result, in a fixed order on both sides; with the table empty, the shapes
 * of what the hand carries, so one can be chosen before it is laid down. Button {@code SHAPE + i} sets the
 * saw's filter to shape i, as its filter slot would; {@link #ANY_SHAPE} clears it; {@link #TAKE_ALL} is Create's own
 * empty-handed click (everything on the table into the inventory).
 */
public class SawMenu extends BridgeMenu {
    public static final int TABLE = 0;
    public static final int TAKE_ALL = 0, ANY_SHAPE = 1, SHAPE = 2;
    /** At most this many shapes on the dial. */
    public static final int MAX_SHAPES = 9;

    /** Server: the slot over the saw's table. */
    private SawMenu(int id, Inventory inventory, BlockPos pos, SawBlockEntity saw) {
        super(CineMenus.SAW.get(), id, inventory, pos, 1, 1);
        addSlot(new TableSlot(saw));
        addPlayerInventory(inventory, 8, 84);
    }

    /** Client: a plain copy the server keeps in sync. */
    private SawMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.SAW.get(), id, inventory, pos, 1, 1);
        Level level = inventory.player.level();
        addSlot(new Slot(new SimpleContainer(1), 0, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return level.getBlockEntity(pos) instanceof SawBlockEntity saw && takes(saw, stack);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return level.getBlockEntity(pos) instanceof SawBlockEntity saw ? limit(saw, stack) : 1;
            }
        });
        addPlayerInventory(inventory, 8, 84);
    }

    public static SawMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new SawMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SawBlockEntity saw)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new SawMenu(id, inventory, pos, saw),
                Component.translatable("cinecreate.menu.mechanical_saw"));
    }

    static boolean takes(SawBlockEntity saw, ItemStack stack) {
        return !stack.isEmpty() && saw.inventory.insertItem(0, stack, true).getCount() < stack.getCount();
    }

    static int limit(SawBlockEntity saw, ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), saw.inventory.getSlotLimit(0));
    }

    /**
     * What this input can be cut into on the saw: the results of Create's cutting recipes for it, and of stonecutting
     * ones when the server lets the saw do those, each once, ordered by the item's id (the same on both sides).
     */
    public static List<ItemStack> shapes(Level level, ItemStack input) {
        List<ItemStack> shapes = new ArrayList<>();
        if (input.isEmpty()) {
            return shapes;
        }
        List<RecipeType<?>> types = new ArrayList<>(List.of(AllRecipeTypes.CUTTING.getType()));
        if (AllConfigs.server().recipes.allowStonecuttingOnSaw.get()) {
            types.add(RecipeType.STONECUTTING);
        }
        for (RecipeType<?> type : types) {
            for (RecipeHolder<?> holder : allOf(level, type)) {
                Recipe<?> recipe = holder.value();
                List<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients.isEmpty() || !ingredients.get(0).test(input)) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (!result.isEmpty() && shapes.stream().noneMatch(shape -> ItemStack.isSameItemSameComponents(shape, result))) {
                    shapes.add(result.copy());
                }
            }
        }
        shapes.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        return shapes.size() > MAX_SHAPES ? shapes.subList(0, MAX_SHAPES) : shapes;
    }

    /** What the dial offers shapes for: what lies on the table, or, with the table empty, what the hand carries. */
    public static ItemStack input(ItemStack onTable, ItemStack carried) {
        return onTable.isEmpty() ? carried : onTable;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> allOf(Level level, RecipeType<?> type) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    /** Server: a shape for the filter, no filter, or Create's empty-handed click. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server) || !(player.level().getBlockEntity(pos) instanceof SawBlockEntity saw) || !player.mayBuild()) {
            return false;
        }
        if (id == TAKE_ALL) {
            return getCarried().isEmpty()
                    && Bridge.useWithCarried(server, this, pos, Direction.UP) != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        FilteringBehaviour filter = BlockEntityBehaviour.get(saw, FilteringBehaviour.TYPE);
        if (filter == null) {
            return false;
        }
        if (id == ANY_SHAPE) {
            filter.setFilter(ItemStack.EMPTY);
            return true;
        }
        List<ItemStack> shapes = shapes(player.level(), input(saw.inventory.getStackInSlot(0), getCarried()));
        int shape = id - SHAPE;
        if (shape < 0 || shape >= shapes.size()) {
            return false;
        }
        filter.setFilter(shapes.get(shape));
        return true;
    }

    /** Server: what lies on the saw's table, in and out through the saw's own inventory. */
    private static final class TableSlot extends Slot {
        private final SawBlockEntity saw;

        TableSlot(SawBlockEntity saw) {
            super(new SimpleContainer(1), 0, 80, 20);
            this.saw = saw;
        }

        @Override
        public ItemStack getItem() {
            return saw.inventory.getStackInSlot(0);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return takes(saw, stack);
        }

        @Override
        public void set(ItemStack stack) {
            if (stack.isEmpty()) {
                saw.inventory.setStackInSlot(0, ItemStack.EMPTY);
            } else if (getItem().isEmpty()) {
                // как брошенная на пилу вещь: через её инвентарь, который и запускает распил
                saw.inventory.insertItem(0, stack, false);
            } else {
                saw.inventory.setStackInSlot(0, stack);
            }
            saw.notifyUpdate();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack taken = saw.inventory.extractItem(0, amount, false);
            saw.notifyUpdate();
            return taken;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return limit(saw, stack);
        }
    }
}
