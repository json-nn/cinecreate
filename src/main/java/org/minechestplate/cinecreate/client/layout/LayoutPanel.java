package org.minechestplate.cinecreate.client.layout;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.client.Settings;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.client.scene.DevView;

/** CI's layout editor (its look, keys and strings) for our scenes, which CI's own cannot serve: where each scene's hand works. */
public final class LayoutPanel {
    private LayoutPanel() {}

    private static final int W = 180, H = 244, ROW_Y = 98, ROW_H = 22, HINT_Y = 172, RESET_Y = 188, SAVE_Y = 214;
    private static final String[] GEAR = {
        ".....####.....", ".....####.....", "..##.####.##..", "..##########..", "...########...", "##############", "#####....#####",
        "#####....#####", "##############", "...########...", "..##########..", "..##.####.##..", ".....####.....", ".....####....."};

    private static boolean open, saveError;
    private static double shown;
    @Nullable
    private static Layouts.Profile[] drafts;
    private static int profile, region = -1, slider = -1, tooltipField = -1;
    private static double tooltipAt;
    private static float scale = 1;
    private static double x, y, toggleX, toggleY;
    @Nullable
    private static Vec3 dragFrom;
    @Nullable
    private static Layouts.Profile dragLayout;
    @Nullable
    private static LayoutScene editing;

    /** Development (SmokeTest): developer mode on. */
    public static boolean smokeForce;

    public static boolean isOpen() {
        return open;
    }

    @Nullable
    private static LayoutScene scene() {
        return Scene.phase == Scene.Phase.ACTIVE && Scene.station instanceof LayoutScene scene && !scene.spotNames().isEmpty() ? scene : null;
    }

    private static boolean developer() {
        return Settings.developerMode() || smokeForce;
    }

    public static void open() {
        LayoutScene scene = scene();
        if (scene == null || !developer() || ((ClientStation) scene).busy()) {
            return;
        }
        editing = scene;
        drafts = Layouts.profiles(scene.layoutId());
        profile = Layouts.selected(scene.layoutId());
        region = -1;
        open = true;
        saveError = false;
        Layouts.preview(scene.layoutId(), drafts[profile]);
        ((ClientStation) scene).clearCursor();
    }

    public static void cancel() {
        open = false;
        release();
        if (editing != null) {
            Layouts.endPreview(editing.layoutId());
        }
    }

    private static void save() {
        if (editing != null && Layouts.save(editing.layoutId(), drafts, profile)) {
            cancel();
        } else {
            saveError = true;
        }
    }

    /** Development (SmokeTest): sets a field of a spot (-1: the whole layout) and saves. */
    public static void smokeSet(int spot, int field, double value) {
        region = spot;
        change(field, value);
        save();
    }

    /** Development (SmokeTest): the profile shown back to no edits, saved. */
    public static void smokeReset() {
        open();
        if (drafts != null) {
            drafts[profile] = Layouts.Profile.NONE;
            save();
        }
    }

    private static void change(int field, double value) {
        drafts[profile] = drafts[profile].with(region, drafts[profile].region(region).with(field, value));
        Layouts.preview(editing.layoutId(), drafts[profile]);
        saveError = false;
    }

    /** The field's string, as CI names it for a top or a face (the whole layout follows the first spot). */
    private static String fieldKey(int field) {
        boolean face = editing != null && editing.spotOnFace(Math.max(region, 0));
        return field == 0 ? "offset_x" : field == 1 ? face ? "offset_y" : "towards" : face ? "outward" : "height";
    }

    private static void layout() {
        Window window = Minecraft.getInstance().getWindow();
        int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
        scale = Math.min(1, Math.min(height / 470f, width / 700f));
        x = width - 8 - W * scale + (1 - shown) * 192 * scale;
        y = height * 0.14;
        toggleX = width - 8 - 30 * scale;
        // CI's guide button sits at 0.45 when CI sees no editor of its own
        toggleY = height * 0.45 + 28 * scale + 6;
    }

    private static double lx(double mx) {
        return (mx - x) / scale;
    }

    private static double ly(double my) {
        return (my - y) / scale;
    }

    private static boolean inPanel(double mx, double my) {
        return (open || shown > 0.01) && lx(mx) >= 0 && lx(mx) < W && ly(my) >= 0 && ly(my) < H;
    }

    private static boolean toggle(double mx, double my) {
        return developer() && !open && shown < 0.01 && mx >= toggleX && mx < toggleX + 30 * scale && my >= toggleY && my < toggleY + 28 * scale;
    }

    private static int rowField(double my) {
        int row = (int) ((ly(my) - ROW_Y) / ROW_H);
        return ly(my) >= ROW_Y && row < Region.FIELDS ? row : -1;
    }

    private static void slide(double mx) {
        double t = Mth.clamp((lx(mx) - 11) / 158, 0, 1);
        double value = Mth.lerp(t, -Region.MAX, Region.MAX);
        change(slider, Math.round(value / Region.STEP) * Region.STEP);
    }

    private static void release() {
        slider = -1;
        dragFrom = null;
    }

    private static int spotUnderCursor(double mx, double my) {
        int best = -1;
        double nearest = 12 * scale;
        for (int i = 0; i < editing.spotNames().size(); i++) {
            double[] at = Picking.toGui(Layouts.point(editing, i));
            if (at != null && Math.hypot(at[0] - mx, at[1] - my) < nearest) {
                nearest = Math.hypot(at[0] - mx, at[1] - my);
                best = i;
            }
        }
        return best;
    }

    /** The editing hint CI's stations show in place of their own while the editor is open. */
    public static Component hint() {
        return Component.translatable("cinematic.surface_editor.hint");
    }

    // ---- input (CI's scene screen is open: we see its events first) ----

    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (scene() == null) {
            return;
        }
        layout();
        double mx = event.getMouseX(), my = event.getMouseY();
        if (toggle(mx, my)) {
            if (event.getButton() == 0) {
                open();
            }
            event.setCanceled(true);
            return;
        }
        if (!open) {
            return;
        }
        event.setCanceled(true);
        if (event.getButton() != 0) {
            return;
        }
        if (inPanel(mx, my)) {
            double px = lx(mx), py = ly(my);
            if (py >= 29 && py <= 48) {
                profile = Math.floorMod(profile + (px < 90 ? -1 : 1), Layouts.PROFILES);
                Layouts.preview(editing.layoutId(), drafts[profile]);
            } else if (py >= 55 && py <= 75) {
                region = Math.floorMod(region + 1 + (px < 90 ? -1 : 1), editing.spotNames().size() + 1) - 1;
                release();
            } else if (rowField(my) >= 0) {
                slider = rowField(my);
                slide(mx);
            } else if (py >= RESET_Y && py <= RESET_Y + 16) {
                drafts[profile] = Layouts.Profile.NONE;
                Layouts.preview(editing.layoutId(), drafts[profile]);
                saveError = false;
            } else if (py >= SAVE_Y && py <= SAVE_Y + 18) {
                if (px < 90) {
                    save();
                } else {
                    cancel();
                }
            }
            return;
        }
        int hit = spotUnderCursor(mx, my);
        if (hit >= 0) {
            if (region >= 0) {
                region = hit;
            }
            dragFrom = Picking.cursorOnPlane(Layouts.point(editing, hit), Layouts.axes(editing, hit)[2]);
            dragLayout = drafts[profile];
        }
    }

    public static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!open || scene() == null) {
            return;
        }
        event.setCanceled(true);
        if (slider >= 0) {
            slide(event.getMouseX());
        } else if (dragFrom != null && dragLayout != null) {
            Vec3[] axes = Layouts.axes(editing, Math.max(region, 0));
            Vec3 hit = Picking.cursorOnPlane(dragFrom, axes[2]);
            if (hit != null) {
                Vec3 delta = hit.subtract(dragFrom);
                Region base = dragLayout.region(region);
                Region moved = base.with(0, base.x() + delta.dot(axes[0]) * 16).with(1, base.along() + delta.dot(axes[1]) * 16);
                drafts[profile] = dragLayout.with(region, moved);
                Layouts.preview(editing.layoutId(), drafts[profile]);
                saveError = false;
            }
        }
    }

    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (open) {
            release();
            event.setCanceled(true);
        }
    }

    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!open || scene() == null) {
            return;
        }
        event.setCanceled(true);
        int field = rowField(event.getMouseY());
        if (inPanel(event.getMouseX(), event.getMouseY()) && field >= 0) {
            change(field, drafts[profile].region(region).value(field) + Math.signum(event.getScrollDeltaY()) * Region.STEP);
        }
    }

    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (scene() == null || !developer()) {
            return;
        }
        int key = event.getKeyCode();
        if (!open) {
            if (key == GLFW.GLFW_KEY_G) {
                open();
                event.setCanceled(true);
            }
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
        } else if (key == GLFW.GLFW_KEY_C) {
            DevView.copyCameraPose();
        }
        event.setCanceled(true);
    }

    // ---- drawing ----

    public static void onRender(ScreenEvent.Render.Post event) {
        LayoutScene scene = scene();
        if (open && (scene == null || scene != editing || !developer())) {
            cancel();
        }
        shown = Anim.approach(shown, open ? 1 : 0, 16, Scene.frameDt);
        if (scene == null || !developer() && shown < 0.01) {
            return;
        }
        if (open) {
            ((ClientStation) scene).clearCursor();
        }
        layout();
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        double[] mouse = Picking.mouseGui();
        if (shown < 0.01) {
            g.pose().pushPose();
            g.pose().translate(toggleX, toggleY, 0);
            g.pose().scale(scale, scale, 1);
            glass(g, 30, 28, 1);
            if (toggle(mouse[0], mouse[1])) {
                g.fill(1, 1, 29, 27, color(0.12, 255, 255, 255));
                g.renderComponentTooltip(font, List.of(Component.translatable("cinematic.spatial_editor.open"),
                        Component.translatable("cinematic.grid_editor.shortcut")), 34, 14);
            }
            int gear = color(0.9, 103, 211, 231);
            for (int row = 0; row < GEAR.length; row++) {
                for (int col = 0; col < GEAR[row].length(); col++) {
                    if (GEAR[row].charAt(col) == '#') {
                        g.fill(8 + col, 7 + row, 9 + col, 8 + row, gear);
                    }
                }
            }
            g.pose().popPose();
            return;
        }
        if (drafts == null || editing == null) {
            return;
        }
        float a = (float) shown;
        int field = open && inPanel(mouse[0], mouse[1]) && (ly(mouse[1]) - ROW_Y) % ROW_H < 11 && slider < 0 ? rowField(mouse[1]) : -1;
        if (field != tooltipField) {
            tooltipField = field;
            tooltipAt = Scene.clock();
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 20);
        g.pose().scale(scale, scale, 1);
        glass(g, W, H, a);
        g.drawString(font, editing.layoutTitle(), 10, 10, color(a, 242, 234, 216), false);
        selector(g, font, 29, Component.translatable("cinematic.grid_editor.profile", profile + 1), a);
        selector(g, font, 55, region < 0 ? Component.translatable("cinematic.spatial_editor.all") : editing.spotNames().get(region), a);
        g.drawString(font, Component.translatable("cinematic.spatial_editor.placement"), 10, 81, color(a * 0.65, 181, 195, 199), false);
        for (int f = 0; f < Region.FIELDS; f++) {
            int yy = ROW_Y + f * ROW_H;
            double value = drafts[profile].region(region).value(f);
            String number = String.format(Locale.ROOT, "%+.2f px", value);
            g.drawString(font, Component.translatable("cinematic.spatial_editor." + fieldKey(f)), 10, yy, color(a * 0.9, 224, 227, 226), false);
            g.drawString(font, number, 170 - font.width(number), yy, color(a, 103, 211, 231), false);
            int end = 11 + (int) Math.round(158 * (value + Region.MAX) / (2 * Region.MAX));
            g.fill(11, yy + 15, 169, yy + 17, color(a * 0.14, 255, 255, 255));
            g.fill(11, yy + 15, end, yy + 17, color(a * 0.75, 103, 211, 231));
            g.fill(end - 2, yy + 12, end + 2, yy + 20, color(a, 196, 240, 245));
        }
        g.drawString(font, Component.translatable(saveError ? "cinematic.spatial_editor.save_error" : "cinematic.spatial_editor.drag_hint"),
                10, HINT_Y, saveError ? color(a * 0.75, 255, 122, 112) : color(a * 0.75, 181, 195, 199), false);
        button(g, font, 9, RESET_Y, 162, 16, Component.translatable("cinematic.spatial_editor.reset"), a, false);
        button(g, font, 9, SAVE_Y, 78, 18, Component.translatable("cinematic.grid_editor.save"), a, true);
        button(g, font, 93, SAVE_Y, 78, 18, Component.translatable("cinematic.grid_editor.cancel"), a, false);
        g.pose().popPose();
        for (int i = 0; i < editing.spotNames().size(); i++) {
            double[] at = Picking.toGui(Layouts.point(editing, i));
            if (at != null && !inPanel(at[0], at[1])) {
                int px = (int) at[0], py = (int) at[1];
                g.fill(px - 4, py - 4, px + 4, py + 5, color(a * 0.6, 12, 16, 19));
                g.drawCenteredString(font, Integer.toString(i + 1), px, py - 3, color(a * (region >= 0 && i != region ? 0.45 : 1), 196, 240, 245));
            }
        }
        if (tooltipField >= 0 && Scene.clock() - tooltipAt > 0.55) {
            g.renderComponentTooltip(font, List.of(Component.translatable("cinematic.spatial_editor." + fieldKey(tooltipField) + ".tooltip")),
                    (int) mouse[0], (int) mouse[1]);
        }
    }

    /** A marker where each hand works, while the panel shows. */
    public static void renderWorld(LayoutScene scene, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        if (shown < 0.01 || scene != editing) {
            return;
        }
        for (int i = 0; i < scene.spotNames().size(); i++) {
            Vec3[] axes = Layouts.axes(scene, i);
            Vec3 at = Layouts.point(scene, i).add(axes[2].scale(0.006));
            int alpha = (int) (shown * (region >= 0 && i != region ? 65 : 190));
            frame(pose, buffers, camera, at, axes, new double[] {0.06, 0.06}, alpha, 103, 211, 231);
        }
    }

    private static void frame(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 at, Vec3[] axes, double[] half, int alpha, int r, int g, int b) {
        double edge = 0.004;
        patch(pose, buffers, camera, at, axes, -half[0], half[0], -half[1], -half[1] + edge, alpha, r, g, b);
        patch(pose, buffers, camera, at, axes, -half[0], half[0], half[1] - edge, half[1], alpha, r, g, b);
        patch(pose, buffers, camera, at, axes, -half[0], -half[0] + edge, -half[1], half[1], alpha, r, g, b);
        patch(pose, buffers, camera, at, axes, half[0] - edge, half[0], -half[1], half[1], alpha, r, g, b);
    }

    private static void patch(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 at, Vec3[] axes, double x0, double x1, double z0,
            double z1, int alpha, int r, int g, int b) {
        VertexConsumer vertex = buffers.getBuffer(Fx.HIGHLIGHT);
        Matrix4f matrix = pose.last().pose();
        for (double[] p : new double[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
            Vec3 v = at.add(axes[0].scale(p[0])).add(axes[1].scale(p[1])).subtract(camera);
            vertex.addVertex(matrix, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, alpha);
        }
    }

    private static void selector(GuiGraphics g, Font font, int yy, Component label, float a) {
        button(g, font, 9, yy, 162, 19, label, a, false);
        g.drawString(font, "‹", 15, yy + 6, color(a, 103, 211, 231), false);
        g.drawString(font, "›", 160, yy + 6, color(a, 103, 211, 231), false);
    }

    private static void button(GuiGraphics g, Font font, int bx, int by, int w, int h, Component label, float a, boolean accent) {
        double[] mouse = Picking.mouseGui();
        boolean hover = lx(mouse[0]) >= bx && lx(mouse[0]) < bx + w && ly(mouse[1]) >= by && ly(mouse[1]) < by + h;
        g.fill(bx, by, bx + w, by + h, color(a * (accent ? (hover ? 0.3 : 0.2) : (hover ? 0.14 : 0.07)), 103, 211, 231));
        g.drawString(font, label, bx + (w - font.width(label)) / 2, by + (h - 8) / 2, color(a, 224, 238, 238), false);
    }

    private static void glass(GuiGraphics g, int w, int h, float a) {
        g.fill(0, 0, w, h, color(a * 0.85, 12, 16, 19));
        g.fill(0, 0, w, 1, color(a * 0.55, 103, 211, 231));
        g.fill(0, h - 1, w, h, color(a * 0.25, 103, 211, 231));
        g.fill(0, 1, 1, h - 1, color(a * 0.18, 103, 211, 231));
        g.fill(w - 1, 1, w, h - 1, color(a * 0.18, 103, 211, 231));
    }

    private static int color(double a, int r, int g, int b) {
        return Mth.clamp((int) Math.round(a * 255), 0, 255) << 24 | r << 16 | g << 8 | b;
    }
}
