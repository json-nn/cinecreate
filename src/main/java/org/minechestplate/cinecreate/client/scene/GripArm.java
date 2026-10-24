package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import dev.dyslexic.cinematic.client.Scene;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An Extendo Grip as the scenes draw it (DESIGN §8.5), from Create's own models, as Create draws it in the hand: its
 * handle (with the cog turning on it) in the player's fist, the scissor lattice opened out to the work (its links in
 * Create's texture), and at the end the grip's claw, one of Create's deployer hands: pointing when it reaches, holding
 * when it carries or holds on, a fist at rest. The player's own arm is only the one holding the handle.
 */
final class GripArm {
    private GripArm() {}

    /** The claw's poses: Create's deployer hands, as Create's grip and deployer use them. */
    enum Claw {
        /** Reaching, empty. */
        POINTING,
        /** Carrying something, or holding on to it. */
        HOLDING,
        /** At rest, a fist (as Create's grip is held in first person). */
        PUNCHING;

        PartialModel model() {
            return switch (this) {
                case POINTING -> AllPartialModels.DEPLOYER_HAND_POINTING;
                case HOLDING -> AllPartialModels.DEPLOYER_HAND_HOLDING;
                case PUNCHING -> AllPartialModels.DEPLOYER_HAND_PUNCHING;
            };
        }
    }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("create", "item/extendo_grip");
    /** The grip's handle (the item's own model) and the cog on it: loaded with the game's models, see {@link #load()}. */
    private static final PartialModel HANDLE = PartialModel.of(ResourceLocation.fromNamespaceAndPath("create", "item/extendo_grip/item")),
            COG = PartialModel.of(ResourceLocation.fromNamespaceAndPath("create", "item/extendo_grip/cog"));
    /** The grip's size against Create's models (the scenes' arms are the player's at 0.55: the grip a little smaller in the fist). */
    static final float SCALE = 0.4f;
    /** In the handle's model (pixels): where the fist holds it, the front of its cap (the lattice starts there), the cog's axle. */
    private static final Vector3f FIST = new Vector3f(8, 6.5f, 12.5f), FRONT = new Vector3f(8, 9, 1), AXLE = new Vector3f(8, 9, 0);
    /** In the claw's model (pixels): the palm, which goes where the scene sends the hand, and the back of its cap. */
    private static final Vector3f PALM = new Vector3f(8, 8, 16.5f), WRIST = new Vector3f(8, 8, 12);
    /** Along the grip (blocks): from the fist to the front of the cap, and from the claw's cap to its palm. */
    static final double AHEAD = (FIST.z - FRONT.z) / 16 * SCALE, PALM_DEPTH = (PALM.z - WRIST.z) / 16 * SCALE;
    /**
     * The lattice: how long one cross is at most, how long its links are (opened less, they spread wider, as scissors),
     * how far its ends spread at most, a link's width and thickness, and the least it folds to (blocks).
     */
    static final double CROSS = 0.26, BAR = 0.3, SPREAD = 0.08, LINK = 0.03, THICK = 0.012, LATTICE = 0.05;
    /** Folded right up, from the fist to the claw's palm: the grip is never shorter. */
    static final double FOLDED = AHEAD + LATTICE + PALM_DEPTH;
    /** The cog on the handle turns as Create's does, 2° a tick (degrees a second). */
    private static final double COG_SPIN = -40;

    /** Creates the grip's models before the game loads its models (they are loaded with them): from the client's constructor. */
    static void load() {}

    /**
     * Draws the grip from the fist holding it to its claw.
     *
     * @param clawDir which way the claw points (a hook over an edge), or null: along the grip
     * @param facing  the way the player faces, should the grip or the claw point straight up or down
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 hand, Vec3 claw, @Nullable Vec3 clawDir, Claw grip,
                     Vec3 facing, int light) {
        Vec3 way = claw.subtract(hand);
        double length = way.length();
        if (length < 1e-3) {
            return;
        }
        Vec3 d = way.scale(1 / length);
        Vec3 c = clawDir == null ? d : clawDir.normalize();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        // рукоять Create в кулаке, ствол — к клешне (модель смотрит в −z)
        Quaternionf handle = along(d.scale(-1), facing);
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        pose.mulPose(handle);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-FIST.x / 16, -FIST.y / 16, -FIST.z / 16);
        model(pose, vc, HANDLE.get(), light);
        // шестерня крутится на своей оси, как у Create
        pose.translate(AXLE.x / 16, AXLE.y / 16, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) (Scene.clock() * COG_SPIN % 360)));
        pose.translate(-AXLE.x / 16, -AXLE.y / 16, 0);
        model(pose, vc, COG.get(), light);
        pose.popPose();
        // клешня: ладонь там, куда сцена послала руку
        Quaternionf turn = along(c, facing);
        pose.pushPose();
        pose.translate(claw.x, claw.y, claw.z);
        pose.mulPose(turn);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-PALM.x / 16, -PALM.y / 16, -PALM.z / 16);
        model(pose, vc, grip.model().get(), light);
        pose.popPose();
        // решётка: от колпачка рукояти до запястья клешни
        Vec3 from = hand.add(offset(handle, FRONT, FIST)), to = claw.add(offset(turn, WRIST, PALM));
        lattice(pose, vc, from, to, light);
        pose.popPose();
    }

    /** Scissors from {@code from} to {@code to}, lying flat (from above, their crosses show). */
    private static void lattice(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, int light) {
        Vec3 span = to.subtract(from);
        double reach = span.length();
        if (reach < 1e-3) {
            return;
        }
        Vec3 d = span.scale(1 / reach);
        Vec3 across = new Vec3(0, 1, 0).cross(d);
        if (across.lengthSqr() < 1e-6) {
            across = new Vec3(1, 0, 0);
        }
        across = across.normalize();
        Vec3 normal = d.cross(across).normalize();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TEXTURE);
        int crosses = Math.max(1, (int) Math.ceil(reach / CROSS));
        double step = reach / crosses;
        // ножницы: чем короче шаг, тем шире расходятся концы звеньев
        double spread = Math.min(SPREAD, Math.sqrt(Math.max(0, BAR * BAR - step * step)) / 2);
        for (int i = 0; i < crosses; i++) {
            Vec3 a = from.add(d.scale(i * step)), b = from.add(d.scale((i + 1) * step));
            bar(pose, vc, a.add(across.scale(spread)), b.subtract(across.scale(spread)), normal, LINK, THICK, sprite, 4, 0, 6, 13, light);
            bar(pose, vc, a.subtract(across.scale(spread)), b.add(across.scale(spread)), normal, LINK, THICK, sprite, 4, 0, 6, 13, light);
        }
    }

    /** A model's point (pixels) from another, turned and scaled as drawn (blocks). */
    private static Vec3 offset(Quaternionf turn, Vector3f point, Vector3f from) {
        Vector3f v = turn.transform(new Vector3f(point).sub(from).mul(SCALE / 16));
        return new Vec3(v.x, v.y, v.z);
    }

    /** The turn that sets a model's +z along {@code z}, its +y as near up as can be (else toward {@code facing}). */
    private static Quaternionf along(Vec3 z, Vec3 facing) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 y = up.subtract(z.scale(up.dot(z)));
        if (y.lengthSqr() < 1e-4) {
            y = facing.subtract(z.scale(facing.dot(z)));
        }
        if (y.lengthSqr() < 1e-8) {
            y = new Vec3(1, 0, 0).subtract(z.scale(z.x));
        }
        y = y.normalize();
        Vec3 x = y.cross(z);
        return new Matrix3f(new Vector3f((float) x.x, (float) x.y, (float) x.z), new Vector3f((float) y.x, (float) y.y, (float) y.z),
                new Vector3f((float) z.x, (float) z.y, (float) z.z)).getNormalizedRotation(new Quaternionf());
    }

    /** A baked model's quads, as the game draws block models (nothing if Create's model is missing). */
    private static void model(PoseStack pose, VertexConsumer vc, @Nullable BakedModel model, int light) {
        if (model == null) {
            return;
        }
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, null, model, 1f, 1f, 1f, light,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** A box from {@code a} to {@code b}, its cross-section {@code w} by {@code t}, its long faces textured from the sprite's pixel rectangle. */
    private static void bar(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 normal, double w, double t,
                            TextureAtlasSprite sprite, float u0, float v0, float u1, float v1, int light) {
        Vec3 len = b.subtract(a);
        if (len.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 d = len.normalize();
        Vec3 x = normal.cross(d).normalize().scale(w / 2), y = d.cross(x).normalize().scale(t / 2);
        Vec3[] corners = {a.add(x).add(y), a.subtract(x).add(y), a.subtract(x).subtract(y), a.add(x).subtract(y)};
        Vec3[] ends = {b.add(x).add(y), b.subtract(x).add(y), b.subtract(x).subtract(y), b.add(x).subtract(y)};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            Vec3 out = corners[i].add(corners[j]).scale(0.5).subtract(a).normalize();
            quad(pose, vc, corners[i], corners[j], ends[j], ends[i], out, sprite, u0, v0, u1, v1, light);
        }
        quad(pose, vc, corners[3], corners[2], corners[1], corners[0], d.scale(-1), sprite, u0, v0, u1, v0 + 2, light);
        quad(pose, vc, ends[0], ends[1], ends[2], ends[3], d, sprite, u0, v0, u1, v0 + 2, light);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 n, TextureAtlasSprite sprite,
                             float u0, float v0, float u1, float v1, int light) {
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        Vec3[] p = {p0, p1, p2, p3};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose.last(), (float) p[i].x, (float) p[i].y, (float) p[i].z)
                    .setColor(255, 255, 255, 255)
                    .setUv(sprite.getU(uv[i][0] / 16f), sprite.getV(uv[i][1] / 16f))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
        }
    }
}
