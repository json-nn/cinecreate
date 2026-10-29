package org.minechestplate.cinecreate.station;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import org.minechestplate.cinecreate.CineCreate;

/**
 * What a Mechanical Crafter shows of itself. Create keeps the crafter's phase (idle, passing items on, crafting...) and
 * its countdown to itself, in protected fields of a package-private type; they are read here by reflection, fail-soft:
 * if Create renames them, the phase reads as unknown and the scene simply does not watch.
 */
public final class Crafters {
    private Crafters() {}

    public static final String IDLE = "IDLE", CRAFTING = "CRAFTING";
    /** Create's CRAFTING phase counts down from 2000; the result forms (and {@code crafter_craft} plays) at 1000. */
    public static final int RESULT_AT = 1000;

    @Nullable
    private static final Field PHASE = field("phase"), COUNT_DOWN = field("countDown");

    @Nullable
    private static Field field(String name) {
        try {
            Field field = MechanicalCrafterBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CineCreate.LOGGER.warn("[cinecreate] cannot read the Mechanical Crafter's {}: the crafter scene will not watch it craft ({})",
                    name, e.toString());
            return null;
        }
    }

    @Nullable
    public static String phase(MechanicalCrafterBlockEntity crafter) {
        try {
            return PHASE != null && PHASE.get(crafter) instanceof Enum<?> phase ? phase.name() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Idle: takes items, and a click works its window. Unknown counts as idle (Create's own click would act too). */
    public static boolean idle(MechanicalCrafterBlockEntity crafter) {
        String phase = phase(crafter);
        return phase == null || IDLE.equals(phase);
    }

    public static int countDown(MechanicalCrafterBlockEntity crafter) {
        try {
            return COUNT_DOWN != null ? COUNT_DOWN.getInt(crafter) : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** The window holds a Slot Cover (it never holds an item as well). */
    public static boolean covered(MechanicalCrafterBlockEntity crafter) {
        return crafter.craftingItemOrCoverPresent() && !crafter.craftingItemPresent();
    }

    /** What the window holds while the crafter is idle (at work, the items have moved on). */
    public static ItemStack held(MechanicalCrafterBlockEntity crafter) {
        return crafter.getInventory().getItem(0);
    }

    /** Create's own click covers an empty window with this, instead of putting it in. */
    public static boolean isCover(ItemStack stack) {
        return AllItems.CRAFTER_SLOT_COVER.isIn(stack);
    }

    /** Whether this item would go into the window now (Create's own insertion, simulated). */
    public static boolean takes(MechanicalCrafterBlockEntity crafter, ItemStack stack) {
        return !stack.isEmpty() && !isCover(stack) && crafter.getInventory().insertItem(0, stack.copyWithCount(1), true).isEmpty();
    }

    public static boolean coverable(MechanicalCrafterBlockEntity crafter) {
        return idle(crafter) && !covered(crafter) && held(crafter).isEmpty();
    }
}
