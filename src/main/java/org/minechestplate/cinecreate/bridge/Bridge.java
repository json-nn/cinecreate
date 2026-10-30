package org.minechestplate.cinecreate.bridge;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import dev.dyslexic.cinematic.ClientBridge;
import dev.dyslexic.cinematic.station.Sessions;
import dev.dyslexic.cinematic.station.StationKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.minechestplate.cinecreate.registry.CineTags;
import org.minechestplate.cinecreate.station.Composites;
import org.minechestplate.cinecreate.station.SceneBlock;
import org.minechestplate.cinecreate.station.SceneBlocks;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.Trigger;

/** What our Cinematic Interactions hooks do for Create's blocks (see the mixin package). */
public final class Bridge {
    private Bridge() {}

    private static final double REACH_SQR = 6.0 * 6.0;
    /**
     * How far from the player's feet a scene's block can be, to its middle: CI's server lets a scene begin within 6 and
     * its client sends the start before any walk, so farther (an Extendo Grip reaches 7.5) a scene would be cut off at
     * once. Beyond this the click is Create's own; a little short of 6, so the two sides agree.
     */
    public static final double SCENE_REACH = 5.9;

    /** Client side: whether the server runs our scenes (it has the add-on). Set by the client; a server always runs them. */
    public static BooleanSupplier serverRunsScenes = () -> true;
    /** Development only ({@code SmokeTest}): both sides act as on a server without the add-on, to test client mode in one game. */
    public static volatile boolean devNoAddon;

    /**
     * Instead of {@code MenuActions.open}: the bridge menu, with the position (and any {@link BridgeMenu.ExtraData}) sent
     * along so client and server build the same slots.
     */
    public static boolean open(ServerPlayer player, BlockPos pos, Direction side) {
        opening = side;
        try {
            return open(player, pos);
        } finally {
            opening = null;
        }
    }

    @Nullable
    private static Direction opening;

    /** Which way the player faces the block in the scene being opened (CI's session side; walked round, not where they clicked from). */
    public static Direction facing(Player player) {
        return opening != null ? opening.getOpposite() : player.getDirection();
    }

    public static boolean open(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        if (!allowed(player, pos, Direction.UP)) {
            return false;
        }
        SceneBlock scene = SceneBlocks.of(level.getBlockState(pos));
        MenuProvider provider = scene == null ? null : scene.menu().create(level, pos);
        if (provider == null) {
            return false;
        }
        player.openMenu(provider, buf -> {
            buf.writeBlockPos(pos);
            if (provider instanceof BridgeMenu.ExtraData extra) {
                extra.write(buf);
            }
        });
        return scene.menuClass().isInstance(player.containerMenu);
    }

    /**
     * No scene here (out of reach, no room, turned off): Create's own right-click on the working face instead. Replaces
     * {@code Sessions.openWindow}, which would open a window these blocks do not have.
     */
    public static void vanillaUse(ServerPlayer player, BlockPos pos) {
        if (Sessions.get(player) != null) {
            Sessions.end(player, false);
        }
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        SceneBlock scene = SceneBlocks.of(state);
        if (scene == null || player.isSpectator() || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR || !allowed(player, pos, Direction.UP)) {
            return;
        }
        scene.use().use(player, state, pos);
    }

    /** Create's use of a block as a right-click on it would do it: with the held item, then without. */
    public static void useOn(ServerPlayer player, BlockState state, BlockHitResult hit) {
        Level level = player.level();
        ItemInteractionResult result = state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            state.useWithoutItem(level, player, hit);
        }
    }

    /**
     * Create's own right-click on a face of a block, done with what the scene's cursor carries: the carried stack is in
     * the main hand for the call, and what Create leaves there goes back to the cursor. With nothing carried (an empty
     * hand taking everything out), what Create puts into the emptied hand goes into the inventory instead, as Create's
     * own empty-handed click leaves it.
     */
    public static ItemInteractionResult useWithCarried(ServerPlayer player, AbstractContainerMenu menu, BlockPos pos, Direction face) {
        return useWithCarried(player, menu, pos, face, false);
    }

    /** The same; {@code intoHand}: what an empty hand gets is the cursor's (the deployer's hand swaps into the scene's). */
    public static ItemInteractionResult useWithCarried(ServerPlayer player, AbstractContainerMenu menu, BlockPos pos, Direction face,
                                                       boolean intoHand) {
        Level level = player.level();
        if (!allowed(player, pos, face)) {
            return ItemInteractionResult.FAIL;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack own = player.getMainHandItem();
        ItemStack carried = menu.getCarried();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)),
                face, pos, false);
        boolean empty = carried.isEmpty();
        player.setItemInHand(InteractionHand.MAIN_HAND, carried);
        try {
            return state.useItemOn(carried, level, player, InteractionHand.MAIN_HAND, hit);
        } finally {
            ItemStack left = player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, own);
            if (empty && !intoHand && !left.isEmpty()) {
                // Create положил взятое в пустой на время слот: в инвентарь, а не в руку сцены
                player.getInventory().placeItemBackInInventory(left);
            } else {
                menu.setCarried(left);
            }
        }
    }

    /**
     * Clicks Cinematic Interactions must leave alone, decided the same way on both sides: Create's value boxes, scenes
     * started on the key or not at all, items with their own Create interaction (and per block: a dye on a valve), faces
     * that are not worked, and blocks with no scene where they stand (a crafter group bigger than 10×10).
     */
    public static boolean leaveToCreate(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        SceneBlock scene = SceneBlocks.of(state);
        if (scene == null) {
            return false;
        }
        // сервер без дополнения: сцен там нет, клик — за Create (сцену режима клиента начинает само нажатие: ClientMode)
        if (devNoAddon || event.getLevel().isClientSide() && !serverRunsScenes.getAsBoolean()) {
            return true;
        }
        if (onValueBox(event.getLevel(), event.getPos(), event.getEntity(), event.getHitVec()) || beyondScenes(event.getEntity(), event.getPos())) {
            return true;
        }
        // KEY и OFF: правый клик целиком за Create (клиент смотрит в свой конфиг, сервер — в присланный им);
        // и там, где сцены нет вовсе (группа крафтеров больше 10×10)
        SceneKind kind = scene.kindAt().at(event.getLevel(), event.getPos());
        if (kind == null || ScenePrefs.of(event.getEntity(), kind) != Trigger.USE) {
            return true;
        }
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && (held.is(CineTags.SCENE_BYPASS) || scene.bypass().test(held))) {
            return true;
        }
        return !scene.usedOn().test(state, event.getHitVec().getDirection());
    }

    /**
     * HIGH priority, both sides: a click on a machine working one of our blocks (a press: the depot under it) starts
     * that block's scene as CI starts its own stations; CI's handler would not, the machine is not a station. A block
     * in hand is still placed against the machine, and its value boxes stay Create's. Clicks on our blocks themselves go
     * through CI's handler and {@link #leaveToCreate}.
     */
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (automated(player) || probing) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getUseBlock() == TriState.FALSE
                || player.isSecondaryUseActive() || player.isSpectator()) {
            return;
        }
        BlockPos clicked = event.getPos();
        ItemStack held = event.getItemStack();
        if (devNoAddon || level.isClientSide() && !serverRunsScenes.getAsBoolean()) {
            return;
        }
        BlockPos target = Composites.workedBy(level, clicked);
        if (target == null || held.getItem() instanceof BlockItem || onValueBox(level, clicked, player, event.getHitVec())) {
            return;
        }
        BlockState state = level.getBlockState(target);
        SceneBlock scene = SceneBlocks.of(state);
        SceneKind kind = scene == null ? null : scene.kindAt().at(level, target);
        if (kind == null || held.is(CineTags.SCENE_BYPASS) || scene.bypass().test(held) || beyondScenes(player, target)
                || !StationKind.ADDON.usable(level, target, state) || ScenePrefs.of(player, kind) != Trigger.USE) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (level.isClientSide()) {
            // то же, что делает CI для своих станций: сцену начинает клиент (Scene.begin за этим мостом)
            ClientBridge.useStation.accept(target.immutable(), StationKind.ADDON);
        }
    }

    /**
     * Client mode: whether this click would start a scene where the server has the add-on (one of our blocks or a machine
     * working one, its trigger a right-click, nothing in hand that Create handles itself).
     */
    public static boolean clientModeStarts(Level level, Player player, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockPos target = SceneBlocks.target(level, clicked);
        ItemStack held = player.getMainHandItem();
        if (target == null || player.isSecondaryUseActive() || player.isSpectator() || beyondScenes(player, target)
                || onValueBox(level, clicked, player, hit)) {
            return false;
        }
        SceneBlock scene = SceneBlocks.of(level.getBlockState(target));
        SceneKind kind = scene == null ? null : scene.kindAt().at(level, target);
        if (kind == null || ScenePrefs.of(player, kind) != Trigger.USE || held.is(CineTags.SCENE_BYPASS) || scene.bypass().test(held)) {
            return false;
        }
        boolean machine = !target.equals(clicked);
        return machine ? !(held.getItem() instanceof BlockItem) : scene.usedOn().test(level.getBlockState(target), hit.getDirection());
    }

    /** A deployer's (or another mod's) fake player: never a scene, and CI's handler must not see it (it reads the player's connection). */
    public static boolean automated(Player player) {
        return player instanceof FakePlayer || player instanceof ServerPlayer server && (server.connection == null || server.connection.getConnection().channel() == null);
    }

    private static boolean probing, probePassed;

    /**
     * Whether spawn protection and claim mods let the player use the block. Claims refuse a right-click, so one is posted
     * and {@link #probe} stops it at HIGH priority, before Create or CI could act on it (claims at HIGHEST and HIGH decide).
     */
    public static boolean allowed(ServerPlayer player, BlockPos pos, Direction face) {
        if (!player.serverLevel().mayInteract(player, pos)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, pos, false);
        probing = true;
        probePassed = false;
        try {
            CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
            return probePassed;
        } finally {
            probing = false;
        }
    }

    public static void probe(PlayerInteractEvent.RightClickBlock event) {
        if (probing) {
            probePassed = event.getUseBlock() != TriState.FALSE;
            event.setCanceled(true);
        }
    }

    /** True while {@link #allowed} asks: our handlers and CI's let it pass untouched. */
    public static boolean probing() {
        return probing;
    }

    public static boolean beyondScenes(Player player, BlockPos pos) {
        return player.position().distanceToSqr(Vec3.atCenterOf(pos)) > SCENE_REACH * SCENE_REACH;
    }

    /**
     * Whether Create's own input takes this click: one of the block's value boxes (a stack size, the valve's angle, a
     * filter...) or a redstone-link frequency slot. Decided as Create's {@code ValueSettingsInputHandler} and
     * {@code LinkHandler} decide it, on either side, so the box gets the click whatever order the handlers run in.
     */
    public static boolean onValueBox(Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit == null || !ValueSettingsInputHandler.canInteract(player) || !(level.getBlockEntity(pos) instanceof SmartBlockEntity be)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        for (BlockEntityBehaviour behaviour : be.getAllBehaviours()) {
            if (behaviour instanceof LinkBehaviour link) {
                if (!AllItems.WRENCH.isIn(held) && (link.testHit(true, hit.getLocation()) || link.testHit(false, hit.getLocation()))) {
                    return true;
                }
                continue;
            }
            if (!(behaviour instanceof ValueSettingsBehaviour value) || value.bypassesInput(held) || !value.mayInteract(player)) {
                continue;
            }
            if (behaviour instanceof SidedFilteringBehaviour sided && sided.get(hit.getDirection()) == null) {
                continue;
            }
            if (!value.isActive() || value.onlyVisibleWithWrench() && !held.is(Tags.Items.TOOLS_WRENCH)) {
                continue;
            }
            if (value.getSlotPositioning() instanceof ValueBoxTransform.Sided sided) {
                // как у Create: коробка той стороны, по которой кликнули, и только если на этой стороне она есть
                // (у вентиля testHit сторону не проверяет)
                sided.fromSide(hit.getDirection());
                if (!sided.shouldRender(level, pos, be.getBlockState())) {
                    continue;
                }
            }
            if (value.testHit(hit.getLocation())) {
                return true;
            }
        }
        return false;
    }
}
