package org.minechestplate.cinecreate.client;

import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Reach;
import dev.dyslexic.cinematic.station.StationKind;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.minechestplate.cinecreate.client.scene.Grip;

/**
 * Small cards that say why a scene did not start, or why it ended (DESIGN §8.4): they slide in from the right edge, a
 * little below the status effects, stay a few seconds and slide out, at most three at a time. Each has the block's
 * icon, its name and the reason; the same reason again only brings its card back.
 */
public final class Notices {
    private Notices() {}

    /** Seconds: sliding in, staying, sliding out. */
    static final double IN = 0.35, STAY = 3.4, OUT = 0.3;
    /** At most this many cards; the oldest goes first. */
    static final int MOST = 3;
    /** Card layout, in GUI pixels: widest text, padding, the icon's room, the gap between cards, the right margin. */
    static final int TEXT = 168, PAD = 5, ICON = 20, GAP = 4, MARGIN = 6;
    /** The top of the first card, as a share of the screen's height (under the status effects). */
    static final double TOP = 0.22;
    /** How long a reason noted while Cinematic Interactions plans a scene is good for (it refuses in the same tick). */
    static final long FRESH_MS = 250;

    private record Card(ItemStack icon, Component title, Component detail, double shownAt) {}

    private static final List<Card> CARDS = new ArrayList<>();
    /** Each card's drawn height so far (cards move up smoothly when one goes). */
    private static final List<Double> SLOTS = new ArrayList<>();

    @Nullable
    private static BlockPos whyAt;
    @Nullable
    private static Component why;
    private static long whyNotedAt;

    private static double now() {
        return Util.getMillis() / 1000.0;
    }

    /** Shows a card (or brings the same one back). */
    public static void show(ItemStack icon, Component title, Component detail) {
        for (Iterator<Card> it = CARDS.iterator(); it.hasNext(); ) {
            Card card = it.next();
            if (card.title().getString().equals(title.getString()) && card.detail().getString().equals(detail.getString())) {
                it.remove();
            }
        }
        CARDS.add(new Card(icon.copy(), title, detail, now()));
        while (CARDS.size() > MOST) {
            CARDS.remove(0);
        }
    }

    /** A card about one of our blocks: its icon and name, and the reason. */
    public static void about(BlockPos pos, Component detail) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        show(new ItemStack(state.getBlock()), state.getBlock().getName(), detail);
    }

    /**
     * Our own reason this block's scene cannot start from here (a block where the player would stand, a wall between
     * the player and the scene's view), noted while Cinematic Interactions plans it: if CI then refuses, the card says
     * this rather than CI's general reason.
     */
    public static void why(BlockPos pos, Component detail) {
        whyAt = pos.immutable();
        why = detail;
        whyNotedAt = Util.getMillis();
    }

    /** From the hook on CI's refusal: a card for our blocks' scenes, with our reason if one was just noted. */
    public static void refused(ClientStation at, @Nullable Reach.Reason reason, @Nullable Component blocker) {
        if (at.kind() != StationKind.ADDON) {
            return;
        }
        Component detail;
        if (why != null && at.pos.equals(whyAt) && Util.getMillis() - whyNotedAt <= FRESH_MS) {
            detail = why;
        } else if (reason == Reach.Reason.COVERED && blocker != null) {
            detail = Component.translatable("cinecreate.notice.covered", blocker);
        } else if (reason != null) {
            String key = "cinecreate.notice." + reason.name().toLowerCase(Locale.ROOT);
            LocalPlayer player = Minecraft.getInstance().player;
            // одна рука захвата дотянулась бы — так и сказать
            boolean grip = (reason == Reach.Reason.TOO_FAR || reason == Reach.Reason.TOO_HIGH) && player != null
                    && Grip.wouldReach(player.position(), at.pos);
            detail = Component.translatable(grip ? key + ".grip" : key);
        } else {
            return;
        }
        why = null;
        about(at.pos, detail);
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /** HUD layer, above everything else. */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (CARDS.isEmpty()) {
            SLOTS.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        double t = now();
        CARDS.removeIf(card -> t - card.shownAt() > IN + STAY + OUT);
        while (SLOTS.size() < CARDS.size()) {
            SLOTS.add(-1.0);
        }
        while (SLOTS.size() > CARDS.size()) {
            SLOTS.remove(0);
        }
        int y = (int) (g.guiHeight() * TOP);
        for (int i = 0; i < CARDS.size(); i++) {
            Card card = CARDS.get(i);
            List<FormattedCharSequence> lines = font.split(card.detail(), TEXT);
            int textW = Math.max(font.width(card.title()), lines.stream().mapToInt(font::width).max().orElse(0));
            int w = PAD + ICON + Math.min(TEXT, textW) + PAD;
            int h = PAD + 10 + lines.size() * 9 + PAD - 1;
            // место карточки в стопке подъезжает плавно, когда верхняя уходит
            double slot = SLOTS.get(i) < 0 ? y : Mth.lerp(0.25, SLOTS.get(i), y);
            SLOTS.set(i, slot);
            double age = t - card.shownAt();
            double in = outBack(Mth.clamp(age / IN, 0, 1));
            double out = Mth.clamp((age - IN - STAY) / OUT, 0, 1);
            float alpha = (float) (Mth.clamp(age / (IN * 0.6), 0, 1) * (1 - out));
            int x = (int) Math.round(g.guiWidth() - MARGIN - w + (1 - in) * (w + MARGIN) + out * out * (w + MARGIN));
            int top = (int) Math.round(slot);
            draw(g, font, card, lines, x, top, w, h, alpha);
            y += h + GAP;
        }
    }

    private static void draw(GuiGraphics g, Font font, Card card, List<FormattedCharSequence> lines, int x, int y, int w, int h, float alpha) {
        if (alpha < 0.03f) {
            return;
        }
        g.fill(x, y, x + w, y + h, argb(0.62f * alpha, 14, 13, 18));
        // латунная полоска слева, как у Create
        g.fill(x, y, x + 2, y + h, argb(0.95f * alpha, 226, 178, 92));
        g.fill(x + 2, y + h - 1, x + w, y + h, argb(0.35f * alpha, 226, 178, 92));
        if (alpha > 0.5f) {
            g.renderItem(card.icon(), x + PAD, y + (h - 16) / 2);
        }
        int tx = x + PAD + ICON;
        g.drawString(font, card.title(), tx, y + PAD, argb(alpha, 240, 214, 160), false);
        int ty = y + PAD + 10;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, tx, ty, argb(alpha, 236, 232, 222), false);
            ty += 9;
        }
    }

    private static double outBack(double k) {
        double c = 1.3;
        return 1 + (c + 1) * Math.pow(k - 1, 3) + c * Math.pow(k - 1, 2);
    }

    private static int argb(float a, int r, int g, int b) {
        return Mth.clamp(Math.round(a * 255), 0, 255) << 24 | r << 16 | g << 8 | b;
    }

    /** Development ({@code SmokeTest}): the details of the cards up now. */
    public static List<String> shown() {
        List<String> all = new ArrayList<>();
        for (Card card : CARDS) {
            all.add(card.detail().getString());
        }
        return all;
    }
}
