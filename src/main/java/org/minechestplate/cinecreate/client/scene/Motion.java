package org.minechestplate.cinecreate.client.scene;

import dev.dyslexic.cinematic.client.Scene;

/** Whether a machine part read every frame is moving: it changed within the last moment (a stalled part is not busy). */
final class Motion {
    private double last = Double.NaN, movedAt = -10;

    boolean moving(double value) {
        if (!Double.isNaN(last) && Math.abs(value - last) > 1e-4) {
            movedAt = Scene.clock();
        }
        last = value;
        return Scene.clock() - movedAt < 0.2;
    }
}
