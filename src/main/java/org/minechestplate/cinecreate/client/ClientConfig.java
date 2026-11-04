package org.minechestplate.cinecreate.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
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
    public static final ModConfigSpec.BooleanValue INVITATION;
    public static final ModConfigSpec.BooleanValue FOLLOW_LAUNCHES;
    public static final ModConfigSpec.IntValue WATCH_REACH;
    public static final ModConfigSpec.BooleanValue WALL_MAP, EDGE_TURN;
    public static final ModConfigSpec.BooleanValue JOLTS, FLASHES;
    public static final ModConfigSpec.BooleanValue CLIENT_MODE;
    public static final ModConfigSpec.BooleanValue PARTICLES, FOLLOW_GAME;
    public static final ModConfigSpec.IntValue AMOUNT;
    private static final Map<ResourceLocation, ModConfigSpec.BooleanValue> PARTICLES_PER_KIND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Map<ResourceLocation, ModConfigSpec.EnumValue<Trigger>> triggers = new LinkedHashMap<>();
        b.translation("cinecreate.configuration.scenes").push("scenes");
        for (SceneKind kind : SceneKinds.all()) {
            triggers.put(kind.id(), b
                    .comment("How the " + kind.name() + " scene starts: USE = right-click, KEY = press H (right-click stays"
                            + " normal), OFF = no scene.")
                    .translation("cinecreate.configuration." + kind.name())
                    .defineEnum(kind.name(), kind.trigger()));
        }
        b.pop();
        INVITATION = b.comment("Show the \"[H] Work at...\" hint under the crosshair on blocks set to KEY.")
                .translation("cinecreate.configuration.invitation")
                .define("invitation", true);
        FOLLOW_LAUNCHES = b.comment("At a Weighted Ejector, the view follows what it throws.")
                .translation("cinecreate.configuration.followLaunches")
                .define("followLaunches", true);
        WATCH_REACH = b.comment("Farthest launch target the view follows, in blocks.")
                .translation("cinecreate.configuration.watchReach")
                .defineInRange("watchReach", 32, 8, 64);
        WALL_MAP = b.comment("With an Extendo Grip on a big crafter wall, show a map of the part in view.")
                .translation("cinecreate.configuration.wallMap")
                .define("wallMap", true);
        EDGE_TURN = b.comment("With an Extendo Grip on a big crafter wall, resting the cursor at a screen edge turns the view.")
                .translation("cinecreate.configuration.edgeTurn")
                .define("edgeTurn", true);
        JOLTS = b.comment("Small camera kicks on press strokes, impacts and launches.")
                .translation("cinecreate.configuration.jolts")
                .define("cameraJolts", true);
        FLASHES = b.comment("A soft flash when something is made.")
                .translation("cinecreate.configuration.flashes")
                .define("resultFlashes", true);
        CLIENT_MODE = b.comment("Play the scenes on servers without this add-on (applies the next time you join).")
                .translation("cinecreate.configuration.clientMode")
                .define("clientMode", false);
        b.translation("cinecreate.configuration.particles").push("particles");
        PARTICLES = b.comment("All scene particles on or off.")
                .translation("cinecreate.configuration.particles.enabled")
                .define("enabled", true);
        AMOUNT = b.comment("How many particles spawn, in percent.")
                .translation("cinecreate.configuration.particles.amount")
                .defineInRange("amount", 100, 0, 100);
        FOLLOW_GAME = b.comment("Use fewer particles when Minecraft's Particles option is lowered.")
                .translation("cinecreate.configuration.particles.followGame")
                .define("followGame", true);
        Map<ResourceLocation, ModConfigSpec.BooleanValue> perKind = new LinkedHashMap<>();
        b.translation("cinecreate.configuration.particles.blocks").push("blocks");
        for (SceneKind kind : SceneKinds.all()) {
            perKind.put(kind.id(), b.comment("Particles in the " + kind.name() + " scene.")
                    .translation("cinecreate.configuration.particles." + kind.name())
                    .define(kind.name(), true));
        }
        b.pop(2);
        SPEC = b.build();
        TRIGGERS = Collections.unmodifiableMap(triggers);
        PARTICLES_PER_KIND = Collections.unmodifiableMap(perKind);
    }

    public static Trigger trigger(SceneKind kind) {
        if (!CiApi.ok()) {
            return Trigger.OFF;
        }
        ModConfigSpec.EnumValue<Trigger> value = TRIGGERS.get(kind.id());
        return value == null || !SPEC.isLoaded() ? kind.trigger() : value.get();
    }

    public static boolean invitation() {
        return !SPEC.isLoaded() || INVITATION.get();
    }

    public static boolean followLaunches() {
        return !SPEC.isLoaded() || FOLLOW_LAUNCHES.get();
    }

    public static int watchReach() {
        return SPEC.isLoaded() ? WATCH_REACH.get() : WATCH_REACH.getDefault();
    }

    public static boolean wallMap() {
        return !SPEC.isLoaded() || WALL_MAP.get();
    }

    public static boolean edgeTurn() {
        return !SPEC.isLoaded() || EDGE_TURN.get();
    }

    public static boolean jolts() {
        return !SPEC.isLoaded() || JOLTS.get();
    }

    public static boolean flashes() {
        return !SPEC.isLoaded() || FLASHES.get();
    }

    public static ModConfigSpec.EnumValue<Trigger> triggerValue(SceneKind kind) {
        return TRIGGERS.get(kind.id());
    }

    public static ModConfigSpec.BooleanValue particlesValue(SceneKind kind) {
        return PARTICLES_PER_KIND.get(kind.id());
    }

    public static boolean clientMode() {
        return SPEC.isLoaded() && CLIENT_MODE.get();
    }

    /**
     * How much of a scene's particles start, 0..1: none with all particles or this block's off, else the amount, halved
     * or none by the game's own setting when it is followed. A null kind: a scene of no block.
     */
    public static double particleShare(@Nullable SceneKind kind) {
        if (SPEC.isLoaded()) {
            ModConfigSpec.BooleanValue own = kind == null ? null : PARTICLES_PER_KIND.get(kind.id());
            if (!PARTICLES.get() || own != null && !own.get()) {
                return 0;
            }
        }
        double share = SPEC.isLoaded() ? AMOUNT.get() / 100.0 : 1;
        if (!SPEC.isLoaded() || FOLLOW_GAME.get()) {
            ParticleStatus status = Minecraft.getInstance().options.particles().get();
            share *= status == ParticleStatus.ALL ? 1 : status == ParticleStatus.DECREASED ? 0.5 : 0;
        }
        return share;
    }

    /** Development (SmokeTest): a block's particles on or off (null: all of them), as the config screen sets them. */
    public static void setParticles(@Nullable SceneKind kind, boolean on) {
        (kind == null ? PARTICLES : PARTICLES_PER_KIND.get(kind.id())).set(on);
        SPEC.save();
    }

    /** Development (SmokeTest): changes a scene's trigger the way the config screen does (saved, then applied). */
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
    public static void sendToServer() {
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
