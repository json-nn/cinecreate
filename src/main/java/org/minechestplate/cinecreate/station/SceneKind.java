package org.minechestplate.cinecreate.station;

import net.minecraft.resources.ResourceLocation;

/**
 * A scene as players know it and pick its trigger: the bare depot, the depot under a press. One block can play
 * several (a depot is pressing once a press stands over it), so the trigger belongs to the kind, not to the block.
 *
 * @param id      the config key and the {@code scene_prefs} key; its path names the lang keys
 *                ({@code cinecreate.invite.<path>}, {@code cinecreate.configuration.<path>})
 * @param trigger how it starts until the player picks otherwise (DESIGN §6)
 */
public record SceneKind(ResourceLocation id, Trigger trigger) {

    public String name() {
        return id.getPath();
    }
}
