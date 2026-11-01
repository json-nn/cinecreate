package org.minechestplate.cinecreate;

import dev.dyslexic.cinematic.api.client.AddonScenes;
import dev.dyslexic.cinematic.client.ClientStation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
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
import org.minechestplate.cinecreate.client.BridgeScreen;
import org.minechestplate.cinecreate.client.ClientConfig;
import org.minechestplate.cinecreate.client.ClientEvents;
import org.minechestplate.cinecreate.client.ClientMode;
import org.minechestplate.cinecreate.client.WorkKey;
import org.minechestplate.cinecreate.client.scene.BasinScene;
import org.minechestplate.cinecreate.client.scene.BurnerScene;
import org.minechestplate.cinecreate.client.scene.ClientModeStation;
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
import org.minechestplate.cinecreate.registry.CineMenus;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;
import org.minechestplate.cinecreate.station.SceneBlock;
import org.minechestplate.cinecreate.station.SceneBlocks;

// Не загружается на выделенном сервере: только здесь можно трогать клиентские классы (AddonScenes, сцены).
@Mod(value = CineCreate.MOD_ID, dist = Dist.CLIENT)
public class CineCreateClient {

    public CineCreateClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // общий код (хук 3, рабочие стороны станции) спрашивает о выборе своего игрока здесь
        ScenePrefs.useOnClient(ClientConfig::trigger, () -> Minecraft.getInstance().isSameThread());
        Bridge.serverRunsScenes = ClientMode::serverHasAddon;
        // модели захвата (рукоять Create) загружаются вместе с остальными, если созданы до этого
        Grip.loadModels();
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::registerLayers);
        modEventBus.addListener(ClientConfig::onReload);
        ClientEvents.register(NeoForge.EVENT_BUS);
        // по имени: тестового класса нет ни в публичном jar, ни в исходниках в git (только у разработчика)
        if (Boolean.getBoolean("cinecreate.smoke")) {
            try {
                Class.forName("org.minechestplate.cinecreate.client.dev.SmokeTest").getMethod("register", IEventBus.class)
                        .invoke(null, NeoForge.EVENT_BUS);
            } catch (ReflectiveOperationException e) {
                CineCreate.LOGGER.error("[cinecreate] the smoke test was asked for, but this build has none", e);
            }
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // после общей настройки: сцена регистрируется только для станции, которую уже принял CI
        event.enqueueWork(() -> {
            scene(SceneBlocks.DEPOT, (pos, state, player, side) -> depotScene(player.level(), pos, side));
            for (SceneBlock valve : SceneBlocks.VALVES) {
                scene(valve, (pos, state, player, side) -> new ValveScene(pos, side));
            }
            scene(SceneBlocks.BASIN, (pos, state, player, side) -> new BasinScene(pos, side));
            scene(SceneBlocks.BURNER, (pos, state, player, side) -> new BurnerScene(pos, side));
            scene(SceneBlocks.CRANK, (pos, state, player, side) -> new CrankScene(pos, side));
            scene(SceneBlocks.CRAFTER, (pos, state, player, side) -> new CrafterScene(pos, side));
            scene(SceneBlocks.DRAIN, (pos, state, player, side) -> new DrainScene(pos, side));
            scene(SceneBlocks.DEPLOYER, (pos, state, player, side) -> new DeployerScene(pos, side));
            scene(SceneBlocks.MILLSTONE, (pos, state, player, side) -> new MillstoneScene(pos, side));
            scene(SceneBlocks.SAW, (pos, state, player, side) -> new SawScene(pos, side));
            scene(SceneBlocks.EJECTOR, (pos, state, player, side) -> new EjectorScene(pos, side));
        });
    }

    /** The block's scene, once CI has taken its station; on a server without the add-on, the client-mode scene. */
    private static void scene(SceneBlock block, AddonScenes.Factory factory) {
        if (SceneBlocks.registered(block)) {
            AddonScenes.register(block.sceneId(), (pos, state, player, side) -> ClientMode.active()
                    ? new ClientModeStation(pos, side, state) : factory.create(pos, state, player, side));
        }
    }

    /** A depot plays the scene of the machine working it (press, spout, deployer, fan's stream), chosen as it begins. */
    private static ClientStation depotScene(Level level, BlockPos pos, Direction side) {
        SceneKind kind = SceneBlocks.kindAt(level, pos);
        if (kind == SceneKinds.PRESSING) {
            return new PressScene(pos, side);
        }
        if (kind == SceneKinds.SPOUTING) {
            return new SpoutScene(pos, side);
        }
        if (kind == SceneKinds.DEPLOYING) {
            return new DeployingScene(pos, side);
        }
        return kind == SceneKinds.FAN ? new FanScene(pos, side) : new DepotScene(pos, side);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.<DepotMenu, BridgeScreen<DepotMenu>>register(CineMenus.DEPOT.get(), BridgeScreen::new);
        event.<ValveMenu, BridgeScreen<ValveMenu>>register(CineMenus.VALVE.get(), BridgeScreen::new);
        event.<CrafterMenu, BridgeScreen<CrafterMenu>>register(CineMenus.CRAFTER.get(), BridgeScreen::new);
        event.<CrankMenu, BridgeScreen<CrankMenu>>register(CineMenus.CRANK.get(), BridgeScreen::new);
        event.<BasinMenu, BridgeScreen<BasinMenu>>register(CineMenus.BASIN.get(), BridgeScreen::new);
        event.<BurnerMenu, BridgeScreen<BurnerMenu>>register(CineMenus.BURNER.get(), BridgeScreen::new);
        event.<DrainMenu, BridgeScreen<DrainMenu>>register(CineMenus.DRAIN.get(), BridgeScreen::new);
        event.<DeployerMenu, BridgeScreen<DeployerMenu>>register(CineMenus.DEPLOYER.get(), BridgeScreen::new);
        event.<MillstoneMenu, BridgeScreen<MillstoneMenu>>register(CineMenus.MILLSTONE.get(), BridgeScreen::new);
        event.<SawMenu, BridgeScreen<SawMenu>>register(CineMenus.SAW.get(), BridgeScreen::new);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(WorkKey.WORK);
    }

    private void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, CineCreate.id("invitation"), WorkKey::render);
    }
}
