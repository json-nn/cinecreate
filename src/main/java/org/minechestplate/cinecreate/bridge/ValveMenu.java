package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
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
 * A Valve Handle: no item slots, only actions, sent as the vanilla menu button packet (as CI's stonecutter and loom
 * send theirs). The server does what Create's own click and value box do: {@link ValveHandleBlockEntity#activate}
 * turns the wheel (and what it drives) by the set angle, or back; the angle is Create's own setting on the block.
 */
public class ValveMenu extends BridgeMenu {
    /** Buttons: turn by the set angle, turn back. */
    public static final int TURN = 0, TURN_BACK = 1;
    /** Button {@code ANGLE + 180 + angle} sets the angle (-180..180, not 0), as the value box would. */
    public static final int ANGLE = 1000;
    public static final int MAX_ANGLE = 180;

    private ValveMenu(int id, Inventory inventory, BlockPos pos) {
        super(CineMenus.VALVE.get(), id, inventory, pos, 0, 0);
        addPlayerInventory(inventory, 8, 84);
    }

    public static ValveMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new ValveMenu(id, inventory, buf.readBlockPos());
    }

    @Nullable
    public static MenuProvider provider(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ValveHandleBlockEntity)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> new ValveMenu(id, inventory, pos),
                Component.translatable("cinecreate.menu.valve_handle"));
    }

    public static int angleButton(int angle) {
        return ANGLE + MAX_ANGLE + angle;
    }

    /** Server: the button packet. False refuses it (the wheel is turning, or the network is driven by something else). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof ValveHandleBlockEntity valve) || !player.mayBuild()) {
            return false;
        }
        if (id == TURN || id == TURN_BACK) {
            // как клик по самому вентилю (ValveHandleBlock.clicked), только без перекраски
            return valve.activate(id == TURN_BACK);
        }
        int angle = id - ANGLE - MAX_ANGLE;
        if (angle < -MAX_ANGLE || angle > MAX_ANGLE || angle == 0) {
            return false;
        }
        valve.angleInput.setValue(angle);
        return true;
    }
}
