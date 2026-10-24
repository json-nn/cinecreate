package org.minechestplate.cinecreate.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.minechestplate.cinecreate.CineCreate;

public final class CineTags {
    private CineTags() {}

    /** Items with their own Create interaction (wrench, goggles, filters...): a click with them never starts a scene. */
    public static final TagKey<Item> SCENE_BYPASS = TagKey.create(Registries.ITEM, CineCreate.id("scene_bypass"));
}
