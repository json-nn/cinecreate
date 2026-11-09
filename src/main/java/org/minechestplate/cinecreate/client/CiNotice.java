package org.minechestplate.cinecreate.client;

import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Reach;
import dev.dyslexic.cinematic.client.ReachNotice;
import dev.dyslexic.cinematic.client.Scene;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.client.scene.CineStation;
import org.minechestplate.cinecreate.client.scene.Grip;

/** Our notices, shown in Cinematic Interactions' own notice line ({@code ReachNotice}). */
public final class CiNotice {
    private CiNotice() {}

    /** A reason noted while CI plans a scene is good for this long: CI refuses in the same tick. */
    private static final long FRESH_MS = 250;

    @Nullable
    private static Field line, shownAt;
    private static boolean resolved;
    @Nullable
    private static BlockPos whyAt;
    @Nullable
    private static Component why;
    private static long whyNotedAt;

    public static void show(Component text) {
        if (fields()) {
            try {
                if (text.getString().equals(ReachNotice.text()) && Scene.clock() - shownAt.getDouble(null) < 1) {
                    return;
                }
                line.set(null, text);
                shownAt.set(null, Scene.clock());
                RECENT.add(new Shown(text.getString(), Util.getMillis()));
                return;
            } catch (IllegalAccessException | IllegalArgumentException ignored) {
                line = null;
            }
        }
        Minecraft.getInstance().gui.setOverlayMessage(text, false);
    }

    /** Our own reason this block's scene cannot start from here, said instead of CI's if CI then refuses it. */
    public static void why(BlockPos pos, Component text) {
        whyAt = pos.immutable();
        why = text;
        whyNotedAt = Util.getMillis();
    }

    /** Only for our scenes: every add-on's station is of kind ADDON, and another add-on's must keep CI's own notice. */
    public static void refused(ClientStation at, @Nullable Reach.Reason reason, @Nullable Component blocker) {
        if (at instanceof CineStation<?>) {
            refused(at.pos, reason, blocker);
        }
    }

    public static void refused(BlockPos pos, @Nullable Reach.Reason reason, @Nullable Component blocker) {
        Component ours = why != null && pos.equals(whyAt) && Util.getMillis() - whyNotedAt <= FRESH_MS ? why : null;
        why = null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (ours == null && (reason == Reach.Reason.TOO_FAR || reason == Reach.Reason.TOO_HIGH) && player != null
                && Grip.wouldReach(player.position(), pos)) {
            ours = Component.translatable("cinecreate.notice." + reason.name().toLowerCase(Locale.ROOT) + ".grip");
        }
        if (ours != null) {
            show(ours);
        } else if (reason != null) {
            ReachNotice.show(reason, blocker);
        }
    }

    private record Shown(String text, long at) {}

    private static final java.util.Deque<Shown> RECENT = new java.util.ArrayDeque<>();

    /** Development (SmokeTest): the notice up now. */
    public static List<String> shown() {
        String text = ReachNotice.text();
        return text == null ? List.of() : List.of(text);
    }

    /** Development (SmokeTest): every notice of the last 8 seconds, CI's own included. */
    public static List<String> recent() {
        String now = ReachNotice.text();
        if (now != null && RECENT.stream().noneMatch(shown -> shown.text().equals(now))) {
            RECENT.add(new Shown(now, Util.getMillis()));
        }
        RECENT.removeIf(shown -> Util.getMillis() - shown.at() > 8000);
        return RECENT.stream().map(Shown::text).toList();
    }

    private static boolean fields() {
        if (!resolved) {
            resolved = true;
            try {
                line = ReachNotice.class.getDeclaredField("line");
                shownAt = ReachNotice.class.getDeclaredField("shownAt");
                line.setAccessible(true);
                shownAt.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                line = null;
                CineCreate.LOGGER.warn("[cinecreate] Cinematic Interactions' notice line changed ({}): notices go to the action bar", e.toString());
            }
        }
        return line != null && shownAt != null;
    }
}
