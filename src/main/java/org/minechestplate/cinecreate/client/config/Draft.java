package org.minechestplate.cinecreate.client.config;

import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** A setting as the screen edits it: written to the config only on Done. */
final class Draft<T> {
    /** The setting's lang key; its description is the same key with {@code .tooltip}. */
    final String key;
    private final ModConfigSpec.ConfigValue<T> value;
    private final Function<T, Component> shown;
    T now;

    Draft(String key, ModConfigSpec.ConfigValue<T> value, Function<T, Component> shown) {
        this.key = key;
        this.value = value;
        this.shown = shown;
        this.now = value.get();
    }

    T standard() {
        return value.getDefault();
    }

    boolean standing() {
        return now.equals(standard());
    }

    boolean edited() {
        return !now.equals(value.get());
    }

    void reset() {
        now = standard();
    }

    void store() {
        if (edited()) {
            value.set(now);
        }
    }

    Component show(T of) {
        return shown.apply(of);
    }
}
