package org.minechestplate.cinecreate.station;

import com.simibubi.create.content.kinetics.crank.ValveHandleBlock;
import dev.dyslexic.cinematic.api.AddonStations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.bridge.BasinMenu;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.bridge.BurnerMenu;
import org.minechestplate.cinecreate.bridge.CrafterMenu;
import org.minechestplate.cinecreate.bridge.CrankMenu;
import org.minechestplate.cinecreate.bridge.DeployerMenu;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.bridge.DrainMenu;
import org.minechestplate.cinecreate.bridge.MillstoneMenu;
import org.minechestplate.cinecreate.bridge.SawMenu;
import org.minechestplate.cinecreate.bridge.ScenePrefs;
import org.minechestplate.cinecreate.bridge.ValveMenu;

/** The Create blocks that get a scene, and their registration with Cinematic Interactions. */
public final class SceneBlocks {
    private SceneBlocks() {}

    public static final List<Direction> ANY_SIDE = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    /** The depot's plate is 13 px up. */
    public static final double DEPOT_TOP = 13 / 16.0;

    public static final SceneBlock DEPOT = new SceneBlock(CineCreate.id("depot"), create("depot"), DepotMenu.class,
            state -> ANY_SIDE,
            // Create's depot only reacts to its top: its sides take blocks placed against them
            (state, face) -> face == Direction.UP,
            DepotMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, topHit(pos, DEPOT_TOP)),
            List.of(SceneKinds.DEPOT, SceneKinds.PRESSING, SceneKinds.SPOUTING, SceneKinds.DEPLOYING, SceneKinds.FAN),
            // машина над депо (или поток вентилятора через него) делает сцену своей, и начинается она по-своему
            SceneBlocks::depotKind,
            stack -> false);

    /** The copper Valve Handle and its 16 dyed copies: one station each (CI takes one block per definition). */
    public static final List<SceneBlock> VALVES = valves();

    /** The Hand Crank: turned by hand in the scene, through Create's own turn. */
    public static final SceneBlock CRANK = new SceneBlock(CineCreate.id("hand_crank"), create("hand_crank"), CrankMenu.class,
            SceneBlocks::frontOrAnySide,
            (state, face) -> true,
            CrankMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, faceHit(pos, state.getValue(BlockStateProperties.FACING))),
            List.of(SceneKinds.CRANK),
            (level, pos) -> SceneKinds.CRANK,
            stack -> false);

    /** The Basin: things dropped in, fluids poured, and the watch when a mixer or a press above works it. */
    public static final SceneBlock BASIN = new SceneBlock(CineCreate.id("basin"), create("basin"), BasinMenu.class,
            state -> ANY_SIDE,
            // в бассейн кладут сверху; к его бокам ставят блоки (воронки, лотки)
            (state, face) -> face == Direction.UP,
            BasinMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, topHit(pos, 1)),
            List.of(SceneKinds.BASIN),
            (level, pos) -> SceneKinds.BASIN,
            stack -> false);

    /** The Blaze Burner: fed through its cage, lit with flint and steel when empty. */
    public static final SceneBlock BURNER = new SceneBlock(CineCreate.id("blaze_burner"), create("blaze_burner"), BurnerMenu.class,
            state -> ANY_SIDE,
            // сверху на горелку ставят бассейн: верх остаётся за Create
            (state, face) -> face != Direction.UP,
            BurnerMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, faceHit(pos, player.getDirection().getOpposite())),
            List.of(SceneKinds.BURNER),
            (level, pos) -> SceneKinds.BURNER,
            stack -> false);

    /** The Item Drain: a container laid on its grate is drained by Create. */
    public static final SceneBlock DRAIN = new SceneBlock(CineCreate.id("item_drain"), create("item_drain"), DrainMenu.class,
            state -> ANY_SIDE,
            // работает верхняя решётка; к бокам ставят воронки и лотки
            (state, face) -> face == Direction.UP,
            DrainMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, topHit(pos, 1)),
            List.of(SceneKinds.DRAIN),
            (level, pos) -> SceneKinds.DRAIN,
            stack -> false);

    /**
     * The Deployer, for the hand-off at its hand. Only sideways: one facing down works a depot (that depot's scene), and
     * nobody stands at the hand of one facing up.
     */
    public static final SceneBlock DEPLOYER = new SceneBlock(CineCreate.id("deployer"), create("deployer"), DeployerMenu.class,
            state -> {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                return facing.getAxis().isHorizontal() ? List.of(facing) : List.of();
            },
            (state, face) -> face == state.getValue(BlockStateProperties.FACING),
            DeployerMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, faceHit(pos, state.getValue(BlockStateProperties.FACING))),
            List.of(SceneKinds.DEPLOYER),
            (level, pos) -> SceneKinds.DEPLOYER,
            stack -> false);

    /** The Millstone: poured into at its mouth, ground output gathered. */
    public static final SceneBlock MILLSTONE = new SceneBlock(CineCreate.id("millstone"), create("millstone"), MillstoneMenu.class,
            state -> ANY_SIDE,
            (state, face) -> true,
            MillstoneMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, topHit(pos, 1)),
            List.of(SceneKinds.MILLSTONE),
            (level, pos) -> SceneKinds.MILLSTONE,
            stack -> false);

    /** The Mechanical Saw facing up: the stonecutter of Create (one facing sideways is a machine on a belt). */
    public static final SceneBlock SAW = new SceneBlock(CineCreate.id("mechanical_saw"), create("mechanical_saw"), SawMenu.class,
            state -> state.getValue(BlockStateProperties.FACING) == Direction.UP ? ANY_SIDE : List.of(),
            (state, face) -> face == Direction.UP,
            SawMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, topHit(pos, 1)),
            List.of(SceneKinds.SAW),
            (level, pos) -> level.getBlockState(pos).getValue(BlockStateProperties.FACING) == Direction.UP ? SceneKinds.SAW : null,
            stack -> false);

    /** The Weighted Ejector: a depot that throws (its depot behaviour, so the depot's bridge). */
    public static final SceneBlock EJECTOR = new SceneBlock(CineCreate.id("weighted_ejector"), create("weighted_ejector"), DepotMenu.class,
            state -> ANY_SIDE,
            (state, face) -> face == Direction.UP,
            DepotMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, topHit(pos, DEPOT_TOP)),
            List.of(SceneKinds.EJECTOR),
            (level, pos) -> SceneKinds.EJECTOR,
            stack -> false);

    /** Mechanical Crafters: the scene works the whole group the clicked one stands in ({@link CrafterGroup}). */
    public static final SceneBlock CRAFTER = new SceneBlock(CineCreate.id("mechanical_crafter"), create("mechanical_crafter"),
            CrafterMenu.class,
            state -> List.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
            // окна только спереди: там и клик Create, а к другим граням ставят блоки (и подводят вращение)
            (state, face) -> face == state.getValue(BlockStateProperties.HORIZONTAL_FACING),
            CrafterMenu::provider,
            (player, state, pos) -> Bridge.useOn(player, state, faceHit(pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING))),
            List.of(SceneKinds.CRAFTER),
            // группа больше 5×5 в кадр не помещается: такой клик остаётся за Create
            (level, pos) -> CrafterGroup.of(level, pos) != null ? SceneKinds.CRAFTER : null,
            stack -> false);

    private static final List<SceneBlock> ALL = all();

    private static volatile Map<Block, SceneBlock> byBlock = Map.of();
    private static volatile Map<ResourceLocation, SceneBlock> byScene = Map.of();

    /** Common setup, both sides: our blocks go to Cinematic Interactions, but only with every hook in place. */
    public static void register() {
        if (!CiHooks.inPlace()) {
            return;
        }
        Map<Block, SceneBlock> blocks = new HashMap<>();
        Map<ResourceLocation, SceneBlock> scenes = new HashMap<>();
        for (SceneBlock scene : ALL) {
            Block block = BuiltInRegistries.BLOCK.getOptional(scene.blockId()).orElse(null);
            if (block == null) {
                CineCreate.LOGGER.warn("[cinecreate] {} is not registered: no scene for it", scene.blockId());
                continue;
            }
            // когда у блока выключены (OFF) все сцены, на клиенте у него нет рабочих сторон: CI не предложит перейти
            // к нему из другой сцены (позиции CI сюда не передаёт, так что голое депо и депо под прессом не различить)
            AddonStations.register(new AddonStations.Definition(scene.sceneId(), scene.blockId(), scene.menuClass(),
                    state -> ScenePrefs.allOffHere(scene) ? List.of() : scene.sides().apply(state)));
            blocks.put(block, scene);
            scenes.put(scene.sceneId(), scene);
        }
        byBlock = Map.copyOf(blocks);
        byScene = Map.copyOf(scenes);
        CineCreate.LOGGER.info("[cinecreate] Cinematic Interactions scenes for {}", scenes.keySet());
    }

    /** Our scene for this block, or null (also when the scenes are off). */
    @Nullable
    public static SceneBlock of(BlockState state) {
        return byBlock.get(state.getBlock());
    }

    /** Whether this Cinematic Interactions add-on station id is ours. */
    public static boolean owns(@Nullable ResourceLocation sceneId) {
        return sceneId != null && byScene.containsKey(sceneId);
    }

    public static boolean registered(SceneBlock scene) {
        return byScene.containsKey(scene.sceneId());
    }

    /** Which scene plays at this position of one of our blocks (null when the block is not ours). */
    @Nullable
    public static SceneKind kindAt(BlockGetter level, BlockPos pos) {
        SceneBlock scene = of(level.getBlockState(pos));
        return scene == null ? null : scene.kindAt().at(level, pos);
    }

    /**
     * The block of ours a click on this one is about: the block itself, or the one a machine on it works (a press: the
     * depot two blocks below). Null for anything else.
     */
    @Nullable
    public static BlockPos target(BlockGetter level, BlockPos pos) {
        // деплоер вниз над депо — его машина: клик по нему — это депо (боком у деплоера своя сцена)
        BlockPos worked = Composites.workedBy(level, pos);
        return worked != null ? worked : of(level.getBlockState(pos)) != null ? pos : null;
    }

    /** What a depot plays: the machine over it, or the fan stream through it, or the bare depot. */
    private static SceneKind depotKind(BlockGetter level, BlockPos pos) {
        if (Composites.pressOver(level, pos) != null) {
            return SceneKinds.PRESSING;
        }
        if (Composites.spoutOver(level, pos) != null) {
            return SceneKinds.SPOUTING;
        }
        if (Composites.deployerOver(level, pos) != null) {
            return SceneKinds.DEPLOYING;
        }
        Composites.FanStream stream = Composites.fanStream(level, pos);
        return stream != null && stream.type() != null ? SceneKinds.FAN : SceneKinds.DEPOT;
    }

    private static List<SceneBlock> valves() {
        List<SceneBlock> valves = new ArrayList<>();
        valves.add(valve("copper_valve_handle"));
        for (DyeColor color : DyeColor.values()) {
            valves.add(valve(color.getSerializedName() + "_valve_handle"));
        }
        return List.copyOf(valves);
    }

    private static SceneBlock valve(String path) {
        return new SceneBlock(CineCreate.id(path), create(path), ValveMenu.class,
                SceneBlocks::frontOrAnySide,
                (state, face) -> true,
                ValveMenu::provider,
                // свой поворот Create делает в обработчике клика, а не в useItemOn
                (player, state, pos) -> {
                    if (state.getBlock() instanceof ValveHandleBlock valve) {
                        valve.clicked(player.level(), pos, state, player, InteractionHand.MAIN_HAND);
                    }
                },
                List.of(SceneKinds.VALVE),
                (level, pos) -> SceneKinds.VALVE,
                stack -> DyeColor.getColor(stack) != null);
    }

    private static List<SceneBlock> all() {
        List<SceneBlock> all = new ArrayList<>();
        all.add(DEPOT);
        all.addAll(VALVES);
        all.add(CRAFTER);
        all.add(CRANK);
        all.add(BASIN);
        all.add(BURNER);
        all.add(DRAIN);
        all.add(DEPLOYER);
        all.add(MILLSTONE);
        all.add(SAW);
        all.add(EJECTOR);
        return List.copyOf(all);
    }

    /** Something fixed to a wall is worked from the front only; on a floor or a ceiling, from any side. */
    private static List<Direction> frontOrAnySide(BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        return facing.getAxis().isHorizontal() ? List.of(facing) : ANY_SIDE;
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }

    private static BlockHitResult topHit(BlockPos pos, double height) {
        return new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, height, 0), Direction.UP, pos, false);
    }

    private static BlockHitResult faceHit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, pos, false);
    }
}
