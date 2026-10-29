package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.layout.LayoutPanel;

/**
 * Developer mode's markers and state panel (DESIGN §8.7), shown while the layout editor is open: the work point (gold),
 * the cursor's aim (cyan) and targets (white), the walk spots (green) and the grip (magenta).
 */
public final class DevView {
    private DevView() {}

    private static boolean smokeShown;
    private static double copiedAt = -10;

    private record Target(Vec3 center, double radius) {}

    private static final List<Target> targets = new ArrayList<>();
    private static long targetsFrame = -1;

    /** Development (SmokeTest): the view on or off without the editor. */
    public static void smokeShow(boolean on) {
        smokeShown = on;
    }

    /** Development (SmokeTest): the panel's lines this frame. */
    public static List<String> smokeLines() {
        return lines(Minecraft.getInstance());
    }

    private static boolean ours() {
        return Scene.station instanceof CineStation<?> || Scene.station instanceof ClientModeStation;
    }

    private static boolean on() {
        return (smokeShown || LayoutPanel.isOpen()) && Scene.active() && ours();
    }

    /** A cursor target tested this frame (the targets of the last frame that tested any stay shown). */
    static void target(Vec3 center, double radius) {
        if (!on()) {
            return;
        }
        long frame = FrameCounter.frame();
        if (frame != targetsFrame) {
            targets.clear();
            targetsFrame = frame;
        }
        targets.add(new Target(center, radius));
    }

    static void drawWorld(ClientStation station, @Nullable Vec3 aim, List<Vec3> stand, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        if (!on()) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        box(pose, lines, camera, station.center(), 0.05, 1f, 0.75f, 0.2f);
        if (aim != null) {
            box(pose, lines, camera, aim, 0.03, 0.3f, 0.9f, 1f);
        }
        for (Target target : targets) {
            box(pose, lines, camera, target.center(), target.radius() * 0.5, 0.95f, 0.95f, 0.95f);
        }
        for (Vec3 feet : stand) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 0.05, feet.z + 0.3)
                    .move(-camera.x, -camera.y, -camera.z), 0.3f, 1f, 0.4f, 1f);
        }
        if (station instanceof CineStation<?> scene && scene.gripDrawn() != null) {
            Vec3[] grip = scene.gripDrawn();
            line(pose, lines, grip[0].subtract(camera), grip[1].subtract(camera), 0xFFFF55FF);
        }
    }

    private static void box(PoseStack pose, VertexConsumer lines, Vec3 camera, Vec3 at, double half, float r, float g, float b) {
        LevelRenderer.renderLineBox(pose, lines, new AABB(at, at).inflate(half).move(-camera.x, -camera.y, -camera.z), r, g, b, 1f);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 a, Vec3 b, int argb) {
        Vec3 n = b.subtract(a).normalize();
        PoseStack.Pose last = pose.last();
        lines.addVertex(last, (float) a.x, (float) a.y, (float) a.z).setColor(argb).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
        lines.addVertex(last, (float) b.x, (float) b.y, (float) b.z).setColor(argb).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
    }

    public static void copyCameraPose() {
        Minecraft.getInstance().keyboardHandler.setClipboard(cameraPose());
        copiedAt = Scene.clock();
    }

    /** The camera as a scene's {@code lean} would give it: its eye from the block's corner, its yaw and pitch. */
    private static String cameraPose() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition().subtract(Vec3.atLowerCornerOf(Scene.station.pos));
        return String.format(Locale.ROOT, "new CameraRig.Pose(Vec3.atLowerCornerOf(pos).add(%.3f, %.3f, %.3f), %.1ff, %.1ff, 0)",
                eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot());
    }

    public static void onScreen(ScreenEvent.Render.Post event) {
        if (!on()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        List<String> lines = lines(mc);
        Component title = Component.translatable("cinecreate.dev.title");
        Component keys = Scene.clock() - copiedAt < 2 ? Component.translatable("cinecreate.dev.copied") : Component.translatable("cinecreate.dev.keys");
        int width = Math.max(lines.stream().mapToInt(font::width).max().orElse(0), Math.max(font.width(title), font.width(keys)));
        graphics.fill(3, 3, 9 + width, 16 + (lines.size() + 1) * 10 + 2, 0xB0101014);
        graphics.drawString(font, title, 6, 6, 0xFFFFC040);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), 6, 16 + i * 10, 0xFFE0E0E0);
        }
        graphics.drawString(font, keys, 6, 16 + lines.size() * 10, 0xFF9AA0A6);
    }

    private static List<String> lines(Minecraft mc) {
        ClientStation station = Scene.station;
        BlockPos pos = station.pos;
        List<String> lines = new ArrayList<>();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        lines.add(station.getClass().getSimpleName() + " · " + (state == null ? "?" : BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                + " at " + pos.toShortString());
        if (state != null && !state.getValues().isEmpty()) {
            lines.add(state.getValues().entrySet().stream().map(e -> e.getKey().getName() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        lines.add(String.format(Locale.ROOT, "phase %s · %.2f s", Scene.phase, Scene.time));
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition().subtract(Vec3.atLowerCornerOf(pos));
        lines.add(String.format(Locale.ROOT, "camera: corner + (%.2f, %.2f, %.2f) · yaw %.1f · pitch %.1f", eye.x, eye.y, eye.z,
                camera.getYRot(), camera.getXRot()));
        LocalPlayer player = mc.player;
        if (player != null) {
            String reach = String.format(Locale.ROOT, "feet → centre %.2f (scenes ≤ %.1f) · reach %.1f", player.position().distanceTo(Vec3.atCenterOf(pos)),
                    Bridge.SCENE_REACH, player.blockInteractionRange());
            if (station instanceof CineStation<?> scene && Grip.inHand()) {
                reach += String.format(Locale.ROOT, " · grip%s, drawn %.2f", scene.fromAfar() ? " (from afar)" : "", scene.gripLength());
            }
            lines.add(reach);
        }
        lines.add("cursor targets: " + targets.size());
        if (station instanceof CineStation<?> scene) {
            scene.devLines(lines);
        }
        return lines;
    }
}
