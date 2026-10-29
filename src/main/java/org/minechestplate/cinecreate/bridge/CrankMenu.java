package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.infrastructure.config.AllConfigs;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.minechestplate.cinecreate.registry.CineMenus;

/**
 * A Hand Crank: no item slots, only the crank itself. Holding the crank in the scene sends a button every few ticks,
 * as holding Create's use does; each one is Create's own turn ({@link HandCrankBlockEntity#turn}), which keeps the crank
 * generating for half a second, and costs the hunger Create's click costs.
 */
public class CrankMenu extends BridgeMenu {
    /** Buttons: crank on, crank back (Create's sneak-click). */
    public static final int TURN = 0, TURN_BACK = 1;
    /**
     * As holding use repeats itself, the scene sends a turn every this many ticks (a slow server may take several in one
     * tick). More would gain nothing: a turn only keeps the crank going, and each costs its hunger.
     */
    public static final int TURN_EVERY = 4;
    /** Create's crank turns at this speed while held. */
    public static final int RPM = 32;

    private CrankMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.CRANK.get(), id, inventory, pos, 0, 0);
        addPlayerInventory(inventory, 8, 84);
    }

    public static CrankMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new CrankMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HandCrankBlockEntity)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new CrankMenu(id, inventory, pos),
                Component.translatable("cinecreate.menu.hand_crank"));
    }

    /** Server: a turn, as Create's {@code HandCrankBlock.useItemOn} does it, but the direction from the button. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != TURN && id != TURN_BACK || !(player.level().getBlockEntity(pos) instanceof HandCrankBlockEntity crank)
                || !player.mayBuild()) {
            return false;
        }
        crank.turn(id == TURN_BACK);
        if (!player.getMainHandItem().is(AllItems.EXTENDO_GRIP.get())) {
            int speed = crank.getBlockState().getBlock() instanceof HandCrankBlock block ? block.getRotationSpeed() : RPM;
            player.causeFoodExhaustion(speed * AllConfigs.server().kinetics.crankHungerMultiplier.getF());
        }
        if (player.getFoodData().getFoodLevel() == 0) {
            AllAdvancements.HAND_CRANK.awardTo(player);
        }
        return true;
    }
}
