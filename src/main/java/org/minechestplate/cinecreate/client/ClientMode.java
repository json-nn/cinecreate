package org.minechestplate.cinecreate.client;

import dev.dyslexic.cinematic.api.client.AddonScenes;
import dev.dyslexic.cinematic.client.Reach;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.station.StationKind;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.bridge.BridgeMenu;
import org.minechestplate.cinecreate.bridge.ScenePrefsPayload;
import org.minechestplate.cinecreate.station.SceneBlock;
import org.minechestplate.cinecreate.station.SceneBlocks;

/**
 * Scenes on a server without the add-on (DESIGN §8.6), as CI plays its own there: the block's own scene, begun only
 * from where the player stands, on a copy of its menu that lives on the client. Taken when joining.
 */
public final class ClientMode {
    private ClientMode() {}

    /** The client-only menu's id: out of the server's range (it counts its menus 1–100), so a real menu never shares it. */
    public static final int MENU_ID = 120;

    private static boolean enabledForConnection;
    @Nullable
    private static BridgeMenu menu;

    static void onLogin() {
        enabledForConnection = ClientConfig.clientMode();
    }

    public static boolean serverHasAddon() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return !Bridge.devNoAddon && (connection == null || connection.hasChannel(ScenePrefsPayload.TYPE));
    }

    /** On for this connection, and the server lacks the add-on. */
    public static boolean active() {
        return enabledForConnection && !serverHasAddon();
    }

    @Nullable
    public static BridgeMenu menu() {
        LocalPlayer player = Minecraft.getInstance().player;
        return menu != null && player != null && player.containerMenu == menu ? menu : null;
    }

    public static boolean menuOpen(int containerId) {
        return containerId == MENU_ID && menu() != null;
    }

    /**
     * CI keeps a client-only scene while the menu it began with is open: the block's menu, built here on the client.
     */
    public static boolean begin(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active() || !idle(player) || mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        SceneBlock scene = SceneBlocks.of(state);
        if (scene == null || !ClientModeActions.workable(state, pos, player)) {
            return false;
        }
        return start(pos, scene);
    }

    private static boolean idle(@Nullable LocalPlayer player) {
        return player != null && Scene.phase == Scene.Phase.NONE && player.containerMenu == player.inventoryMenu
                && player.inventoryMenu.getCarried().isEmpty();
    }

    /**
     * Whether {@link #begin} would begin a scene here now, CI's plan included (on the side its private
     * ClientStation.freeSide picks): the plate shows only then.
     */
    public static boolean canBegin(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active() || !idle(player) || mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (SceneBlocks.of(state) == null || !ClientModeActions.workable(state, pos, player)) {
            return false;
        }
        Direction side = side(state, pos, player);
        return side != null && Reach.planHere(AddonScenes.create(pos, state, player, side), player).scene();
    }

    @Nullable
    private static Direction side(BlockState state, BlockPos pos, LocalPlayer player) {
        double dx = player.getX() - pos.getX() - 0.5, dz = player.getZ() - pos.getZ() - 0.5;
        List<Direction> sides = StationKind.ADDON.sides(state);
        Direction best = null;
        double most = 0;
        for (Direction side : sides) {
            double toward = side.getStepX() * dx + side.getStepZ() * dz;
            if (toward > most) {
                most = toward;
                best = side;
            }
        }
        if (best != null || sides.isEmpty()) {
            return best;
        }
        Direction facing = Direction.fromYRot(player.getYRot()).getOpposite();
        return sides.contains(facing) ? facing : sides.get(0);
    }

    private static boolean start(BlockPos pos, SceneBlock scene) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MenuProvider provider = scene.menu().create(mc.level, pos);
        if (provider == null || !(provider.createMenu(MENU_ID, player.getInventory(), player) instanceof BridgeMenu opened)) {
            return false;
        }
        menu = opened;
        player.containerMenu = opened;
        if (Scene.beginClientOnly(pos, StationKind.ADDON)) {
            return true;
        }
        player.containerMenu = player.inventoryMenu;
        menu = null;
        return false;
    }

    /** The server's close put the hand back in the inventory; the client's copy of it is cleared to match. */
    static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu != null && (player == null || player.containerMenu != menu)) {
            menu = null;
            if (player != null) {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        ClientModeActions.tick();
    }

    /** Taken on the key: a cancelled click is still sent, and the server would do Create's click at once. */
    static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && active() && mc.level != null && mc.player != null
                && mc.hitResult instanceof BlockHitResult hit && Bridge.clientModeStarts(mc.level, mc.player, hit)
                && begin(SceneBlocks.target(mc.level, hit.getBlockPos()))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Development (SmokeTest). */
    public static void force(boolean on) {
        enabledForConnection = on;
    }
}
