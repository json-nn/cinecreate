package org.minechestplate.cinecreate.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.bridge.BurnerMenu;
import org.minechestplate.cinecreate.client.FrameCounter;

/**
 * The Blaze Burner (DESIGN §7.7): crouched at the cage, the player tosses fuel to the blaze (a Blaze Cake makes it
 * seethe); an empty burner is lit with flint and steel, struck twice.
 */
public class BurnerScene extends CineStation<BurnerMenu> {
    /** Feeding: seconds, the wind-up, the beat (it lands in the blaze), the arc's rise, how far the hand draws back. */
    static final double TOSS = 0.6, WIND_UP = 0.15, TOSS_BEAT = 0.40, ARC = 0.12, DRAW_BACK = 0.08;
    /** Lighting: the first strike (only seen), the second one later (the beat), and the whole move. */
    static final double FIRST_STRIKE = 0.15, STRIKE_GAP = 0.25, STRIKE = 0.6;
    /** The blaze's height in its cage; how far the camera stands back, and how high. */
    static final double BLAZE = 0.45, CAMERA_BACK = 1.05, CAMERA_UP = 0.6;
    /** The flash of a meal and of a cake, the cake's jolt, and its shimmer (degrees of roll, per second, how long). */
    static final double FEED_FLASH = 0.04, CAKE_FLASH = 0.12, CAKE_KICK = 0.30, FLASH_DECAY = 8, KICK_DECAY = 10;
    static final double SHIMMER = 0.5, SHIMMER_HZ = 6, SHIMMER_TIME = 0.8;
    static final int ORANGE = 0xFF9A3A, CAKE_BLUE = 0x9BD8FF;
    static final double REFUSED_HINT = 2.0;

    /** Development (SmokeTest): the cage counts as pointed at wherever the cursor is. */
    public static boolean smokePin;
    /** Development (SmokeTest): meals (cakes among them), cakes, strikes that lit it, refusals. */
    public int feeds, cakes, strikes, denials;

    private final Vec3 base, blaze;
    private boolean hovered;
    /** The move under way: striking flint and steel (else tossing fuel), a cake, the last of the stack. */
    private boolean striking, cake, lastOne, firstStruck;
    private ItemStack flying = ItemStack.EMPTY;
    @Nullable
    private Vec3 releaseFrom;
    private double flashAt = -10, flashStrength, kickAt = -10, refusedAt = -10;
    private int flashColor = ORANGE;
    private long drawnFrame = -1;

    public BurnerScene(BlockPos pos, Direction side) {
        super(pos, side, BurnerMenu.class, 0);
        base = Vec3.atBottomCenterOf(pos);
        blaze = base.add(0, BLAZE, 0);
    }

    // ---- staging -------------------------------------------------------------------------------------

    @Override
    public Vec3 center() {
        return blaze;
    }

    @Override
    public List<Component> spotNames() {
        return List.of(Component.translatable("cinecreate.layout.spot.cage"));
    }

    @Override
    public Vec3 spotBase(int spot) {
        return blaze.add(side().scale(0.5));
    }

    /** Where the hand feeds through the cage (the layout's cage spot). */
    private Vec3 cage() {
        return spot(0);
    }

    @Override
    public boolean crouches() {
        return true;
    }

    @Override
    public boolean workOnTop() {
        return false;
    }

    @Override
    public boolean worksAtAnAngle() {
        return true;
    }

    @Override
    protected List<Vec3> stand(LocalPlayer player) {
        Vec3 stand = new Vec3(base.x + side.getStepX() * 0.86, player.getY(), base.z + side.getStepZ() * 0.86);
        Views.noteStand(this, stand);
        boolean walk = Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06;
        return walk ? List.of(stand) : List.of();
    }

    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = base.add(side().scale(CAMERA_BACK)).add(0, CAMERA_UP, 0);
        Vec3 look = blaze.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        double age = Scene.clock() - kickAt;
        double shimmer = cake && age < SHIMMER_TIME
                ? SHIMMER * Math.sin(2 * Math.PI * SHIMMER_HZ * age) * (1 - age / SHIMMER_TIME) : 0;
        return new CameraRig.Pose(eye, yaw, pitch, (float) shimmer);
    }

    @Override
    public double leanFov() {
        return 66;
    }

    @Override
    public float bodyPitch() {
        return 30f;
    }

    @Override
    protected Vec3 restAt(boolean right) {
        return blaze.add(side().scale(0.32)).add(right().scale(right ? 0.36 : -0.36)).add(0, -0.05, 0);
    }

    // ---- interaction ---------------------------------------------------------------------------------

    @Override
    public void updateCursor(boolean overPanel) {
        if (moving()) {
            return;
        }
        if (smokePin) {
            hover();
            return;
        }
        hovered = false;
        aim = null;
        if (overPanel) {
            return;
        }
        Vec3 front = base.add(side().scale(0.5)).add(cage().subtract(spotBase(0)));
        double half = 0.5 * spotArea(0);
        Vec3 hit = Picking.cursorOnPlane(front, side());
        if (hit != null && Math.abs(hit.subtract(front).dot(right())) <= half && Math.abs(hit.y - front.y - 0.5) <= half) {
            hover();
        }
    }

    /** Development (SmokeTest): points at the cage as the cursor would. */
    public void hover() {
        hovered = true;
        aim = cage();
    }

    @Override
    public void clearCursor() {
        if (!moving() && !smokePin) {
            hovered = false;
            aim = null;
        }
    }

    @Override
    protected Vec3 reach(Vec3 target, Vec3 shoulder, boolean carrying, double tap) {
        // протянута к решётке, не к камере
        Vec3 ready = target.add(side().scale(0.08)).add(0, 0.05, 0);
        double t = moveTime();
        if (t < 0) {
            return ready.add(0, -0.02 * tap, 0);
        }
        if (striking) {
            double second = FIRST_STRIKE + STRIKE_GAP;
            double jab = Anim.hump(Anim.window(t, FIRST_STRIKE - 0.08, FIRST_STRIKE + 0.06))
                    + Anim.hump(Anim.window(t, second - 0.08, second + 0.06));
            return ready.subtract(side().scale(0.12 * jab));
        }
        if (Grip.inHand()) {
            // захватом не бросают: его рука сама вносит топливо сквозь решётку к всполоху и возвращается
            double in = Anim.inOutCubic(Anim.window(t, 0.05, TOSS_BEAT)) - Anim.inOutCubic(Anim.window(t, TOSS_BEAT, TOSS));
            return Anim.lerp(ready, blaze, 0.85 * in);
        }
        double back = Anim.outCubic(Anim.window(t, 0, WIND_UP)) * (1 - Anim.window(t, WIND_UP, WIND_UP + 0.05));
        double flick = Anim.hump(Anim.window(t, WIND_UP, WIND_UP + 0.18));
        return ready.add(side().scale(DRAW_BACK * back - 0.1 * flick));
    }

    /** The last of a stack flies itself; with more in hand, renderScene draws the one flying. */
    @Override
    protected Vec3 carry(Vec3 hand, Vec3 shoulder) {
        Vec3 held = hand.add(side().scale(-0.05)).add(0, -0.05, 0);
        double t = moveTime();
        if (t < 0 || striking || Grip.inHand()) {
            return held;
        }
        if (t < WIND_UP) {
            releaseFrom = held;
            return held;
        }
        return lastOne && t < TOSS_BEAT ? flight(t) : held;
    }

    private Vec3 flight(double t) {
        Vec3 from = releaseFrom != null ? releaseFrom : blaze;
        double k = Anim.window(t, WIND_UP, TOSS_BEAT);
        return Anim.lerp(from, blaze, k).add(0, ARC * Anim.hump(k), 0);
    }

    /** Fuel goes in only if Create's own test (simulated) takes it; flint and steel lights an empty burner. */
    @Override
    protected void act(int button, boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        BurnerMenu menu = menu();
        if (!hovered || menu == null || mc.level == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        BlockState state = mc.level.getBlockState(pos);
        if (carried.isEmpty() || !state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            return;
        }
        if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.NONE) {
            if (carried.getItem() instanceof FlintAndSteelItem) {
                strike();
            } else {
                refuse();
            }
            return;
        }
        if (BlazeBurnerBlock.tryInsert(state, mc.level, pos, carried.copy(), true, true, true).getResult() != InteractionResult.SUCCESS) {
            refuse();
            return;
        }
        toss(carried);
    }

    private void toss(ItemStack carried) {
        striking = false;
        cake = AllItems.BLAZE_CAKE.isIn(carried) || AllItems.CREATIVE_BLAZE_CAKE.isIn(carried);
        lastOne = carried.getCount() == 1;
        flying = carried.copyWithCount(1);
        releaseFrom = null;
        startMove(TOSS, TOSS_BEAT, () -> {
            feed();
            eatenUp();
            feeds++;
            double now = Scene.clock();
            flashAt = now;
            flashStrength = cake ? CAKE_FLASH : FEED_FLASH;
            flashColor = cake ? CAKE_BLUE : ORANGE;
            if (cake) {
                cakes++;
                kickAt = now;
                Fx.motes(blaze, 0.15, 18, 0.6f, 0.85f, 1f, 0.3);
            } else {
                Fx.motes(blaze, 0.1, 12, 1f, 0.6f, 0.23f, 0.15);
            }
        });
    }

    private void strike() {
        striking = true;
        firstStruck = false;
        startMove(STRIKE, FIRST_STRIKE + STRIKE_GAP, () -> {
            feed();
            strikes++;
            sparks();
        });
    }

    private void sparks() {
        Fx.hotSparks(cage().subtract(side().scale(0.05)), side().scale(-1).add(0, 0.5, 0), 0.6, 10, 1.6);
    }

    /** Create's click with what the hand carries, on the side the player works from. */
    private void feed() {
        Minecraft mc = Minecraft.getInstance();
        BurnerMenu menu = menu();
        if (menu != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, BurnerMenu.FEED + side.get3DDataValue());
        }
    }

    /** The hand lets go of one at once; the server's answer (a bucket back from lava) comes after. */
    private void eatenUp() {
        Minecraft mc = Minecraft.getInstance();
        BurnerMenu menu = menu();
        if (menu != null && mc.player != null && !mc.player.isCreative() && !AllItems.CREATIVE_BLAZE_CAKE.isIn(menu.getCarried())) {
            ItemStack carried = menu.getCarried().copy();
            carried.shrink(1);
            menu.setCarried(carried);
        }
    }

    private void refuse() {
        denials++;
        refusedAt = Scene.clock();
        deny();
    }

    /** The first strike of flint and steel is only seen and heard; the second is the move's beat. */
    @Override
    public void beforeFrame() {
        super.beforeFrame();
        if (striking && !firstStruck && moveTime() >= FIRST_STRIKE) {
            firstStruck = true;
            sparks();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.level.playLocalSound(blaze.x, blaze.y, blaze.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.35f, 1.1f, false);
            }
        }
    }

    @Override
    protected Vec3 restPoint(int slot) {
        return blaze;
    }

    public HeatLevel heat() {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        return state != null && state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) ? state.getValue(BlazeBurnerBlock.HEAT_LEVEL) : HeatLevel.NONE;
    }

    @Override
    public Component hint() {
        if (Scene.clock() - refusedAt < REFUSED_HINT) {
            return Component.translatable("cinecreate.hint.blaze_burner.refused");
        }
        HeatLevel heat = heat();
        if (heat == HeatLevel.NONE) {
            return Component.translatable("cinecreate.hint.blaze_burner.empty");
        }
        return Component.translatable("cinecreate.hint.blaze_burner", Component.translatable("cinecreate.heat." + heat.getSerializedName()));
    }

    @Override
    public double cameraKick() {
        return cake ? CAKE_KICK * Math.exp(-(Scene.clock() - kickAt) * KICK_DECAY) : 0;
    }

    @Override
    public double flash() {
        return flashStrength * Math.exp(-(Scene.clock() - flashAt) * FLASH_DECAY);
    }

    @Override
    public int flashColor() {
        return flashColor;
    }

    // ---- drawing -------------------------------------------------------------------------------------

    /** The fuel in the air while more of the stack stays in hand; drawn once a frame (CI calls this twice). */
    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        long frame = FrameCounter.frame();
        if (frame == drawnFrame) {
            return;
        }
        drawnFrame = frame;
        double t = moveTime();
        if (!striking && !lastOne && !Grip.inHand() && t >= WIND_UP && t < TOSS_BEAT && !flying.isEmpty()) {
            ItemPlacer.renderOne(pose, buffers, camera.getPosition(), flying, flight(t), side, 77L, spotSize(0),
                    (float) (18 + 360 * Anim.window(t, WIND_UP, TOSS_BEAT)), light);
        }
    }

    @Override
    protected void drawWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        BurnerMenu menu = menu();
        Minecraft mc = Minecraft.getInstance();
        if (!hovered || moving() || menu == null || mc.level == null || menu.getCarried().isEmpty()) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        ItemStack carried = menu.getCarried();
        boolean ok = state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) && (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.NONE
                ? carried.getItem() instanceof FlintAndSteelItem
                : BlazeBurnerBlock.tryInsert(state, mc.level, pos, carried.copy(), true, true, true).getResult() == InteractionResult.SUCCESS);
        float p = (float) (0.2 + 0.06 * Math.sin(Scene.clock() * 5));
        Fx.facePatch(pose, buffers, camera, cage().add(side().scale(0.01)), right(), new Vec3(0, 1, 0), 0.3,
                p, ok ? p * 0.7f : p * 0.25f, ok ? p * 0.4f : p * 0.2f);
    }
}
