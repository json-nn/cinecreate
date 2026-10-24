package org.minechestplate.cinecreate.client;

import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Counts rendered frames, for drawing that must happen once a frame however often it is asked for. */
public final class FrameCounter {
    private FrameCounter() {}

    private static long frame;

    public static long frame() {
        return frame;
    }

    static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }
}
