package org.minechestplate.cinecreate.client.config;

import net.minecraft.client.gui.GuiGraphics;

/** The settings screen's look: Create's brass on andesite, plates with cut corners and rivets. */
final class Look {
    private Look() {}

    static final int PANEL = 0xD8131417, RAIL = 0xCC0E0F11, FIELD = 0xF00B0C0E, BAND = 0xB00A0B0D;
    static final int LINE = 0xFF34373E, LINE_SOFT = 0x6634373E, RIVET = 0xFF4A4E57;
    static final int BRASS = 0xFFD9A441, BRASS_DIM = 0xFF8C6A2E, BRASS_WASH = 0x30D9A441;
    static final int TEXT = 0xFFEDE6D6, DIM = 0xFF989DA5, MUTED = 0xFF5F636B, INK = 0xFF1A1408;
    static final int TEAL = 0xFF62C4BA, SLATE = 0xFF737780;
    static final int CARD = 0xFF1B1D22, CARD_HOVER = 0xFF23262D, HOVER = 0x12FFFFFF;

    /** A plate with its corners cut by a pixel, as Create's brass panels are. */
    static void plate(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int border) {
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        g.fill(x0 + 2, y0, x1 - 2, y0 + 1, border);
        g.fill(x0 + 2, y1 - 1, x1 - 2, y1, border);
        g.fill(x0, y0 + 2, x0 + 1, y1 - 2, border);
        g.fill(x1 - 1, y0 + 2, x1, y1 - 2, border);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, border);
        g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, border);
        g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, border);
        g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, border);
    }

    /** A plate with a rivet in each corner, for the screen's big panels. */
    static void casing(GuiGraphics g, int x0, int y0, int x1, int y1, int fill) {
        plate(g, x0, y0, x1, y1, fill, LINE);
        for (int[] at : new int[][] {{x0 + 3, y0 + 3}, {x1 - 5, y0 + 3}, {x0 + 3, y1 - 5}, {x1 - 5, y1 - 5}}) {
            g.fill(at[0], at[1], at[0] + 2, at[1] + 2, RIVET);
        }
    }

    /** A switch: a pill track, brass when on, its knob slid by {@code on} (0..1). */
    static void toggle(GuiGraphics g, int x, int y, double on, boolean hover, boolean dim) {
        int w = 26, h = 12;
        int track = blend(0xFF2A2C32, dim ? BRASS_DIM : BRASS, on);
        plate(g, x, y, x + w, y + h, track, hover ? BRASS : blend(LINE, BRASS_DIM, on));
        int knob = x + 2 + (int) Math.round(on * (w - 12));
        plate(g, knob, y + 2, knob + 8, y + h - 2, on > 0.5 ? 0xFFF4EAD2 : 0xFF8A8E96, 0x00000000);
    }

    /** A small label on its own tint. */
    static void chip(GuiGraphics g, int x, int y, int w, int color) {
        plate(g, x, y, x + w, y + 12, (color & 0x00FFFFFF) | 0x38000000, (color & 0x00FFFFFF) | 0xA0000000);
    }

    static int blend(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            out |= (int) Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
