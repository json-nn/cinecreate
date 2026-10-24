package org.minechestplate.cinecreate.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import org.minechestplate.cinecreate.bridge.ScenePrefsPayload;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;
import org.minechestplate.cinecreate.station.Trigger;

/**
 * The player's own settings: {@code config/cinecreate-client.toml}, also behind the mod list's Config button. A change
 * applies at once, without relogging: the server hears of it through {@link ScenePrefsPayload}.
 */
public final class ClientConfig {
    private ClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final Map<ResourceLocation, ModConfigSpec.EnumValue<Trigger>> TRIGGERS;
    private static final ModConfigSpec.BooleanValue INVITATION;
    private static final ModConfigSpec.BooleanValue FOLLOW_LAUNCHES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Map<ResourceLocation, ModConfigSpec.EnumValue<Trigger>> triggers = new LinkedHashMap<>();
        b.translation("cinecreate.configuration.scenes").push("scenes");
        for (SceneKind kind : SceneKinds.all()) {
            triggers.put(kind.id(), b
                    .comment("How the " + kind.name() + " scene starts. USE: a right-click starts it. KEY: a right-click is"
                            + " Create's own, the \"Work at the machine\" key (H) starts it. OFF: never a scene.")
                    .translation("cinecreate.configuration." + kind.name())
                    .defineEnum(kind.name(), kind.trigger()));
        }
        b.pop();
        INVITATION = b.comment("The \"[H] Work at the depot\" plate under the crosshair at blocks whose scene starts on the key."
                        + " The key works without it too.")
                .translation("cinecreate.configuration.invitation")
                .define("invitation", true);
        FOLLOW_LAUNCHES = b.comment("At a Weighted Ejector, the head turns to follow what it throws to where it lands, then back.")
                .translation("cinecreate.configuration.followLaunches")
                .define("followLaunches", true);
        SPEC = b.build();
        TRIGGERS = Collections.unmodifiableMap(triggers);
    }

    public static Trigger trigger(SceneKind kind) {
        ModConfigSpec.EnumValue<Trigger> value = TRIGGERS.get(kind.id());
        return value == null || !SPEC.isLoaded() ? kind.trigger() : value.get();
    }

    public static boolean invitation() {
        return !SPEC.isLoaded() || INVITATION.get();
    }

    public static boolean followLaunches() {
        return !SPEC.isLoaded() || FOLLOW_LAUNCHES.get();
    }

    /** Development ({@code SmokeTest}): changes a scene's trigger the way the config screen does (saved, then applied). */
    public static void set(SceneKind kind, Trigger trigger) {
        TRIGGERS.get(kind.id()).set(trigger);
        SPEC.save();
    }

    /** Mod bus: the config screen saved, or the file was edited. */
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            // файл могут поменять и из потока слежения за конфигами: отправка — в клиентском потоке
            Minecraft.getInstance().execute(ClientConfig::sendToServer);
        }
    }

    /** At login and after every change: the server decides right-clicks by the same choices. */
    static void sendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(ScenePrefsPayload.TYPE)) {
            return;
        }
        Map<ResourceLocation, Trigger> triggers = new LinkedHashMap<>();
        for (SceneKind kind : SceneKinds.all()) {
            triggers.put(kind.id(), trigger(kind));
        }
        PacketDistributor.sendToServer(new ScenePrefsPayload(triggers));
    }
}
