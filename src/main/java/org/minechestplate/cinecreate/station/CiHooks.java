package org.minechestplate.cinecreate.station;

import dev.dyslexic.cinematic.station.MenuActions;
import dev.dyslexic.cinematic.station.Sessions;
import dev.dyslexic.cinematic.station.StationEvents;
import dev.dyslexic.cinematic.station.StationKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.minechestplate.cinecreate.CineCreate;

/**
 * The three Cinematic Interactions methods we hook (see the mixin package). Their mixins never fail the game when a
 * newer Cinematic Interactions changes them; this check finds out instead, and without every hook in place no scene is
 * registered, so Create's blocks simply keep their own use.
 */
public final class CiHooks {
    private CiHooks() {}

    public static boolean inPlace() {
        try {
            MenuActions.class.getDeclaredMethod("open", ServerPlayer.class, Sessions.Session.class);
            Sessions.class.getDeclaredMethod("openWindow", ServerPlayer.class, BlockPos.class, StationKind.class, int.class);
            StationEvents.class.getDeclaredMethod("onRightClickBlock", PlayerInteractEvent.RightClickBlock.class);
        } catch (NoSuchMethodException | LinkageError e) {
            CineCreate.LOGGER.error("[cinecreate] this Cinematic Interactions version changed a method we hook ({}): "
                    + "Create scenes are off, Create's blocks keep their own use", e.getMessage());
            return false;
        }
        boolean applied = CiHookMarker.class.isAssignableFrom(MenuActions.class)
                && CiHookMarker.class.isAssignableFrom(Sessions.class)
                && CiHookMarker.class.isAssignableFrom(StationEvents.class);
        if (!applied) {
            CineCreate.LOGGER.error("[cinecreate] our Cinematic Interactions hooks were not applied: Create scenes are off");
        }
        return applied;
    }
}
