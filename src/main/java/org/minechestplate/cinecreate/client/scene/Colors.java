package org.minechestplate.cinecreate.client.scene;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Colours for the scenes' effects: 0xRRGGBB split into 0..1 channels, and a fluid's colour as the game draws it. */
final class Colors {
    private Colors() {}

    static float red(int color) {
        return (color >> 16 & 0xFF) / 255f;
    }

    static float green(int color) {
        return (color >> 8 & 0xFF) / 255f;
    }

    static float blue(int color) {
        return (color & 0xFF) / 255f;
    }

    /** The fluid's colour (its tint; water's is blue), or white for no fluid. */
    static int of(FluidStack fluid) {
        return fluid.isEmpty() ? 0xFFFFFF : IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid) & 0xFFFFFF;
    }
}
