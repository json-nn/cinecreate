package org.minechestplate.cinecreate.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The menu a client-mode scene holds open. Cinematic Interactions keeps a client-only scene only while a menu of its own
 * is open (on a server without its mod, the block's vanilla menu); our blocks have none, so this empty one stands in. It
 * exists on the client only: closing it tells the server no more than "closed", which is harmless there.
 */
final class ClientModeMenu extends AbstractContainerMenu {
    /** Out of the server's range (it counts its menus 1–100), so a real menu it opens never shares the id. */
    static final int ID = 120;

    ClientModeMenu() {
        super(null, ID);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
