package org.minechestplate.cinecreate;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.minechestplate.cinecreate.bridge.Bridge;
import org.minechestplate.cinecreate.bridge.ScenePrefs;
import org.minechestplate.cinecreate.registry.CineMenus;
import org.minechestplate.cinecreate.station.SceneBlocks;
import org.slf4j.Logger;

// Общая точка входа (клиент и сервер). Сцены Create регистрируются здесь через AddonStations.
@Mod(CineCreate.MOD_ID)
public class CineCreate {
    public static final String MOD_ID = "cinecreate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CineCreate(IEventBus modEventBus, ModContainer modContainer) {
        CineMenus.MENUS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ScenePrefs::registerPayload);
        NeoForge.EVENT_BUS.addListener(ScenePrefs::forget);
        // клики, сцену по которым начинаем мы сами (пресс над депо, ступица вентиля): раньше всех остальных
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Bridge::probe);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Bridge::onUse);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // до входа в мир, на обеих сторонах: блоки Create отдаются Cinematic Interactions
        event.enqueueWork(SceneBlocks::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
