package org.minechestplate.cinecreate.client.layout;

/** One spot's edits (or the whole layout's), in CI's units: pixels (1/16 block), degrees and percent. */
public record Region(double x, double towards, double rotation, double width, double depth, double height, double size) {
    public static final Region NONE = new Region(0, 0, 0, 100, 100, 0, 100);
    public static final int FIELDS = 7;

    public double value(int field) {
        return switch (field) {
            case 0 -> x;
            case 1 -> towards;
            case 2 -> rotation;
            case 3 -> width;
            case 4 -> depth;
            case 5 -> height;
            default -> size;
        };
    }

    public Region with(int field, double value) {
        double v = Math.max(min(field), Math.min(max(field), value));
        return new Region(field == 0 ? v : x, field == 1 ? v : towards, field == 2 ? v : rotation, field == 3 ? v : width,
                field == 4 ? v : depth, field == 5 ? v : height, field == 6 ? v : size);
    }

    static boolean percent(int field) {
        return field == 3 || field == 4 || field == 6;
    }

    static double min(int field) {
        return percent(field) ? 50 : field == 2 ? -180 : field == 5 ? -2 : -6;
    }

    static double max(int field) {
        return percent(field) ? 150 : -min(field);
    }

    static double step(int field) {
        return percent(field) || field == 2 ? 1 : 0.125;
    }

    double[] values() {
        return new double[] {x, towards, rotation, width, depth, height, size};
    }

    static Region of(double[] v) {
        Region r = NONE;
        for (int i = 0; i < Math.min(FIELDS, v.length); i++) {
            r = r.with(i, v[i]);
        }
        return r;
    }
}
