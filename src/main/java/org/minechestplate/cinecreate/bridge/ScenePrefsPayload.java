package org.minechestplate.cinecreate.bridge;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.station.Trigger;

/**
 * Client to server: how this player wants each kind of scene started (kind id → trigger), from the client config. Sent
 * at login and whenever the config changes; see {@link ScenePrefs}.
 */
public record ScenePrefsPayload(Map<ResourceLocation, Trigger> triggers) implements CustomPacketPayload {
    public static final Type<ScenePrefsPayload> TYPE = new Type<>(CineCreate.id("scene_prefs"));

    /** Far more scenes than the mod will ever have. */
    private static final int MAX_SCENES = 256;

    public static final StreamCodec<FriendlyByteBuf, ScenePrefsPayload> STREAM_CODEC = ByteBufCodecs
            .<FriendlyByteBuf, ResourceLocation, Trigger, Map<ResourceLocation, Trigger>>map(HashMap::new,
                    ResourceLocation.STREAM_CODEC, NeoForgeStreamCodecs.enumCodec(Trigger.class), MAX_SCENES)
            .map(ScenePrefsPayload::new, ScenePrefsPayload::triggers);

    @Override
    public Type<ScenePrefsPayload> type() {
        return TYPE;
    }
}
