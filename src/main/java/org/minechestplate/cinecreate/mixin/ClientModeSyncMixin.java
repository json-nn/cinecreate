package org.minechestplate.cinecreate.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.InventoryMenu;
import org.minechestplate.cinecreate.client.ClientMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Режим клиента: пока открыто меню сцены, игра отбрасывает изменения инвентаря вне хотбара — применяем их сами
@Mixin(ClientPacketListener.class)
public abstract class ClientModeSyncMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void cinecreate$inventory(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && packet.getContainerId() == 0 && !InventoryMenu.isHotbarSlot(packet.getSlot())
                && ClientMode.menuOpen(player.containerMenu.containerId)) {
            player.inventoryMenu.setItem(packet.getSlot(), packet.getStateId(), packet.getItem());
        }
    }
}
