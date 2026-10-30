package org.minechestplate.cinecreate.client.layout;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** A scene whose hands' working spots can be moved in developer mode (DESIGN §8.7), read back through {@link Layouts#point}. */
public interface LayoutScene {
    /** Profiles are kept per id, shared by every block of the scene. */
    String layoutId();

    Component layoutTitle();

    List<Component> spotNames();

    /** Where the spot is with no edits. */
    Vec3 spotBase(int spot);

    /** Right, as the player faces the work (horizontal). */
    Vec3 layoutAcross();

    /** From the work towards the player (horizontal). */
    Vec3 layoutTowards();

    /** On a face towards the player rather than on a top: offsets go up the face and out of it. */
    default boolean spotOnFace(int spot) {
        return false;
    }
}
