package org.minechestplate.cinecreate.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.minechestplate.cinecreate.client.ClientMode;
import org.minechestplate.cinecreate.client.ClientModeActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Режим клиента: щелчки сцены и CI по меню, которого нет на сервере, разыгрываются тем, что сервер понимает
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientModeClickMixin {

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void cinecreate$slot(int containerId, int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (ClientMode.menuOpen(containerId)) {
            ClientModeActions.click(slot, button, type);
            ci.cancel();
        }
    }

    @Inject(method = "handleInventoryButtonClick", at = @At("HEAD"), cancellable = true)
    private void cinecreate$button(int containerId, int id, CallbackInfo ci) {
        if (ClientMode.menuOpen(containerId)) {
            ClientModeActions.button(id);
            ci.cancel();
        }
    }
}
