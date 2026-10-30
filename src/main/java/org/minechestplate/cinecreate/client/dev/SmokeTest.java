package org.minechestplate.cinecreate.client.dev;

import dev.dyslexic.cinematic.api.client.AddonScenes;

import net.neoforged.fml.loading.FMLPaths;

import java.util.Locale;

import java.nio.file.Path;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.item.SmartInventory;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.MenuStation;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.net.StationActionPayload;
import dev.dyslexic.cinematic.station.StationKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.createmod.catnip.math.Pointing;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.bridge.BridgeMenu;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.client.ClientConfig;
import org.minechestplate.cinecreate.client.FrameCounter;
import org.minechestplate.cinecreate.client.CiNotice;
import org.minechestplate.cinecreate.client.WorkKey;
import org.minechestplate.cinecreate.client.scene.BasinScene;
import org.minechestplate.cinecreate.client.scene.BurnerScene;
import org.minechestplate.cinecreate.client.ClientMode;
import org.minechestplate.cinecreate.client.scene.CineStation;
import org.minechestplate.cinecreate.client.scene.ClientModeStation;
import org.minechestplate.cinecreate.client.layout.LayoutPanel;
import org.minechestplate.cinecreate.client.layout.Layouts;
import org.minechestplate.cinecreate.client.scene.DevView;
import org.minechestplate.cinecreate.client.scene.CrafterScene;
import org.minechestplate.cinecreate.client.scene.CrankScene;
import org.minechestplate.cinecreate.client.scene.DeployerScene;
import org.minechestplate.cinecreate.client.scene.DeployingScene;
import org.minechestplate.cinecreate.client.scene.DepotScene;
import org.minechestplate.cinecreate.client.scene.DrainScene;
import org.minechestplate.cinecreate.client.scene.EjectorScene;
import org.minechestplate.cinecreate.client.scene.FanScene;
import org.minechestplate.cinecreate.client.scene.Grip;
import org.minechestplate.cinecreate.client.scene.MillstoneScene;
import org.minechestplate.cinecreate.client.scene.PressScene;
import org.minechestplate.cinecreate.client.scene.SawScene;
import org.minechestplate.cinecreate.client.scene.SpoutScene;
import org.minechestplate.cinecreate.client.scene.ValveScene;
import org.minechestplate.cinecreate.station.Composites;
import org.minechestplate.cinecreate.station.Crafters;
import org.minechestplate.cinecreate.station.SceneBlocks;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;
import org.minechestplate.cinecreate.station.Trigger;

/**
 * Development only, with {@code -Dcinecreate.smoke=true} (the {@code smoke} run; not in the release jar). Opens a flat
 * test world, builds each machine and works its scene the way a player would (real right-clicks, slot clicks and clicks
 * on the scene), checks the blocks and the inventory on the integrated server, saves screenshots to
 * {@code run-smoke/screenshots} and the results to {@code run-smoke/smoke-results.txt}, then quits.
 */
public final class SmokeTest {
    private SmokeTest() {}

    public static final boolean ENABLED = Boolean.getBoolean("cinecreate.smoke");
    /** Development: the step to go on with after the set-up (empty: every step), for working on one block. */
    private static final String FROM = System.getProperty("cinecreate.smoke.from", "");

    private static final String WORLD = "cinecreate_smoke";
    /** Flat world: the grass is at -61, so things stand at -60. */
    private static final BlockPos DEPOT = new BlockPos(0, -60, 3);
    private static final BlockPos PRESS = DEPOT.above(Composites.PRESS_HEIGHT), MOTOR = PRESS.east();
    private static final int IRON = 16;
    /** The press runs at Create's top speed: its effects must stay readable even there. */
    private static final int PRESS_IRON = 4, PRESS_RPM = 256;
    private static final BlockPos VALVE = new BlockPos(-3, -59, 3), VALVE_SHAFT = VALVE.south();
    private static final int DYE = 3;
    private static float turnedTo;
    /** A 3×3 wall of crafters facing north, by its top left as the player sees it (the columns run west). */
    private static final BlockPos CRAFTERS = new BlockPos(9, -58, 3);
    private static final BlockPos CRAFTER_COG = new BlockPos(6, -60, 3), CRAFTER_MOTOR = CRAFTER_COG.south();
    private static final int CRAFTER_RPM = 64;
    private static final BlockPos LONE_CRAFTER = new BlockPos(12, -60, 3);
    private static final BlockPos BIG_CRAFTERS = new BlockPos(27, -51, 3);
    private static final int PLANKS = 0, SLOT_COVER = 1, EMPTY_HAND = 8;
    /** A chest: every window of the 3×3 (reading order) but the middle one, which gets the Slot Cover. */
    private static final int[] RING = {0, 1, 2, 3, 5, 6, 7, 8};
    private static final int MIDDLE = 4;
    private static int filled;
    private static boolean tossShot;
    private static final BlockPos CRANK = new BlockPos(-6, -59, 3), CRANK_SHAFT = CRANK.south();
    private static double crankAngle;
    private static float crankOn;
    private static final BlockPos BASIN = new BlockPos(-10, -59, 3), BASIN_BURNER = BASIN.below(), MIXER = BASIN.above(2),
            MIXER_COG = MIXER.east(), MIXER_MOTOR = MIXER_COG.above(), EMPTY_BURNER = new BlockPos(-13, -60, 3);
    private static final int MIXER_RPM = 64;
    private static final int ANDESITE = 0, NUGGET = 1, WATER = 2, COAL = 3, CAKE = 4, DIRT = 5, FLINT = 6;
    /**
     * Wave 2: a row west of the basin, every machine facing the player north of it. The fan blows west along the row
     * through a lit campfire onto its depot: a campfire's full bottom face would stop a stream blowing up.
     */
    private static final BlockPos DRAIN = new BlockPos(-17, -60, 3), SPOUT_DEPOT = new BlockPos(-20, -60, 3),
            SPOUT = SPOUT_DEPOT.above(2), DEPLOYING_DEPOT = new BlockPos(-23, -60, 3), DOWN_DEPLOYER = DEPLOYING_DEPOT.above(2),
            HANDOFF_DEPLOYER = new BlockPos(-25, -59, 3), FAN_DEPOT = new BlockPos(-29, -60, 3), CAMPFIRE = FAN_DEPOT.east(),
            FAN = CAMPFIRE.east(), FAN_MOTOR = FAN.east(), MILLSTONE = new BlockPos(-32, -60, 3), MILL_MOTOR = MILLSTONE.below(),
            SAW = new BlockPos(-35, -60, 3), EJECTOR = new BlockPos(-38, -60, 3);
    /** Turned this way, this fan (facing west, its motor behind it) blows its air out; at -64 RPM it pulls it in. */
    private static final int DEPLOYER_RPM = 64, FAN_RPM = 64, MILL_RPM = 128, SAW_RPM = 32, EJECTOR_RPM = 64;
    private static final int EJECTOR_REACH = 4;
    private static final int FULL_BUCKET = 0, EMPTY_BUCKET = 1, BOTTLE = 2, LOG = 3, HANDED = 4, BEEF = 5, WHEAT = 6, STONE_SLOT = 7;
    private static final int GRIP = 3;
    /** A basin on two blocks of stone, out of a bare arm's reach. */
    private static final BlockPos HIGH_BASIN = new BlockPos(4, -58, 8);
    private static final int TIMEOUT = 400;

    /**
     * One step: runs every tick once its delay has passed, until it returns true (false: try again next tick);
     * {@link #TIMEOUT} ticks more and it fails.
     */
    private interface Action {
        boolean run(Minecraft mc);
    }

    private record Step(String name, int delay, Action action) {}

    private static final List<String> RESULTS = new ArrayList<>();
    private static final List<Step> STEPS = Boolean.getBoolean("cinecreate.explore") ? exploreSteps() : List.of(
            new Step("set up the depot", 60, SmokeTest::setUp),
            new Step("wrench click", 30, mc -> use(mc, 1, Direction.UP)),
            new Step("wrench is left to Create", 10, mc -> {
                check("right-click with a wrench starts no scene", !Scene.active());
                Scene.abort();
                onServer(mc, SmokeTest::reset);
                return true;
            }),
            new Step("block against the side", 4, mc -> use(mc, 2, Direction.EAST)),
            new Step("side is left to Create", 10, mc -> {
                check("a block placed against the depot's side is placed, no scene",
                        !Scene.active() && onServer(mc, p -> p.serverLevel().getBlockState(DEPOT.east()).is(Blocks.STONE)));
                Scene.abort();
                onServer(mc, SmokeTest::reset);
                return true;
            }),
            new Step("fallback request", 10, mc -> {
                // the request carries no hotbar choice: the server must already know the iron is selected
                mc.player.getInventory().selected = 0;
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));
                PacketDistributor.sendToServer(new StationActionPayload(DEPOT, StationKind.ADDON, StationActionPayload.Action.WINDOW, 0, 0, false, 0));
                return true;
            }),
            new Step("fallback is Create's use", 10, mc -> {
                check("without a scene, the click is Create's own use (the stack lands on the depot)", depot(mc).getCount() == IRON);
                onServer(mc, SmokeTest::reset);
                return true;
            }),
            new Step("right-click the plate", 4, mc -> use(mc, 0, Direction.UP)),
            new Step("leaning in", 0, mc -> {
                if (Scene.phase == Scene.Phase.INTRO && Scene.introProgress() > 0.6) {
                    shot(mc, "01_leaning_in");
                    return true;
                }
                return Scene.phase == Scene.Phase.ACTIVE;
            }),
            new Step("at work", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("right-click on the plate starts the depot scene", Scene.station instanceof DepotScene);
                return true;
            }),
            new Step("layout editor", 4, mc -> {
                LayoutPanel.smokeForce = true;
                LayoutPanel.open();
                check("developer mode: the layout editor opens on the depot's scene", LayoutPanel.isOpen());
                SmokeTest.plateBefore = Layouts.point(scene(), 0);
                return true;
            }),
            new Step("layout edited", 6, mc -> {
                shot(mc, "01b_layout_editor");
                LayoutPanel.smokeSet(0, 0, 4.0);
                Layouts.smokeSettle();
                Vec3 moved = Layouts.point(scene(), 0).subtract(SmokeTest.plateBefore);
                scene().hover();
                Path saved = FMLPaths.CONFIGDIR.get().resolve("cinecreate-layouts.json");
                check("the plate spot moves 4 px to the right, the hand goes there, and the profile is saved (moved "
                        + String.format(Locale.ROOT, "%.3f", moved.dot(scene().layoutAcross())) + ")",
                        Math.abs(moved.dot(scene().layoutAcross()) - 0.25) < 1e-6 && scene().aim.distanceTo(Layouts.point(scene(), 0)) < 1e-6
                                && !LayoutPanel.isOpen() && Files.exists(saved));
                LayoutPanel.open();
                LayoutPanel.smokeSet(0, 2, 2.0);
                Layouts.smokeSettle();
                scene().hover();
                Vec3 hand = Layouts.point(scene(), 0).subtract(SmokeTest.plateBefore);
                check("the hand's height too: 2 px up, the hand goes there, and the plate Create puts the stack on stays ("
                        + String.format(Locale.ROOT, "%.3f up", hand.y) + ")",
                        Math.abs(hand.y - 0.125) < 1e-6 && scene().aim.distanceTo(Layouts.point(scene(), 0)) < 1e-6
                                && scene().spotBase(0).distanceTo(SmokeTest.plateBefore) < 1e-6);
                LayoutPanel.smokeReset();
                Layouts.smokeSettle();
                check("reset, the plate spot is back where it was", Layouts.point(scene(), 0).distanceTo(SmokeTest.plateBefore) < 1e-6);
                LayoutPanel.smokeForce = false;
                DepotScene.smokePin = false;
                scene().clearCursor();
                return true;
            }),
            new Step("pick up the iron", 20, mc -> {
                shot(mc, "02_at_work");
                DepotScene.smokePin = true;
                scene().hover();
                DepotMenu menu = scene().menu();
                check("the bridge menu is open on the client", menu != null);
                if (menu == null) {
                    return true;
                }
                mc.gameMode.handleInventoryMouseClick(menu.containerId, menu.inventoryIndex(0), 0, ClickType.PICKUP, mc.player);
                return true;
            }),
            new Step("carrying", 10, mc -> {
                shot(mc, "03_carrying");
                check("the iron is in hand", carried(mc).getCount() == IRON);
                scene().click(0, false);
                return true;
            }),
            new Step("setting down", 6, mc -> {
                shot(mc, "04_setting_down");
                return true;
            }),
            new Step("set down", 20, mc -> {
                if (moving()) {
                    return false;
                }
                shot(mc, "05_set_down");
                check("set down: the depot holds the stack, the hand is empty", depot(mc).getCount() == IRON && carried(mc).isEmpty());
                scene().click(0, false);
                return true;
            }),
            new Step("taking", 4, mc -> {
                shot(mc, "06_taking");
                return true;
            }),
            new Step("taken", 16, mc -> {
                if (moving()) {
                    return false;
                }
                shot(mc, "07_taken");
                check("taken: the depot is empty, the stack is in hand", depot(mc).isEmpty() && carried(mc).getCount() == IRON);
                scene().click(1, false);
                return true;
            }),
            new Step("one item", 20, mc -> {
                if (moving()) {
                    return false;
                }
                check("right click sets down just one", depot(mc).getCount() == 1 && carried(mc).getCount() == IRON - 1);
                DepotMenu menu = scene().menu();
                mc.gameMode.handleInventoryMouseClick(menu.containerId, menu.inventoryIndex(0), 0, ClickType.PICKUP, mc.player);
                scene().quickMove(0, menu.inventoryIndex(0));
                return true;
            }),
            new Step("drawer quick move", 15, mc -> {
                check("shift-click in the drawer moves the stack onto the depot", depot(mc).getCount() == IRON);
                Scene.leave();
                return true;
            }),
            new Step("stepping back", 8, mc -> {
                shot(mc, "08_stepping_back");
                return true;
            }),
            new Step("left", 0, mc -> {
                if (Scene.phase != Scene.Phase.NONE) {
                    return false;
                }
                check("after leaving: the stack stays on the depot, nothing lost, the session's menu is closed",
                        depot(mc).getCount() == IRON && onServer(mc, p -> p.getInventory().countItem(Items.IRON_INGOT) == 0
                                && p.containerMenu == p.inventoryMenu));
                return true;
            }),
            // ---- the trigger policy: switched in the config the way its screen does it, without relogging ----
            new Step("key mode", 10, mc -> {
                ClientConfig.set(SceneKinds.DEPOT, Trigger.KEY);
                onServer(mc, player -> standAtStart(player) && reset(player));
                return true;
            }),
            new Step("right-click in key mode", 4, mc -> use(mc, 0, Direction.UP)),
            new Step("key mode right-click is Create's", 10, mc -> {
                // сервер тоже должен знать про KEY: иначе его CI отменил бы клик, и депо осталось бы пустым
                check("KEY: a right-click on the plate starts no scene, and Create puts the stack down on the server",
                        !Scene.active() && depot(mc).getCount() == IRON);
                Scene.abort();
                onServer(mc, SmokeTest::reset);
                face(mc);
                return true;
            }),
            new Step("invitation", 10, mc -> {
                shot(mc, "09_invitation");
                check("KEY: looking at the depot shows the \"[H] Work at the depot\" invitation", DEPOT.equals(WorkKey.invited()));
                KeyMapping.click(WorkKey.WORK.getKey());
                return true;
            }),
            new Step("key scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                shot(mc, "10_key_scene");
                check("KEY: the Work at the machine key (H, shared with CI's campfire) starts the depot scene",
                        Scene.station instanceof DepotScene && scene().menu() != null);
                Scene.leave();
                return true;
            }),
            new Step("left the key scene", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("off mode", 10, mc -> {
                // все сцены депо выключены (их пять): иначе CI по-прежнему видит у блока рабочие стороны (ROADMAP, ограничение 13)
                for (SceneKind kind : SceneBlocks.DEPOT.kinds()) {
                    ClientConfig.set(kind, Trigger.OFF);
                }
                onServer(mc, player -> standAtStart(player) && reset(player));
                return true;
            }),
            new Step("right-click in off mode", 4, mc -> use(mc, 0, Direction.UP)),
            new Step("off mode right-click is Create's", 10, mc -> {
                check("OFF: a right-click on the plate starts no scene, and Create puts the stack down on the server",
                        !Scene.active() && depot(mc).getCount() == IRON);
                boolean client = StationKind.ADDON.usable(mc.level, DEPOT, mc.level.getBlockState(DEPOT));
                boolean server = onServer(mc, p -> StationKind.ADDON.usable(p.serverLevel(), DEPOT, p.serverLevel().getBlockState(DEPOT)));
                check("OFF (every depot scene): the client's Cinematic Interactions sees no working side (no moving over to it),"
                        + " the server's still does", !client && server);
                Scene.abort();
                onServer(mc, SmokeTest::reset);
                face(mc);
                return true;
            }),
            new Step("key in off mode", 10, mc -> {
                check("OFF: no invitation", WorkKey.invited() == null);
                KeyMapping.click(WorkKey.WORK.getKey());
                return true;
            }),
            new Step("off starts nothing", 30, mc -> {
                check("OFF: the key starts no scene", !Scene.active());
                Scene.abort();
                return true;
            }),
            // ---- pressing: a press over the depot, driven by a creative motor ----
            new Step("build the press", 2, mc -> {
                // голое депо снова по клавише (как по умолчанию): правый клик начнёт сцену только из-за пресса
                ClientConfig.set(SceneKinds.DEPOT, Trigger.KEY);
                ClientConfig.set(SceneKinds.PRESSING, Trigger.USE);
                onServer(mc, player -> buildPress(player) && standAtStart(player) && reset(player, PRESS_IRON));
                return true;
            }),
            new Step("spin up", 5, mc -> onServer(mc, player -> motor(player, PRESS_RPM))),
            new Step("right-click the depot under the press", 4, mc -> use(mc, 0, Direction.UP)),
            new Step("pressing scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("with the bare depot on KEY, a right-click on a depot under a press starts the pressing scene",
                        Scene.station instanceof PressScene);
                scene().hover();
                DepotMenu menu = scene().menu();
                mc.gameMode.handleInventoryMouseClick(menu.containerId, menu.inventoryIndex(0), 0, ClickType.PICKUP, mc.player);
                return true;
            }),
            new Step("lay the iron under the press", 10, mc -> {
                scene().click(0, false);
                return true;
            }),
            new Step("impact", 0, mc -> {
                if (pressScene().impacts.isEmpty()) {
                    return false;
                }
                shot(mc, "11_press_impact");
                return true;
            }),
            new Step("pressed", 0, mc -> {
                // последний слиток Create прессует на месте: его лист остаётся на плите, остальные — в выходах депо;
                // сервер опережает клиента на несколько тиков, так что ждём и последний удар у клиента
                if (sheets(mc) < PRESS_IRON || !pressIdle(mc, 10)) {
                    return false;
                }
                shot(mc, "12_pressed");
                List<PressScene.Impact> impacts = pressScene().impacts;
                check("the press works the stack on the server: " + PRESS_IRON + " iron sheets on the depot, no iron left",
                        sheets(mc) == PRESS_IRON && !depot(mc).is(Items.IRON_INGOT));
                check("one impact per ingot pressed (" + impacts.size() + ")", impacts.size() == PRESS_IRON);
                check("each pressed ingot shows its result: motes and a chime (" + pressScene().results + ")",
                        pressScene().results == PRESS_IRON);
                check("at " + PRESS_RPM + " RPM the camera kicks at most once per 0.35 s", kicksApart(impacts));
                check("every impact lands within one frame of Create's head reaching the plate", onHeadFrames(impacts));
                return true;
            }),
            new Step("watching", 200, mc -> {
                check("the pressing scene stays while the player only watches (10 s)",
                        Scene.phase == Scene.Phase.ACTIVE && Scene.station instanceof PressScene);
                return true;
            }),
            new Step("stop the motor", 0, mc -> onServer(mc, player -> motor(player, 0))),
            new Step("stopped", 20, mc -> {
                shot(mc, "13_press_stopped");
                check("with the press stopped the hint says it has no rotation", pressScene().stopped()
                        && pressScene().hint().getContents() instanceof TranslatableContents hint
                        && hint.getKey().equals("cinecreate.hint.pressing.stopped"));
                Scene.leave();
                return true;
            }),
            new Step("left the press", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("back to the start", 2, mc -> onServer(mc, player -> standAtStart(player) && reset(player, PRESS_IRON))),
            new Step("right-click the press", 4, mc -> use(mc, 0, PRESS, Direction.NORTH)),
            new Step("press click scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on the press itself starts the pressing scene at the depot below",
                        Scene.station instanceof PressScene && Scene.station.pos.equals(DEPOT));
                Scene.leave();
                return true;
            }),
            new Step("left again", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- the Valve Handle: on a shaft, turned by hand ----
            new Step("build the valve", 2, mc -> {
                ClientConfig.set(SceneKinds.VALVE, Trigger.USE);
                onServer(mc, player -> buildValve(player) && reset(player) && give(player, DYE, new ItemStack(Items.RED_DYE))
                        && standAtValve(player));
                return true;
            }),
            new Step("right-click the hub", 4, mc -> {
                if (!use(mc, 0, VALVE, Direction.NORTH, Vec3.atCenterOf(VALVE))) {
                    return false;
                }
                check("a right-click on the valve's hub (Create's value box) is Create's: its angle setting begins",
                        valueInputTook(VALVE));
                return true;
            }),
            new Step("no scene from the hub", 10, mc -> {
                check("and no valve scene starts", Scene.phase == Scene.Phase.NONE);
                return true;
            }),
            // on the wheel, off the hub (Create's rim is 5–8 px out)
            new Step("right-click the wheel", 4, mc -> use(mc, 0, VALVE, Direction.NORTH, Vec3.atCenterOf(VALVE).add(0.4, 0, 0))),
            new Step("valve scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on the wheel starts the valve scene", Scene.station instanceof ValveScene);
                return true;
            }),
            new Step("set the angle", 10, mc -> {
                // 45° по умолчанию, по 5° за щелчок колеса: девять щелчков — 90°
                for (int i = 0; i < 9; i++) {
                    valveScene().scroll(1);
                }
                return true;
            }),
            new Step("angle readout", 4, mc -> {
                shot(mc, "14_valve_angle");
                return true;
            }),
            new Step("angle steady", 0, mc -> {
                // каждый тик, пока сервер не примет: на табло ни разу не мелькнул прежний угол
                SmokeTest.angleFlicker |= valveScene().shownAngle() != 90;
                return valveSetting(mc) == 90 && ++SmokeTest.angleTicks >= 10;
            }),
            new Step("angle stored", 0, mc -> {
                check("the scroll wheel sets the angle on the valve itself (server: " + valveSetting(mc) + "°)",
                        valveSetting(mc) == 90);
                check("and the readout keeps the new angle while the server takes it, never the old one for a moment",
                        !SmokeTest.angleFlicker);
                valveScene().click(0, false);
                return true;
            }),
            new Step("turning", 0, mc -> {
                if (!valveTurning(mc)) {
                    return false;
                }
                float shaft = shaftSpeed(mc);
                shot(mc, "15_valve_turning");
                valveScene().click(0, false);
                check("a click turns the valve, and the shaft behind it turns too (server: " + shaft + " RPM)", shaft != 0);
                return true;
            }),
            new Step("turned", 0, mc -> {
                if (valveTurning(mc) || valveScene().clunks < 1) {
                    return false;
                }
                turnedTo = valveWheel(mc);
                check("Create stored the turn: the wheel stands a quarter turn on (server: " + turnedTo + "°)",
                        Math.abs(turnedTo) == 90);
                check("a click while the wheel turns is refused", valveScene().denials >= 1);
                check("the turn ends with the clunk", valveScene().clunks == 1);
                return true;
            }),
            new Step("turn back", 10, mc -> {
                // CI drops a click while the turn's move still plays (slow frames stretch it)
                if (moving()) {
                    return false;
                }
                valveScene().click(1, false);
                return true;
            }),
            new Step("turned back", 0, mc -> {
                if (valveTurning(mc) || valveScene().clunks < 2 || moving()) {
                    return false;
                }
                float back = valveWheel(mc);
                check("the right button turns it back the other way (server: " + turnedTo + "° -> " + back + "°)",
                        back == -turnedTo && valveScene().clunks == 2);
                DevView.smokeShow(true);
                return true;
            }),
            new Step("developer view", 4, mc -> {
                shot(mc, "13b_developer_view");
                List<String> lines = DevView.smokeLines();
                check("developer mode: the panel names the scene and its own state (" + String.join(" | ", lines) + ")",
                        lines.get(0).startsWith("ValveScene") && lines.stream().anyMatch(line -> line.startsWith("angle: shown")));
                DevView.smokeShow(false);
                Scene.leave();
                return true;
            }),
            new Step("left the valve", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // сервер не принимает клики, пока клиент не подтвердил телепорт: перенос и клик — разными шагами
            new Step("back to the valve", 2, mc -> onServer(mc, SmokeTest::standAtValve)),
            new Step("dye the valve", 4, mc -> {
                // по верхней грани колеса: на ней нет коробки значения
                return use(mc, DYE, VALVE, Direction.UP, Vec3.atLowerCornerOf(VALVE).add(0.5, 15 / 16.0, 0.6));
            }),
            new Step("dyed", 10, mc -> {
                check("a dye on the valve stays Create's: no scene, and the valve turns red", !Scene.active()
                        && onServer(mc, p -> p.serverLevel().getBlockState(VALVE).is(AllBlocks.DYED_VALVE_HANDLES.get(DyeColor.RED).get())));
                Scene.abort();
                return true;
            }),
            new Step("right-click the red valve", 4, mc -> use(mc, 0, VALVE, Direction.NORTH, Vec3.atCenterOf(VALVE).add(0.4, 0, 0))),
            new Step("red valve scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                shot(mc, "16_red_valve");
                check("a dyed valve (one of the 16 colours) has the valve scene too", Scene.station instanceof ValveScene
                        && onServer(mc, p -> p.serverLevel().getBlockState(VALVE).is(AllBlocks.DYED_VALVE_HANDLES.get(DyeColor.RED).get())));
                Scene.leave();
                return true;
            }),
            new Step("left the red valve", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- Mechanical Crafters: a chest filled into a 3×3, a Slot Cover, the watch; framing; the limit ----
            new Step("build the crafters", 2, mc -> {
                ClientConfig.set(SceneKinds.CRAFTER, Trigger.USE);
                onServer(mc, player -> buildCrafters(player) && crafterKit(player) && standBefore(player, CRAFTERS.west(1), 2.5));
                return true;
            }),
            new Step("spin up the crafters", 5, mc -> onServer(mc, player -> crafterMotor(player, CRAFTER_RPM))),
            new Step("right-click a corner crafter", 4, mc -> use(mc, EMPTY_HAND, crafterCell(0, 0), Direction.NORTH)),
            new Step("crafter scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on a corner crafter starts the scene for its whole 3×3 group",
                        Scene.station instanceof CrafterScene scene && scene.group().width() == 3 && scene.group().height() == 3
                                && scene.group().cells().size() == 9);
                return true;
            }),
            new Step("framed", 20, mc -> {
                shot(mc, "17_crafters");
                String missing = crafterScene().outOfFrame();
                check("the whole 3×3 is in frame, every window clear of the drawer and the hint line ("
                        + crafterScene().framing() + (missing.isEmpty() ? "" : "; not shown: " + missing) + ")", missing.isEmpty());
                pickUp(mc, SLOT_COVER);
                return true;
            }),
            new Step("cover the middle", 10, mc -> pinAndClick(MIDDLE)),
            new Step("covered", 4, mc -> {
                if (moving()) {
                    return false;
                }
                check("a Crafter Slot Cover in hand covers an empty window (server: covered, the cover used up)",
                        covered(mc, crafterCell(1, 1)) && carried(mc).isEmpty());
                crafterScene().click(0, false);
                return true;
            }),
            new Step("uncovered", 4, mc -> {
                if (moving()) {
                    return false;
                }
                check("an empty hand takes the cover off again, into the inventory", !covered(mc, crafterCell(1, 1))
                        && onServer(mc, p -> p.getInventory().countItem(AllItems.CRAFTER_SLOT_COVER.get()) == 1));
                pickUp(mc, PLANKS);
                filled = 0;
                return true;
            }),
            new Step("fill the ring", 4, mc -> {
                if (moving()) {
                    if (!tossShot && crafterScene().inFlight()) {
                        tossShot = true;
                        shot(mc, "18_toss");
                    }
                    return false;
                }
                if (filled < RING.length) {
                    pinAndClick(RING[filled++]);
                    return false;
                }
                return true;
            }),
            new Step("ring filled", 6, mc -> {
                if (moving()) {
                    return false;
                }
                CrafterScene scene = crafterScene();
                check("eight windows hold one plank each, the middle one is still empty (server)", ringFilled(mc));
                // и заглушка в начале тоже полетела броском
                check("the windows of a 3×3 are out of arm's reach from where it is all in frame: each got a toss ("
                        + scene.tosses + " tosses with the Slot Cover, " + scene.inserts + " by hand)",
                        scene.tosses == RING.length + 1 && scene.inserts == 0);
                pinAndClick(0);
                return true;
            }),
            new Step("full window refuses", 6, mc -> {
                shot(mc, "19_refused");
                check("a full window refuses another plank (the hands shake)", crafterScene().denials == 1 && carried(mc).getCount() == 1);
                // последнюю доску — обратно в свой слот (он теперь пуст)
                pickUp(mc, PLANKS);
                return true;
            }),
            new Step("take one out", 6, mc -> pinAndClick(0)),
            new Step("taken out", 4, mc -> {
                if (moving()) {
                    return false;
                }
                check("an empty hand takes a plank back out of its window, into the hand",
                        crafterWindow(mc, crafterCell(0, 0)).isEmpty() && carried(mc).is(Items.OAK_PLANKS));
                crafterScene().click(0, false);
                return true;
            }),
            new Step("put back", 4, mc -> {
                if (moving()) {
                    return false;
                }
                check("and puts it back", crafterWindow(mc, crafterCell(0, 0)).is(Items.OAK_PLANKS) && carried(mc).isEmpty());
                pickUp(mc, SLOT_COVER);
                return true;
            }),
            new Step("cover completes the chest", 6, mc -> pinAndClick(MIDDLE)),
            new Step("watch", 0, mc -> {
                if (!crafterScene().watching()) {
                    return false;
                }
                // курсор больше не держим: пусть сцена ведёт себя как с настоящим
                CrafterScene.smokePin = -1;
                check("every window filled or covered, and turning: the crafters set to work, and the scene watches", true);
                return true;
            }),
            new Step("watching", 12, mc -> {
                shot(mc, "20_crafting");
                return true;
            }),
            new Step("crafted", 0, mc -> {
                if (crafterScene().crafts < 1) {
                    return false;
                }
                shot(mc, "21_crafted");
                check("the moment the result forms is caught: the output window flashes, once", crafterScene().crafts == 1);
                return true;
            }),
            new Step("chest", 0, mc -> {
                if (!madeChest(mc) || crafterScene().watching()) {
                    return false;
                }
                check("Create made the chest, and the crafters are idle and empty again", crafterWallIdle(mc));
                Scene.leave();
                return true;
            }),
            new Step("left the crafters", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("to the lone crafter", 2, mc -> onServer(mc, player -> standBefore(player, LONE_CRAFTER, 2.0))),
            new Step("right-click the lone crafter", 4, mc -> use(mc, EMPTY_HAND, LONE_CRAFTER, Direction.NORTH)),
            new Step("lone crafter scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a crafter by itself is a group of one", Scene.station instanceof CrafterScene scene
                        && scene.group().cells().size() == 1);
                pickUp(mc, PLANKS);
                return true;
            }),
            new Step("put in by hand", 10, mc -> pinAndClick(0)),
            new Step("pushing in", 8, mc -> {
                shot(mc, "22_by_hand");
                return true;
            }),
            new Step("in by hand", 0, mc -> {
                if (moving()) {
                    return false;
                }
                CrafterScene scene = crafterScene();
                check("a window within reach is filled by hand, not tossed (server: the plank is in)",
                        scene.inserts == 1 && scene.tosses == 0 && crafterWindow(mc, LONE_CRAFTER).is(Items.OAK_PLANKS));
                Scene.leave();
                return true;
            }),
            new Step("left the lone crafter", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("lone crafter 3.5 off", 2, mc -> onServer(mc, player -> {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                SmokeTest.farFrom = null;
                return standBefore(player, LONE_CRAFTER, 3.5);
            })),
            new Step("right-click: lone crafter 3.5 off", 4, mc -> use(mc, EMPTY_HAND, LONE_CRAFTER, Direction.NORTH)),
            new Step("lone crafter 3.5 off: started", 0, mc -> farCrafter(mc, "lone crafter 3.5 off", false)),
            new Step("lone crafter 3.5 off: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("lone crafter 3.5 off, grip", 2, mc -> onServer(mc, player -> {
                offhandGrip(player);
                SmokeTest.farFrom = null;
                return standBefore(player, LONE_CRAFTER, 3.5);
            })),
            new Step("right-click: lone crafter 3.5 off, grip", 4, mc -> use(mc, EMPTY_HAND, LONE_CRAFTER, Direction.NORTH)),
            new Step("lone crafter 3.5 off, grip: started", 0, mc -> farCrafter(mc, "lone crafter 3.5 off, grip", true)),
            new Step("lone crafter 3.5 off, grip: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("lone crafter 4.5 off, grip", 2, mc -> onServer(mc, player -> {
                offhandGrip(player);
                SmokeTest.farFrom = null;
                return standBefore(player, LONE_CRAFTER, 4.5);
            })),
            new Step("right-click: lone crafter 4.5 off, grip", 4, mc -> use(mc, EMPTY_HAND, LONE_CRAFTER, Direction.NORTH)),
            new Step("lone crafter 4.5 off, grip: started", 0, mc -> farCrafter(mc, "lone crafter 4.5 off, grip", true)),
            new Step("lone crafter 4.5 off, grip: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("3x3 crafters 4.0 off", 2, mc -> onServer(mc, player -> {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                SmokeTest.farFrom = null;
                return standBefore(player, crafterCell(1, 2), 4.0);
            })),
            new Step("right-click: 3x3 crafters 4.0 off", 4, mc -> use(mc, EMPTY_HAND, crafterCell(1, 2), Direction.NORTH)),
            new Step("3x3 crafters 4.0 off: started", 0, mc -> farCrafter(mc, "3x3 crafters 4.0 off", false)),
            new Step("3x3 crafters 4.0 off: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("3x3 crafters 4.0 off, grip", 2, mc -> onServer(mc, player -> {
                offhandGrip(player);
                SmokeTest.farFrom = null;
                return standBefore(player, crafterCell(1, 2), 4.0);
            })),
            new Step("right-click: 3x3 crafters 4.0 off, grip", 4, mc -> use(mc, EMPTY_HAND, crafterCell(1, 2), Direction.NORTH)),
            new Step("3x3 crafters 4.0 off, grip: started", 0, mc -> farCrafter(mc, "3x3 crafters 4.0 off, grip", true)),
            new Step("3x3 crafters 4.0 off, grip: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("bare, flying before the 3x3", 2, mc -> {
                mc.player.getAbilities().flying = true;
                return onServer(mc, player -> {
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    // а перед стеной пусто: камень проверки «камень перед крафтерами» остаётся от частичных прогонов
                    BlockPos.betweenClosed(CRAFTERS.offset(-3, -2, -4), CRAFTERS.offset(1, 1, -1))
                            .forEach(at -> player.serverLevel().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()));
                    return fly(player, true) && standAt(player, crafterCell(1, 1).getX() + 0.5, -58.5, CRAFTERS.getZ() - 3.5);
                });
            }),
            new Step("right-click: bare, flying before the 3x3", 4, mc -> {
                mc.player.getAbilities().flying = true;
                return use(mc, EMPTY_HAND, crafterCell(1, 1), Direction.NORTH);
            }),
            new Step("bare, flying before the 3x3: refused", 30, mc -> {
                List<String> cards = CiNotice.recent();
                check("crafters, flying bare-handed 3.5 off: no walk that cannot happen, the notice says to land or take a grip (" + Scene.phase + ", " + cards + ")",
                        Scene.phase == Scene.Phase.NONE && cards.stream().anyMatch(card -> card.startsWith("Flying")));
                return true;
            }),
            new Step("grip, on a ledge before the 3x3", 2, mc -> {
                mc.player.getAbilities().flying = false;
                return onServer(mc, player -> {
                fly(player, false);
                offhandGrip(player);
                SmokeTest.farFrom = null;
                BlockPos ledge = new BlockPos(crafterCell(1, 1).getX(), -59, CRAFTERS.getZ() - 4);
                player.serverLevel().setBlockAndUpdate(ledge, Blocks.STONE.defaultBlockState());
                return standAt(player, ledge.getX() + 0.5, -58, ledge.getZ() + 0.5);
                });
            }),
            new Step("right-click: grip, on a ledge before the 3x3", 4, mc -> {
                SmokeTest.farClicked = crafterCell(1, 2);
                return use(mc, EMPTY_HAND, SmokeTest.farClicked, Direction.NORTH);
            }),
            new Step("grip, on a ledge before the 3x3: started", 0, mc -> farCrafter(mc, "grip, on a ledge 2 above the bottom row", true)),
            new Step("grip, on a ledge before the 3x3: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("crafters: grip off", 2, mc -> onServer(mc, player -> {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                return true;
            })),
            new Step("build a 10×10", 2, mc -> {
                onServer(mc, player -> buildBigCrafters(player) && crafterKit(player) && give(player, GRIP, AllItems.EXTENDO_GRIP.asStack())
                        && standBefore(player, BIG_CRAFTERS.west(5), 2.5));
                return true;
            }),
            new Step("right-click the 10×10 by hand", 4, mc -> use(mc, EMPTY_HAND, BIG_CRAFTERS.west(5).below(8), Direction.NORTH)),
            new Step("too big by hand", 20, mc -> {
                List<String> cards = CiNotice.recent();
                check("a 10×10 wall is too big to work by hand: no scene, and the card asks for an Extendo Grip (" + cards + ")",
                        Scene.phase == Scene.Phase.NONE && cards.stream().anyMatch(card -> card.startsWith("Too big") && card.contains("Extendo Grip")));
                return true;
            }),
            new Step("right-click the 10×10 with the grip", 4, mc -> use(mc, GRIP, BIG_CRAFTERS.west(5).below(8), Direction.NORTH)),
            new Step("10×10 scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("with the grip, a 10×10 wall, the largest Create builds, is one group", Scene.station instanceof CrafterScene scene
                        && scene.gripMode() && scene.group().width() == 10 && scene.group().height() == 10 && scene.group().cells().size() == 100);
                return true;
            }),
            new Step("10×10 framed", 20, mc -> {
                shot(mc, "23_crafters_10x10_grip");
                CameraRig.Pose camera = CameraRig.last();
                double off = camera == null ? 99 : camera.pos().distanceTo(mc.player.getEyePosition());
                int[] section = crafterScene().section();
                String missing = crafterScene().outOfFrame();
                check("the view is the player's own eyes (" + String.format("%.2f", off) + " off them, no flying), looking at a section of the wall ("
                        + crafterScene().framing() + (missing.isEmpty() ? "" : "; not shown: " + missing) + ")",
                        off < 0.3 && !crafterScene().freeCamera() && section[2] * section[3] > 1 && missing.isEmpty());
                pickUp(mc, PLANKS);
                return true;
            }),
            new Step("the grip fills a window", 6, mc -> {
                SmokeTest.bigCell = crafterScene().windowIn(true);
                return pinAndClick(SmokeTest.bigCell);
            }),
            new Step("filled by the grip", 0, mc -> {
                if (moving()) {
                    return false;
                }
                BlockPos cell = crafterScene().group().cells().get(SmokeTest.bigCell);
                check("the grip's hand puts a plank into a window within its reach, no toss (server: " + crafterWindow(mc, cell) + ")",
                        crafterWindow(mc, cell).is(Items.OAK_PLANKS) && crafterScene().tosses == 0 && crafterScene().inserts == 1);
                CrafterScene.smokePin = -1;
                SmokeTest.lookedAt = crafterScene().lookPitch();
                crafterScene().keyPressed(GLFW.GLFW_KEY_W);
                return true;
            }),
            new Step("look up", 12, mc -> {
                crafterScene().keyPressed(GLFW.GLFW_KEY_W);
                return true;
            }),
            new Step("looked up", 12, mc -> {
                int[] section = crafterScene().section();
                float pitch = crafterScene().lookPitch();
                shot(mc, "23b_crafters_10x10_up");
                check("W turns the head up the wall, two sections (row " + section[1] + " of " + section[3] + "; pitch " + Math.round(SmokeTest.lookedAt)
                        + "° -> " + Math.round(pitch) + "°), the eyes where they were", section[1] == section[3] - 3 && pitch < SmokeTest.lookedAt - 15);
                SmokeTest.bigCell = crafterScene().windowIn(true);
                return pinAndClick(SmokeTest.bigCell);
            }),
            new Step("filled up the wall", 0, mc -> {
                if (moving()) {
                    return false;
                }
                BlockPos cell = crafterScene().group().cells().get(SmokeTest.bigCell);
                check("up there too, the grip puts a plank into a window it reaches (server: " + crafterWindow(mc, cell) + ")",
                        crafterWindow(mc, cell).is(Items.OAK_PLANKS) && crafterScene().inserts == 2);
                CrafterScene.smokePin = -1;
                crafterScene().keyPressed(GLFW.GLFW_KEY_W);
                return true;
            }),
            new Step("the top of the wall", 12, mc -> {
                SmokeTest.bigCell = crafterScene().windowIn(false);
                if (SmokeTest.bigCell < 0) {
                    check("at the top of the wall, some windows are out of the grip's reach", false);
                    return true;
                }
                return pinAndClick(SmokeTest.bigCell);
            }),
            new Step("out of the grip's reach", 6, mc -> {
                List<String> cards = CiNotice.recent();
                BlockPos cell = SmokeTest.bigCell < 0 ? null : crafterScene().group().cells().get(SmokeTest.bigCell);
                check("a window at the top is out of the grip's reach from the ground: refused, and the card says so (" + cards + ")",
                        cell != null && crafterScene().denials == 1 && crafterWindow(mc, cell).isEmpty()
                                && cards.stream().anyMatch(card -> card.startsWith("Out of the grip's reach")));
                CrafterScene.smokePin = -1;
                Scene.leave();
                return true;
            }),
            new Step("left the 10×10", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip, flying at the 10x10's middle", 2, mc -> {
                mc.player.getAbilities().flying = true;
                return onServer(mc, player -> {
                    offhandGrip(player);
                    SmokeTest.farFrom = null;
                    return fly(player, true) && standAt(player, BIG_CRAFTERS.west(5).getX() + 0.5, -56, BIG_CRAFTERS.getZ() - 3.5);
                });
            }),
            new Step("right-click: grip, flying at the 10x10's middle", 4, mc -> {
                // на земле клиент сам снимает полёт: включается, когда игрок уже в воздухе
                mc.player.getAbilities().flying = true;
                mc.player.setDeltaMovement(Vec3.ZERO);
                SmokeTest.farClicked = BIG_CRAFTERS.west(5).below(5);
                return use(mc, EMPTY_HAND, SmokeTest.farClicked, Direction.NORTH);
            }),
            new Step("grip, flying at the 10x10's middle: started", 0, mc -> farCrafter(mc, "grip, flying at the 10x10's middle", true)),
            new Step("grip, flying at the 10x10's middle: left", 0, mc -> {
                mc.player.getAbilities().flying = false;
                return Scene.phase == Scene.Phase.NONE;
            }),
            new Step("crafters in the air: done", 2, mc -> onServer(mc, player -> {
                fly(player, false);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                player.serverLevel().setBlockAndUpdate(new BlockPos(crafterCell(1, 1).getX(), -59, CRAFTERS.getZ() - 4), Blocks.AIR.defaultBlockState());
                return standAtStart(player);
            })),
            // ---- the Hand Crank: turned by hand, held as long as the player likes ----
            new Step("build the crank", 2, mc -> {
                ClientConfig.set(SceneKinds.CRANK, Trigger.USE);
                onServer(mc, player -> buildCrank(player) && crafterKit(player) && standAtCrank(player));
                return true;
            }),
            new Step("right-click the crank", 4, mc -> use(mc, EMPTY_HAND, CRANK, Direction.NORTH)),
            new Step("crank scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on the Hand Crank starts the crank scene", Scene.station instanceof CrankScene);
                // на мирной сложности насыщение само растёт, так что расход виден только по усталости, пока она меньше 4
                onServer(mc, player -> {
                    player.getFoodData().setExhaustion(0);
                    return true;
                });
                crankAngle = crankAngle(mc);
                CrankScene.smokeMarks = true;
                crankScene().hold(0);
                return true;
            }),
            new Step("cranking", 22, mc -> {
                shot(mc, "24_cranking");
                return true;
            }),
            new Step("cranking on", 8, mc -> {
                float shaft = shaftSpeed(mc, CRANK_SHAFT);
                double turned = crankAngle(mc) - crankAngle;
                shot(mc, "25_cranking_on");
                check("holding the left button cranks: the shaft behind turns (server: " + shaft + " RPM), and the hands ride "
                        + "Create's handle round (" + Math.round(turned) + "°)", shaft != 0 && Math.abs(turned) > 90);
                // по 32 × 0.01 за поворот (настройка Create по умолчанию); последний может быть ещё в пути
                float spent = onServer(mc, player -> player.getFoodData().getExhaustionLevel()), each = 32 * 0.01f;
                int turns = crankScene().turns;
                check("each turn costs the hunger Create's own does (" + String.format("%.2f", spent) + " exhaustion for "
                        + turns + " turns)", spent <= each * turns + 0.01f && spent >= each * (turns - 1) - 0.01f);
                crankOn = Math.signum(shaft);
                crankScene().hold(-1);
                crankAngle = crankAngle(mc);
                return true;
            }),
            new Step("coasting", 5, mc -> {
                check("let go, the handle still turns on a little (Create's crank runs down)", crankAngle(mc) != crankAngle);
                return true;
            }),
            new Step("let go", 30, mc -> {
                check("and then stops: the shaft stands still (server)", shaftSpeed(mc, CRANK_SHAFT) == 0 && !crankScene().cranking());
                crankScene().hold(1);
                return true;
            }),
            new Step("crank back", 30, mc -> {
                float shaft = shaftSpeed(mc, CRANK_SHAFT);
                check("the right button cranks the other way (server: " + shaft + " RPM)", shaft != 0 && Math.signum(shaft) == -crankOn);
                crankScene().hold(-1);
                onServer(mc, player -> {
                    // на мирной сложности еда сама прибывает: с запасом ниже порога в 6
                    player.getFoodData().setFoodLevel(2);
                    return true;
                });
                return true;
            }),
            new Step("tired", 4, mc -> {
                String said = crankScene().hint().getContents() instanceof TranslatableContents hint ? hint.getKey() : "-";
                check("a hungry player gets tired, and the hint says so (food " + mc.player.getFoodData().getFoodLevel() + ", hint "
                        + said + ")", crankScene().tired() && said.equals("cinecreate.hint.hand_crank.tired"));
                onServer(mc, player -> {
                    player.getFoodData().setFoodLevel(20);
                    return true;
                });
                Scene.leave();
                return true;
            }),
            new Step("left the crank", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- the Basin under a mixer, on a Blaze Burner ----
            new Step("build the basin", 2, mc -> {
                ClientConfig.set(SceneKinds.BASIN, Trigger.USE);
                ClientConfig.set(SceneKinds.BURNER, Trigger.USE);
                onServer(mc, player -> buildBasin(player) && basinKit(player) && standBefore(player, BASIN, 1.9));
                return true;
            }),
            new Step("spin up the mixer", 5, mc -> onServer(mc, player -> mixerMotor(player, MIXER_RPM))),
            new Step("right-click into the basin", 4, mc -> use(mc, EMPTY_HAND, BASIN, Direction.UP)),
            new Step("basin scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click into a basin starts the basin scene", Scene.station instanceof BasinScene);
                BasinScene.smokePin = true;
                pickUp(mc, WATER);
                return true;
            }),
            new Step("pour", 10, mc -> {
                basinClick();
                return true;
            }),
            new Step("pouring", 10, mc -> {
                shot(mc, "26_pouring");
                return true;
            }),
            new Step("poured", 0, mc -> {
                if (moving()) {
                    return false;
                }
                int water = basinFluid(mc);
                check("a water bucket tipped over the rim pours it in (server: " + water + " mB), and comes back empty",
                        water == 1000 && carried(mc).is(Items.BUCKET));
                basinClick();
                return true;
            }),
            new Step("filled", 4, mc -> {
                if (moving()) {
                    return false;
                }
                // как клик Create: полное ведро уходит в инвентарь, пустое из руки — расходуется
                int water = basinFluid(mc);
                check("the empty bucket dips in and fills up again, into the inventory as Create's click does (server: "
                        + water + " mB left)", water == 0 && carried(mc).isEmpty()
                        && onServer(mc, p -> p.getInventory().countItem(Items.WATER_BUCKET) == 1));
                return true;
            }),
            new Step("andesite in hand", 4, mc -> {
                pickUp(mc, ANDESITE);
                return true;
            }),
            new Step("drop the andesite", 4, mc -> {
                basinClick();
                return true;
            }),
            new Step("andesite in", 4, mc -> {
                if (moving()) {
                    return false;
                }
                check("dropped in: the andesite lies in the basin (server)", basinHolds(mc, Items.ANDESITE));
                pickUp(mc, NUGGET);
                return true;
            }),
            new Step("drop the nugget", 4, mc -> {
                basinClick();
                return true;
            }),
            new Step("mixing", 0, mc -> {
                if (!basinScene().mixing()) {
                    if (SmokeTest.waited % 100 == 0) {
                        CineCreate.LOGGER.info("[cinecreate smoke] waiting for the mixer: {}", basinContents(mc));
                    }
                    return false;
                }
                check("both ingredients in: the mixer above comes down into the basin, and the scene watches", true);
                return true;
            }),
            new Step("whisking", 15, mc -> {
                shot(mc, "27_mixing");
                return true;
            }),
            new Step("mixed", 0, mc -> {
                if (basinScene().outputs < 1) {
                    return false;
                }
                check("the result appears in the basin and flashes in (server: andesite alloy)",
                        basinHolds(mc, AllItems.ANDESITE_ALLOY.get()));
                return true;
            }),
            new Step("take it all", 10, mc -> {
                if (moving() || basinScene().mixing()) {
                    return false;
                }
                basinClick();
                return true;
            }),
            new Step("taken", 4, mc -> {
                if (moving()) {
                    return false;
                }
                check("an empty hand takes everything out, into the inventory (the andesite alloy)", basinEmpty(mc)
                        && onServer(mc, p -> p.getInventory().countItem(AllItems.ANDESITE_ALLOY.get()) == 1));
                BasinScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the basin", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- the Blaze Burner under it: fed, and a cake ----
            new Step("to the burner", 2, mc -> onServer(mc, player -> standBefore(player, BASIN_BURNER, 1.6))),
            new Step("right-click the burner", 4, mc -> use(mc, EMPTY_HAND, BASIN_BURNER, Direction.NORTH)),
            new Step("burner scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on a Blaze Burner's cage starts the burner scene", Scene.station instanceof BurnerScene);
                BurnerScene.smokePin = true;
                pickUp(mc, DIRT);
                return true;
            }),
            new Step("offer dirt", 6, mc -> {
                burnerClick();
                return true;
            }),
            new Step("dirt refused", 6, mc -> {
                String said = burnerScene().hint().getContents() instanceof TranslatableContents hint ? hint.getKey() : "-";
                check("the blaze won't eat dirt: refused, and the hint says so (" + said + ")",
                        burnerScene().denials == 1 && said.equals("cinecreate.hint.blaze_burner.refused"));
                pickUp(mc, DIRT);
                return true;
            }),
            new Step("coal in hand", 4, mc -> {
                pickUp(mc, COAL);
                return true;
            }),
            new Step("feed coal", 4, mc -> {
                burnerClick();
                return true;
            }),
            new Step("tossing", 6, mc -> {
                shot(mc, "28_feeding");
                return true;
            }),
            new Step("fed", 0, mc -> {
                if (moving()) {
                    return false;
                }
                HeatLevel heat = heat(mc, BASIN_BURNER);
                check("coal tossed through the cage kindles the blaze (server: " + heat + "), one coal is eaten",
                        heat == HeatLevel.KINDLED && carried(mc).getCount() == 1);
                pickUp(mc, COAL);
                return true;
            }),
            new Step("cake in hand", 4, mc -> {
                pickUp(mc, CAKE);
                return true;
            }),
            new Step("feed the cake", 4, mc -> {
                burnerClick();
                return true;
            }),
            new Step("seething", 0, mc -> {
                if (moving()) {
                    return false;
                }
                shot(mc, "29_seething");
                HeatLevel heat = heat(mc, BASIN_BURNER);
                check("a Blaze Cake makes it seethe, with the blue flash (server: " + heat + ")",
                        heat == HeatLevel.SEETHING && burnerScene().cakes == 1);
                BurnerScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the burner", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("back to the basin", 2, mc -> onServer(mc, player -> standBefore(player, BASIN, 1.9))),
            new Step("right-click the mixer", 4, mc -> use(mc, EMPTY_HAND, MIXER, Direction.NORTH)),
            new Step("heat in the basin", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on the mixer works the basin under it; the seething burner glows on its floor",
                        Scene.station instanceof BasinScene scene && scene.heat() == HeatLevel.SEETHING);
                return true;
            }),
            new Step("glowing", 15, mc -> {
                shot(mc, "30_basin_heat");
                Scene.leave();
                return true;
            }),
            new Step("left the basin again", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- an empty burner, lit with flint and steel ----
            new Step("to the empty burner", 2, mc -> onServer(mc, player -> standBefore(player, EMPTY_BURNER, 1.6))),
            new Step("right-click the empty burner", 4, mc -> use(mc, EMPTY_HAND, EMPTY_BURNER, Direction.NORTH)),
            new Step("empty burner scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("an empty burner (no blaze, no block entity) has the burner scene too",
                        Scene.station instanceof BurnerScene scene && scene.heat() == HeatLevel.NONE);
                BurnerScene.smokePin = true;
                pickUp(mc, FLINT);
                return true;
            }),
            new Step("strike", 6, mc -> {
                burnerClick();
                return true;
            }),
            new Step("lit", 0, mc -> {
                if (!onServer(mc, p -> p.serverLevel().getBlockState(EMPTY_BURNER).is(AllBlocks.LIT_BLAZE_BURNER.get()))) {
                    return false;
                }
                check("flint and steel struck at it lights the empty burner, as Create's click does (server: lit)", true);
                BurnerScene.smokePin = false;
                return true;
            }),
            new Step("scene over", 0, mc -> {
                if (Scene.phase == Scene.Phase.ACTIVE) {
                    Scene.leave();
                }
                return Scene.phase == Scene.Phase.NONE;
            }),
            // ---- Wave 2: the drain, a spout, a deployer and a fan over depots, the hand-off, the millstone, the saw, the ejector ----
            new Step("build the drain", 2, mc -> {
                // the depot composites were switched OFF with the depot in the trigger-policy steps
                for (SceneKind kind : List.of(SceneKinds.SPOUTING, SceneKinds.DEPLOYING, SceneKinds.FAN, SceneKinds.DRAIN,
                        SceneKinds.DEPLOYER, SceneKinds.MILLSTONE, SceneKinds.SAW, SceneKinds.EJECTOR)) {
                    ClientConfig.set(kind, Trigger.USE);
                }
                onServer(mc, player -> buildWave2(player) && wave2Kit(player) && standBefore(player, DRAIN, 1.6));
                return true;
            }),
            new Step("right-click the drain", 4, mc -> use(mc, EMPTY_HAND, DRAIN, Direction.UP)),
            new Step("drain scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on the Item Drain's grate starts the drain scene", Scene.station instanceof DrainScene);
                DrainScene.smokePin = true;
                pickUp(mc, FULL_BUCKET);
                return true;
            }),
            new Step("lay the bucket", 6, mc -> {
                drainScene().hover();
                drainScene().click(0, false);
                return true;
            }),
            new Step("draining", 0, mc -> {
                if (drainScene().drained < 1 && drainFluid(mc) < 1000) {
                    return false;
                }
                shot(mc, "31_draining");
                return true;
            }),
            new Step("drained", 0, mc -> {
                if (drainScene().caught < 1) {
                    return false;
                }
                int water = drainFluid(mc);
                check("a water bucket laid on the grate rolls in and is drained (server: " + water + " mB), the empty one rolls "
                        + "back out to the player", water == 1000 && drainScene().laid == 1 && drainScene().drained == 1);
                return true;
            }),
            new Step("bucket back", 0, mc -> {
                if (!onServer(mc, p -> p.getInventory().countItem(Items.BUCKET) == 2)) {
                    return false;
                }
                check("the empty bucket comes back into the inventory", true);
                DrainScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the drain", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("to the spout", 2, mc -> onServer(mc, player -> wave2Kit(player) && standBefore(player, SPOUT_DEPOT, 1.6))),
            new Step("right-click under the spout", 4, mc -> use(mc, EMPTY_HAND, SPOUT_DEPOT, Direction.UP)),
            new Step("spout scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a depot under a Spout has the spouting scene", Scene.station instanceof SpoutScene);
                DepotScene.smokePin = true;
                pickUp(mc, BOTTLE);
                return true;
            }),
            new Step("bottle down", 6, mc -> {
                scene().hover();
                scene().click(1, false);
                return true;
            }),
            new Step("pouring in", 0, mc -> {
                if (!(mc.level.getBlockEntity(SPOUT) instanceof SpoutBlockEntity spout) || spout.processingTicks <= 5) {
                    return false;
                }
                shot(mc, "32_spouting");
                return true;
            }),
            new Step("bottle filled", 0, mc -> {
                ItemStack lying = onServer(mc, p -> {
                    DepotBehaviour depot = BlockEntityBehaviour.get(p.serverLevel(), SPOUT_DEPOT, DepotBehaviour.TYPE);
                    return depot == null ? ItemStack.EMPTY : depot.getHeldItemStack().copy();
                });
                // сервер наполняет на тик-другой позже, чем клиентский отсчёт проходит 5
                if (((SpoutScene) Scene.station).fills < 1 || !lying.is(Items.POTION)) {
                    return false;
                }
                check("the spout pours, and the bottle on the plate flares as it fills (server: " + lying + ")", true);
                return true;
            }),
            new Step("flared", 3, mc -> {
                shot(mc, "33_spout_flare");
                DepotScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the spout", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("to the deployer over a depot", 2, mc -> onServer(mc, player -> wave2Kit(player) && emptyDepot(player, DEPLOYING_DEPOT)
                    && deployerMotor(player, DEPLOYER_RPM) && deployerHolds(player, DOWN_DEPLOYER, AllItems.ANDESITE_ALLOY.asStack())
                    && standBefore(player, DEPLOYING_DEPOT, 1.6))),
            new Step("right-click under the deployer", 4, mc -> use(mc, EMPTY_HAND, DEPLOYING_DEPOT, Direction.UP)),
            new Step("deploying scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a depot under a Deployer pointing down has the deploying scene", Scene.station instanceof DeployingScene);
                DepotScene.smokePin = true;
                pickUp(mc, LOG);
                return true;
            }),
            new Step("log down", 6, mc -> {
                scene().hover();
                scene().click(0, false);
                return true;
            }),
            new Step("applied", 0, mc -> {
                DeployingScene deploying = (DeployingScene) Scene.station;
                if (deploying.applied < 1) {
                    return false;
                }
                ItemStack lying = onServer(mc, p -> {
                    DepotBehaviour depot = BlockEntityBehaviour.get(p.serverLevel(), DEPLOYING_DEPOT, DepotBehaviour.TYPE);
                    return depot == null ? ItemStack.EMPTY : depot.getHeldItemStack().copy();
                });
                shot(mc, "34_deploying");
                check("the deployer punches down with the andesite alloy (" + deploying.punches + " punch), and the log becomes "
                        + "andesite casing (server: " + lying + ")", deploying.punches >= 1 && lying.is(AllBlocks.ANDESITE_CASING.asItem()));
                DepotScene.smokePin = false;
                // рука активатора, не выходя из сцены (мотор стоит: иначе активатор тут же пустит в ход то, что дали)
                onServer(mc, player -> deployerMotor(player, 0));
                DeployingScene.smokeHand = true;
                pickUp(mc, HANDED);
                return true;
            }),
            new Step("hand the deployer andesite", 12, mc -> {
                // CI играет взятие из ящика ходом сцены: пока он идёт, клик пропал бы
                if (moving()) {
                    return false;
                }
                ((DeployingScene) Scene.station).hoverHand();
                ((DeployingScene) Scene.station).click(0, false);
                return true;
            }),
            new Step("the deployer took it", 0, mc -> {
                if (moving()) {
                    return false;
                }
                ItemStack held = deployerHeld(mc, DOWN_DEPLOYER);
                shot(mc, "34b_deployer_hand");
                check("from the depot's scene, the hand reaches up to the deployer's hand and gives it the andesite (server: "
                        + held + "; the hand: " + carried(mc) + ")", held.is(Items.ANDESITE) && carried(mc).isEmpty()
                        && ((DeployingScene) Scene.station).swaps == 1);
                return true;
            }),
            new Step("take it back", 12, mc -> {
                ((DeployingScene) Scene.station).hoverHand();
                ((DeployingScene) Scene.station).click(0, false);
                return true;
            }),
            new Step("took it back", 0, mc -> {
                if (moving()) {
                    return false;
                }
                ItemStack held = deployerHeld(mc, DOWN_DEPLOYER);
                check("an empty hand takes it back into the scene's hand (server: " + held + "; the hand: " + carried(mc) + ")",
                        held.isEmpty() && carried(mc).is(Items.ANDESITE));
                pickUp(mc, HANDED);
                DeployingScene.smokeHand = false;
                onServer(mc, player -> deployerMotor(player, DEPLOYER_RPM));
                Scene.leave();
                return true;
            }),
            new Step("left the deploying", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // фальшивый игрок активатора жмёт ПКМ по нашему блоку: обработчик CI читал его соединение и ронял сервер
            new Step("a deployer clicks an empty depot", 2, mc -> onServer(mc, player -> {
                emptyDepot(player, DEPLOYING_DEPOT);
                deployerHolds(player, DOWN_DEPLOYER, new ItemStack(Items.STICK));
                return deployerMotor(player, 64);
            })),
            new Step("the deployer clicked", 100, mc -> {
                ItemStack held = deployerHeld(mc, DOWN_DEPLOYER);
                check("a deployer's fake player right-clicking one of our blocks is Create's own click, and the server keeps running (it holds "
                        + held + ")", held.is(Items.STICK));
                return onServer(mc, player -> deployerMotor(player, 0));
            }),
            new Step("to the deployer's hand", 2, mc -> onServer(mc, player -> wave2Kit(player)
                    && deployerHolds(player, HANDOFF_DEPLOYER, new ItemStack(Items.FLINT)) && standBefore(player, HANDOFF_DEPLOYER, 1.3))),
            new Step("right-click the deployer's hand", 4, mc -> use(mc, EMPTY_HAND, HANDOFF_DEPLOYER, Direction.NORTH)),
            new Step("hand-off scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on a Deployer's hand starts the hand-off scene", Scene.station instanceof DeployerScene);
                DeployerScene.smokePin = true;
                pickUp(mc, HANDED);
                return true;
            }),
            new Step("hand it over", 6, mc -> {
                ((DeployerScene) Scene.station).hover();
                ((DeployerScene) Scene.station).click(0, false);
                return true;
            }),
            new Step("handing over", 6, mc -> {
                shot(mc, "35_handoff");
                return true;
            }),
            new Step("handed over", 0, mc -> {
                if (moving()) {
                    return false;
                }
                ItemStack held = deployerHeld(mc, HANDOFF_DEPLOYER);
                check("the claw takes the andesite as the fingers let go, and the hand comes back with the flint it held "
                        + "(server: the deployer holds " + held + ")", held.is(Items.ANDESITE) && carried(mc).is(Items.FLINT)
                        && ((DeployerScene) Scene.station).swaps == 1);
                pickUp(mc, HANDED);
                DeployerScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the deployer", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- deployers facing up and down: the hand-off works at every facing ----
            new Step("a deployer facing up", 2, mc -> onServer(mc, player -> {
                ServerLevel level = player.serverLevel();
                renew(level, SmokeTest.UP_DEPLOYER, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
                return wave2Kit(player) && deployerHolds(player, SmokeTest.UP_DEPLOYER, new ItemStack(Items.FLINT))
                        && standBefore(player, SmokeTest.UP_DEPLOYER, 0.5);
            })),
            new Step("right-click the deployer facing up", 4, mc -> use(mc, EMPTY_HAND, SmokeTest.UP_DEPLOYER, Direction.UP)),
            new Step("hand-off facing up", 0, mc -> handOffBegins(mc, "up")),
            new Step("hand it up", 6, mc -> handOver()),
            new Step("handed up", 0, mc -> handedOver(mc, SmokeTest.UP_DEPLOYER, "35b_handoff_up")),
            new Step("left the deployer facing up", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("a deployer facing down", 2, mc -> onServer(mc, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(SmokeTest.DOWN_HANDOFF.below(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(SmokeTest.DOWN_HANDOFF.below(2), Blocks.AIR.defaultBlockState());
                renew(level, SmokeTest.DOWN_HANDOFF, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
                return wave2Kit(player) && deployerHolds(player, SmokeTest.DOWN_HANDOFF, new ItemStack(Items.FLINT))
                        && standBefore(player, SmokeTest.DOWN_HANDOFF, 0.6);
            })),
            new Step("right-click the deployer facing down", 4, mc -> use(mc, EMPTY_HAND, SmokeTest.DOWN_HANDOFF, Direction.DOWN)),
            new Step("hand-off facing down", 0, mc -> handOffBegins(mc, "down")),
            new Step("hand it down", 6, mc -> handOver()),
            new Step("handed down", 0, mc -> handedOver(mc, SmokeTest.DOWN_HANDOFF, "35c_handoff_down")),
            new Step("left the deployer facing down", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip beside the deployer", 2, mc -> onServer(mc, player -> {
                wave2Kit(player);
                offhandGrip(player);
                deployerHolds(player, HANDOFF_DEPLOYER, new ItemStack(Items.FLINT));
                player.teleportTo(player.serverLevel(), HANDOFF_DEPLOYER.getX() + 3.0, -60, HANDOFF_DEPLOYER.getZ() - 2.5, 50f, 20f);
                return true;
            })),
            new Step("right-click the deployer from aside", 4, mc -> {
                SmokeTest.gripFrom = mc.player.position();
                return use(mc, EMPTY_HAND, HANDOFF_DEPLOYER, Direction.NORTH);
            }),
            new Step("from aside", 0, mc -> {
                if (Scene.phase == Scene.Phase.NONE && CiNotice.shown().isEmpty() && SmokeTest.gripFrom != null) {
                    return false;
                }
                if (Scene.phase != Scene.Phase.ACTIVE && Scene.phase != Scene.Phase.NONE) {
                    return false;
                }
                double moved = mc.player.position().distanceTo(SmokeTest.gripFrom);
                check("with the grip, at an angle to the deployer's hand, the player never walks up (moved " + String.format("%.2f", moved)
                        + "; " + (Scene.active() ? "worked from here" : "refused: " + CiNotice.shown()) + ")", moved < 0.1);
                if (Scene.active()) {
                    Scene.leave();
                }
                return true;
            }),
            new Step("left the deployer from aside", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip off again", 2, mc -> onServer(mc, player -> {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                return true;
            })),
            new Step("to the fan stream", 2, mc -> onServer(mc, player -> wave2Kit(player) && fanMotor(player, FAN_RPM)
                    && standBefore(player, FAN_DEPOT, 1.6))),
            new Step("the stream blows", 0, mc -> {
                Boolean pushing = onServer(mc, p -> p.serverLevel().getBlockEntity(FAN) instanceof EncasedFanBlockEntity fan
                        && fan.getAirCurrent() != null && fan.getAirCurrent().maxDistance > 0 ? Boolean.valueOf(fan.getAirCurrent().pushing) : null);
                if (pushing == null) {
                    return false;
                }
                // втягивающий вентилятор тоже обрабатывает, но тогда костёр оказывается за депо по ходу воздуха
                check("the fan blows its air out through the campfire onto the depot (it does not pull it in)", pushing);
                return true;
            }),
            new Step("right-click in the stream", 4, mc -> use(mc, EMPTY_HAND, FAN_DEPOT, Direction.UP)),
            new Step("fan scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a depot in a fan's stream over a campfire has the bulk smoking scene (" + (Scene.station instanceof FanScene fan
                        ? fan.streamKind() : "-") + ")", Scene.station instanceof FanScene fan && "smoking".equals(fan.streamKind()));
                DepotScene.smokePin = true;
                pickUp(mc, BEEF);
                return true;
            }),
            new Step("beef down", 6, mc -> {
                scene().hover();
                scene().click(1, false);
                return true;
            }),
            new Step("smoking", 30, mc -> {
                shot(mc, "36_fan_smoking");
                return true;
            }),
            new Step("smoked", 0, mc -> {
                if (((FanScene) Scene.station).results < 1) {
                    return false;
                }
                ItemStack lying = onServer(mc, p -> {
                    DepotBehaviour depot = BlockEntityBehaviour.get(p.serverLevel(), FAN_DEPOT, DepotBehaviour.TYPE);
                    return depot == null ? ItemStack.EMPTY : depot.getHeldItemStack().copy();
                });
                check("the stream smokes the beef on the plate, with the result's flash (server: " + lying + ")",
                        lying.is(Items.COOKED_BEEF));
                DepotScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the stream", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("to the millstone", 2, mc -> onServer(mc, player -> wave2Kit(player) && standBefore(player, MILLSTONE, 1.6))),
            new Step("right-click the millstone", 4, mc -> use(mc, EMPTY_HAND, MILLSTONE, Direction.UP)),
            new Step("millstone scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on a Millstone starts the millstone scene", Scene.station instanceof MillstoneScene);
                MillstoneScene.smokePin = true;
                pickUp(mc, WHEAT);
                return true;
            }),
            new Step("pour the wheat", 6, mc -> {
                millstoneScene().hover();
                millstoneScene().click(0, false);
                return true;
            }),
            new Step("trickling", 8, mc -> {
                shot(mc, "37_millstone_pour");
                return true;
            }),
            new Step("ground", 0, mc -> {
                if (!onServer(mc, p -> p.serverLevel().getBlockEntity(MILLSTONE) instanceof MillstoneBlockEntity millstone
                        && millstone.inputInv.getStackInSlot(0).isEmpty() && !millstone.outputInv.getStackInSlot(0).isEmpty())) {
                    return false;
                }
                check("poured into the mouth, the wheat is ground, flour dust puffing from the gap (" + millstoneScene().puffs
                        + " motes)", millstoneScene().pours == 1 && millstoneScene().puffs > 0);
                millstoneScene().hover();
                millstoneScene().click(0, false);
                return true;
            }),
            new Step("gathered", 4, mc -> {
                // сервер, отстающий от клиента, может не успеть: ждать его, а не тиков
                if (moving() || millstoneScene().gathers < 1 || !onServer(mc, p -> p.getInventory().countItem(AllItems.WHEAT_FLOUR.get()) >= 1)) {
                    return false;
                }
                check("an empty hand gathers what is ground into the inventory (wheat flour)", true);
                MillstoneScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the millstone", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("to the saw", 2, mc -> onServer(mc, player -> {
                // фильтр от прошлого прогона (с -PsmokeFrom мир не пересобирается) сузил бы диск до одной формы
                FilteringBehaviour filter = BlockEntityBehaviour.get(player.serverLevel(), SAW, FilteringBehaviour.TYPE);
                if (filter != null) {
                    filter.setFilter(ItemStack.EMPTY);
                }
                return wave2Kit(player) && standBefore(player, SAW, 1.6);
            })),
            new Step("right-click the saw", 4, mc -> use(mc, EMPTY_HAND, SAW, Direction.UP)),
            new Step("saw scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on a Mechanical Saw facing up starts the saw scene", Scene.station instanceof SawScene);
                SawScene.smokePin = true;
                pickUp(mc, STONE_SLOT);
                return true;
            }),
            new Step("the dial", 10, mc -> {
                List<ItemStack> dial = sawScene().dial();
                int bricks = -1;
                for (int k = 0; k < dial.size(); k++) {
                    if (dial.get(k).is(Items.STONE_BRICKS)) {
                        bricks = k;
                    }
                }
                shot(mc, "38_saw_dial");
                check("stone in hand: the shapes it can be cut into hang on the dial (" + dial.size() + ", stone bricks among them)",
                        bricks >= 0);
                sawScene().hoverShape(Math.max(0, bricks));
                sawScene().click(0, false);
                return true;
            }),
            new Step("shape chosen", 0, mc -> {
                if (moving() || !sawScene().filter().is(Items.STONE_BRICKS)) {
                    return false;
                }
                check("touching a shape sets the saw's filter to it (client: " + sawScene().filter() + ")", sawScene().chosen == 1);
                sawScene().hoverTable();
                sawScene().click(1, false);
                return true;
            }),
            new Step("cutting", 0, mc -> {
                if (!sawScene().cutting() || sawScene().progress() < 0.3) {
                    return false;
                }
                shot(mc, "39_saw_cutting");
                check("laid on the table, the stone is cut; the hand pushes it along with Create's progress ("
                        + String.format("%.2f", sawScene().progress()) + "), dust spraying", sawScene().laid == 1 && sawScene().sparks > 0);
                return true;
            }),
            new Step("cut", 0, mc -> {
                if (sawScene().cuts < 1) {
                    return false;
                }
                check("the saw cuts the shape chosen on the dial (" + sawScene().lastCut + ")", sawScene().lastCut.is(Items.STONE_BRICKS));
                pickUp(mc, HANDED);
                return true;
            }),
            new Step("andesite over the dial", 20, mc -> {
                List<ItemStack> dial = sawScene().dial();
                int[] turn = sawScene().dialTurn();
                shot(mc, "39b_saw_dial");
                check("andesite in hand: all " + dial.size() + " of its shapes hang on the dial, " + SawScene.DIAL + " at a time, as on "
                        + "CI's stonecutter (it turns " + turn[1] + " further)", dial.size() > SawScene.DIAL && turn[0] == 0 && turn[1] == dial.size() - SawScene.DIAL);
                for (int i = 0; i < 4; i++) {
                    sawScene().scroll(-1);
                }
                return true;
            }),
            new Step("the dial turned", 16, mc -> {
                int[] turn = sawScene().dialTurn();
                shot(mc, "39c_saw_dial_turned");
                check("the wheel turns the dial, a shape at a time (turned " + turn[0] + ")", turn[0] == 4);
                return true;
            }),
            new Step("choose the last shape", 16, mc -> {
                List<ItemStack> dial = sawScene().dial();
                SmokeTest.lastShape = dial.get(dial.size() - 1).copy();
                sawScene().hoverShape(dial.size() - 1);
                sawScene().click(0, false);
                return true;
            }),
            new Step("the last shape chosen", 0, mc -> {
                if (moving() || !filterOn(mc, SAW).is(SmokeTest.lastShape.getItem())) {
                    return false;
                }
                check("the last of them can be chosen too (server: " + filterOn(mc, SAW) + ")", true);
                pickUp(mc, HANDED);
                SawScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the saw", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("to the ejector", 2, mc -> onServer(mc, player -> wave2Kit(player) && aimEjector(player)
                    && standBefore(player, EJECTOR, 1.6))),
            new Step("ejector charged", 0, mc -> mc.level.getBlockEntity(EJECTOR) instanceof EjectorBlockEntity ejector
                    && ejector.getState() == EjectorBlockEntity.State.CHARGED),
            new Step("right-click the ejector", 4, mc -> use(mc, EMPTY_HAND, EJECTOR, Direction.UP)),
            new Step("ejector scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("a right-click on a Weighted Ejector's plate starts the ejector scene", Scene.station instanceof EjectorScene);
                DepotScene.smokePin = true;
                pickUp(mc, HANDED);
                return true;
            }),
            new Step("iron on the plate", 6, mc -> {
                scene().hover();
                scene().click(1, false);
                return true;
            }),
            new Step("launched", 0, mc -> {
                if (!ejectorScene().watchingNow()) {
                    return false;
                }
                CameraRig.Pose camera = CameraRig.last();
                SmokeTest.launchCamera = camera == null ? null : camera.pos();
                check("the plate snaps and throws the iron; the player stays put (no free camera) and watches it",
                        ejectorScene().launches == 1 && !ejectorScene().freeCamera());
                // площадка поднята: зона занята — клик не кладёт, подсказка говорит почему
                SmokeTest.busyHint = scene().hint().getString();
                scene().hover();
                scene().click(0, false);
                return true;
            }),
            new Step("watching", 6, mc -> {
                shot(mc, "40_ejector_watch");
                ItemStack onPlate = onServer(mc, p -> {
                    DepotBehaviour depot = BlockEntityBehaviour.get(p.serverLevel(), EJECTOR, DepotBehaviour.TYPE);
                    return depot == null ? ItemStack.EMPTY : depot.getHeldItemStack().copy();
                });
                check("while the plate is up the zone is busy: the hint says so, and a click puts nothing on it (\"" + SmokeTest.busyHint
                        + "\", server: " + onPlate + ")", SmokeTest.busyHint.startsWith("The plate is up") && onPlate.isEmpty());
                return true;
            }),
            new Step("landed", 0, mc -> {
                if (ejectorScene().watched < 1) {
                    return false;
                }
                shot(mc, "41_ejector_landed");
                CameraRig.Pose camera = CameraRig.last();
                double moved = camera == null || SmokeTest.launchCamera == null ? 99 : camera.pos().distanceTo(SmokeTest.launchCamera);
                check("the head turns after the iron (" + Math.round(ejectorScene().turnedMost) + "°) while the eyes stay where they"
                        + " were (" + String.format("%.2f", moved) + " moved)", ejectorScene().turnedMost >= 10 && moved < 0.3);
                return true;
            }),
            new Step("look back", 0, mc -> {
                if (ejectorScene().watchingNow()) {
                    return false;
                }
                BlockPos target = EJECTOR.relative(Direction.SOUTH, EJECTOR_REACH);
                check("the gaze rests where the iron lands, then comes back to the ejector (server: the iron lies by the target)",
                        onServer(mc, p -> !p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1.5),
                                item -> item.getItem().is(Items.IRON_INGOT)).isEmpty()));
                DepotScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the ejector", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- Create's value boxes stay Create's, on every block: the click is Create's own setting, no scene ----
            new Step("the ejector's stack size", 4, mc -> {
                BlockHitResult box = valueBox(mc, EJECTOR, ScrollValueBehaviour.TYPE);
                if (box != null && !use(mc, EMPTY_HAND, EJECTOR, box.getDirection(), box.getLocation())) {
                    return false;
                }
                check("a right-click on the Weighted Ejector's stack-size box (" + (box == null ? "-" : box.getDirection())
                        + " face) is Create's: its setting begins", box != null && valueInputTook(EJECTOR));
                return true;
            }),
            new Step("no scene from the stack size", 10, mc -> {
                check("and no ejector scene starts", Scene.phase == Scene.Phase.NONE);
                return true;
            }),
            new Step("to the saw's filter", 2, mc -> onServer(mc, player -> standBefore(player, SAW, 1.6))),
            new Step("the saw's filter", 4, mc -> {
                BlockHitResult box = valueBox(mc, SAW, FilteringBehaviour.TYPE);
                if (box != null && !use(mc, STONE_SLOT, SAW, box.getDirection(), box.getLocation())) {
                    return false;
                }
                return true;
            }),
            new Step("saw filter set", 10, mc -> {
                ItemStack filter = filterOn(mc, SAW);
                check("a right-click with stone on the Mechanical Saw's filter box sets its filter, as Create's own click does "
                        + "(server: " + filter + "), and no saw scene starts", filter.is(Items.STONE) && Scene.phase == Scene.Phase.NONE);
                return true;
            }),
            new Step("to the deployer's filter", 2, mc -> onServer(mc, player -> standBefore(player, DEPLOYING_DEPOT, 1.6))),
            new Step("the deployer's filter", 4, mc -> {
                BlockHitResult box = valueBox(mc, DOWN_DEPLOYER, FilteringBehaviour.TYPE);
                if (box != null && !use(mc, LOG, DOWN_DEPLOYER, box.getDirection(), box.getLocation())) {
                    return false;
                }
                return true;
            }),
            new Step("deployer filter set", 10, mc -> {
                ItemStack filter = filterOn(mc, DOWN_DEPLOYER);
                check("on a Deployer working a depot, its filter box is Create's too: a stripped log sets the filter (server: "
                        + filter + "), and the depot's scene does not start", filter.is(Items.STRIPPED_OAK_LOG) && Scene.phase == Scene.Phase.NONE);
                return true;
            }),
            // ---- blocks where the player would stand: never a view through them, and a card says why ----
            new Step("stone before the crafters", 2, mc -> onServer(mc, player -> {
                buildCrafters(player);
                crafterKit(player);
                ServerLevel level = player.serverLevel();
                // одна клетка до сборщиков пустая, дальше стена в три блока: как раз там, где стоят для сцены
                for (int x = 5; x <= 12; x++) {
                    for (int y = -60; y <= -58; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, y, 1), Blocks.STONE.defaultBlockState());
                    }
                }
                player.teleportTo(level, 8.5, -60, 2.5, 0f, 10f);
                return true;
            })),
            new Step("click from the gap", 4, mc -> use(mc, EMPTY_HAND, crafterCell(1, 1), Direction.NORTH)),
            new Step("from the gap", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                CameraRig.Pose camera = CameraRig.last();
                BlockPos at = camera == null ? null : BlockPos.containing(camera.pos());
                boolean inStone = at != null && !mc.level.getBlockState(at).getCollisionShape(mc.level, at).isEmpty();
                check("in the gap between the crafters and a stone wall, the scene plays with its view in the gap, not in the stone",
                        Scene.station instanceof CrafterScene && camera != null && !inStone);
                Scene.leave();
                return true;
            }),
            new Step("left the gap", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("on the stone", 2, mc -> onServer(mc, player -> {
                ServerLevel level = player.serverLevel();
                for (int x = 5; x <= 12; x++) {
                    level.setBlockAndUpdate(new BlockPos(x, -58, 1), Blocks.AIR.defaultBlockState());
                }
                player.teleportTo(level, 8.5, -58, 1.5, 0f, 10f);
                return true;
            })),
            new Step("click from the stone", 4, mc -> use(mc, EMPTY_HAND, crafterCell(1, 0), Direction.NORTH)),
            new Step("refused on the stone", 20, mc -> {
                List<String> cards = CiNotice.recent();
                check("standing on the stone, above the crafters' bottom row: no scene, and the card says it is too low (" + cards + ")",
                        Scene.phase == Scene.Phase.NONE && cards.stream().anyMatch(card -> card.startsWith("Too low")));
                return true;
            }),
            new Step("behind the stone", 2, mc -> onServer(mc, player -> {
                player.teleportTo(player.serverLevel(), 8.5, -60, 0.4, 0f, 0f);
                return true;
            })),
            new Step("click from behind", 4, mc -> use(mc, EMPTY_HAND, crafterCell(1, 0), Direction.NORTH)),
            new Step("refused behind", 20, mc -> {
                List<String> cards = CiNotice.recent();
                shot(mc, "42_notice");
                check("behind the stone, no scene (its view would pass through the stone), and the card names it (" + cards + ")",
                        Scene.phase == Scene.Phase.NONE && cards.stream().anyMatch(card -> card.contains("Stone")));
                return true;
            }),
            // ---- the Extendo Grip: worked from where the player stands, the grip's hand at work; put away, the scene starts again ----
            new Step("grip at the depot", 2, mc -> {
                ClientConfig.set(SceneKinds.DEPOT, Trigger.USE);
                onServer(mc, player -> {
                    reset(player);
                    give(player, GRIP, AllItems.EXTENDO_GRIP.asStack());
                    player.teleportTo(player.serverLevel(), 0.5, -60, -1.0, 0f, 30f);
                    return true;
                });
                return true;
            }),
            new Step("right-click the depot from afar", 4, mc -> {
                SmokeTest.gripFrom = mc.player.position();
                return use(mc, GRIP, Direction.UP);
            }),
            new Step("worked from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                double moved = mc.player.position().distanceTo(SmokeTest.gripFrom);
                check("with an Extendo Grip in hand, the depot 4.5 blocks off is worked from where the player stands (moved "
                        + String.format("%.2f", moved) + ")", Scene.station instanceof DepotScene scene && scene.fromAfar() && moved < 0.1);
                DepotScene.smokePin = true;
                pickUp(mc, 0);
                return true;
            }),
            new Step("the grip sets it down", 6, mc -> {
                scene().hover();
                scene().click(1, false);
                return true;
            }),
            new Step("set down by the grip", 0, mc -> {
                if (moving()) {
                    return false;
                }
                shot(mc, "43_grip_depot");
                check("the grip's hand sets one iron on the plate (server: " + depot(mc) + ")", depot(mc).is(Items.IRON_INGOT)
                        && depot(mc).getCount() == 1);
                pickUp(mc, 0);
                return true;
            }),
            new Step("put the grip away", 6, mc -> {
                // в руке теперь камень: захвата нет
                mc.player.getInventory().selected = 2;
                return true;
            }),
            new Step("walked up", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE || !(Scene.station instanceof DepotScene scene) || scene.fromAfar()) {
                    return false;
                }
                List<String> cards = CiNotice.recent();
                double off = Math.hypot(mc.player.getX() - 0.5, mc.player.getZ() - 3.5);
                check("put away, the grip no longer reaches: the scene starts again and the player walks up ("
                        + String.format("%.2f", off) + " from the depot; " + cards + ")",
                        off < 1.6 && cards.stream().anyMatch(card -> card.contains("Extendo Grip")));
                DepotScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the depot", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("a high basin", 2, mc -> onServer(mc, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(HIGH_BASIN.below(2), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(HIGH_BASIN.below(), Blocks.STONE.defaultBlockState());
                if (level.getBlockEntity(HIGH_BASIN) instanceof BasinBlockEntity basin) {
                    basin.clearContent();
                }
                level.setBlockAndUpdate(HIGH_BASIN, AllBlocks.BASIN.getDefaultState());
                player.getInventory().clearContent();
                give(player, 0, new ItemStack(Items.ANDESITE));
                give(player, GRIP, AllItems.EXTENDO_GRIP.asStack());
                return standBefore(player, HIGH_BASIN, 0.9);
            })),
            new Step("bare hands at the high basin", 4, mc -> use(mc, EMPTY_HAND, HIGH_BASIN, Direction.UP)),
            new Step("too high bare", 20, mc -> {
                List<String> cards = CiNotice.recent();
                check("a basin 2.1 blocks up is out of a bare arm's reach: no scene, and the card says a grip would reach it (" + cards + ")",
                        Scene.phase == Scene.Phase.NONE && cards.stream().anyMatch(card -> card.startsWith("Too high") && card.contains("Extendo Grip")));
                return true;
            }),
            new Step("hook the rim", 4, mc -> {
                SmokeTest.shortestGrip = Double.MAX_VALUE;
                SmokeTest.gripComing = false;
                SmokeTest.comingShot = false;
                return use(mc, GRIP, HIGH_BASIN, Direction.UP);
            }),
            new Step("hanging", 0, mc -> {
                sampleGrip(mc, "44a_grip_basin_hooking");
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("with the grip, the player hooks the rim and hangs from it", Scene.station instanceof BasinScene scene && scene.hangingNow());
                BasinScene.smokePin = true;
                pickUp(mc, 0);
                return true;
            }),
            new Step("drop in while hanging", 6, mc -> {
                basinScene().hover();
                basinScene().click(0, false);
                return true;
            }),
            new Step("dropped in while hanging", 0, mc -> {
                if (moving()) {
                    return false;
                }
                shot(mc, "44_grip_basin_hang");
                Vec3[] grip = basinScene().gripDrawn();
                check("hanging, the left fist holds the grip below the rim and Create's claw holds the rim above it (fist "
                        + (grip == null ? "-" : String.format("%.2f", grip[0].y - HIGH_BASIN.getY()) + ", claw " + String.format("%.2f", grip[1].y - HIGH_BASIN.getY()))
                        + " over the basin's foot; the shortest grip drawn while hooking on: " + String.format("%.2f", SmokeTest.shortestGrip) + ")",
                        grip != null && grip[0].y < grip[1].y - 0.2 && grip[1].y > HIGH_BASIN.getY() + 0.7 && SmokeTest.shortestGrip > 0.3);
                check("the hooked grip came up with the left hand as the scene began (drawn while the hands rose)", SmokeTest.gripComing);
                check("hanging, the bare hand drops the andesite in (server)", onServer(mc, p -> p.serverLevel().getBlockEntity(HIGH_BASIN)
                        instanceof BasinBlockEntity basin && basin.getInputInventory().getStackInSlot(0).is(Items.ANDESITE)));
                return true;
            }),
            new Step("let go of the grip", 4, mc -> {
                mc.player.getInventory().selected = EMPTY_HAND;
                return true;
            }),
            new Step("dropped down", 0, mc -> {
                if (Scene.phase != Scene.Phase.NONE) {
                    return false;
                }
                List<String> cards = CiNotice.recent();
                check("letting go of the grip drops the player off the rim: the scene ends, and the card says so (" + cards + ")",
                        cards.stream().anyMatch(card -> card.startsWith("You let go")));
                BasinScene.smokePin = false;
                return true;
            }),
            new Step("grip at the valve", 2, mc -> {
                ClientConfig.set(SceneKinds.VALVE, Trigger.USE);
                onServer(mc, player -> {
                    buildValve(player);
                    player.getInventory().clearContent();
                    give(player, GRIP, AllItems.EXTENDO_GRIP.asStack());
                    player.teleportTo(player.serverLevel(), VALVE.getX() + 0.5, -60, VALVE.getZ() - 3.0, 0f, 15f);
                    return true;
                });
                return true;
            }),
            new Step("right-click the wheel from afar", 4, mc -> use(mc, GRIP, VALVE, Direction.NORTH, Vec3.atCenterOf(VALVE).add(0.4, 0, 0))),
            new Step("valve from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from the valve, with the grip, its scene starts where the player stands",
                        Scene.station instanceof ValveScene scene && scene.fromAfar());
                SmokeTest.turnedTo = valveWheel(mc);
                valveScene().click(0, false);
                return true;
            }),
            new Step("grip turning", 8, mc -> {
                shot(mc, "45_grip_valve");
                return true;
            }),
            new Step("turned with the grip", 0, mc -> {
                if (valveTurning(mc) || moving()) {
                    return false;
                }
                float now = valveWheel(mc);
                check("the grip's hand turns the wheel (server: " + SmokeTest.turnedTo + "° -> " + now + "°)", now != SmokeTest.turnedTo);
                Scene.leave();
                return true;
            }),
            new Step("left the valve from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- the grip close to the work: folded in the fist, Create's claw ahead of it (never a second arm on the hand) ----
            new Step("grip by the basin", 2, mc -> {
                ClientConfig.set(SceneKinds.BASIN, Trigger.USE);
                return onServer(mc, player -> {
                    buildBasin(player);
                    player.getInventory().clearContent();
                    give(player, ANDESITE, new ItemStack(Items.ANDESITE));
                    give(player, GRIP, AllItems.EXTENDO_GRIP.asStack());
                    return standBefore(player, BASIN, 0.9);
                });
            }),
            new Step("right-click the basin with the grip", 4, mc -> {
                SmokeTest.shortestGrip = Double.MAX_VALUE;
                SmokeTest.gripComing = false;
                SmokeTest.comingShot = false;
                return use(mc, GRIP, BASIN, Direction.UP);
            }),
            new Step("the grip comes up with the hand", 0, mc -> {
                sampleGrip(mc, "46_grip_basin_coming");
                return Scene.phase == Scene.Phase.ACTIVE;
            }),
            new Step("the grip at rest by the basin", 20, mc -> {
                sampleGrip(mc, null);
                shot(mc, "47_grip_basin_rest");
                check("by the basin, with the grip in hand, the player leans over the rim (no grip needed: none from afar)",
                        Scene.station instanceof BasinScene scene && !scene.fromAfar() && !scene.hangingNow());
                check("close to the work the grip is folded in the fist, its claw ahead and never on the hand (shortest drawn "
                        + String.format("%.2f", SmokeTest.shortestGrip) + ", folded " + String.format("%.2f", Grip.folded()) + ")",
                        SmokeTest.shortestGrip >= Grip.folded() - 0.01 && SmokeTest.shortestGrip < 9);
                check("the grip came up with the hand as the scene began (drawn while the hands rose)", SmokeTest.gripComing);
                BasinScene.smokePin = true;
                pickUp(mc, ANDESITE);
                return true;
            }),
            new Step("the claw drops it in", 6, mc -> {
                basinScene().hover();
                basinScene().click(0, false);
                return true;
            }),
            new Step("the claw over the basin", 5, mc -> {
                shot(mc, "48_grip_basin_drop");
                return true;
            }),
            new Step("dropped in by the claw", 0, mc -> {
                if (moving()) {
                    return false;
                }
                check("the grip's claw drops the andesite into the basin (server)", onServer(mc, p -> p.serverLevel().getBlockEntity(BASIN)
                        instanceof BasinBlockEntity basin && basin.getInputInventory().getStackInSlot(0).is(Items.ANDESITE)));
                BasinScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the basin with the grip", 0, mc -> Scene.phase == Scene.Phase.NONE),
            // ---- the grip's claw at every other machine, worked from three blocks off (the grip in the off hand) ----
            new Step("grip at the crank", 2, mc -> {
                ClientConfig.set(SceneKinds.CRANK, Trigger.USE);
                return onServer(mc, player -> {
                    player.getInventory().clearContent();
                    player.getFoodData().setFoodLevel(20);
                    return offhandGrip(player) && standBefore(player, CRANK, SmokeTest.GRIP_OFF);
                });
            }),
            new Step("right-click the crank from afar", 4, mc -> use(mc, EMPTY_HAND, CRANK, Direction.NORTH)),
            new Step("crank from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from the Hand Crank, with the grip, its scene starts where the player stands",
                        Scene.station instanceof CrankScene scene && scene.fromAfar());
                crankScene().hold(0);
                return true;
            }),
            new Step("the claw cranks", 20, mc -> {
                shot(mc, "49_grip_crank");
                return true;
            }),
            new Step("cranked by the claw", 6, mc -> {
                float shaft = shaftSpeed(mc, CRANK_SHAFT);
                check("both hands on the grip's handle, its claw turns the crank: the shaft turns (server: " + shaft + " RPM)", shaft != 0);
                crankScene().hold(-1);
                Scene.leave();
                return true;
            }),
            new Step("left the crank from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip at the burner", 2, mc -> onServer(mc, player -> {
                renew(player.serverLevel(), BASIN_BURNER, AllBlocks.BLAZE_BURNER.getDefaultState()
                        .setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
                player.getInventory().clearContent();
                give(player, COAL, new ItemStack(Items.COAL, 2));
                return offhandGrip(player) && standBefore(player, BASIN_BURNER, SmokeTest.GRIP_OFF);
            })),
            new Step("right-click the burner from afar", 4, mc -> use(mc, EMPTY_HAND, BASIN_BURNER, Direction.NORTH)),
            new Step("burner from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from a Blaze Burner, with the grip, its scene starts where the player stands",
                        Scene.station instanceof BurnerScene scene && scene.fromAfar());
                BurnerScene.smokePin = true;
                pickUp(mc, COAL);
                return true;
            }),
            new Step("the claw feeds coal", 6, mc -> {
                if (moving()) {
                    return false;
                }
                burnerClick();
                return true;
            }),
            new Step("feeding with the claw", 8, mc -> {
                shot(mc, "50_grip_burner");
                return true;
            }),
            new Step("fed by the claw", 0, mc -> {
                if (moving()) {
                    return false;
                }
                HeatLevel heat = heat(mc, BASIN_BURNER);
                check("the claw pushes the coal in through the cage, no toss: the blaze kindles (server: " + heat + ")",
                        heat == HeatLevel.KINDLED && carried(mc).getCount() == 1);
                pickUp(mc, COAL);
                BurnerScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the burner from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip at the drain", 2, mc -> onServer(mc, player -> {
                renew(player.serverLevel(), DRAIN, AllBlocks.ITEM_DRAIN.getDefaultState());
                return wave2Kit(player) && offhandGrip(player) && standBefore(player, DRAIN, SmokeTest.GRIP_OFF);
            })),
            new Step("right-click the drain from afar", 4, mc -> use(mc, EMPTY_HAND, DRAIN, Direction.UP)),
            new Step("drain from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from an Item Drain, with the grip, its scene starts where the player stands",
                        Scene.station instanceof DrainScene scene && scene.fromAfar());
                DrainScene.smokePin = true;
                pickUp(mc, FULL_BUCKET);
                return true;
            }),
            new Step("the claw lays the bucket", 6, mc -> {
                if (moving()) {
                    return false;
                }
                drainScene().hover();
                drainScene().click(0, false);
                return true;
            }),
            new Step("laid by the claw", 8, mc -> {
                shot(mc, "51_grip_drain");
                return true;
            }),
            new Step("drained from afar", 0, mc -> {
                if (drainFluid(mc) < 1000) {
                    return false;
                }
                check("the claw lays the water bucket on the grate, and it is drained (server: " + drainFluid(mc) + " mB)", true);
                DrainScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the drain from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip at the deployer's hand", 2, mc -> onServer(mc, player -> wave2Kit(player) && offhandGrip(player)
                    && deployerHolds(player, HANDOFF_DEPLOYER, new ItemStack(Items.FLINT)) && standBefore(player, HANDOFF_DEPLOYER, SmokeTest.GRIP_OFF))),
            new Step("right-click the deployer's hand from afar", 4, mc -> use(mc, EMPTY_HAND, HANDOFF_DEPLOYER, Direction.NORTH)),
            new Step("hand-off from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from a Deployer's hand, with the grip, the hand-off starts where the player stands",
                        Scene.station instanceof DeployerScene scene && scene.fromAfar());
                DeployerScene.smokePin = true;
                pickUp(mc, HANDED);
                return true;
            }),
            new Step("claw to claw", 6, mc -> {
                if (moving()) {
                    return false;
                }
                ((DeployerScene) Scene.station).hover();
                ((DeployerScene) Scene.station).click(0, false);
                return true;
            }),
            new Step("handing over claw to claw", 6, mc -> {
                shot(mc, "52_grip_handoff");
                return true;
            }),
            new Step("handed over claw to claw", 0, mc -> {
                if (moving()) {
                    return false;
                }
                ItemStack held = deployerHeld(mc, HANDOFF_DEPLOYER);
                check("claw to claw: the deployer takes the andesite from the grip's claw and gives back its flint (server: "
                        + held + ")", held.is(Items.ANDESITE) && carried(mc).is(Items.FLINT));
                pickUp(mc, HANDED);
                DeployerScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the deployer from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip at the millstone", 2, mc -> onServer(mc, player -> {
                renew(player.serverLevel(), MILLSTONE, AllBlocks.MILLSTONE.getDefaultState());
                return wave2Kit(player) && offhandGrip(player) && standBefore(player, MILLSTONE, SmokeTest.GRIP_OFF);
            })),
            new Step("right-click the millstone from afar", 4, mc -> use(mc, EMPTY_HAND, MILLSTONE, Direction.UP)),
            new Step("millstone from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from a Millstone, with the grip, its scene starts where the player stands",
                        Scene.station instanceof MillstoneScene scene && scene.fromAfar());
                MillstoneScene.smokePin = true;
                pickUp(mc, WHEAT);
                return true;
            }),
            new Step("the claw pours the wheat", 6, mc -> {
                if (moving()) {
                    return false;
                }
                millstoneScene().hover();
                millstoneScene().click(0, false);
                return true;
            }),
            new Step("pouring with the claw", 8, mc -> {
                shot(mc, "53_grip_millstone");
                return true;
            }),
            new Step("ground from afar", 0, mc -> {
                if (!onServer(mc, p -> p.serverLevel().getBlockEntity(MILLSTONE) instanceof MillstoneBlockEntity millstone
                        && millstone.inputInv.getStackInSlot(0).isEmpty() && !millstone.outputInv.getStackInSlot(0).isEmpty())) {
                    return false;
                }
                check("the claw pours the wheat into the mouth, and it is ground (server)", millstoneScene().pours == 1);
                millstoneScene().hover();
                millstoneScene().click(0, false);
                return true;
            }),
            new Step("gathered by the claw", 4, mc -> {
                if (moving() || !onServer(mc, p -> p.getInventory().countItem(AllItems.WHEAT_FLOUR.get()) >= 1)) {
                    return false;
                }
                check("and gathers the flour into the inventory (server)", true);
                MillstoneScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the millstone from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip at the saw", 2, mc -> onServer(mc, player -> {
                FilteringBehaviour filter = BlockEntityBehaviour.get(player.serverLevel(), SAW, FilteringBehaviour.TYPE);
                if (filter != null) {
                    filter.setFilter(new ItemStack(Items.STONE_BRICKS));
                }
                return wave2Kit(player) && offhandGrip(player) && standBefore(player, SAW, SmokeTest.GRIP_OFF);
            })),
            new Step("right-click the saw from afar", 4, mc -> use(mc, EMPTY_HAND, SAW, Direction.UP)),
            new Step("saw from afar", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("three blocks from a Mechanical Saw, with the grip, its scene starts where the player stands",
                        Scene.station instanceof SawScene scene && scene.fromAfar());
                SawScene.smokePin = true;
                pickUp(mc, STONE_SLOT);
                return true;
            }),
            new Step("the claw lays the stone", 6, mc -> {
                if (moving()) {
                    return false;
                }
                sawScene().hoverTable();
                sawScene().click(1, false);
                return true;
            }),
            new Step("the claw pushes it in", 0, mc -> {
                if (!sawScene().cutting() || sawScene().progress() < 0.3) {
                    return false;
                }
                shot(mc, "54_grip_saw");
                check("the claw lays one stone on the table and pushes it into the blade with Create's progress ("
                        + String.format("%.2f", sawScene().progress()) + ")", sawScene().laid == 1);
                return true;
            }),
            new Step("cut from afar", 0, mc -> {
                if (sawScene().cuts < 1) {
                    return false;
                }
                check("the saw cuts it (" + sawScene().lastCut + ")", sawScene().lastCut.is(Items.STONE_BRICKS));
                pickUp(mc, STONE_SLOT);
                SawScene.smokePin = false;
                Scene.leave();
                return true;
            }),
            new Step("left the saw from afar", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("the grip put away", 2, mc -> onServer(mc, player -> {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                return true;
            })),
            // ---- the edge of the grip's reach, and a player on a seat ----
            new Step("grip at the edge of reach", 2, mc -> {
                ClientConfig.set(SceneKinds.DEPOT, Trigger.USE);
                return onServer(mc, player -> {
                    reset(player);
                    give(player, GRIP, AllItems.EXTENDO_GRIP.asStack());
                    player.teleportTo(player.serverLevel(), 0.5, -60, DEPOT.getZ() + 0.5 - SmokeTest.EDGE, 0f, 20f);
                    return true;
                });
            }),
            new Step("right-click from the edge", 4, mc -> use(mc, GRIP, Direction.UP)),
            new Step("from the edge", 0, mc -> Scene.phase == Scene.Phase.ACTIVE),
            new Step("held from the edge", 40, mc -> {
                check("at the edge of the grip's reach (" + SmokeTest.EDGE + " from the depot), the scene starts and holds (" + Scene.phase + ")",
                        Scene.phase == Scene.Phase.ACTIVE && Scene.station instanceof DepotScene);
                Scene.leave();
                return true;
            }),
            new Step("left the edge", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("grip beyond the scenes' reach", 2, mc -> onServer(mc, player -> {
                reset(player);
                player.getInventory().removeItem(player.getInventory().getItem(GRIP));
                player.setItemInHand(InteractionHand.OFF_HAND, AllItems.EXTENDO_GRIP.asStack());
                player.teleportTo(player.serverLevel(), 0.5, -60, DEPOT.getZ() + 0.5 - SmokeTest.BEYOND, 0f, 20f);
                return true;
            })),
            new Step("right-click from beyond", 4, mc -> use(mc, 0, Direction.UP)),
            new Step("from beyond", 30, mc -> {
                check("beyond the scenes' reach (" + SmokeTest.BEYOND + " from the depot) but within the grip's, the click is Create's: "
                        + "no scene, and the iron goes on the depot (" + Scene.phase + ", server: " + depot(mc) + ")",
                        Scene.phase == Scene.Phase.NONE && depot(mc).is(Items.IRON_INGOT));
                return onServer(mc, player -> {
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    return true;
                });
            }),
            new Step("a seat before the depot", 2, mc -> onServer(mc, player -> {
                ServerLevel level = player.serverLevel();
                reset(player);
                player.getInventory().removeItem(player.getInventory().getItem(GRIP));
                level.setBlockAndUpdate(SmokeTest.SEAT, AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState());
                player.teleportTo(level, SmokeTest.SEAT.getX() + 0.5, SmokeTest.SEAT.getY() + 0.5, SmokeTest.SEAT.getZ() + 0.5, 0f, 40f);
                SeatBlock.sitDown(level, SmokeTest.SEAT, player);
                return true;
            })),
            new Step("right-click from the seat", 10, mc -> mc.player.isPassenger() && use(mc, 0, Direction.UP)),
            new Step("seated scene", 0, mc -> Scene.phase == Scene.Phase.ACTIVE),
            new Step("held from the seat", 40, mc -> {
                check("seated before the depot, its scene starts and holds (" + Scene.phase + ", passenger " + mc.player.isPassenger() + ")",
                        Scene.phase == Scene.Phase.ACTIVE && Scene.station instanceof DepotScene);
                Scene.leave();
                return true;
            }),
            new Step("left the seat's scene", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("off the seat", 2, mc -> onServer(mc, player -> {
                player.stopRiding();
                player.serverLevel().setBlockAndUpdate(SmokeTest.SEAT, Blocks.AIR.defaultBlockState());
                return true;
            })),
            new Step("a seat away from the depot", 4, mc -> onServer(mc, player -> {
                ServerLevel level = player.serverLevel();
                reset(player);
                BlockPos seat = SmokeTest.SEAT.north(2);
                level.setBlockAndUpdate(seat, AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState());
                player.teleportTo(level, seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5, 0f, 30f);
                SeatBlock.sitDown(level, seat, player);
                return true;
            })),
            new Step("right-click from the far seat", 10, mc -> mc.player.isPassenger() && use(mc, 0, Direction.UP)),
            new Step("from the far seat", 30, mc -> {
                List<String> cards = CiNotice.recent();
                check("seated three blocks from the depot: no walk that cannot happen, the card says to get up, and the click is "
                        + "Create's own (" + Scene.phase + ", " + cards + ", server: " + depot(mc) + ")", Scene.phase == Scene.Phase.NONE
                        && cards.stream().anyMatch(card -> card.startsWith("Seated")) && depot(mc).is(Items.IRON_INGOT));
                if (Scene.active()) {
                    Scene.leave();
                }
                return true;
            }),
            new Step("left the far seat's scene", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("off the far seat", 2, mc -> onServer(mc, player -> {
                player.stopRiding();
                player.serverLevel().setBlockAndUpdate(SmokeTest.SEAT.north(2), Blocks.AIR.defaultBlockState());
                return true;
            })),
            // ---- client mode: a server without the add-on (both sides act so), Create's own clicks played out ----
            new Step("client mode", 2, mc -> {
                Bridge.devNoAddon = true;
                ClientMode.force(true);
                ClientConfig.set(SceneKinds.DEPOT, Trigger.USE);
                return onServer(mc, player -> reset(player) && standAtStart(player));
            }),
            new Step("right-click the depot in client mode", 4, mc -> {
                if (!settled(mc, DEPOT)) {
                    return false;
                }
                // настоящее нажатие: сперва событие клавиши (там начинается сцена режима клиента), клик — только если его не взяли
                mc.player.getInventory().selected = 0;
                BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(DEPOT).add(0, SceneBlocks.DEPOT_TOP, 0), Direction.UP, DEPOT, false);
                mc.hitResult = hit;
                if (!ClientHooks.onClickInput(1, mc.options.keyUse, InteractionHand.MAIN_HAND).isCanceled()) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                }
                return true;
            }),
            new Step("client-mode scene", 0, mc -> {
                if (Scene.phase != Scene.Phase.ACTIVE) {
                    return false;
                }
                check("on a server without the add-on, with client mode on, a right-click on the depot plays a client-mode scene,"
                        + " and the click itself never reaches the server (the iron stays in hand: " + mc.player.getMainHandItem() + ")",
                        Scene.station instanceof ClientModeStation && mc.player.getMainHandItem().is(Items.IRON_INGOT) && depot(mc).isEmpty());
                return true;
            }),
            new Step("client-mode set down", 6, mc -> {
                ((ClientModeStation) Scene.station).hover();
                ((ClientModeStation) Scene.station).click(0, false);
                return true;
            }),
            new Step("client-mode laid", 14, mc -> {
                shot(mc, "55_client_mode");
                check("its click is Create's own, with the item in hand: the iron goes on the depot (server: " + depot(mc) + ")",
                        depot(mc).is(Items.IRON_INGOT));
                ((ClientModeStation) Scene.station).keyPressed(GLFW.GLFW_KEY_9);
                return true;
            }),
            new Step("client-mode take", 4, mc -> {
                ((ClientModeStation) Scene.station).hover();
                ((ClientModeStation) Scene.station).click(0, false);
                return true;
            }),
            new Step("client-mode taken", 14, mc -> {
                check("1–9 choose the item in hand; with an empty hand Create's click takes the iron back (server: " + depot(mc) + ")",
                        depot(mc).isEmpty() && mc.player.getInventory().selected == 8);
                Scene.leave();
                return true;
            }),
            new Step("left client mode's scene", 0, mc -> Scene.phase == Scene.Phase.NONE),
            new Step("client mode off", 2, mc -> {
                Bridge.devNoAddon = false;
                ClientMode.force(false);
                return true;
            }));

    private static boolean angleFlicker;
    private static int angleTicks;
    private static ItemStack lastShape = ItemStack.EMPTY;

    /** From the depot's middle: just within and beyond {@link Bridge#SCENE_REACH}; a seat in front of the depot. */
    private static final double EDGE = 5.8, BEYOND = 6.5;
    private static final BlockPos SEAT = DEPOT.north();

    private static boolean started;
    private static boolean finished;
    private static int index;
    private static int waited;

    private record Took(String step, int ticks, long millis) {}

    private static final List<Took> TOOK = new ArrayList<>();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static long stepStarted, runStarted;
    /** The machine's health over the run: the server's worst average tick (ms), how often it was over a tick's 50 ms, the lowest frame rate. */
    private static double worstTick;
    private static int slowTicks, lowestFps = Integer.MAX_VALUE;

    public static void register(IEventBus bus) {
        bus.addListener(SmokeTest::onTick);
        bus.addListener(SmokeTest::onRenderLevel);
        if (Boolean.getBoolean("cinecreate.explore")) {
            bus.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false,
                    net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, SmokeTest::claim);
        }
    }

    /**
     * Frames on which Create's press head was first drawn at (or, frames skipped, past) the plate in its cycle, sampled
     * while the level is drawn, apart from the scene.
     */
    private static final List<Long> HEAD_DOWN = new ArrayList<>();
    private static boolean headWasDown;
    private static int sampledTicks = -1;

    private static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || mc.level == null
                || !(mc.level.getBlockEntity(PRESS) instanceof MechanicalPressBlockEntity press)) {
            return;
        }
        PressingBehaviour pressing = press.getPressingBehaviour();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float head = pressing.running ? pressing.getRenderedHeadOffset(pt) : 0;
        int ticks = pressing.running ? Math.abs(pressing.runningTicks) : -1;
        if (ticks < sampledTicks) {
            headWasDown = false;
        }
        sampledTicks = ticks;
        boolean down = pressing.running && Mth.lerp(pt, pressing.prevRunningTicks, ticks) >= PressingBehaviour.CYCLE / 2f;
        if (down && !headWasDown) {
            HEAD_DOWN.add(FrameCounter.frame());
            headWasDown = true;
        }
        if (pressing.running != pressWasRunning) {
            CineCreate.LOGGER.info("[cinecreate smoke] press {} on frame {} (ticks {}, highest head {})",
                    pressing.running ? "runs" : "stops", FrameCounter.frame(), pressing.runningTicks, highestHead);
            highestHead = 0;
        }
        pressWasRunning = pressing.running;
        highestHead = Math.max(highestHead, head);
    }

    private static boolean pressWasRunning;
    private static float highestHead;

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (!started) {
            if (mc.screen instanceof TitleScreen) {
                started = true;
                // во весь экран, но окном с рамкой (не полноэкранный режим)
                long window = mc.getWindow().getWindow();
                GLFW.glfwMaximizeWindow(window);
                CineCreate.LOGGER.info("[cinecreate smoke] window: maximized {}, decorated {}, fullscreen {}",
                        GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
                        GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE,
                        mc.getWindow().isFullscreen());
                openWorld(mc);
            }
            return;
        }
        if (mc.player != null) {
            trace(mc);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.screen != null && !Scene.active()) {
            return;
        }
        if (index >= STEPS.size()) {
            finish(mc);
            return;
        }
        sampleHealth(mc);
        Step step = STEPS.get(index);
        if (waited == 0) {
            stepStarted = System.nanoTime();
            if (runStarted == 0) {
                runStarted = stepStarted;
            }
            // с точностью до миллисекунды: по этим строкам режется видео
            CineCreate.LOGGER.info("[cinecreate smoke] step: {} @{}", step.name(), LocalTime.now().format(STAMP));
        }
        if (++waited < step.delay()) {
            return;
        }
        boolean done;
        try {
            done = step.action().run(mc);
        } catch (RuntimeException e) {
            CineCreate.LOGGER.error("[cinecreate smoke] {} threw", step.name(), e);
            check(step.name() + " (threw " + e + ")", false);
            done = true;
        }
        if (!done && waited > step.delay() + TIMEOUT) {
            check(step.name() + " (timed out)", false);
            done = true;
        }
        if (done) {
            TOOK.add(new Took(step.name(), waited, (System.nanoTime() - stepStarted) / 1_000_000));
            index++;
            waited = 0;
            if (index == 1 && !FROM.isEmpty()) {
                skipAhead();
            }
        }
    }

    /** Samples the server's average tick and the frame rate, once the world is up (so loading doesn't count). */
    private static void sampleHealth(Minecraft mc) {
        if (index == 0) {
            return;
        }
        double tick = mc.getSingleplayerServer().getAverageTickTimeNanos() / 1e6;
        worstTick = Math.max(worstTick, tick);
        if (tick > 50) {
            slowTicks++;
        }
        lowestFps = Math.min(lowestFps, mc.getFps());
    }

    /** The run's time, its slowest steps, and the machine's health: a failure in a slow run may be the machine's. */
    private static void reportTimes() {
        long total = (System.nanoTime() - runStarted) / 1_000_000;
        RESULTS.add(String.format("time: %d:%02d for %d steps; slowest: %s", total / 60_000, total / 1000 % 60, TOOK.size(),
                TOOK.stream().sorted(Comparator.comparingLong(Took::millis).reversed()).limit(8)
                        .map(took -> took.step() + " " + String.format("%.1f s", took.millis() / 1000.0))
                        .collect(Collectors.joining(", "))));
        RESULTS.add(String.format("health: the server's worst average tick %.0f ms (%s), %d samples over 50 ms; lowest %d fps",
                worstTick, worstTick > 50 ? "it fell behind: timing checks may fail" : "kept up", slowTicks,
                lowestFps == Integer.MAX_VALUE ? 0 : lowestFps));
    }

    /** Straight to the step {@link #FROM} names; the results then say which steps were left out. */
    private static void skipAhead() {
        for (int i = 1; i < STEPS.size(); i++) {
            if (STEPS.get(i).name().equals(FROM)) {
                RESULTS.add("(steps 2-" + i + " left out: the run starts at \"" + FROM + "\")");
                index = i;
                return;
            }
        }
        check("the step to start at exists: \"" + FROM + "\"", false);
    }

    private static Scene.Phase tracedPhase = Scene.Phase.NONE;
    private static String tracedScreen = "-";

    /** The log says when a scene starts or ends and which screen is up, with the step and the player's state. */
    private static void trace(Minecraft mc) {
        String screen = mc.screen == null ? "-" : mc.screen.getClass().getSimpleName();
        if (Scene.phase == tracedPhase && screen.equals(tracedScreen)) {
            return;
        }
        CineCreate.LOGGER.info("[cinecreate smoke] ({}) scene {} -> {}, screen {} -> {}, hurt {}, on ground {}, fall {}",
                index < STEPS.size() ? STEPS.get(index).name() : "-", tracedPhase, Scene.phase, tracedScreen, screen,
                mc.player.hurtTime, mc.player.onGround(), mc.player.fallDistance);
        tracedPhase = Scene.phase;
        tracedScreen = screen;
    }

    private static void openWorld(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        // no "move with WASD" toast over the screenshots
        mc.getTutorial().setStep(TutorialSteps.NONE);
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().openWorld(WORLD, () -> check("the test world opens", false));
            return;
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(),
                mc.screen);
    }

    // ---- steps ---------------------------------------------------------------------------------------

    private static boolean setUp(Minecraft mc) {
        check("our depot station is registered with Cinematic Interactions", SceneBlocks.registered(SceneBlocks.DEPOT));
        // первые шаги проверяют запуск правым кликом, а по умолчанию у депо KEY
        ClientConfig.set(SceneKinds.DEPOT, Trigger.USE);
        ClientConfig.set(SceneKinds.PRESSING, Trigger.USE);
        onServer(mc, player -> {
            ServerLevel level = player.serverLevel();
            // the world is reused: the last run's depot is emptied first, or breaking it drops its stack at our feet
            DepotBehaviour old = BlockEntityBehaviour.get(level, DEPOT, DepotBehaviour.TYPE);
            if (old != null) {
                old.removeHeldItem();
                for (int i = 1; i < old.itemHandler.getSlots(); i++) {
                    old.itemHandler.extractItem(i, Integer.MAX_VALUE, false);
                }
            }
            for (BlockPos at : BlockPos.betweenClosed(-4, -60, -3, 4, -57, 8)) {
                level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(DEPOT, AllBlocks.DEPOT.getDefaultState());
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            standAtStart(player);
            reset(player);
            // как в первый раз: Create выдаёт свои достижения посреди сцен (первый лист из-под пресса)
            player.server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "advancement revoke @s everything");
            return true;
        });
        return true;
    }

    /** Three blocks in front of the depot, looking at it. */
    private static boolean standAtStart(ServerPlayer player) {
        player.teleportTo(player.serverLevel(), 0.5, -60, 0.5, 0f, 30f);
        return true;
    }

    /** The crosshair on the middle of the plate (what the key looks at). */
    private static void face(Minecraft mc) {
        Vec3 look = Vec3.atBottomCenterOf(DEPOT).add(0, SceneBlocks.DEPOT_TOP, 0).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
    }

    /** Depot empty, the hotbar as the steps expect it: iron, a wrench, stone. */
    private static boolean reset(ServerPlayer player) {
        return reset(player, IRON);
    }

    private static boolean reset(ServerPlayer player, int iron) {
        ServerLevel level = player.serverLevel();
        DepotBehaviour depot = BlockEntityBehaviour.get(level, DEPOT, DepotBehaviour.TYPE);
        if (depot != null) {
            depot.removeHeldItem();
            for (int i = 1; i < depot.itemHandler.getSlots(); i++) {
                depot.itemHandler.extractItem(i, Integer.MAX_VALUE, false);
            }
            depot.blockEntity.notifyUpdate();
        }
        level.setBlockAndUpdate(DEPOT.east(), Blocks.AIR.defaultBlockState());
        // nothing left lying about to be picked up between the steps
        level.getEntitiesOfClass(ItemEntity.class, new AABB(DEPOT).inflate(8)).forEach(Entity::discard);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, iron));
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        player.getInventory().setItem(2, new ItemStack(Items.STONE, 4));
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** A real right-click on a face of the depot with a hotbar slot selected. */
    private static boolean use(Minecraft mc, int hotbar, Direction face) {
        Vec3 at = face == Direction.UP
                ? Vec3.atBottomCenterOf(DEPOT).add(0, SceneBlocks.DEPOT_TOP, 0)
                : Vec3.atCenterOf(DEPOT).add(face.getStepX() * 0.5, -0.2, face.getStepZ() * 0.5);
        return use(mc, hotbar, DEPOT, face, at);
    }

    /** The same on the middle of a face of another block. */
    private static boolean use(Minecraft mc, int hotbar, BlockPos pos, Direction face) {
        return use(mc, hotbar, pos, face, Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)));
    }

    /** A real right-click, once {@link #settled} (else false: the step tries again next tick). */
    private static boolean use(Minecraft mc, int hotbar, BlockPos pos, Direction face, Vec3 at) {
        if (!settled(mc, pos)) {
            return false;
        }
        mc.player.getInventory().selected = hotbar;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
        return true;
    }

    /**
     * The client has caught up with the server: the player stands where the server has them (it ignores clicks until the
     * client confirms a teleport), the block is synced, and no move of a scene plays.
     */
    private static boolean settled(Minecraft mc, BlockPos pos) {
        if (moving()) {
            return false;
        }
        Vec3 here = mc.player.position();
        BlockState state = mc.level.getBlockState(pos);
        return onServer(mc, player -> player.position().distanceToSqr(here) < 1e-4 && player.serverLevel().getBlockState(pos).equals(state));
    }

    private static boolean buildValve(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(VALVE_SHAFT, AllBlocks.SHAFT.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        level.setBlockAndUpdate(VALVE, AllBlocks.COPPER_VALVE_HANDLE.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        return true;
    }

    private static boolean standAtValve(ServerPlayer player) {
        player.teleportTo(player.serverLevel(), VALVE.getX() + 0.5, -60, VALVE.getZ() - 1.4, 0f, 15f);
        return true;
    }

    private static boolean give(ServerPlayer player, int slot, ItemStack stack) {
        player.getInventory().setItem(slot, stack);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static boolean buildPress(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(PRESS, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        level.setBlockAndUpdate(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        return true;
    }

    private static boolean motor(ServerPlayer player, int rpm) {
        if (player.serverLevel().getBlockEntity(MOTOR) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
        }
        return true;
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private static DepotScene scene() {
        return (DepotScene) Scene.station;
    }

    private static PressScene pressScene() {
        return (PressScene) Scene.station;
    }

    private static ValveScene valveScene() {
        return (ValveScene) Scene.station;
    }

    private static float valveWheel(Minecraft mc) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(VALVE) instanceof ValveHandleBlockEntity v
                ? v.getIndependentAngle(0) : Float.NaN);
    }

    /** The angle set on the valve (its value box), read on the server. */
    private static int valveSetting(Minecraft mc) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(VALVE) instanceof ValveHandleBlockEntity v
                ? v.angleInput.getValue() : 0);
    }

    private static BlockPos crafterCell(int column, int row) {
        return CRAFTERS.west(column).below(row);
    }

    /**
     * The 3×3, set up for a chest: the side columns pass their items to the middle column, which passes them down to
     * the bottom middle, the output (it points into the ground, so Create throws the result out in front). And a
     * crafter by itself a little way off.
     */
    private static boolean buildCrafters(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        clearCrafters(level);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                Direction to = column == 0 ? Direction.WEST : column == 2 ? Direction.EAST : Direction.DOWN;
                level.setBlockAndUpdate(crafterCell(column, row), crafter(to));
            }
        }
        level.setBlockAndUpdate(CRAFTER_COG, AllBlocks.COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        level.setBlockAndUpdate(CRAFTER_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        level.setBlockAndUpdate(LONE_CRAFTER, crafter(Direction.UP));
        return true;
    }

    private static boolean crafterMotor(ServerPlayer player, int rpm) {
        if (player.serverLevel().getBlockEntity(CRAFTER_MOTOR) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
        }
        return true;
    }

    /** A 10×10 wall, each crafter passing up (its columns are chains of ten; the wall is one group all the same). */
    private static boolean buildBigCrafters(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        clearCrafters(level);
        for (int column = 0; column < 10; column++) {
            for (int row = 0; row < 10; row++) {
                level.setBlockAndUpdate(BIG_CRAFTERS.west(column).below(row), crafter(Direction.UP));
            }
        }
        return true;
    }

    /** A crafter facing north whose items go this way (Create's POINTING, found by trying each). */
    private static BlockState crafter(Direction to) {
        BlockState state = AllBlocks.MECHANICAL_CRAFTER.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (Pointing pointing : Pointing.values()) {
            BlockState pointed = state.setValue(MechanicalCrafterBlock.POINTING, pointing);
            if (MechanicalCrafterBlock.getTargetDirection(pointed) == to) {
                return pointed;
            }
        }
        return state;
    }

    /** The crafters' corner cleared: what the last run left there, and the flat world's animals (they walk into shots). */
    private static void clearCrafters(ServerLevel level) {
        for (BlockPos at : BlockPos.betweenClosed(5, -60, 1, 28, -50, 5)) {
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(4, -61, 0, 30, -48, 7)).forEach(Entity::discard);
        level.getEntitiesOfClass(Animal.class, new AABB(-6, -61, -8, 34, -46, 14)).forEach(Entity::discard);
    }

    private static boolean crafterKit(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setItem(PLANKS, new ItemStack(Items.OAK_PLANKS, 9));
        player.getInventory().setItem(SLOT_COVER, AllItems.CRAFTER_SLOT_COVER.asStack());
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** This far north of the block, looking south at it (machines face north, toward the player). */
    private static boolean standBefore(ServerPlayer player, BlockPos column, double out) {
        player.teleportTo(player.serverLevel(), column.getX() + 0.5, -60, column.getZ() - out, 0f, 10f);
        return true;
    }

    private static Vec3 farFrom, plateBefore;
    private static boolean xPiecesLeft;
    private static String busyHint = "";
    private static BlockPos farClicked = BlockPos.ZERO;
    private static int farWait;

    private static boolean fly(ServerPlayer player, boolean on) {
        player.getAbilities().flying = on;
        player.onUpdateAbilities();
        return true;
    }

    private static boolean standAt(ServerPlayer player, double x, double y, double z) {
        player.teleportTo(player.serverLevel(), x, y, z, 0f, 10f);
        return true;
    }

    /** A crafter clicked from afar: the scene starts (walked up to bare-handed, from here with the grip), or the check fails. */
    private static boolean farCrafter(Minecraft mc, String name, boolean grip) {
        if (SmokeTest.farFrom == null) {
            SmokeTest.farFrom = mc.player.position();
            SmokeTest.farWait = 0;
        }
        if (Scene.phase != Scene.Phase.ACTIVE && ++SmokeTest.farWait < 120) {
            return false;
        }
        double moved = mc.player.position().distanceTo(SmokeTest.farFrom);
        boolean started = Scene.phase == Scene.Phase.ACTIVE && Scene.station instanceof CrafterScene;
        BlockState state = mc.level.getBlockState(SmokeTest.farClicked);
        String why = "phase " + Scene.phase + ", notice " + CiNotice.recent() + ", sneaking " + mc.player.isSecondaryUseActive() + ", flying "
                + mc.player.getAbilities().flying + ", on ground " + mc.player.onGround() + ", held " + mc.player.getMainHandItem() + ", block "
                + state.getBlock() + ", scenes " + AddonScenes.available(state) + ", screen " + mc.screen;
        check("crafters, " + name + ": the scene starts" + (grip ? " where the player stands" : "") + " ("
                + (started ? "moved " + String.format("%.2f", moved) : why) + ")", started && (!grip || moved < 0.1));
        if (Scene.active()) {
            Scene.leave();
        }
        return true;
    }

    private static CrafterScene crafterScene() {
        return (CrafterScene) Scene.station;
    }

    /** Holds the cursor on a window and clicks it. */
    private static boolean pinAndClick(int cell) {
        CrafterScene.smokePin = cell;
        crafterScene().hover(cell);
        crafterScene().click(0, false);
        return true;
    }

    /** A click on a hotbar slot in the scene's inventory: picks its stack up, or puts the carried one down there. */
    private static void pickUp(Minecraft mc, int hotbar) {
        if (mc.player.containerMenu instanceof BridgeMenu menu) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, menu.inventoryIndex(hotbar), 0, ClickType.PICKUP, mc.player);
        }
    }

    /**
     * A Blaze Burner with its blaze, a Basin on it, a Mechanical Mixer two above (a cog itself: a cogwheel beside it
     * turns it, with a creative motor on top); an empty burner (no blaze yet) beside them.
     */
    private static boolean buildBasin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos at : BlockPos.betweenClosed(-14, -60, 1, -8, -55, 5)) {
            if (level.getBlockEntity(at) instanceof BasinBlockEntity basin) {
                // мир тот же, что в прошлый раз: из чаши ничего не должно высыпаться под ноги
                basin.clearContent();
            }
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(-15, -61, 0, -7, -54, 6)).forEach(Entity::discard);
        level.setBlockAndUpdate(BASIN_BURNER, AllBlocks.BLAZE_BURNER.getDefaultState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
        level.setBlockAndUpdate(BASIN, AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(MIXER, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        level.setBlockAndUpdate(MIXER_COG, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        level.setBlockAndUpdate(MIXER_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        level.setBlockAndUpdate(EMPTY_BURNER, AllBlocks.BLAZE_BURNER.getDefaultState());
        return true;
    }

    private static boolean mixerMotor(ServerPlayer player, int rpm) {
        if (player.serverLevel().getBlockEntity(MIXER_MOTOR) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
        }
        return true;
    }

    private static boolean basinKit(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setItem(ANDESITE, new ItemStack(Items.ANDESITE));
        player.getInventory().setItem(NUGGET, new ItemStack(Items.IRON_NUGGET));
        player.getInventory().setItem(WATER, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(COAL, new ItemStack(Items.COAL, 2));
        player.getInventory().setItem(CAKE, AllItems.BLAZE_CAKE.asStack());
        player.getInventory().setItem(DIRT, new ItemStack(Items.DIRT));
        player.getInventory().setItem(FLINT, new ItemStack(Items.FLINT_AND_STEEL));
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static BasinScene basinScene() {
        return (BasinScene) Scene.station;
    }

    private static BurnerScene burnerScene() {
        return (BurnerScene) Scene.station;
    }

    /** A click into the basin; the hand is pointed there first (CI moves the cursor's target only when the mouse moves). */
    private static void basinClick() {
        basinScene().hover();
        basinScene().click(0, false);
    }

    private static void burnerClick() {
        burnerScene().hover();
        burnerScene().click(0, false);
    }

    private static int basinFluid(Minecraft mc) {
        return onServer(mc, SmokeTest::basinFluid);
    }

    private static int basinFluid(ServerPlayer player) {
        IFluidHandler tanks = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, BASIN, null);
        int amount = 0;
        for (int i = 0; tanks != null && i < tanks.getTanks(); i++) {
            amount += tanks.getFluidInTank(i).getAmount();
        }
        return amount;
    }

    private static boolean basinHolds(Minecraft mc, Item item) {
        return onServer(mc, p -> {
            if (!(p.serverLevel().getBlockEntity(BASIN) instanceof BasinBlockEntity basin)) {
                return false;
            }
            for (SmartInventory inventory : List.of(basin.getInputInventory(), basin.getOutputInventory())) {
                for (int i = 0; i < inventory.getSlots(); i++) {
                    if (inventory.getStackInSlot(i).is(item)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static String basinContents(Minecraft mc) {
        return onServer(mc, p -> {
            StringBuilder said = new StringBuilder();
            if (p.serverLevel().getBlockEntity(BASIN) instanceof BasinBlockEntity basin) {
                for (SmartInventory inventory : List.of(basin.getInputInventory(), basin.getOutputInventory())) {
                    for (int i = 0; i < inventory.getSlots(); i++) {
                        if (!inventory.getStackInSlot(i).isEmpty()) {
                            said.append(inventory.getStackInSlot(i)).append(' ');
                        }
                    }
                }
            }
            said.append("fluid ").append(basinFluid(p));
            if (p.serverLevel().getBlockEntity(MIXER) instanceof MechanicalMixerBlockEntity mixer) {
                said.append(", mixer speed ").append(mixer.getSpeed()).append(" running ").append(mixer.running);
            }
            return said.toString();
        });
    }

    private static boolean basinEmpty(Minecraft mc) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(BASIN) instanceof BasinBlockEntity basin
                && basin.getInputInventory().isEmpty() && basin.getOutputInventory().isEmpty());
    }

    private static HeatLevel heat(Minecraft mc, BlockPos burner) {
        return onServer(mc, p -> {
            BlockState state = p.serverLevel().getBlockState(burner);
            return state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) ? state.getValue(BlazeBurnerBlock.HEAT_LEVEL) : HeatLevel.NONE;
        });
    }

    private static boolean covered(Minecraft mc, BlockPos cell) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(cell) instanceof MechanicalCrafterBlockEntity crafter
                && Crafters.covered(crafter));
    }

    private static ItemStack crafterWindow(Minecraft mc, BlockPos cell) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(cell) instanceof MechanicalCrafterBlockEntity crafter
                ? Crafters.held(crafter).copy() : ItemStack.EMPTY);
    }

    private static boolean ringFilled(Minecraft mc) {
        for (int cell : RING) {
            if (!crafterWindow(mc, crafterCell(cell % 3, cell / 3)).is(Items.OAK_PLANKS)) {
                return false;
            }
        }
        return crafterWindow(mc, crafterCell(1, 1)).isEmpty();
    }

    private static boolean madeChest(Minecraft mc) {
        return onServer(mc, p -> p.getInventory().countItem(Items.CHEST) > 0 || !p.serverLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(CRAFTERS).inflate(4), item -> item.getItem().is(Items.CHEST)).isEmpty());
    }

    private static boolean crafterWallIdle(Minecraft mc) {
        return onServer(mc, p -> {
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 3; row++) {
                    if (!(p.serverLevel().getBlockEntity(crafterCell(column, row)) instanceof MechanicalCrafterBlockEntity crafter)
                            || !Crafters.idle(crafter) || !Crafters.held(crafter).isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    private static boolean valveTurning(Minecraft mc) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(VALVE) instanceof ValveHandleBlockEntity v && v.inUse > 0);
    }

    private static float shaftSpeed(Minecraft mc) {
        return shaftSpeed(mc, VALVE_SHAFT);
    }

    private static float shaftSpeed(Minecraft mc, BlockPos shaft) {
        return onServer(mc, p -> p.serverLevel().getBlockEntity(shaft) instanceof KineticBlockEntity k ? k.getSpeed() : 0f);
    }

    private static boolean buildCrank(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(CRANK_SHAFT, AllBlocks.ANDESITE_ENCASED_SHAFT.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        level.setBlockAndUpdate(CRANK, AllBlocks.HAND_CRANK.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        player.getFoodData().setFoodLevel(20);
        return true;
    }

    private static boolean standAtCrank(ServerPlayer player) {
        player.teleportTo(player.serverLevel(), CRANK.getX() + 0.5, -60, CRANK.getZ() - 1.4, 0f, 15f);
        return true;
    }

    private static CrankScene crankScene() {
        return (CrankScene) Scene.station;
    }

    /** The handle's angle on the client's crank, in degrees (the scene reads the same each frame it draws). */
    private static double crankAngle(Minecraft mc) {
        return mc.level.getBlockEntity(CRANK) instanceof HandCrankBlockEntity crank ? crank.getIndependentAngle(0) : Double.NaN;
    }

    /** The Wave 2 row, built afresh (what the last run left in it is cleared away). */
    private static boolean buildWave2(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos at : BlockPos.betweenClosed(-40, -60, 2, -15, -55, 9)) {
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(-41, -62, 0, -14, -54, 12)).forEach(Entity::discard);
        // earlier runs sank a fan under the depot: the ground is put back
        level.setBlockAndUpdate(new BlockPos(-29, -61, 3), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(-29, -62, 3), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(-29, -63, 3), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(DRAIN, AllBlocks.ITEM_DRAIN.getDefaultState());
        level.setBlockAndUpdate(SPOUT_DEPOT, AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(SPOUT, AllBlocks.SPOUT.getDefaultState());
        IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, SPOUT, null);
        if (tank != null) {
            tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        }
        level.setBlockAndUpdate(DEPLOYING_DEPOT, AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(DOWN_DEPLOYER, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        motorAlong(level, DOWN_DEPLOYER, DEPLOYER_RPM);
        level.setBlockAndUpdate(HANDOFF_DEPLOYER, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        level.setBlockAndUpdate(FAN_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        level.setBlockAndUpdate(FAN, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        motorSpeed(level, FAN_MOTOR, FAN_RPM);
        level.setBlockAndUpdate(CAMPFIRE, Blocks.CAMPFIRE.defaultBlockState());
        level.setBlockAndUpdate(FAN_DEPOT, AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(MILL_MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        level.setBlockAndUpdate(MILLSTONE, AllBlocks.MILLSTONE.getDefaultState());
        motorSpeed(level, MILL_MOTOR, MILL_RPM);
        level.setBlockAndUpdate(SAW, AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        motorAlong(level, SAW, SAW_RPM);
        level.setBlockAndUpdate(EJECTOR, AllBlocks.WEIGHTED_EJECTOR.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        motorAlong(level, EJECTOR, EJECTOR_RPM);
        return true;
    }

    /** A creative motor beside the machine, on the positive side of its shaft's axis, turning at this speed. */
    private static void motorAlong(ServerLevel level, BlockPos machine, int rpm) {
        BlockState state = level.getBlockState(machine);
        Direction out = Direction.get(Direction.AxisDirection.POSITIVE, ((IRotate) state.getBlock()).getRotationAxis(state));
        level.setBlockAndUpdate(machine.relative(out), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, out.getOpposite()));
        motorSpeed(level, machine.relative(out), rpm);
    }

    private static void motorSpeed(ServerLevel level, BlockPos motor, int rpm) {
        if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(rpm);
        }
    }

    /** Nothing on this depot (what lies on it and what processing left), as a run starting part way may find it. */
    private static boolean emptyDepot(ServerPlayer player, BlockPos pos) {
        DepotBehaviour depot = BlockEntityBehaviour.get(player.serverLevel(), pos, DepotBehaviour.TYPE);
        if (depot != null) {
            depot.removeHeldItem();
            for (int i = 1; i < depot.itemHandler.getSlots(); i++) {
                depot.itemHandler.extractItem(i, Integer.MAX_VALUE, false);
            }
            depot.blockEntity.notifyUpdate();
        }
        return true;
    }

    /** Deployers for the hand-off facing up (on the ground) and down (its block one over the head), a row behind the others. */
    private static final BlockPos UP_DEPLOYER = new BlockPos(-25, -60, 7), DOWN_HANDOFF = new BlockPos(-28, -58, 7);

    private static boolean handOffBegins(Minecraft mc, String way) {
        if (Scene.phase != Scene.Phase.ACTIVE) {
            return false;
        }
        check("a right-click on the hand of a deployer facing " + way + " starts the hand-off", Scene.station instanceof DeployerScene);
        DeployerScene.smokePin = true;
        pickUp(mc, HANDED);
        return true;
    }

    private static boolean handOver() {
        if (moving()) {
            return false;
        }
        ((DeployerScene) Scene.station).hover();
        ((DeployerScene) Scene.station).click(0, false);
        return true;
    }

    private static boolean handedOver(Minecraft mc, BlockPos deployer, String shot) {
        if (moving()) {
            return false;
        }
        ItemStack held = deployerHeld(mc, deployer);
        shot(mc, shot);
        check("the claw takes the andesite and the hand comes back with the flint (server: the deployer holds " + held + ")",
                held.is(Items.ANDESITE) && carried(mc).is(Items.FLINT));
        pickUp(mc, HANDED);
        DeployerScene.smokePin = false;
        Scene.leave();
        return true;
    }

    /** How far from a machine the grip's claw tests stand (out of a bare arm's reach, well within the grip's). */
    private static final double GRIP_OFF = 3.0;

    /** An Extendo Grip in the off hand (the main hand stays free for the scene's clicks). */
    private static boolean offhandGrip(ServerPlayer player) {
        player.setItemInHand(InteractionHand.OFF_HAND, AllItems.EXTENDO_GRIP.asStack());
        return true;
    }

    /** The block placed afresh (a new block entity: an empty tank, a cold burner), even where it already stands. */
    private static void renew(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, state);
    }

    /** The creative motor turning the deployer over the depot (as {@link #motorAlong} placed it). */
    private static boolean deployerMotor(ServerPlayer player, int rpm) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(DOWN_DEPLOYER);
        if (state.getBlock() instanceof IRotate rotate) {
            motorSpeed(level, DOWN_DEPLOYER.relative(Direction.get(Direction.AxisDirection.POSITIVE, rotate.getRotationAxis(state))), rpm);
        }
        return true;
    }

    private static boolean fanMotor(ServerPlayer player, int rpm) {
        motorSpeed(player.serverLevel(), FAN_MOTOR, rpm);
        return true;
    }

    private static boolean wave2Kit(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setItem(FULL_BUCKET, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(EMPTY_BUCKET, new ItemStack(Items.BUCKET));
        player.getInventory().setItem(BOTTLE, new ItemStack(Items.GLASS_BOTTLE));
        player.getInventory().setItem(LOG, new ItemStack(Items.STRIPPED_OAK_LOG));
        player.getInventory().setItem(HANDED, new ItemStack(Items.ANDESITE));
        player.getInventory().setItem(BEEF, new ItemStack(Items.BEEF));
        player.getInventory().setItem(WHEAT, new ItemStack(Items.WHEAT));
        player.getInventory().setItem(STONE_SLOT, new ItemStack(Items.STONE));
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** The deployer's hand holds this (through Create's own item handler, as a hopper would fill it). */
    private static boolean deployerHolds(ServerPlayer player, BlockPos deployer, ItemStack stack) {
        IItemHandler hand = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, deployer, null);
        if (hand != null) {
            int held = hand.getSlots() - 1;
            hand.extractItem(held, 64, false);
            hand.insertItem(held, stack, false);
        }
        return true;
    }

    private static ItemStack deployerHeld(Minecraft mc, BlockPos deployer) {
        return onServer(mc, p -> {
            IItemHandler hand = p.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, deployer, null);
            return hand == null ? ItemStack.EMPTY : hand.getStackInSlot(hand.getSlots() - 1).copy();
        });
    }

    /** The ejector throws four blocks south, on the level; the iron to throw goes in the hand-off slot. */
    private static boolean aimEjector(ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(EJECTOR) instanceof EjectorBlockEntity ejector) {
            ejector.setTarget(EJECTOR_REACH, 0);
        }
        player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(EJECTOR).inflate(8)).forEach(Entity::discard);
        return give(player, HANDED, new ItemStack(Items.IRON_INGOT));
    }

    private static int drainFluid(Minecraft mc) {
        return onServer(mc, p -> {
            IFluidHandler tank = p.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, DRAIN, null);
            int amount = 0;
            for (int i = 0; tank != null && i < tank.getTanks(); i++) {
                amount += tank.getFluidInTank(i).getAmount();
            }
            return amount;
        });
    }

    private static DrainScene drainScene() {
        return (DrainScene) Scene.station;
    }

    private static MillstoneScene millstoneScene() {
        return (MillstoneScene) Scene.station;
    }

    private static SawScene sawScene() {
        return (SawScene) Scene.station;
    }

    private static EjectorScene ejectorScene() {
        return (EjectorScene) Scene.station;
    }

    /**
     * A point on one of the block's value boxes of this kind, as Create places it, on the first face that has one
     * (the player's side first): where a player clicks to reach Create's own setting.
     */
    @Nullable
    private static BlockHitResult valueBox(Minecraft mc, BlockPos pos, BehaviourType<?> type) {
        if (!(BlockEntityBehaviour.get(mc.level, pos, type) instanceof ValueSettingsBehaviour value)) {
            return null;
        }
        BlockState state = mc.level.getBlockState(pos);
        for (Direction face : List.of(Direction.NORTH, Direction.UP, Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.DOWN)) {
            ValueBoxTransform slot = value.getSlotPositioning();
            if (slot instanceof ValueBoxTransform.Sided sided) {
                sided.fromSide(face);
            }
            if (!slot.shouldRender(mc.level, pos, state)) {
                continue;
            }
            Vec3 at = Vec3.atLowerCornerOf(pos).add(slot.getLocalOffset(mc.level, pos, state));
            if (value.testHit(at)) {
                return new BlockHitResult(at, face, pos, false);
            }
        }
        return null;
    }

    /** Create's value input took the click just made on this block (its setting began); let go of at once. */
    private static boolean valueInputTook(BlockPos pos) {
        ValueSettingsClient input = CreateClient.VALUE_SETTINGS_HANDLER;
        boolean took = pos.equals(input.interactHeldPos) && input.interactHeldTicks >= 0;
        input.cancelInteraction();
        return took;
    }

    private static ItemStack filterOn(Minecraft mc, BlockPos pos) {
        return onServer(mc, p -> {
            FilteringBehaviour filter = BlockEntityBehaviour.get(p.serverLevel(), pos, FilteringBehaviour.TYPE);
            return filter == null ? ItemStack.EMPTY : filter.getFilter().copy();
        });
    }

    private static int bigCell = -1;
    private static float lookedAt;

    @Nullable
    private static Vec3 gripFrom;

    private static double shortestGrip;
    private static boolean gripComing, comingShot;

    /** Notes the shortest grip drawn (fist to claw) and whether it came up with the hands; one shot as they rise (when named). */
    private static void sampleGrip(Minecraft mc, @Nullable String coming) {
        if (Scene.station instanceof CineStation<?> station) {
            double length = station.gripLength();
            if (length >= 0) {
                shortestGrip = Math.min(shortestGrip, length);
                gripComing |= Scene.phase == Scene.Phase.INTRO;
            }
        }
        if (coming != null && !comingShot && Scene.phase == Scene.Phase.INTRO && Scene.introProgress() > 0.7) {
            comingShot = true;
            shot(mc, coming);
        }
    }

    @Nullable
    private static Vec3 launchCamera;

    private static int sheets(Minecraft mc) {
        return onServer(mc, player -> {
            DepotBehaviour depot = BlockEntityBehaviour.get(player.serverLevel(), DEPOT, DepotBehaviour.TYPE);
            int count = 0;
            for (int i = 0; depot != null && i < depot.itemHandler.getSlots(); i++) {
                ItemStack stack = depot.itemHandler.getStackInSlot(i);
                count += stack.is(AllItems.IRON_SHEET.get()) ? stack.getCount() : 0;
            }
            return count;
        });
    }

    private static int pressIdleTicks;

    /** The client's press has stood still for this many ticks in a row (asked once a tick). */
    private static boolean pressIdle(Minecraft mc, int ticks) {
        boolean running = mc.level.getBlockEntity(PRESS) instanceof MechanicalPressBlockEntity press
                && press.getPressingBehaviour().running;
        pressIdleTicks = running ? 0 : pressIdleTicks + 1;
        return pressIdleTicks >= ticks;
    }

    /** At least one kick, and no two closer than the scene's limit. */
    private static boolean kicksApart(List<PressScene.Impact> impacts) {
        double last = Double.NEGATIVE_INFINITY;
        int kicks = 0;
        for (PressScene.Impact impact : impacts) {
            if (impact.kicked()) {
                if (impact.at() - last < 0.35 - 1e-6) {
                    return false;
                }
                last = impact.at();
                kicks++;
            }
        }
        return kicks > 0;
    }

    /** Each impact was on a frame (give or take one) on which the head was seen reaching the plate. */
    private static boolean onHeadFrames(List<PressScene.Impact> impacts) {
        CineCreate.LOGGER.info("[cinecreate smoke] head reached the plate on frames {}", HEAD_DOWN);
        for (PressScene.Impact impact : impacts) {
            CineCreate.LOGGER.info("[cinecreate smoke] impact on frame {} at {} s (kick: {})",
                    impact.frame(), String.format("%.3f", impact.at()), impact.kicked());
            if (HEAD_DOWN.stream().noneMatch(frame -> Math.abs(frame - impact.frame()) <= 1)) {
                return false;
            }
        }
        return !impacts.isEmpty();
    }

    /**
     * A move of the scene is still playing. Clicks and drawer moves sent meanwhile are dropped by Cinematic
     * Interactions, and the moves run on its frame clock, which falls behind the ticks when frames are slow.
     */
    private static boolean moving() {
        return Scene.station instanceof MenuStation<?> station && station.moving();
    }

    private static ItemStack carried(Minecraft mc) {
        return mc.player.containerMenu.getCarried();
    }

    private static ItemStack depot(Minecraft mc) {
        return onServer(mc, player -> {
            DepotBehaviour depot = BlockEntityBehaviour.get(player.serverLevel(), DEPOT, DepotBehaviour.TYPE);
            return depot == null ? ItemStack.EMPTY : depot.getHeldItemStack().copy();
        });
    }

    private static <T> T onServer(Minecraft mc, Function<ServerPlayer, T> query) {
        IntegratedServer server = mc.getSingleplayerServer();
        return server.submit(() -> query.apply(server.getPlayerList().getPlayer(mc.player.getUUID()))).join();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "smoke_" + name + ".png", mc.getMainRenderTarget(), message -> {});
    }

    private static void check(String what, boolean ok) {
        String line = (ok ? "PASS  " : "FAIL  ") + what;
        RESULTS.add(line);
        CineCreate.LOGGER.info("[cinecreate smoke] {}", line);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        DepotScene.smokePin = false;
        CrafterScene.smokePin = -1;
        CrankScene.smokeMarks = false;
        BasinScene.smokePin = false;
        BurnerScene.smokePin = false;
        DrainScene.smokePin = false;
        DeployerScene.smokePin = false;
        MillstoneScene.smokePin = false;
        SawScene.smokePin = false;
        DeployingScene.smokeHand = false;
        for (SceneKind kind : SceneKinds.all()) {
            ClientConfig.set(kind, kind.trigger());
        }
        long failed = RESULTS.stream().filter(line -> line.startsWith("FAIL")).count();
        long passed = RESULTS.stream().filter(line -> line.startsWith("PASS")).count();
        reportTimes();
        RESULTS.add(failed == 0 ? "ALL PASSED (" + passed + ")" : failed + " FAILED, " + passed + " passed");
        try {
            Files.write(mc.gameDirectory.toPath().resolve("smoke-results.txt"), RESULTS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CineCreate.LOGGER.error("[cinecreate smoke] could not write the results", e);
        }
        CineCreate.LOGGER.info("[cinecreate smoke] done: {}", RESULTS.get(RESULTS.size() - 1));
        mc.stop();
    }

    // ---- exploratory steps (-Dcinecreate.explore=true: gradlew runSmoke -Pexplore=true) ---------------------------
    // Each block of steps builds its own rig on the row z = 14 (clear of the smoke's rigs), sets up one situation the
    // smoke never plays (a machine mid-move, a claim, a block replaced, a walk round...), drives real clicks, keys and
    // cursor moves, and checks the outcome: what the server holds and what the player sees. PASS = the mod behaves right.

    private static final int XZ = 14;
    private static final BlockPos X_SAW = new BlockPos(20, -60, XZ), X_BASIN = new BlockPos(14, -60, XZ),
            X_MIX_BASIN = new BlockPos(8, -60, XZ), X_CLAIM = new BlockPos(-8, -60, XZ), X_DRAIN = new BlockPos(-14, -60, XZ),
            X_REPL = new BlockPos(-20, -60, XZ), X_PRESS_DEPOT = new BlockPos(26, -60, XZ), X_SPOUT_DEPOT = new BlockPos(32, -60, XZ),
            X_DEP_DEPOT = new BlockPos(38, -60, XZ), X_MILL = new BlockPos(44, -60, XZ);
    private static final java.util.Set<BlockPos> CLAIMED = new java.util.HashSet<>();
    private static volatile boolean claimOn;
    private static int xBefore, xBefore2, xWait;
    private static double xValue;
    private static boolean xFlag;
    private static String xNote = "";

    /** A claim mod's rule, as most have it: right-clicks on a claimed block are cancelled (both sides). */
    private static void claim(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (claimOn && CLAIMED.contains(event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    private static Vec3 xTop(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, SceneBlocks.DEPOT_TOP, 0);
    }

    private static void xClear(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (BlockPos at : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (level.getBlockEntity(at) instanceof BasinBlockEntity basin) {
                basin.clearContent();
            }
            DepotBehaviour depot = BlockEntityBehaviour.get(level, at, DepotBehaviour.TYPE);
            if (depot != null) {
                depot.removeHeldItem();
                for (int i = 1; i < depot.itemHandler.getSlots(); i++) {
                    depot.itemHandler.extractItem(i, 64, false);
                }
            }
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(x0 - 3, y0 - 2, z0 - 3, x1 + 4, y1 + 3, z1 + 4)).forEach(Entity::discard);
    }

    private static boolean xKit(ServerPlayer player, ItemStack... stacks) {
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        for (int i = 0; i < stacks.length; i++) {
            player.getInventory().setItem(i, stacks[i]);
        }
        player.getInventory().selected = 8;
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static void xDepot(ServerLevel level, BlockPos pos, ItemStack stack) {
        DepotBehaviour depot = BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
        if (depot != null) {
            depot.setCenteredHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(stack));
            depot.blockEntity.notifyUpdate();
        }
    }

    private static ItemStack xDepotHeld(ServerPlayer player, BlockPos pos) {
        DepotBehaviour depot = BlockEntityBehaviour.get(player.serverLevel(), pos, DepotBehaviour.TYPE);
        return depot == null ? ItemStack.EMPTY : depot.getHeldItemStack().copy();
    }

    /** What the player has of an item: the inventory and what the open menu's cursor (the scene's hand) carries. */
    private static int xCount(ServerPlayer player, Item item) {
        ItemStack carried = player.containerMenu.getCarried();
        return player.getInventory().countItem(item) + (carried.is(item) ? carried.getCount() : 0);
    }

    private static int xTotal(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            total += player.getInventory().getItem(i).getCount();
        }
        return total + player.containerMenu.getCarried().getCount();
    }

    private static String xPieces(com.simibubi.create.content.kinetics.saw.SawBlockEntity saw) {
        StringBuilder said = new StringBuilder();
        for (int i = 0; i < saw.inventory.getSlots(); i++) {
            if (!saw.inventory.getStackInSlot(i).isEmpty()) {
                said.append("slot ").append(i).append(": ").append(saw.inventory.getStackInSlot(i)).append("; ");
            }
        }
        return said.toString();
    }

    private static int xFluid(ServerPlayer player, BlockPos pos) {
        IFluidHandler tanks = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        int amount = 0;
        for (int i = 0; tanks != null && i < tanks.getTanks(); i++) {
            amount += tanks.getFluidInTank(i).getAmount();
        }
        return amount;
    }

    /** The CI MenuStation refused a click within the last second (its protected denyAt). */
    private static boolean xDenied() {
        try {
            java.lang.reflect.Field field = MenuStation.class.getDeclaredField("denyAt");
            field.setAccessible(true);
            return Scene.clock() - field.getDouble(Scene.station) < 1.0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Where CI draws a hand this frame (its private Hands.position), or null. */
    @Nullable
    private static Vec3 xHand(boolean right) {
        try {
            java.lang.reflect.Method method = Class.forName("dev.dyslexic.cinematic.client.Hands").getDeclaredMethod("position", boolean.class);
            method.setAccessible(true);
            return (Vec3) method.invoke(null, right);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Vec3 xCamera() {
        CameraRig.Pose pose = CameraRig.last();
        return pose == null ? Vec3.ZERO : pose.pos();
    }

    private static String xFmt(@Nullable Vec3 v) {
        return v == null ? "-" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    /** The real mouse put at this GUI point (CI moves the scene's cursor to it on the next frame). */
    private static void xMouse(Minecraft mc, double guiX, double guiY) {
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
            y.setDouble(mc.mouseHandler, guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            CineCreate.LOGGER.warn("[explore] cannot move the mouse", e);
        }
    }

    private static void xFace(Minecraft mc, Vec3 at) {
        Vec3 look = at.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
    }

    /** The drain's held item: where it rolls (Create's insertedFrom is the side it leaves by) and how far, or null. */
    @Nullable
    private static String xDrainHeld(ServerPlayer player, BlockPos pos) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity drain)) {
            return null;
        }
        try {
            java.lang.reflect.Field field = com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity.class.getDeclaredField("heldItem");
            field.setAccessible(true);
            Object held = field.get(drain);
            if (held instanceof com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack t) {
                return t.stack + " leaving by " + t.insertedFrom.getSerializedName() + " at " + String.format(Locale.ROOT, "%.2f", t.beltPosition);
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    private static String xItemsNear(ServerPlayer player, BlockPos pos) {
        StringBuilder said = new StringBuilder();
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4))) {
            said.append(item.getItem()).append(" at ").append(xFmt(item.position().subtract(Vec3.atCenterOf(pos)))).append("; ");
        }
        return said.toString();
    }

    /** A bucket lies on the ground on this side of the block (rolled off toward a player standing there). */
    private static boolean xBucketOn(ServerPlayer player, BlockPos pos, Direction side) {
        Vec3 out = Vec3.atLowerCornerOf(side.getNormal());
        return !player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                item -> item.getItem().is(Items.BUCKET) && item.position().subtract(Vec3.atCenterOf(pos)).dot(out) > 0.3).isEmpty();
    }

    private static BlockPos xMotorOf(ServerLevel level, BlockPos machine) {
        BlockState state = level.getBlockState(machine);
        return machine.relative(Direction.get(Direction.AxisDirection.POSITIVE, ((IRotate) state.getBlock()).getRotationAxis(state)));
    }

    private static boolean xActive(Class<?> type) {
        return Scene.phase == Scene.Phase.ACTIVE && type.isInstance(Scene.station);
    }

    private static List<Step> exploreSteps() {
        return List.of(
                new Step("set up the depot", 60, SmokeTest::setUp),

                // ---- A: the saw's cut pieces left on the table (outputs blocked by full depots) ----
                new Step("x saw: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.SAW, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_SAW.getX() - 3, -60, XZ - 3, X_SAW.getX() + 3, -56, XZ + 3);
                        level.setBlockAndUpdate(X_SAW, AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
                        motorAlong(level, X_SAW, SAW_RPM);
                        Vec3 along = level.getBlockEntity(X_SAW) instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity saw
                                ? saw.getItemMovementVec() : new Vec3(1, 0, 0);
                        BlockPos a = X_SAW.offset((int) Math.signum(along.x), 0, (int) Math.signum(along.z));
                        BlockPos b = X_SAW.offset(-(int) Math.signum(along.x), 0, -(int) Math.signum(along.z));
                        for (BlockPos end : List.of(a, b)) {
                            if (level.getBlockState(end).isAir()) {
                                level.setBlockAndUpdate(end, AllBlocks.DEPOT.getDefaultState());
                                xDepot(level, end, new ItemStack(Items.DIRT, 64));
                            }
                        }
                        CineCreate.LOGGER.info("[explore] saw moves items along {}; full depots at {} and {}", along, a, b);
                        FilteringBehaviour filter = BlockEntityBehaviour.get(level, X_SAW, FilteringBehaviour.TYPE);
                        if (filter != null) {
                            filter.setFilter(new ItemStack(Items.STONE_BRICKS));
                        }
                        return xKit(p, new ItemStack(Items.STONE, 4)) && standBefore(p, X_SAW, 1.6);
                    });
                }),
                new Step("x saw: right-click", 6, mc -> use(mc, 8, X_SAW, Direction.UP)),
                new Step("x saw: scene", 0, mc -> {
                    if (!xActive(SawScene.class)) {
                        return false;
                    }
                    SawScene.smokePin = true;
                    pickUp(mc, 0);
                    return true;
                }),
                new Step("x saw: lay the stone", 8, mc -> {
                    sawScene().hoverTable();
                    sawScene().click(0, false);
                    return true;
                }),
                new Step("x saw: cut", 0, mc -> onServer(mc, p -> p.serverLevel().getBlockEntity(X_SAW)
                        instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity saw
                        && saw.inventory.appliedRecipe && saw.inventory.getStackInSlot(0).isEmpty() && !saw.inventory.isEmpty())),
                new Step("x saw: pieces stay", 60, mc -> {
                    String pieces = onServer(mc, p -> p.serverLevel().getBlockEntity(X_SAW)
                            instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity saw ? xPieces(saw) : "");
                    boolean clientEmpty = sawScene().slot(org.minechestplate.cinecreate.bridge.SawMenu.TABLE).isEmpty();
                    sawScene().hoverTable();
                    shot(mc, "x01_saw_pieces_on_table");
                    CineCreate.LOGGER.info("[explore] saw holds (server) {}; the scene's table slot is empty: {}; tooltip {}", pieces, clientEmpty,
                            sawScene().tooltip(ItemStack.EMPTY));
                    check("x saw: pieces that cannot leave stay on the table (server: " + pieces + "), the scene's table slot reads empty: "
                            + clientEmpty, !pieces.isEmpty());
                    // the saw took one stone (its slot holds one when bulk cutting is off): the rest goes back, the hand is empty
                    pickUp(mc, 0);
                    SmokeTest.xNote = "carried before the click: " + carried(mc);
                    return true;
                }),
                new Step("x saw: empty-handed click", 6, mc -> {
                    SmokeTest.xNote += ", at the click: " + carried(mc);
                    SmokeTest.xBefore = onServer(mc, SmokeTest::xTotal);
                    sawScene().hoverTable();
                    sawScene().click(0, false);
                    SmokeTest.xFlag = sawScene().moving();
                    SmokeTest.xBefore2 = xDenied() ? 1 : 0;
                    return true;
                }),
                new Step("x saw: empty hand on the pieces", 30, mc -> {
                    String pieces = onServer(mc, p -> p.serverLevel().getBlockEntity(X_SAW)
                            instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity saw ? xPieces(saw) : "");
                    int got = onServer(mc, SmokeTest::xTotal) - SmokeTest.xBefore;
                    check("x saw: an empty hand on the table takes the cut pieces, as Create's own empty-handed click does (" + SmokeTest.xNote
                            + "; a move started: " + SmokeTest.xFlag + ", refused: " + (SmokeTest.xBefore2 == 1) + "; server: the saw still holds "
                            + pieces + "; the player got " + got + " items)", got > 0);
                    SawScene.smokePin = false;
                    Scene.leave();
                    return true;
                }),
                new Step("x saw: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
                new Step("x saw: Create's own click", 4, mc -> {
                    ClientConfig.set(SceneKinds.SAW, Trigger.KEY);
                    SmokeTest.xBefore = onServer(mc, SmokeTest::xTotal);
                    // осталось ли что брать: сцена теперь сама забирает куски
                    SmokeTest.xPiecesLeft = onServer(mc, p -> p.serverLevel().getBlockEntity(X_SAW)
                            instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity saw && !xPieces(saw).isEmpty());
                    return true;
                }),
                new Step("x saw: right-click as Create", 10, mc -> use(mc, 8, X_SAW, Direction.UP)),
                new Step("x saw: Create took them", 10, mc -> {
                    int got = onServer(mc, SmokeTest::xTotal) - SmokeTest.xBefore;
                    check("x saw (control): Create's own empty-handed click takes what pieces are left (left before: " + SmokeTest.xPiecesLeft
                            + ", the player got " + got + " items)", !SmokeTest.xPiecesLeft || got > 0);
                    ClientConfig.set(SceneKinds.SAW, Trigger.USE);
                    onServer(mc, p -> {
                        motorSpeed(p.serverLevel(), xMotorOf(p.serverLevel(), X_SAW), 0);
                        return true;
                    });
                    return true;
                }),

                // ---- B: an empty bucket dipped into a basin with less than a bucket of water ----
                new Step("x basin: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.BASIN, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_BASIN.getX() - 2, -60, XZ - 3, X_BASIN.getX() + 2, -56, XZ + 2);
                        level.setBlockAndUpdate(X_BASIN, AllBlocks.BASIN.getDefaultState());
                        IFluidHandler tanks = level.getCapability(Capabilities.FluidHandler.BLOCK, X_BASIN, null);
                        int in = tanks == null ? -1 : tanks.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
                        CineCreate.LOGGER.info("[explore] basin filled with {} mB", in);
                        return xKit(p, new ItemStack(Items.BUCKET), new ItemStack(Items.GLASS_BOTTLE)) && standBefore(p, X_BASIN, 1.6);
                    });
                }),
                new Step("x basin: right-click", 6, mc -> use(mc, 8, X_BASIN, Direction.UP)),
                new Step("x basin: scene", 0, mc -> {
                    if (!xActive(BasinScene.class)) {
                        return false;
                    }
                    BasinScene.smokePin = true;
                    pickUp(mc, 0);
                    return true;
                }),
                new Step("x basin: dip the bucket", 8, mc -> {
                    SmokeTest.xBefore = basinScene().pours;
                    SmokeTest.xBefore2 = basinScene().denials;
                    basinClick();
                    return true;
                }),
                new Step("x basin: dipping", 8, mc -> {
                    shot(mc, "x02_basin_bucket_dip");
                    return true;
                }),
                new Step("x basin: dipped", 0, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int fluid = onServer(mc, p -> xFluid(p, X_BASIN));
                    int water = onServer(mc, p -> xCount(p, Items.WATER_BUCKET));
                    int played = basinScene().pours - SmokeTest.xBefore, refused = basinScene().denials - SmokeTest.xBefore2;
                    // что видит игрок: ни движения налива, ни налитого ведра (как отказ показан — дело сцены)
                    check("x basin: an empty bucket dipped into 250 mB (Create fills a bucket only from 1000) is not played as a fill"
                            + " (fill moves played " + played + ", refusals " + refused + "; server: basin " + fluid + " mB, water buckets "
                            + water + ", hand " + carried(mc) + ")", played == 0 && fluid == 250 && water == 0);
                    BasinScene.smokePin = false;
                    Scene.leave();
                    return true;
                }),
                new Step("x basin: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- C: the basin's watch camera under a mixer set in a frame (a casing beside the mixer, on the player's side) ----
                new Step("x mixer: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.BASIN, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        BlockPos mixer = X_MIX_BASIN.above(2);
                        xClear(level, X_MIX_BASIN.getX() - 2, -60, XZ - 3, X_MIX_BASIN.getX() + 2, -55, XZ + 2);
                        level.setBlockAndUpdate(X_MIX_BASIN, AllBlocks.BASIN.getDefaultState());
                        level.setBlockAndUpdate(mixer, AllBlocks.MECHANICAL_MIXER.getDefaultState());
                        level.setBlockAndUpdate(mixer.north(), AllBlocks.ANDESITE_CASING.getDefaultState());
                        level.setBlockAndUpdate(mixer.east(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
                        level.setBlockAndUpdate(mixer.east().above(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                                .setValue(BlockStateProperties.FACING, Direction.DOWN));
                        motorSpeed(level, mixer.east().above(), MIXER_RPM);
                        return xKit(p, new ItemStack(Items.ANDESITE), new ItemStack(Items.IRON_NUGGET)) && standBefore(p, X_MIX_BASIN, 1.6);
                    });
                }),
                new Step("x mixer: right-click", 6, mc -> use(mc, 8, X_MIX_BASIN, Direction.UP)),
                new Step("x mixer: scene", 0, mc -> {
                    if (!xActive(BasinScene.class)) {
                        return false;
                    }
                    Vec3 cam = xCamera();
                    CineCreate.LOGGER.info("[explore] basin camera at the start {} in {}", xFmt(cam), mc.level.getBlockState(BlockPos.containing(cam)));
                    BasinScene.smokePin = true;
                    pickUp(mc, 0);
                    return true;
                }),
                new Step("x mixer: drop the andesite", 8, mc -> {
                    basinClick();
                    return true;
                }),
                new Step("x mixer: nugget in hand", 0, mc -> {
                    if (moving()) {
                        return false;
                    }
                    pickUp(mc, 1);
                    return true;
                }),
                new Step("x mixer: drop the nugget", 8, mc -> {
                    basinClick();
                    return true;
                }),
                new Step("x mixer: mixing", 0, mc -> basinScene().mixing()),
                new Step("x mixer: watching", 18, mc -> {
                    Vec3 cam = xCamera();
                    BlockPos at = BlockPos.containing(cam);
                    BlockState state = mc.level.getBlockState(at);
                    shot(mc, "x03_mixer_watch_camera");
                    CineCreate.LOGGER.info("[explore] basin watch camera {} in {} at {}", xFmt(cam), state, at);
                    check("x mixer: while the mixer works, the basin's watch camera stays out of blocks (camera " + xFmt(cam) + " in "
                            + state.getBlock().getName().getString() + " at " + at.toShortString() + ")", state.getCollisionShape(mc.level, at).isEmpty());
                    BasinScene.smokePin = false;
                    Scene.leave();
                    return true;
                }),
                new Step("x mixer: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- D: a depot in a claim (a mod that cancels RightClickBlock there) ----
                new Step("x claim: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.DEPOT, Trigger.KEY);
                    SmokeTest.CLAIMED.add(X_CLAIM);
                    SmokeTest.claimOn = true;
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_CLAIM.getX() - 2, -60, XZ - 3, X_CLAIM.getX() + 2, -57, XZ + 2);
                        level.setBlockAndUpdate(X_CLAIM, AllBlocks.DEPOT.getDefaultState());
                        xDepot(level, X_CLAIM, new ItemStack(Items.GOLD_INGOT, 16));
                        return xKit(p) && standBefore(p, X_CLAIM, 1.6);
                    });
                }),
                new Step("x claim: right-click", 10, mc -> use(mc, 8, X_CLAIM, Direction.UP, xTop(X_CLAIM))),
                new Step("x claim: the claim holds a right-click", 10, mc -> {
                    int gold = onServer(mc, p -> xCount(p, Items.GOLD_INGOT));
                    ItemStack on = onServer(mc, p -> xDepotHeld(p, X_CLAIM));
                    check("x claim (control): a right-click on a claimed depot does nothing (player " + gold + " gold, depot " + on + ")",
                            gold == 0 && on.getCount() == 16 && !Scene.active());
                    Scene.abort();
                    xFace(mc, xTop(X_CLAIM));
                    return true;
                }),
                new Step("x claim: H", 10, mc -> {
                    xFace(mc, xTop(X_CLAIM));
                    SmokeTest.xNote = "invited " + WorkKey.invited();
                    SmokeTest.xWait = 0;
                    SmokeTest.xFlag = false;
                    KeyMapping.click(WorkKey.WORK.getKey());
                    return true;
                }),
                new Step("x claim: key scene", 0, mc -> {
                    // сцена может и не начаться (отказ): тогда через 3 с дальше, проверка — по предметам на сервере
                    if (!xActive(DepotScene.class)) {
                        return ++SmokeTest.xWait > 60 && Scene.phase == Scene.Phase.NONE;
                    }
                    SmokeTest.xFlag = true;
                    DepotScene.smokePin = true;
                    scene().hover();
                    scene().click(0, false);
                    return true;
                }),
                new Step("x claim: taken", 12, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int gold = onServer(mc, p -> xCount(p, Items.GOLD_INGOT));
                    ItemStack on = onServer(mc, p -> xDepotHeld(p, X_CLAIM));
                    shot(mc, "x04_claim_key_take");
                    check("x claim: the work key cannot take a claimed depot's items (" + SmokeTest.xNote + ", scene began: " + SmokeTest.xFlag
                            + "; server: player " + gold + " gold, depot " + on + ")", gold == 0 && on.getCount() == 16);
                    DepotScene.smokePin = false;
                    if (Scene.active()) {
                        Scene.leave();
                    }
                    return true;
                }),
                new Step("x claim: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
                new Step("x claim: window request", 6, mc -> {
                    onServer(mc, p -> {
                        xDepot(p.serverLevel(), X_CLAIM, new ItemStack(Items.GOLD_INGOT, 16));
                        return xKit(p);
                    });
                    mc.player.getInventory().selected = 8;
                    mc.getConnection().send(new ServerboundSetCarriedItemPacket(8));
                    PacketDistributor.sendToServer(new StationActionPayload(X_CLAIM, StationKind.ADDON, StationActionPayload.Action.WINDOW, 0, 0, false, 0));
                    return true;
                }),
                new Step("x claim: window request taken", 10, mc -> {
                    int gold = onServer(mc, p -> xCount(p, Items.GOLD_INGOT));
                    ItemStack on = onServer(mc, p -> xDepotHeld(p, X_CLAIM));
                    check("x claim: CI's window request (the scene's fallback packet) cannot take a claimed depot's items (server: player "
                            + gold + " gold, depot " + on + ")", gold == 0 && on.getCount() == 16);
                    SmokeTest.claimOn = false;
                    ClientConfig.set(SceneKinds.DEPOT, Trigger.USE);
                    return true;
                }),

                // ---- E: an Item Drain whose front is blocked: CI walks the player round to another side ----
                new Step("x drain: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.DRAIN, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_DRAIN.getX() - 3, -60, XZ - 4, X_DRAIN.getX() + 3, -57, XZ + 3);
                        level.setBlockAndUpdate(X_DRAIN, AllBlocks.ITEM_DRAIN.getDefaultState());
                        level.setBlockAndUpdate(X_DRAIN.north(), Blocks.STONE.defaultBlockState());
                        xKit(p, new ItemStack(Items.WATER_BUCKET));
                        p.teleportTo(level, X_DRAIN.getX() + 0.5, -60, X_DRAIN.getZ() - 2.4, 0f, 25f);
                        return true;
                    });
                }),
                new Step("x drain: right-click", 6, mc -> use(mc, 8, X_DRAIN, Direction.UP)),
                new Step("x drain: scene", 0, mc -> {
                    if (!xActive(DrainScene.class)) {
                        return false;
                    }
                    Direction side = Scene.station.sideDirection();
                    CineCreate.LOGGER.info("[explore] drain scene worked from {} at {}", side, xFmt(mc.player.position()));
                    DrainScene.smokePin = true;
                    pickUp(mc, 0);
                    return true;
                }),
                new Step("x drain: lay the bucket", 8, mc -> {
                    drainScene().hover();
                    drainScene().click(0, false);
                    return true;
                }),
                new Step("x drain: drained", 0, mc -> onServer(mc, p -> p.serverLevel().getBlockEntity(X_DRAIN)
                        instanceof com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity drain && drain.getHeldItemStack().is(Items.BUCKET))),
                new Step("x drain: where the bucket went", 60, mc -> {
                    Direction side = Scene.station == null ? null : Scene.station.sideDirection();
                    String held = onServer(mc, p -> xDrainHeld(p, X_DRAIN));
                    String near = onServer(mc, p -> xItemsNear(p, X_DRAIN));
                    int buckets = onServer(mc, p -> xCount(p, Items.BUCKET));
                    boolean onSide = side != null && onServer(mc, p -> xBucketOn(p, X_DRAIN, side));
                    int caught = Scene.station instanceof DrainScene drain ? drain.caught : -1;
                    shot(mc, "x05_drain_bucket_rolled");
                    CineCreate.LOGGER.info("[explore] drain: worked from {}, held {}, items near {}, player buckets {}, caught {}", side, held, near,
                            buckets, caught);
                    check("x drain: the drained bucket rolls off toward the side the player works from (" + side + "): on the drain " + held
                            + "; items near " + near + "; the player has " + buckets + " bucket(s); the scene counted " + caught
                            + " catch(es)", held == null && (buckets == 1 || onSide));
                    DrainScene.smokePin = false;
                    if (Scene.active()) {
                        Scene.leave();
                    }
                    return true;
                }),
                new Step("x drain: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- F: the depot replaced (broken and set again) while the player is in its scene ----
                new Step("x replaced: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.DEPOT, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_REPL.getX() - 2, -60, XZ - 3, X_REPL.getX() + 2, -57, XZ + 2);
                        level.setBlockAndUpdate(X_REPL, AllBlocks.DEPOT.getDefaultState());
                        return xKit(p, new ItemStack(Items.GOLD_INGOT, 16)) && standBefore(p, X_REPL, 1.6);
                    });
                }),
                new Step("x replaced: right-click", 6, mc -> use(mc, 8, X_REPL, Direction.UP, xTop(X_REPL))),
                new Step("x replaced: scene", 0, mc -> {
                    if (!xActive(DepotScene.class)) {
                        return false;
                    }
                    DepotScene.smokePin = true;
                    pickUp(mc, 0);
                    return true;
                }),
                new Step("x replaced: the depot is replaced", 8, mc -> onServer(mc, p -> {
                    renew(p.serverLevel(), X_REPL, AllBlocks.DEPOT.getDefaultState());
                    return true;
                })),
                new Step("x replaced: set down", 10, mc -> {
                    boolean menu = onServer(mc, p -> p.containerMenu instanceof DepotMenu);
                    CineCreate.LOGGER.info("[explore] after the replacement: scene {}, the server's depot menu still open {}", Scene.phase, menu);
                    SmokeTest.xFlag = xActive(DepotScene.class);
                    if (!SmokeTest.xFlag) {
                        return true;
                    }
                    scene().hover();
                    scene().click(0, false);
                    return true;
                }),
                new Step("x replaced: set down where", 14, mc -> {
                    if (moving()) {
                        return false;
                    }
                    ItemStack on = onServer(mc, p -> xDepotHeld(p, X_REPL));
                    int gold = onServer(mc, p -> xCount(p, Items.GOLD_INGOT));
                    String slot = xActive(DepotScene.class) ? scene().slot(DepotMenu.MAIN).toString() : "-";
                    shot(mc, "x06_replaced_depot_set_down");
                    check("x replaced: a stack set down after the depot was replaced mid-scene lies on the depot standing there (scene went on: "
                            + SmokeTest.xFlag + "; server: depot " + on + ", player " + gold + " gold; the scene's slot " + slot + ")",
                            !SmokeTest.xFlag || on.getCount() == 16);
                    DepotScene.smokePin = false;
                    Scene.leave();
                    return true;
                }),
                new Step("x replaced: left", 0, mc -> Scene.phase == Scene.Phase.NONE),
                new Step("x replaced: after leaving", 10, mc -> {
                    ItemStack on = onServer(mc, p -> xDepotHeld(p, X_REPL));
                    int gold = onServer(mc, p -> xCount(p, Items.GOLD_INGOT));
                    String near = onServer(mc, p -> xItemsNear(p, X_REPL));
                    check("x replaced: after leaving, none of the 16 gold is lost (server: depot " + on + ", player " + gold + ", items near "
                            + near + ")", on.getCount() + gold == 16 || near.contains("gold"));
                    return true;
                }),

                // ---- G: a bottle taken off the depot while the spout pours ----
                new Step("x spout: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.SPOUTING, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_SPOUT_DEPOT.getX() - 2, -60, XZ - 3, X_SPOUT_DEPOT.getX() + 2, -56, XZ + 2);
                        level.setBlockAndUpdate(X_SPOUT_DEPOT, AllBlocks.DEPOT.getDefaultState());
                        level.setBlockAndUpdate(X_SPOUT_DEPOT.above(2), AllBlocks.SPOUT.getDefaultState());
                        IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, X_SPOUT_DEPOT.above(2), null);
                        if (tank != null) {
                            tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                        }
                        return xKit(p, new ItemStack(Items.GLASS_BOTTLE)) && standBefore(p, X_SPOUT_DEPOT, 1.6);
                    });
                }),
                new Step("x spout: right-click", 6, mc -> use(mc, 8, X_SPOUT_DEPOT, Direction.UP, xTop(X_SPOUT_DEPOT))),
                new Step("x spout: scene", 0, mc -> {
                    if (!xActive(SpoutScene.class)) {
                        return false;
                    }
                    DepotScene.smokePin = true;
                    pickUp(mc, 0);
                    return true;
                }),
                new Step("x spout: bottle down", 8, mc -> {
                    scene().hover();
                    scene().click(0, false);
                    return true;
                }),
                new Step("x spout: take it mid-pour", 0, mc -> {
                    if (moving() || !(mc.level.getBlockEntity(X_SPOUT_DEPOT.above(2)) instanceof SpoutBlockEntity spout) || spout.processingTicks < 14) {
                        return false;
                    }
                    SmokeTest.xBefore = ((SpoutScene) Scene.station).fills;
                    CineCreate.LOGGER.info("[explore] spout countdown {} (client) when the hand takes the bottle", spout.processingTicks);
                    scene().hover();
                    scene().click(0, false);
                    return true;
                }),
                new Step("x spout: flare?", 12, mc -> {
                    shot(mc, "x07_spout_flare_after_take");
                    return true;
                }),
                new Step("x spout: taken mid-pour", 30, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int flares = Scene.station instanceof SpoutScene spout ? spout.fills - SmokeTest.xBefore : 0;
                    // наполненная бутылка — в руке или ещё на депо (взять посреди налива могли и не дать)
                    ItemStack lying = onServer(mc, p -> xDepotHeld(p, X_SPOUT_DEPOT));
                    int filled = onServer(mc, p -> xCount(p, Items.POTION)) + (lying.is(Items.POTION) ? lying.getCount() : 0);
                    int empty = onServer(mc, p -> xCount(p, Items.GLASS_BOTTLE)) + (lying.is(Items.GLASS_BOTTLE) ? lying.getCount() : 0);
                    int fluid = onServer(mc, p -> xFluid(p, X_SPOUT_DEPOT.above(2)));
                    check("x spout: a fill flare only for a bottle that is filled (flares " + flares + "; server: " + empty + " empty bottle(s), "
                            + filled + " filled, on the depot " + lying + ", the spout holds " + fluid + " mB)", filled > 0 || flares == 0);
                    DepotScene.smokePin = false;
                    if (Scene.active()) {
                        Scene.leave();
                    }
                    return true;
                }),
                new Step("x spout: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- H: a deployer working the depot, its hand coming down over the plate the cursor rests on ----
                new Step("x deployer: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.DEPLOYING, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        BlockPos deployer = X_DEP_DEPOT.above(2);
                        xClear(level, X_DEP_DEPOT.getX() - 2, -60, XZ - 3, X_DEP_DEPOT.getX() + 2, -56, XZ + 2);
                        level.setBlockAndUpdate(X_DEP_DEPOT, AllBlocks.DEPOT.getDefaultState());
                        level.setBlockAndUpdate(deployer, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
                        motorAlong(level, deployer, 32);
                        deployerHolds(p, deployer, AllItems.ANDESITE_ALLOY.asStack(64));
                        xDepot(level, X_DEP_DEPOT, new ItemStack(Items.STRIPPED_OAK_LOG, 48));
                        return xKit(p) && standBefore(p, X_DEP_DEPOT, 1.6);
                    });
                }),
                new Step("x deployer: right-click", 6, mc -> use(mc, 8, X_DEP_DEPOT, Direction.UP, xTop(X_DEP_DEPOT))),
                new Step("x deployer: scene", 0, mc -> {
                    if (!xActive(DeployingScene.class)) {
                        return false;
                    }
                    // the cursor engages only once the mouse has moved: move it away first
                    xMouse(mc, mc.getWindow().getGuiScaledWidth() * 0.2, mc.getWindow().getGuiScaledHeight() * 0.8);
                    SmokeTest.xWait = 0;
                    SmokeTest.xNote = "";
                    return true;
                }),
                new Step("x deployer: cursor on the plate", 4, mc -> {
                    if (!xActive(DeployingScene.class)) {
                        return true;
                    }
                    double[] gui = Picking.toGui(xTop(X_DEP_DEPOT).add(0, 0.02, 0));
                    if (gui != null) {
                        xMouse(mc, gui[0], gui[1]);
                    }
                    if (mc.level.getBlockEntity(X_DEP_DEPOT.above(2)) instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity deployer) {
                        float offset = deployer.getHandOffset(mc.getTimer().getGameTimeDeltaPartialTick(false));
                        List<net.minecraft.network.chat.Component> tip = Scene.station.tooltip(ItemStack.EMPTY);
                        boolean onHand = tip != null && !tip.isEmpty() && tip.get(0).getContents() instanceof TranslatableContents t
                                && t.getKey().startsWith("cinecreate.deploying");
                        if (offset < 0.3 && !SmokeTest.xNote.contains("up")) {
                            SmokeTest.xNote += "hand up (" + String.format(Locale.ROOT, "%.2f", offset) + "): the cursor targets " + tip + "; ";
                        }
                        // рука активатора внизу, курсор всё там же, на работе: клик — о работе, не о руке
                        if (offset > 0.6 && SmokeTest.xNote.contains("up") && !moving()) {
                            SmokeTest.xNote += "hand down (" + String.format(Locale.ROOT, "%.2f", offset) + "): the same cursor targets " + tip
                                    + " (on the deployer's hand: " + onHand + ")";
                            SmokeTest.xValue = offset;
                            shot(mc, "x08_deployer_hand_over_plate");
                            Scene.station.click(0, false);
                            SmokeTest.xFlag = ((DeployingScene) Scene.station).moving();
                            return true;
                        }
                    }
                    if (++SmokeTest.xWait > 300) {
                        check("x deployer: the deployer's hand came down within 15 s (" + SmokeTest.xNote + ")", false);
                        return true;
                    }
                    return false;
                }),
                new Step("x deployer: what the click did", 4, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int alloy = onServer(mc, p -> xCount(p, AllItems.ANDESITE_ALLOY.get()));
                    int logs = onServer(mc, p -> xCount(p, Items.STRIPPED_OAK_LOG));
                    ItemStack held = deployerHeld(mc, X_DEP_DEPOT.above(2));
                    CineCreate.LOGGER.info("[explore] deployer: {}", SmokeTest.xNote);
                    check("x deployer: with the cursor resting on the plate, the deployer's hand coming down does not take over the click ("
                            + SmokeTest.xNote + "; clicked: " + SmokeTest.xFlag + "; server: the player got " + alloy + " andesite alloy and "
                            + logs + " logs, the deployer holds " + held + ")", alloy == 0);
                    onServer(mc, p -> {
                        motorSpeed(p.serverLevel(), xMotorOf(p.serverLevel(), X_DEP_DEPOT.above(2)), 0);
                        return true;
                    });
                    xMouse(mc, mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0);
                    if (Scene.active()) {
                        Scene.leave();
                    }
                    return true;
                }),
                new Step("x deployer: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- I: the plate under a press whose head is down on it ----
                new Step("x press: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.PRESSING, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        BlockPos press = X_PRESS_DEPOT.above(2);
                        xClear(level, X_PRESS_DEPOT.getX() - 2, -60, XZ - 3, X_PRESS_DEPOT.getX() + 2, -56, XZ + 2);
                        level.setBlockAndUpdate(X_PRESS_DEPOT, AllBlocks.DEPOT.getDefaultState());
                        level.setBlockAndUpdate(press, AllBlocks.MECHANICAL_PRESS.getDefaultState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
                        level.setBlockAndUpdate(press.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                                .setValue(BlockStateProperties.FACING, Direction.WEST));
                        motorSpeed(level, press.east(), 24);
                        xDepot(level, X_PRESS_DEPOT, new ItemStack(Items.IRON_INGOT, 16));
                        return xKit(p) && standBefore(p, X_PRESS_DEPOT, 1.6);
                    });
                }),
                new Step("x press: right-click", 6, mc -> use(mc, 8, X_PRESS_DEPOT, Direction.UP, xTop(X_PRESS_DEPOT))),
                new Step("x press: scene", 0, mc -> {
                    if (!xActive(PressScene.class)) {
                        return false;
                    }
                    DepotScene.smokePin = true;
                    scene().hover();
                    return true;
                }),
                new Step("x press: head on the plate", 0, mc -> {
                    if (moving() || !(mc.level.getBlockEntity(X_PRESS_DEPOT.above(2)) instanceof MechanicalPressBlockEntity press)) {
                        return false;
                    }
                    PressingBehaviour pressing = press.getPressingBehaviour();
                    float head = pressing.running ? pressing.getRenderedHeadOffset(mc.getTimer().getGameTimeDeltaPartialTick(false)) : 0;
                    if (head < 0.8) {
                        return false;
                    }
                    SmokeTest.xValue = head;
                    shot(mc, "x09_press_head_down_hover");
                    scene().hover();
                    scene().click(0, false);
                    SmokeTest.xFlag = moving();
                    return true;
                }),
                new Step("x press: reaching under the head", 3, mc -> {
                    float head = mc.level.getBlockEntity(X_PRESS_DEPOT.above(2)) instanceof MechanicalPressBlockEntity press
                            && press.getPressingBehaviour().running
                            ? press.getPressingBehaviour().getRenderedHeadOffset(mc.getTimer().getGameTimeDeltaPartialTick(false)) : 0;
                    SmokeTest.xNote = "head at the beat " + String.format(Locale.ROOT, "%.2f", head) + ", hands " + xFmt(xHand(true)) + " / "
                            + xFmt(xHand(false)) + ", plate " + xFmt(xTop(X_PRESS_DEPOT));
                    shot(mc, "x09_press_take_under_head");
                    return true;
                }),
                new Step("x press: taken", 0, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int iron = onServer(mc, p -> xCount(p, Items.IRON_INGOT));
                    int sheets = onServer(mc, p -> xCount(p, AllItems.IRON_SHEET.get()));
                    check("x press: the plate is not worked while the press head is down on it (a take started at head offset "
                            + String.format(Locale.ROOT, "%.2f", SmokeTest.xValue) + ": " + SmokeTest.xFlag + "; " + SmokeTest.xNote
                            + "; server: the hand got " + iron + " iron, " + sheets + " sheets)", !SmokeTest.xFlag && iron + sheets == 0);
                    DepotScene.smokePin = false;
                    if (Scene.active()) {
                        Scene.leave();
                    }
                    onServer(mc, p -> {
                        motorSpeed(p.serverLevel(), X_PRESS_DEPOT.above(2).east(), 0);
                        return true;
                    });
                    return true;
                }),
                new Step("x press: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- K: the hand-off when neither the hand nor the deployer holds anything ----
                new Step("x handoff: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.DEPLOYER, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        BlockPos at = new BlockPos(50, -59, XZ);
                        xClear(level, at.getX() - 2, -60, XZ - 3, at.getX() + 2, -57, XZ + 2);
                        level.setBlockAndUpdate(at, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
                        return xKit(p) && standBefore(p, at, 1.6);
                    });
                }),
                new Step("x handoff: right-click", 6, mc -> use(mc, 8, new BlockPos(50, -59, XZ), Direction.NORTH)),
                new Step("x handoff: scene", 0, mc -> {
                    if (!xActive(DeployerScene.class)) {
                        return false;
                    }
                    DeployerScene.smokePin = true;
                    return true;
                }),
                new Step("x handoff: reach out empty", 8, mc -> {
                    SmokeTest.xBefore = ((DeployerScene) Scene.station).swaps;
                    ((DeployerScene) Scene.station).hover();
                    ((DeployerScene) Scene.station).click(0, false);
                    SmokeTest.xFlag = ((DeployerScene) Scene.station).moving();
                    return true;
                }),
                new Step("x handoff: reached", 6, mc -> {
                    shot(mc, "x11_handoff_nothing");
                    return true;
                }),
                new Step("x handoff: done", 0, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int swaps = ((DeployerScene) Scene.station).swaps - SmokeTest.xBefore;
                    check("x handoff: with nothing in the hand and nothing in the deployer's, the hand-off is refused, not played (a move played: "
                            + SmokeTest.xFlag + ", swaps counted " + swaps + ")", !SmokeTest.xFlag);
                    DeployerScene.smokePin = false;
                    Scene.leave();
                    return true;
                }),
                new Step("x handoff: left", 0, mc -> Scene.phase == Scene.Phase.NONE),

                // ---- J: an empty hand at a millstone with nothing ground: Create gives back the mouth's input ----
                new Step("x millstone: build", 2, mc -> {
                    ClientConfig.set(SceneKinds.MILLSTONE, Trigger.USE);
                    return onServer(mc, p -> {
                        ServerLevel level = p.serverLevel();
                        xClear(level, X_MILL.getX() - 2, -60, XZ - 3, X_MILL.getX() + 2, -57, XZ + 2);
                        level.setBlockAndUpdate(X_MILL, AllBlocks.MILLSTONE.getDefaultState());
                        if (level.getBlockEntity(X_MILL) instanceof MillstoneBlockEntity millstone) {
                            millstone.inputInv.setStackInSlot(0, new ItemStack(Items.WHEAT, 8));
                            millstone.sendData();
                        }
                        return xKit(p) && standBefore(p, X_MILL, 1.6);
                    });
                }),
                new Step("x millstone: right-click", 6, mc -> use(mc, 8, X_MILL, Direction.UP)),
                new Step("x millstone: scene", 0, mc -> {
                    if (!xActive(MillstoneScene.class)) {
                        return false;
                    }
                    MillstoneScene.smokePin = true;
                    return true;
                }),
                new Step("x millstone: gather", 8, mc -> {
                    millstoneScene().hover();
                    millstoneScene().click(0, false);
                    return true;
                }),
                new Step("x millstone: gathering", 5, mc -> {
                    Vec3 mouth = Vec3.atBottomCenterOf(X_MILL).add(0, 1, 0);
                    // зазор сбоку, куда раньше тянулась рука за смолотым
                    Vec3 gap = mouth.add(Scene.station.right().scale(0.5)).add(Scene.station.side().scale(0.12)).add(0, -0.3, 0);
                    Vec3 right = xHand(true), left = xHand(false);
                    double toMouth = Math.min(right == null ? 9 : right.distanceTo(mouth), left == null ? 9 : left.distanceTo(mouth));
                    double toGap = Math.min(right == null ? 9 : right.distanceTo(gap), left == null ? 9 : left.distanceTo(gap));
                    SmokeTest.xNote = "hands " + xFmt(right == null ? null : right.subtract(mouth)) + " / " + xFmt(left == null ? null : left.subtract(mouth))
                            + " from the mouth (nearest " + String.format(Locale.ROOT, "%.2f", toMouth) + ", to the side gap "
                            + String.format(Locale.ROOT, "%.2f", toGap) + ")";
                    SmokeTest.xValue = toMouth - toGap;
                    shot(mc, "x10_millstone_gather_from_mouth");
                    return true;
                }),
                new Step("x millstone: gathered", 0, mc -> {
                    if (moving()) {
                        return false;
                    }
                    int wheat = onServer(mc, p -> xCount(p, Items.WHEAT));
                    check("x millstone: the hand reaches where what it takes lies, the mouth (Create gave back " + wheat + " unground wheat; "
                            + SmokeTest.xNote + ")", wheat == 8 && SmokeTest.xValue < 0);
                    MillstoneScene.smokePin = false;
                    if (Scene.active()) {
                        Scene.leave();
                    }
                    return true;
                }),
                new Step("x millstone: left", 0, mc -> Scene.phase == Scene.Phase.NONE));
    }
}
