package org.minechestplate.cinecreate.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.minechestplate.cinecreate.bridge.BasinMenu;
import org.minechestplate.cinecreate.bridge.BridgeMenu;
import org.minechestplate.cinecreate.bridge.BurnerMenu;
import org.minechestplate.cinecreate.bridge.CrafterMenu;
import org.minechestplate.cinecreate.bridge.CrankMenu;
import org.minechestplate.cinecreate.bridge.DeployerMenu;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.bridge.DrainMenu;
import org.minechestplate.cinecreate.bridge.MillstoneMenu;
import org.minechestplate.cinecreate.bridge.SawMenu;
import org.minechestplate.cinecreate.bridge.ValveMenu;
import org.minechestplate.cinecreate.client.scene.DeployingScene;
import org.minechestplate.cinecreate.client.scene.Grip;
import org.minechestplate.cinecreate.station.Composites;

/**
 * Client mode's stand-in for the bridge menus' server side (DESIGN §8.6): each click of a client-mode scene is played
 * out as what a server without the add-on understands; where the client guessed wrong, the server's answer puts it
 * right.
 */
public final class ClientModeActions {
    private ClientModeActions() {}

    private enum Hand { ALL, ONE, EMPTY }

    /** Half a dropped item's width, its height; how sure a drop must be to land in, for a scene to begin. */
    static final double ITEM_HALF = 0.125, ITEM_HEIGHT = 0.25, TOSS_SURE = 0.85;
    /** The game's random kick to a dropped item: up (triangular) and along the throw. */
    private static final double[] UP_KICK = {-0.08, -0.06, -0.04, -0.02, 0, 0.02, 0.04, 0.06, 0.08};
    private static final double[] UP_WEIGHT = {1, 2, 3, 4, 5, 4, 3, 2, 1};
    private static final double[] ALONG_KICK = {-0.016, -0.008, 0, 0.008, 0.016};

    /**
     * A drop's target: the block's foot, and a slice through its middle as {from, to, bottom, top} pixels of Create's
     * item shapes (a basin's walls are 13 px for an item).
     */
    private record Opening(Vec3 middle, int[][] boxes) {}

    private static final int[][] BASIN = {{-8, -6, 2, 13}, {6, 8, 2, 13}, {-6, 6, 0, 5}},
            MILLSTONE = {{-8, 8, 0, 6}, {-6, 6, 6, 16}}, SAW = {{-8, 8, 0, 12}}, DRAIN = {{-8, 8, 0, 13}};

    /** Create's take empties every slot at once, and a scene clicks each: repeats within 5 ticks are dropped. */
    @Nullable
    private static BlockPos tookAt;
    private static long tookTick = -10;

    /** Create refuses a valve for 4 ticks after a turn: a turn asked for sooner is held. */
    private static final int VALVE_REST = 6;
    @Nullable
    private static Runnable heldTurn;
    private static long valveMoved = -100;

    /** The last answer of {@link #reaches}: scenes ask every frame. */
    @Nullable
    private static BlockPos askedAt;
    @Nullable
    private static Direction askedFace;
    private static ItemStack askedWith = ItemStack.EMPTY;
    private static boolean askedAnswer;

    /** The last answer of {@link #lands}, for the block and the feet it was worked out for. */
    @Nullable
    private static BlockPos landsAt;
    private static Vec3 landsFrom = Vec3.ZERO;
    private static boolean landsAnswer;

    /**
     * Whether a client-mode scene can do its work from where the player stands: a drop must land in at least {@link
     * #TOSS_SURE} of the time, and a saw takes one only while it turns. With an Extendo Grip in reach Create's own clicks
     * still work (a bucket, a bottle, taking out), so the scene plays and refuses only drops; not at a block above the hand.
     */
    public static boolean workable(BlockState state, BlockPos pos, LocalPlayer player) {
        Opening opening = opening(state, pos);
        if (opening == null) {
            return true;
        }
        if (AllBlocks.MECHANICAL_SAW.has(state) && !running(pos)) {
            return false;
        }
        return lands(pos, opening, player) || Grip.reaches(player.position(), pos) && !above(opening, player);
    }

    private static boolean lands(BlockPos pos, Opening opening, LocalPlayer player) {
        if (!pos.equals(landsAt) || !player.position().equals(landsFrom)) {
            landsAt = pos.immutable();
            landsFrom = player.position();
            landsAnswer = aim(player.position(), player.getEyeHeight(), opening)[2] >= TOSS_SURE;
        }
        return landsAnswer;
    }

    private static boolean above(Opening opening, LocalPlayer player) {
        int top = 0;
        for (int[] box : opening.boxes()) {
            top = Math.max(top, box[3]);
        }
        return opening.middle().y + top / 16.0 > player.getEyeY() - 0.3;
    }

    private static boolean running(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof SawBlockEntity saw && saw.getSpeed() != 0;
    }

    /**
     * Whether a client-mode scene may put this into the slot: Create's click must reach the block, and a saw must turn.
     */
    public static boolean takes(AbstractContainerMenu menu, int slot, ItemStack stack) {
        return refusal(menu, slot, stack) == null;
    }

    /** Why a client-mode scene refuses to put this into the slot (a notice's key), or null. */
    @Nullable
    public static String refusal(AbstractContainerMenu menu, int slot, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(menu instanceof BridgeMenu bridge) || slot >= bridge.machineSlots() || stack.isEmpty() || mc.level == null || mc.player == null) {
            return null;
        }
        if (bridge instanceof SawMenu && slot == SawMenu.TABLE && !running(bridge.pos())) {
            return "cinecreate.notice.client_mode.saw_still";
        }
        if (dropped(bridge, slot)) {
            Opening opening = opening(mc.level.getBlockState(bridge.pos()), bridge.pos());
            return opening == null || lands(bridge.pos(), opening, mc.player) ? null : "cinecreate.notice.client_mode.drop_far";
        }
        BlockHitResult click = putClick(bridge, slot);
        return click == null || reaches(click, stack) ? null : "cinecreate.notice.client_mode.places";
    }

    /** What Create takes only by falling in. */
    private static boolean dropped(BridgeMenu menu, int slot) {
        return menu instanceof BasinMenu && slot < BasinMenu.INPUTS || menu instanceof MillstoneMenu && slot == MillstoneMenu.MOUTH
                || menu instanceof SawMenu && slot == SawMenu.TABLE || menu instanceof DrainMenu && slot == DrainMenu.MAIN;
    }

    public static boolean handsOver(BlockPos deployer, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) {
            return true;
        }
        Direction face = facing(mc.level.getBlockState(deployer));
        return reaches(new BlockHitResult(front(deployer, face), face, deployer, false), stack);
    }

    /**
     * Whether Create's right-click with this item reaches the block: some items place themselves instead (Create's
     * funnels, display links, super glue). Asked of the game's own event, the item put in hand for the moment.
     */
    private static boolean reaches(BlockHitResult click, ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return true;
        }
        if (click.getBlockPos().equals(askedAt) && click.getDirection() == askedFace && ItemStack.isSameItemSameComponents(stack, askedWith)) {
            return askedAnswer;
        }
        Inventory inventory = player.getInventory();
        ItemStack hand = inventory.getItem(inventory.selected);
        inventory.setItem(inventory.selected, stack.copy());
        try {
            PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, click.getBlockPos(), click);
            askedAnswer = !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
        } finally {
            inventory.setItem(inventory.selected, hand);
        }
        askedAt = click.getBlockPos().immutable();
        askedFace = click.getDirection();
        askedWith = stack.copy();
        return askedAnswer;
    }

    /** Where the put's click goes; null for a drop. */
    @Nullable
    private static BlockHitResult putClick(BridgeMenu menu, int slot) {
        BlockPos pos = menu.pos();
        if (menu instanceof DepotMenu && slot == DepotMenu.MAIN) {
            return new BlockHitResult(top(pos), Direction.UP, pos, false);
        }
        if (menu instanceof CrafterMenu crafter && slot < crafter.group().cells().size()) {
            BlockPos cell = crafter.group().cells().get(slot);
            return new BlockHitResult(crafter.group().face(cell), crafter.group().facing(), cell, false);
        }
        return null;
    }

    @Nullable
    private static Opening opening(BlockState state, BlockPos pos) {
        Vec3 middle = Vec3.atBottomCenterOf(pos);
        if (AllBlocks.BASIN.has(state)) {
            return new Opening(middle, BASIN);
        }
        if (AllBlocks.MILLSTONE.has(state)) {
            return new Opening(middle, MILLSTONE);
        }
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            return new Opening(middle, DRAIN);
        }
        return AllBlocks.MECHANICAL_SAW.has(state) ? new Opening(middle, SAW) : null;
    }

    /** Sends a held valve turn once Create's rest is over. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        BridgeMenu menu = ClientMode.menu();
        if (!(menu instanceof ValveMenu) || mc.level == null) {
            heldTurn = null;
            return;
        }
        if (mc.level.getBlockEntity(menu.pos()) instanceof ValveHandleBlockEntity valve && valve.inUse > 0) {
            valveMoved = mc.level.getGameTime();
        } else if (heldTurn != null && mc.level.getGameTime() - valveMoved > VALVE_REST) {
            Runnable turn = heldTurn;
            heldTurn = null;
            turn.run();
        }
    }

    // ---- the menu's clicks ---------------------------------------------------------------------------

    public static void click(int slot, int button, ClickType type) {
        BridgeMenu menu = ClientMode.menu();
        if (menu == null || slot < 0 || slot >= menu.slots.size()) {
            return;
        }
        if (slot >= menu.machineSlots()) {
            int index = inventoryMenuIndex(menu, slot);
            if (type != ClickType.QUICK_MOVE) {
                inventoryClick(index, button, type);
                return;
            }
            int input = firstInput(menu);
            if (input >= 0 && carried().isEmpty()) {
                inventoryClick(index, 0, ClickType.PICKUP);
                put(menu, input, 0);
                inventoryClick(index, 0, ClickType.PICKUP);
            }
            return;
        }
        if (type != ClickType.PICKUP && type != ClickType.QUICK_MOVE) {
            return;
        }
        if (carried().isEmpty()) {
            take(menu, slot);
        } else if (type == ClickType.PICKUP) {
            put(menu, slot, button);
        }
    }

    public static void button(int id) {
        BridgeMenu menu = ClientMode.menu();
        Minecraft mc = Minecraft.getInstance();
        if (menu == null || mc.level == null) {
            return;
        }
        BlockPos pos = menu.pos();
        if (menu instanceof BasinMenu && id == BasinMenu.USE) {
            if (carried().isEmpty()) {
                take(menu, 0);
            } else {
                inHand(Hand.ALL, true, held -> GenericItemEmptying.canItemBeEmptied(mc.level, held)
                        ? GenericItemEmptying.emptyItem(mc.level, held, true).getSecond() : held, () -> use(pos, Direction.UP, top(pos)));
            }
        } else if (menu instanceof BurnerMenu && id >= BurnerMenu.FEED && id < BurnerMenu.FEED + Direction.values().length) {
            Direction side = Direction.from3DDataValue(id - BurnerMenu.FEED);
            inHand(Hand.ALL, true, ClientModeActions::less, () -> use(pos, side, front(pos, side)));
        } else if (menu instanceof CrankMenu && (id == CrankMenu.TURN || id == CrankMenu.TURN_BACK)
                || menu instanceof ValveMenu && (id == ValveMenu.TURN || id == ValveMenu.TURN_BACK)) {
            Direction face = facing(mc.level.getBlockState(pos));
            boolean back = menu instanceof CrankMenu ? id == CrankMenu.TURN_BACK : id == ValveMenu.TURN_BACK;
            // на ободе: середина вентиля — поле значения Create, клик туда открыл бы настройку угла
            Vec3 rim = front(pos, face).add(Vec3.atLowerCornerOf((face.getAxis().isHorizontal() ? face.getClockWise() : Direction.EAST).getNormal()).scale(0.4));
            Runnable turn = () -> inHand(Hand.EMPTY, false, null, () -> sneaking(back, () -> use(pos, face, rim)));
            if (menu instanceof ValveMenu && mc.level.getGameTime() - valveMoved <= VALVE_REST) {
                heldTurn = turn;
            } else {
                turn.run();
            }
        } else if (menu instanceof ValveMenu) {
            angle(pos, id - ValveMenu.ANGLE - ValveMenu.MAX_ANGLE);
        } else if (menu instanceof DeployerMenu && id == DeployerMenu.SWAP) {
            swap(pos);
        } else if (menu instanceof DepotMenu && id == DepotMenu.DEPLOYER_SWAP) {
            DeployerBlockEntity deployer = Composites.deployerOver(mc.level, pos);
            if (deployer != null) {
                swap(deployer.getBlockPos());
            }
        } else if (menu instanceof CrafterMenu crafter) {
            boolean cover = id < CrafterMenu.UNCOVER;
            int cell = cover ? id - CrafterMenu.COVER : id - CrafterMenu.UNCOVER;
            if (cell >= 0 && cell < crafter.group().cells().size()) {
                BlockPos at = crafter.group().cells().get(cell);
                inHand(cover ? Hand.ALL : Hand.EMPTY, cover, ClientModeActions::less, () -> use(at, crafter.group().facing(), crafter.group().face(at)));
            }
        } else if (menu instanceof MillstoneMenu && id == MillstoneMenu.TAKE_ALL || menu instanceof SawMenu && id == SawMenu.TAKE_ALL) {
            take(menu, 0);
        } else if (menu instanceof SawMenu) {
            shape(pos, id);
        }
    }

    // ---- what each machine takes, and gives --------------------------------------------------------------

    private static void put(BridgeMenu menu, int slot, int button) {
        boolean one = button == 1;
        BlockPos pos = menu.pos();
        BlockHitResult click = putClick(menu, slot);
        if (!takes(menu, slot, carried())) {
            return;
        }
        if (menu instanceof DepotMenu && slot == DepotMenu.MAIN) {
            inHand(one ? Hand.ONE : Hand.ALL, true, held -> ItemStack.EMPTY, () -> use(click));
        } else if (dropped(menu, slot)) {
            Minecraft mc = Minecraft.getInstance();
            Opening opening = mc.level == null ? null : opening(mc.level.getBlockState(pos), pos);
            if (opening != null) {
                // сливу достаётся одна ёмкость из стопки, остальное легло бы на решётку
                toss(opening, one || menu instanceof DrainMenu && GenericItemEmptying.canItemBeEmptied(mc.level, carried()));
            }
        } else if (click != null) {
            inHand(Hand.ALL, true, ClientModeActions::less, () -> use(click));
        }
    }

    private static void take(BridgeMenu menu, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (menu instanceof CrafterMenu crafter) {
            if (slot < crafter.group().cells().size()) {
                BlockPos cell = crafter.group().cells().get(slot);
                inHand(Hand.EMPTY, false, null, () -> use(cell, crafter.group().facing(), crafter.group().face(cell)));
            }
            return;
        }
        BlockPos pos = menu.pos();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (pos.equals(tookAt) && now - tookTick < 5) {
            return;
        }
        tookAt = pos;
        tookTick = now;
        inHand(Hand.EMPTY, false, null, () -> use(pos, Direction.UP, top(pos)));
    }

    /** Create's click on the deployer's hand: the two swap what they hold. */
    private static void swap(BlockPos deployer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Direction face = facing(mc.level.getBlockState(deployer));
        ItemStack theirs = mc.level.getBlockEntity(deployer) instanceof DeployerBlockEntity be ? DeployingScene.held(be).copy() : ItemStack.EMPTY;
        if (!handsOver(deployer, carried())) {
            return;
        }
        inHand(carried().isEmpty() ? Hand.EMPTY : Hand.ALL, true, held -> theirs, () -> use(deployer, face, front(deployer, face)));
    }

    /** As Create's value box sets it: row 0 is the negative way. */
    private static void angle(BlockPos pos, int angle) {
        Minecraft mc = Minecraft.getInstance();
        if (angle == 0 || Math.abs(angle) > ValveMenu.MAX_ANGLE || mc.level == null
                || !(mc.level.getBlockEntity(pos) instanceof ValveHandleBlockEntity valve)) {
            return;
        }
        Direction face = facing(mc.level.getBlockState(pos));
        CatnipServices.NETWORK.sendToServer(new ValueSettingsPacket(pos, angle < 0 ? 0 : 1, Math.abs(angle), null,
                new BlockHitResult(front(pos, face), face, pos, false), face, false, valve.angleInput.netId()));
        valve.angleInput.setValue(angle);
    }

    /**
     * Create sets the saw's filter from the item in hand and an empty hand clears it; a shape not in the hotbar gets a
     * card.
     */
    private static void shape(BlockPos pos, int id) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.level.getBlockEntity(pos) instanceof SawBlockEntity saw)) {
            return;
        }
        FilteringBehaviour filter = BlockEntityBehaviour.get(saw, FilteringBehaviour.TYPE);
        if (filter == null) {
            return;
        }
        Runnable apply = () -> CatnipServices.NETWORK.sendToServer(new ValueSettingsPacket(pos, 0, 0, InteractionHand.MAIN_HAND,
                new BlockHitResult(top(pos), Direction.UP, pos, false), Direction.UP, false, filter.netId()));
        if (id == SawMenu.ANY_SHAPE) {
            inHand(Hand.EMPTY, false, null, apply);
            return;
        }
        List<ItemStack> shapes = SawMenu.shapes(mc.level, SawMenu.input(saw.inventory.getStackInSlot(0), carried()));
        int k = id - SawMenu.SHAPE;
        if (k < 0 || k >= shapes.size()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int h = 0; h < Inventory.getSelectionSize(); h++) {
            if (ItemStack.isSameItemSameComponents(inventory.getItem(h), shapes.get(k))) {
                int selected = inventory.selected;
                select(h);
                apply.run();
                select(selected);
                return;
            }
        }
        CiNotice.show(Component.translatable("cinecreate.notice.client_mode.shape", shapes.get(k).getHoverName()));
    }

    // ---- the hand ------------------------------------------------------------------------------------

    /**
     * The hand for one act: an empty hotbar slot (made by moving the selected item aside if needed) gets the scene's
     * hand and is selected; afterwards the rest goes back to the scene's hand and the slots are restored. {@code after}
     * guesses what Create leaves in the hand.
     */
    private static void inHand(Hand hand, boolean pickBack, @Nullable UnaryOperator<ItemStack> after, Runnable act) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        // захват — в левую руку на время дела: Create даёт его досягаемость, только пока он в руке
        int gripSlot = AllItems.EXTENDO_GRIP.isIn(inventory.getItem(selected)) && !AllItems.EXTENDO_GRIP.isIn(player.getOffhandItem())
                ? InventoryMenu.USE_ROW_SLOT_START + selected : -1;
        if (gripSlot >= 0) {
            inventoryClick(gripSlot, Inventory.SLOT_OFFHAND, ClickType.SWAP);
        }
        int free = inventory.getItem(selected).isEmpty() ? selected : -1;
        for (int h = 0; free < 0 && h < Inventory.getSelectionSize(); h++) {
            free = inventory.getItem(h).isEmpty() ? h : -1;
        }
        int aside = -1;
        if (free < 0) {
            for (int i = Inventory.getSelectionSize(); aside < 0 && i < Inventory.INVENTORY_SIZE; i++) {
                aside = inventory.getItem(i).isEmpty() ? i : -1;
            }
            if (aside < 0) {
                if (gripSlot >= 0) {
                    inventoryClick(gripSlot, Inventory.SLOT_OFFHAND, ClickType.SWAP);
                }
                CiNotice.show(Component.translatable("cinecreate.notice.client_mode.full"));
                return;
            }
            inventoryClick(aside, selected, ClickType.SWAP);
            free = selected;
        }
        int slot = InventoryMenu.USE_ROW_SLOT_START + free;
        boolean split = hand == Hand.ONE && carried().getCount() > 1;
        if (hand != Hand.EMPTY && !carried().isEmpty()) {
            inventoryClick(slot, hand == Hand.ONE ? 1 : 0, ClickType.PICKUP);
        }
        select(free);
        act.run();
        if (after != null) {
            inventory.setItem(free, after.apply(inventory.getItem(free).copy()));
        }
        if (pickBack) {
            // остаток собирается в руку и тогда, когда часть ещё в ней: сперва всё в ячейку, потом из неё
            if (split) {
                inventoryClick(slot, 0, ClickType.PICKUP);
            }
            inventoryClick(slot, 0, ClickType.PICKUP);
        }
        select(selected);
        if (aside >= 0) {
            inventoryClick(aside, free, ClickType.SWAP);
        }
        if (gripSlot >= 0) {
            inventoryClick(gripSlot, Inventory.SLOT_OFFHAND, ClickType.SWAP);
        }
    }

    /** Dropped as the drop key drops it, aimed to land in the block. */
    private static void toss(Opening opening, boolean one) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        inHand(Hand.ALL, true, null, () -> {
            float[] aim = aim(player.position(), player.getEyeHeight(), opening);
            player.connection.send(new ServerboundMovePlayerPacket.Rot(aim[0], aim[1], player.onGround()));
            player.drop(!one);
        });
    }

    /**
     * The best drop from these feet: yaw, pitch and the share of the game's random kicks that land in. The pitch is the
     * middle of the best window; across a corner the slice stretches by 1 / max(|cos|, |sin|).
     */
    static float[] aim(Vec3 feet, double eyeHeight, Opening opening) {
        double dx = opening.middle().x - feet.x, dz = opening.middle().z - feet.z, flat = Math.hypot(dx, dz);
        double from = feet.y + eyeHeight - 0.3 - opening.middle().y;
        double stretch = flat / Math.max(Math.max(Math.abs(dx), Math.abs(dz)), 1e-6);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double[] chance = new double[120];
        for (int i = 0; i < chance.length; i++) {
            double r = Math.toRadians(i - 30), in = 0, all = 0;
            for (int k = 0; k < UP_KICK.length; k++) {
                for (double along : ALONG_KICK) {
                    all += UP_WEIGHT[k];
                    in += lands(Math.cos(r) * 0.3 + along, -Math.sin(r) * 0.3 + 0.1 + UP_KICK[k], flat, from, opening.boxes(), stretch) ? UP_WEIGHT[k] : 0;
                }
            }
            chance[i] = in / all;
        }
        int best = 2;
        for (int i = 2; i < chance.length - 2; i++) {
            if (chance[i - 2] + 2 * chance[i] + chance[i + 2] > chance[best - 2] + 2 * chance[best] + chance[best + 2]) {
                best = i;
            }
        }
        return new float[] {yaw, best - 30, (float) chance[best]};
    }

    /**
     * Moved as Entity.collide moves it, down first and then along; in where it comes to rest with its middle over the
     * block (updateEntityAfterFallOn).
     */
    private static boolean lands(double along, double up, double flat, double y, int[][] boxes, double stretch) {
        double h = 0, half = ITEM_HALF * stretch, px = stretch / 16;
        for (int t = 0; t < 60 && y >= 0; t++) {
            up -= 0.04;
            double next = y + up;
            boolean landed = false;
            for (int[] box : boxes) {
                double top = box[3] / 16.0;
                if (h + half > flat + box[0] * px && h - half < flat + box[1] * px && y >= top - 1e-7 && next < top) {
                    next = top;
                    landed = true;
                }
            }
            y = next;
            double to = h + along;
            for (int[] box : boxes) {
                double near = flat + box[0] * px;
                if (y + ITEM_HEIGHT > box[2] / 16.0 && y < box[3] / 16.0 && h + half <= near + 1e-7 && to + half > near) {
                    to = near - half;
                    along = 0;
                }
            }
            h = to;
            if (landed) {
                return Math.abs(h - flat) < 0.5 * stretch;
            }
            along *= 0.98;
            up *= 0.98;
        }
        return false;
    }

    private static void sneaking(boolean on, Runnable act) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!on || player == null) {
            act.run();
            return;
        }
        boolean was = player.isShiftKeyDown();
        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        player.setShiftKeyDown(true);
        act.run();
        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        player.setShiftKeyDown(was);
    }

    private static void select(int hotbar) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getInventory().selected = hotbar;
            player.connection.send(new ServerboundSetCarriedItemPacket(hotbar));
        }
    }

    private static void use(BlockHitResult click) {
        use(click.getBlockPos(), click.getDirection(), click.getLocation());
    }

    private static void use(BlockPos pos, Direction face, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
        }
    }

    // ---- the inventory -------------------------------------------------------------------------------

    /**
     * A click on the player's own inventory menu, guessed here and sent as the game sends one: its cursor is the
     * scene's hand on both sides.
     */
    private static void inventoryClick(int index, int button, ClickType type) {
        LocalPlayer player = Minecraft.getInstance().player;
        BridgeMenu menu = ClientMode.menu();
        if (player == null || menu == null) {
            return;
        }
        InventoryMenu inventory = player.inventoryMenu;
        inventory.setCarried(menu.getCarried());
        List<ItemStack> before = new ArrayList<>(inventory.slots.size());
        for (int i = 0; i < inventory.slots.size(); i++) {
            before.add(inventory.getSlot(i).getItem().copy());
        }
        inventory.clicked(index, button, type, player);
        Int2ObjectMap<ItemStack> changed = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < inventory.slots.size(); i++) {
            ItemStack now = inventory.getSlot(i).getItem();
            if (!ItemStack.matches(before.get(i), now)) {
                changed.put(i, now.copy());
            }
        }
        player.connection.send(new ServerboundContainerClickPacket(inventory.containerId, inventory.getStateId(), index, button, type,
                inventory.getCarried().copy(), changed));
        menu.setCarried(inventory.getCarried());
    }

    /** The inventory menu's index of a bridge menu's player slot (main inventory 9–35, then the hotbar at 36–44). */
    private static int inventoryMenuIndex(BridgeMenu menu, int slot) {
        int k = slot - menu.machineSlots();
        return k < 27 ? k + Inventory.getSelectionSize() : InventoryMenu.USE_ROW_SLOT_START + k - 27;
    }

    /** Create takes one: a crafter's window, a burner's meal, a drain's container. */
    private static ItemStack less(ItemStack stack) {
        return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    private static ItemStack carried() {
        BridgeMenu menu = ClientMode.menu();
        return menu == null ? ItemStack.EMPTY : menu.getCarried();
    }

    /** A shift-click from the inventory goes to the machine's one input; a crafter wall has many, so nowhere. */
    private static int firstInput(BridgeMenu menu) {
        return menu instanceof CrafterMenu ? -1 : 0;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
    }

    private static Vec3 top(BlockPos pos) {
        return Vec3.atCenterOf(pos).add(0, 0.5, 0);
    }

    private static Vec3 front(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
    }
}
