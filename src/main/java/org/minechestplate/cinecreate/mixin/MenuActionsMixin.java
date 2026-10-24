package org.minechestplate.cinecreate.mixin;

import dev.dyslexic.cinematic.station.MenuActions;
import dev.dyslexic.cinematic.station.Sessions;
import dev.dyslexic.cinematic.station.StationKind;
import net.minecraft.server.level.ServerPlayer;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.station.CiHookMarker;
import org.minechestplate.cinecreate.station.SceneBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Хук 1: сцена Create открывает наше меню-мост (CI сам ищет getMenuProvider, которого у блоков Create нет)
@Mixin(value = MenuActions.class, remap = false)
public abstract class MenuActionsMixin implements CiHookMarker {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cinecreate$openBridge(ServerPlayer player, Sessions.Session session, CallbackInfoReturnable<Boolean> cir) {
        if (session.kind() == StationKind.ADDON && SceneBlocks.owns(session.addonId())) {
            cir.setReturnValue(Bridge.open(player, session.pos()));
        }
    }
}
