package org.minechestplate.cinecreate.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.station.SceneBlock;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;
import org.minechestplate.cinecreate.station.Trigger;

/**
 * The trigger policy (DESIGN §5.3): how each player wants each kind of scene started. Client and server decide every
 * right-click the same way, the client from its own config and the server from what that client last sent
 * ({@link ScenePrefsPayload}).
 */
public final class ScenePrefs {
    private ScenePrefs() {}

    /** Server: each player's choices, from their last payload; gone when they log out. */
    private static final Map<UUID, Map<ResourceLocation, Trigger>> PLAYERS = new ConcurrentHashMap<>();

    /** Client: the local player's choices (installed by {@code CineCreateClient}). */
    private static volatile Function<SceneKind, Trigger> local = SceneKind::trigger;
    /** Client: whether the calling thread is the client's own, not the integrated server's next to it. */
    private static volatile BooleanSupplier onClientThread = () -> false;

    public static void useOnClient(Function<SceneKind, Trigger> choices, BooleanSupplier clientThread) {
        local = choices;
        onClientThread = clientThread;
    }

    /**
     * How this player starts this scene. On the server, a player who has sent nothing (a client without cinecreate,
     * or not yet) gets USE.
     */
    public static Trigger of(Player player, SceneKind kind) {
        if (player.level().isClientSide()) {
            return local.apply(kind);
        }
        Map<ResourceLocation, Trigger> prefs = PLAYERS.get(player.getUUID());
        Trigger trigger = prefs == null ? null : prefs.get(kind.id());
        return trigger == null ? kind.trigger() : trigger;
    }

    /**
     * Whether every scene this block can play is OFF, asked without a player or a position: Cinematic Interactions
     * asks a station for its working sides with the block state alone. Only the client's own thread can answer (for its
     * player); everywhere else the answer is no.
     */
    public static boolean allOffHere(SceneBlock scene) {
        if (!onClientThread.getAsBoolean()) {
            return false;
        }
        for (SceneKind kind : scene.kinds()) {
            if (local.apply(kind) != Trigger.OFF) {
                return false;
            }
        }
        return true;
    }

    public static void registerPayload(RegisterPayloadHandlersEvent event) {
        // необязательный канал: клиент без него всё равно подключается, а сервер считает для него USE
        event.registrar("1").optional().playToServer(ScenePrefsPayload.TYPE, ScenePrefsPayload.STREAM_CODEC, ScenePrefs::receive);
    }

    /** Server, main thread: the player's new choices replace the old ones; ids of scenes we do not have are dropped. */
    private static void receive(ScenePrefsPayload payload, IPayloadContext context) {
        Map<ResourceLocation, Trigger> prefs = new HashMap<>();
        payload.triggers().forEach((id, trigger) -> {
            if (SceneKinds.byId(id) != null) {
                prefs.put(id, trigger);
            }
        });
        PLAYERS.put(context.player().getUUID(), Map.copyOf(prefs));
        CineCreate.LOGGER.debug("[cinecreate] {} starts scenes: {}", context.player().getName().getString(), prefs);
    }

    public static void forget(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS.remove(event.getEntity().getUUID());
    }
}
