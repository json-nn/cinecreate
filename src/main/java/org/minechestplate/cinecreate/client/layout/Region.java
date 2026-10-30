package org.minechestplate.cinecreate.client.layout;

/** Where a hand works, moved from the scene's own spot: across, along (towards the player on a top, up on a face) and out, in pixels. */
public record Region(double x, double along, double out) {
    public static final Region NONE = new Region(0, 0, 0);
    public static final int FIELDS = 3;
    static final double MAX = 6, STEP = 0.125;

    public double value(int field) {
        return field == 0 ? x : field == 1 ? along : out;
    }

    public Region with(int field, double value) {
        double v = Math.max(-MAX, Math.min(MAX, value));
        return new Region(field == 0 ? v : x, field == 1 ? v : along, field == 2 ? v : out);
    }

    Region blend(Region to, double t) {
        return new Region(x + (to.x - x) * t, along + (to.along - along) * t, out + (to.out - out) * t);
    }

    double[] values() {
        return new double[] {x, along, out};
    }

    /** From a saved array: [x, along, out], or the older 7/11-field layouts (x, along, …, out at 5). */
    static Region of(double[] v) {
        if (v.length >= 6) {
            return NONE.with(0, v[0]).with(1, v[1]).with(2, v[5]);
        }
        Region r = NONE;
        for (int i = 0; i < Math.min(FIELDS, v.length); i++) {
            r = r.with(i, v[i]);
        }
        return r;
    }
}
