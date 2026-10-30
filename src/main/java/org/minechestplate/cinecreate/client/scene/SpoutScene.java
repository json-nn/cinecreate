package org.minechestplate.cinecreate.client.scene;

import org.minechestplate.cinecreate.bridge.DepotMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.EnchantFx;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.Scene;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.minechestplate.cinecreate.station.Composites;

/**
 * Spouting: a Spout over the depot (DESIGN §7.13). While Create pours, droplets bead on the work in the liquid's colour;
 * the moment it is filled it flares in that colour, like the brewing stand's potions.
 */
public class SpoutScene extends DepotScene {
    /** The flare: how long, from what size to what size, and the flash with it. */
    static final double FLARE_TIME = 0.6, FLARE_FROM = 0.09, FLARE_TO = 0.14, FLASH = 0.04, FLASH_DECAY = 10;

    /** Development (SmokeTest): fills seen. */
    public int fills;

    /** The spout's countdown last tick (Create fills at 5); the liquid's colour; the flare. */
    private int lastTicks = -1;
    private int color = 0xFFFFFF;
    private double flareAt = -10;

    public SpoutScene(BlockPos pos, Direction side) {
        super(pos, side);
    }

    @Override
    public CameraRig.Pose lean() {
        return machineLean();
    }

    @Override
    public double leanFov() {
        return 66;
    }

    @Override
    public float bodyPitch() {
        return 30f;
    }

    @Nullable
    @Override
    protected Component notReady() {
        SpoutBlockEntity spout = spout();
        return spout != null && spout.processingTicks != -1 ? Component.translatable("cinecreate.busy.spout") : null;
    }

    @Nullable
    private SpoutBlockEntity spout() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : Composites.spoutOver(mc.level, pos);
    }

    private FluidStack fluid(SpoutBlockEntity spout) {
        IFluidHandler tank = Minecraft.getInstance().level.getCapability(Capabilities.FluidHandler.BLOCK, spout.getBlockPos(), null);
        return tank == null || tank.getTanks() == 0 ? FluidStack.EMPTY : tank.getFluidInTank(0);
    }

    @Override
    public void tick() {
        super.tick();
        SpoutBlockEntity spout = spout();
        int ticks = spout == null ? -1 : spout.processingTicks;
        if (spout != null && !fluid(spout).isEmpty()) {
            color = Colors.of(fluid(spout));
        }
        // поток без предмета (его забрали): ни искр, ни вспышки — наполнять нечего
        boolean filling = !slot(DepotMenu.MAIN).isEmpty();
        if (ticks > 5 && filling) {
            Fx.motes(plate().add(0, 0.12, 0), 0.07, 1, Colors.red(color), Colors.green(color), Colors.blue(color), 0.0);
        }
        if (lastTicks > 5 && ticks >= 0 && ticks <= 5 && filling) {
            fills++;
            flareAt = Scene.clock();
            Fx.motes(plate().add(0, 0.1, 0), 0.1, 10, Colors.red(color), Colors.green(color), Colors.blue(color), 0.2);
        }
        lastTicks = ticks;
    }

    @Override
    public double flash() {
        return FLASH * Math.exp(-(Scene.clock() - flareAt) * FLASH_DECAY);
    }

    @Override
    public int flashColor() {
        return color;
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        super.drawWorld(pose, buffers, camera, light);
        double age = Scene.clock() - flareAt;
        if (age < 0 || age > FLARE_TIME) {
            return;
        }
        double k = age / FLARE_TIME;
        float fade = (float) (1 - Anim.inOutCubic(k));
        EnchantFx.billboard(pose, buffers, Minecraft.getInstance().gameRenderer.getMainCamera(), plate().add(0, 0.12, 0),
                FLARE_FROM + (FLARE_TO - FLARE_FROM) * k, age, EnchantFx.GLOW,
                Colors.red(color) * fade, Colors.green(color) * fade, Colors.blue(color) * fade);
    }

    @Override
    protected Component sceneHint() {
        return spout() == null ? super.sceneHint() : Component.translatable("cinecreate.hint.spouting");
    }
}
