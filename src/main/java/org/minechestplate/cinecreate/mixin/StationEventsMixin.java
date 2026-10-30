package org.minechestplate.cinecreate.mixin;

import dev.dyslexic.cinematic.station.StationEvents;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.station.CiHookMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Хук 3: гаечный ключ, очки, фильтры, клики не по рабочей грани и сцены не в режиме USE остаются за Create
// (CI не перехватывает их)
@Mixin(value = StationEvents.class, remap = false)
public abstract class StationEventsMixin implements CiHookMarker {

    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cinecreate$useRules(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (Bridge.probing() || Bridge.automated(event.getEntity()) || Bridge.leaveToCreate(event)) {
            ci.cancel();
        }
    }
}
