package org.minechestplate.cinecreate.client.scene;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Scene;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.station.Composites;

/**
 * Bulk processing: the depot in an Encased Fan's stream (DESIGN §7.11). The wind trembles the camera, the item is
 * processed in the stream's colour and the result flashes in; for blasting and haunting the free hand shields the eyes.
 */
public class FanScene extends DepotScene {
    /** The wind's tremble, and how much the view narrows. */
    static final double WIND = 0.08, NARROW = 2;
    /** The stream is looked up again this often (ticks): a fan can stop or turn. */
    static final int LOOK_AGAIN = 10;

    /** What a stream does, by Create's name for it: its colour and its flash when the result appears. */
    private enum Kind {
        SPLASHING(0x6FB7FF, 0.04), SMOKING(0x8A8A8A, 0.03), BLASTING(0xFF7A2A, 0.06), HAUNTING(0x6FE3E8, 0.05);

        final int color;
        final double flash;

        Kind(int color, double flash) {
            this.color = color;
            this.flash = flash;
        }

        @Nullable
        static Kind of(@Nullable FanProcessingType type) {
            ResourceLocation id = type == null ? null : CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
            for (Kind kind : values()) {
                if (id != null && id.getPath().equals(kind.name().toLowerCase(Locale.ROOT))) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** Development (SmokeTest): results seen appearing. */
    public int results;

    @Nullable
    private Composites.FanStream stream;
    @Nullable
    private Kind kind;
    private int ticks;
    private ItemStack lastLying = ItemStack.EMPTY;
    private double flashAt = -10;

    public FanScene(BlockPos pos, Direction side) {
        super(pos, side);
        lookUp();
    }

    private void lookUp() {
        Minecraft mc = Minecraft.getInstance();
        stream = mc.level == null ? null : Composites.fanStream(mc.level, pos);
        kind = stream == null ? null : Kind.of(stream.type());
    }

    @Override
    public double leanFov() {
        return super.leanFov() - (kind == null ? 0 : NARROW);
    }

    @Override
    public double cameraKick() {
        return kind == null ? 0 : WIND;
    }

    @Override
    protected Vec3 restAt(boolean right) {
        if (right || kind != Kind.BLASTING && kind != Kind.HAUNTING) {
            return super.restAt(right);
        }
        Vec3 eye = lean().pos();
        return eye.add(forward().scale(0.32)).add(right().scale(-0.12)).add(0, 0.06, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % LOOK_AGAIN == 0) {
            lookUp();
        }
        Minecraft mc = Minecraft.getInstance();
        ItemStack now = lying();
        if (kind != null && stream != null && stream.type() != null && !now.isEmpty() && mc.level != null
                && stream.type().canProcess(now, mc.level)) {
            Vec3 at = plate().add(0, 0.1, 0);
            float r = Colors.red(kind.color), g = Colors.green(kind.color), b = Colors.blue(kind.color);
            switch (kind) {
                case SPLASHING -> Fx.motes(at, 0.15, 2, r, g, b, 0.25);
                case SMOKING -> Fx.steam(at, 0.12, 1);
                case BLASTING -> Fx.hotSparks(at, new Vec3(0, 1, 0), 0.6, 2, 0.8);
                case HAUNTING -> Fx.motes(at, 0.15, 2, r, g, b, 0.2);
            }
        }
        if (kind != null && !lastLying.isEmpty() && !now.isEmpty() && !ItemStack.isSameItem(lastLying, now)) {
            results++;
            flashAt = Scene.clock();
        }
        lastLying = now.copy();
    }

    @Override
    public double flash() {
        return kind == null ? 0 : kind.flash * Math.exp(-(Scene.clock() - flashAt) * 10);
    }

    @Override
    public int flashColor() {
        return kind == null ? 0xFFFFFF : kind.color;
    }

    @Override
    protected Component sceneHint() {
        return kind == null ? super.sceneHint() : Component.translatable("cinecreate.hint.fan_processing." + kind.name().toLowerCase(Locale.ROOT));
    }

    /** Development (SmokeTest): what the stream does here, by Create's name ("splashing"...), or null. */
    @Nullable
    public String streamKind() {
        return kind == null ? null : kind.name().toLowerCase(Locale.ROOT);
    }
}
