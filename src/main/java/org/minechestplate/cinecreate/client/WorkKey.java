package org.minechestplate.cinecreate.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dyslexic.cinematic.api.client.AddonScenes;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Reach;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.station.StationKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.bridge.ScenePrefsPayload;
import org.minechestplate.cinecreate.client.scene.Grip;
import org.minechestplate.cinecreate.station.SceneBlocks;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.Trigger;

/**
 * The "Work at the machine" key and its "[H] Work at the depot" plate under the crosshair (DESIGN §5.3). H, as CI's
 * campfire: NeoForge hands a key to every mapping on it.
 */
public final class WorkKey {
    private WorkKey() {}

    public static final KeyMapping WORK = new KeyMapping("key.cinecreate.work", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.cinecreate");

    /** As near as Cinematic Interactions wants a player for its campfire; with an Extendo Grip, as far as a scene can be begun. */
    private static final double REACH_SQR = 4.5 * 4.5, GRIP_REACH_SQR = Bridge.SCENE_REACH * Bridge.SCENE_REACH;

    /** The invitation's fade (0..1), and the text it fades out with. */
    private static double shown;
    private static Component text = Component.empty();
    @Nullable
    private static BlockPos checkedPos, checkedFrom;
    private static long checkedAt;
    private static boolean checkedGrip;
    @Nullable
    private static Level checkedLevel;
    @Nullable
    private static Reach.Plan checked;

    /**
     * What the crosshair is on, worked out once a tick: the plate is drawn every frame, and telling a depot's kind of
     * scene looks round it for a fan's stream.
     */
    @Nullable
    private static Target lookedNow;
    private static int lookedTick = -1, ticks;
    private static boolean clientModeNow;

    static void onTick(ClientTickEvent.Post event) {
        ticks++;
        while (WORK.consumeClick()) {
            Target target = looked();
            Reach.Plan plan = target == null ? null : plan(Minecraft.getInstance().player, target.pos());
            if (plan == null) {
                continue;
            }
            if (ClientMode.active()) {
                ClientMode.begin(target.pos());
            } else if (plan.scene()) {
                Scene.begin(target.pos(), StationKind.ADDON);
            } else if (plan.reason() != null) {
                CiNotice.refused(target.pos(), plan.reason(), plan.blocker());
            }
        }
    }

    /** The block the plate invites to, or null. */
    @Nullable
    public static BlockPos invited() {
        Target target = invitation();
        return target == null ? null : target.pos();
    }

    @Nullable
    private static Target invitation() {
        Target target = ClientConfig.invitation() ? looked() : null;
        if (target == null || ClientConfig.trigger(target.kind()) != Trigger.KEY) {
            return null;
        }
        if (ClientMode.active()) {
            return clientModeNow ? target : null;
        }
        Reach.Plan plan = plan(Minecraft.getInstance().player, target.pos());
        return plan != null && plan.scene() ? target : null;
    }

    /** The invitation, a GUI layer over the crosshair (scenes hide it: Cinematic Interactions hides every such layer). */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Target target = invitation();
        if (target != null) {
            text = Component.translatable("cinecreate.invite." + target.kind().name());
        }
        boolean on = target != null;
        shown = Anim.approach(shown, on ? 1 : 0, on ? 9 : 12, Scene.frameDt);
        float alpha = (float) Anim.smoother(shown);
        if (Math.round(alpha * 255) < 8) {
            return;
        }
        // как у костра CI: клавиша в рамке, текст, тёплая черта снизу; выплывает на 5 px снизу
        Font font = Minecraft.getInstance().font;
        Component key = WORK.getTranslatedKeyMessage();
        int keyW = Math.max(11, font.width(key) + 6);
        int w = 6 + keyW + 5 + font.width(text) + 7, h = 15;
        int x = (g.guiWidth() - w) / 2;
        int y = g.guiHeight() / 2 + 13 + Math.round((1 - alpha) * 5);
        g.fill(x, y, x + w, y + h, argb(0.5f * alpha, 12, 12, 16));
        g.fill(x, y + h - 1, x + w, y + h, argb(0.35f * alpha, 255, 196, 120));
        int kx = x + 6, ky = y + 2;
        g.fill(kx, ky, kx + keyW, ky + 11, argb(0.85f * alpha, 242, 234, 216));
        g.fill(kx + 1, ky + 1, kx + keyW - 1, ky + 10, argb(0.9f * alpha, 30, 28, 34));
        g.drawString(font, key, kx + (keyW - font.width(key)) / 2, ky + 2, argb(alpha, 255, 226, 150), false);
        g.drawString(font, text, kx + keyW + 5, y + 4, argb(alpha, 242, 234, 216), false);
    }

    private record Target(BlockPos pos, SceneKind kind) {}

    @Nullable
    private static Target looked() {
        if (lookedTick != ticks) {
            lookedTick = ticks;
            lookedNow = lookUp();
            clientModeNow = lookedNow != null && ClientMode.canBegin(lookedNow.pos());
        }
        return lookedNow;
    }

    @Nullable
    private static Target lookUp() {
        Minecraft mc = Minecraft.getInstance();
        if (Scene.active() || mc.screen != null || mc.player == null || mc.level == null || mc.player.isSpectator()
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = SceneBlocks.target(mc.level, hit.getBlockPos());
        SceneKind kind = pos == null ? null : SceneBlocks.kindAt(mc.level, pos);
        ClientPacketListener connection = mc.getConnection();
        boolean ours = kind != null && ClientConfig.trigger(kind) != Trigger.OFF
                && AddonScenes.available(mc.level.getBlockState(pos))
                && connection != null && (connection.hasChannel(ScenePrefsPayload.TYPE) || ClientMode.active());
        double reach = Grip.inHand() ? GRIP_REACH_SQR : REACH_SQR;
        return ours && mc.player.distanceToSqr(pos.getCenter()) < reach ? new Target(pos, kind) : null;
    }

    /** Whether (and how) the scene can happen there; cached per block, the player's block, grip in hand and world. */
    @Nullable
    private static Reach.Plan plan(LocalPlayer player, BlockPos pos) {
        BlockPos from = player.blockPosition();
        boolean grip = Grip.inHand();
        long now = player.level().getGameTime();
        if (!pos.equals(checkedPos) || !from.equals(checkedFrom) || grip != checkedGrip || player.level() != checkedLevel || now - checkedAt >= 10) {
            checkedAt = now;
            checkedPos = pos.immutable();
            checkedFrom = from;
            checkedGrip = grip;
            checkedLevel = player.level();
            checked = choose(player, pos);
        }
        return checked;
    }

    /**
     * The way Cinematic Interactions picks a place to work from ({@code Scene.choose}, private there): the working
     * sides, nearest first, then standing beside the work. The first side's plan says why, when none works.
     */
    @Nullable
    private static Reach.Plan choose(LocalPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        double dx = player.getX() - pos.getX() - 0.5, dz = player.getZ() - pos.getZ() - 0.5;
        List<Direction> sides = new ArrayList<>(StationKind.ADDON.sides(state));
        sides.sort(Comparator.comparingDouble(side -> -(side.getStepX() * dx + side.getStepZ() * dz)));
        Reach.Plan first = null;
        for (Direction side : sides) {
            Reach.Plan plan = Reach.plan(AddonScenes.create(pos, state, player, side), player);
            if (plan.scene()) {
                return plan;
            }
            if (first == null) {
                first = plan;
            }
        }
        for (Direction side : sides) {
            ClientStation station = AddonScenes.create(pos, state, player, side);
            if (!station.worksAtAnAngle()) {
                break;
            }
            Reach.Plan plan = Reach.planBeside(station, player);
            if (plan.scene()) {
                return plan;
            }
        }
        return first;
    }

    private static int argb(float a, int r, int g, int b) {
        return Math.max(0, Math.min(255, Math.round(a * 255))) << 24 | r << 16 | g << 8 | b;
    }
}
