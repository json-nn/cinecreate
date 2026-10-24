package org.minechestplate.cinecreate.station;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/** How a scene starts. Each player picks one per scene in the client config (DESIGN §5.3, "Trigger policy"). */
public enum Trigger implements TranslatableEnum {
    /** A right-click on the working face starts the scene, as at Cinematic Interactions' own stations. */
    USE,
    /** A right-click is Create's own; the "Work at the machine" key starts the scene. */
    KEY,
    /** Never a scene: always Create's own use. */
    OFF;

    @Override
    public Component getTranslatedName() {
        return Component.translatable("cinecreate.trigger." + name().toLowerCase(Locale.ROOT));
    }
}
