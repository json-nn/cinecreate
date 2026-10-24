package org.minechestplate.cinecreate.mixin;

import dev.dyslexic.cinematic.station.Sessions;
import dev.dyslexic.cinematic.station.StationKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.station.CiHookMarker;
import org.minechestplate.cinecreate.station.SceneBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Хук 2: вместо «окна блока» (его у Create нет) — обычное действие Create по клику
@Mixin(value = Sessions.class, remap = false)
public abstract class SessionsMixin implements CiHookMarker {

    @Inject(method = "openWindow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cinecreate$createUse(ServerPlayer player, BlockPos pos, StationKind kind, int slot, CallbackInfo ci) {
        if (kind == StationKind.ADDON && SceneBlocks.of(player.level().getBlockState(pos)) != null) {
            Bridge.vanillaUse(player, pos);
            ci.cancel();
        }
    }
}
