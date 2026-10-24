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
     * The scene's menu, instead of {@code MenuActions.open}: the bridge menu, opened with the block position (and what
     * else the menu needs, {@link BridgeMenu.ExtraData}) as extra data so client and server build the same slots.
     */
    public static boolean open(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
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
     * No scene here (the work out of reach, no room, the scene turned off): Create's own right-click happens instead,
     * as if the player had clicked the block's working face. Replaces {@code Sessions.openWindow}, which would open a
     * window these blocks do not have.
     */
    public static void vanillaUse(ServerPlayer player, BlockPos pos) {
        if (Sessions.get(player) != null) {
            Sessions.end(player, false);
        }
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        SceneBlock scene = SceneBlocks.of(state);
        if (scene == null || player.isSpectator() || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) {
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
     * Create's own right-click on a face of a block, done with what the scene's cursor carries instead of the held item
     * (a bucket poured into a basin, fuel fed to a burner, flint and steel struck at one). The carried stack is in the
     * main hand for the call; what Create leaves in the hand goes back to the cursor, and the hand gets its own item back.
     */
    public static ItemInteractionResult useWithCarried(ServerPlayer player, AbstractContainerMenu menu, BlockPos pos, Direction face) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        ItemStack own = player.getMainHandItem();
        ItemStack carried = menu.getCarried();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)),
                face, pos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, carried);
        try {
            return state.useItemOn(carried, level, player, InteractionHand.MAIN_HAND, hit);
        } finally {
            menu.setCarried(player.getMainHandItem());
            player.setItemInHand(InteractionHand.MAIN_HAND, own);
        }
    }

    /**
     * Clicks Cinematic Interactions must leave alone (decided the same way on both sides): one of Create's value boxes
     * ({@link #onValueBox}: a filter, a stack size, the valve's angle...), the player starts this scene on the key or
     * not at all ({@link ScenePrefs}), an item with its own Create interaction (the wrench, goggles, filters..., and per
     * block: a dye on a valve), a face of the block that is not worked (a block placed against a depot's side), or a
     * block that has no scene where it stands (a crafter group bigger than 5×5).
     */
    public static boolean leaveToCreate(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        SceneBlock scene = SceneBlocks.of(state);
        if (scene == null) {
            return false;
        }
        if (onValueBox(event.getLevel(), event.getPos(), event.getEntity(), event.getHitVec())) {
            return true;
        }
        // KEY и OFF: правый клик целиком за Create (клиент смотрит в свой конфиг, сервер — в присланный им);
        // и там, где сцены нет вовсе (группа крафтеров больше 5×5)
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
     * that block's scene, the way Cinematic Interactions starts its own stations (cancelled on both sides, the client
     * begins the scene); CI's own handler would not, since the machine is not one of its stations. A block in hand is
     * still placed against the machine (a shaft against the press), and the machine's own value boxes (a deployer's
     * filter) stay Create's. Everything on our blocks themselves goes through CI's own handler and {@link #leaveToCreate}.
     */
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getUseBlock() == TriState.FALSE
                || player.isSecondaryUseActive() || player.isSpectator()) {
            return;
        }
        BlockPos clicked = event.getPos();
        ItemStack held = event.getItemStack();
        BlockPos target = Composites.workedBy(level, clicked);
        if (target == null || held.getItem() instanceof BlockItem || onValueBox(level, clicked, player, event.getHitVec())) {
            return;
        }
        BlockState state = level.getBlockState(target);
        SceneBlock scene = SceneBlocks.of(state);
        SceneKind kind = scene == null ? null : scene.kindAt().at(level, target);
        if (kind == null || held.is(CineTags.SCENE_BYPASS) || scene.bypass().test(held)
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
     * Whether Create's own input takes this click: it lands on one of the block's value boxes (any value setting: a
     * stack size, a speed, the valve's angle; a filter, also a sided one) or on a redstone-link frequency slot. Decided
     * as Create's {@code ValueSettingsInputHandler} and {@code LinkHandler} decide it, on either side, so the box gets
     * the click whatever the order the handlers run in, on every block, ours or a machine working one of ours.
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
