package org.minechestplate.cinecreate.station;

import net.minecraft.resources.ResourceLocation;

/**
 * A scene as players know it and pick its trigger for. One block can play several (a depot is pressing once a press
 * stands over it), so the trigger belongs to the kind, not to the block. {@code trigger} is the default (DESIGN §6);
 * the id is the config and {@code scene_prefs} key, and its path names the lang keys.
 */
public record SceneKind(ResourceLocation id, Trigger trigger) {

    public String name() {
        return id.getPath();
    }
}
