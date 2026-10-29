package org.minechestplate.cinecreate.client;

import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.station.StationKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.bridge.ScenePrefsPayload;
import org.minechestplate.cinecreate.station.SceneBlocks;

/**
 * Scenes on a server without the add-on (DESIGN §8.6). Without it there is no menu to work through, so our blocks keep
 * Create's own clicks; with client mode on, those clicks are played out as a scene ({@code ClientModeStation}). As in
 * Cinematic Interactions, the choice is taken when joining: a change applies from the next connection.
 */
public final class ClientMode {
    private ClientMode() {}

    private static boolean enabledForConnection;

    static void onLogin() {
        enabledForConnection = ClientConfig.clientMode();
    }

    /** The server runs our scenes: it has the add-on (its channel is there). */
    public static boolean serverHasAddon() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return !Bridge.devNoAddon && (connection == null || connection.hasChannel(ScenePrefsPayload.TYPE));
    }

    /** Client mode is on for this connection, and needed: the server lacks the add-on. */
    public static boolean active() {
        return enabledForConnection && !serverHasAddon();
    }

    /**
     * A client-mode scene at one of our blocks, if client mode is active and one can begin from where the player is. CI
     * keeps a client-only scene only while a menu of its own is open: ours, held open on the client, stands in.
     */
    public static boolean begin(BlockPos pos) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!active() || Scene.phase != Scene.Phase.NONE || player == null || player.containerMenu != player.inventoryMenu) {
            return false;
        }
        player.containerMenu = new ClientModeMenu();
        if (Scene.beginClientOnly(pos, StationKind.ADDON)) {
            return true;
        }
        player.containerMenu = player.inventoryMenu;
        return false;
    }

    /**
     * A right-click that would start a scene where the server has the add-on starts the client-mode one instead. Taken
     * on the key, before the game sends the click: the server would otherwise do Create's click at once (a depot takes
     * the whole stack in hand), and the scene's own click would then find the hand empty.
     */
    static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && active() && mc.level != null && mc.player != null
                && mc.hitResult instanceof BlockHitResult hit && Bridge.clientModeStarts(mc.level, mc.player, hit)
                && begin(SceneBlocks.target(mc.level, hit.getBlockPos()))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Development (SmokeTest): client mode for this connection, as if joined with it on. */
    public static void force(boolean on) {
        enabledForConnection = on;
    }
}
