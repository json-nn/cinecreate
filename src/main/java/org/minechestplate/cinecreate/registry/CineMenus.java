package org.minechestplate.cinecreate.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.minechestplate.cinecreate.CineCreate;
import org.minechestplate.cinecreate.bridge.BasinMenu;
import org.minechestplate.cinecreate.bridge.BurnerMenu;
import org.minechestplate.cinecreate.bridge.CrafterMenu;
import org.minechestplate.cinecreate.bridge.CrankMenu;
import org.minechestplate.cinecreate.bridge.DeployerMenu;
import org.minechestplate.cinecreate.bridge.DepotMenu;
import org.minechestplate.cinecreate.bridge.DrainMenu;
import org.minechestplate.cinecreate.bridge.MillstoneMenu;
import org.minechestplate.cinecreate.bridge.SawMenu;
import org.minechestplate.cinecreate.bridge.ValveMenu;

// Меню-мосты: окна у них нет, сцена Cinematic Interactions работает поверх них
public final class CineMenus {
    private CineMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CineCreate.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<DepotMenu>> DEPOT =
            MENUS.register("depot", () -> IMenuTypeExtension.create(DepotMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<ValveMenu>> VALVE =
            MENUS.register("valve_handle", () -> IMenuTypeExtension.create(ValveMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<CrankMenu>> CRANK =
            MENUS.register("hand_crank", () -> IMenuTypeExtension.create(CrankMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<BasinMenu>> BASIN =
            MENUS.register("basin", () -> IMenuTypeExtension.create(BasinMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<BurnerMenu>> BURNER =
            MENUS.register("blaze_burner", () -> IMenuTypeExtension.create(BurnerMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<DrainMenu>> DRAIN =
            MENUS.register("item_drain", () -> IMenuTypeExtension.create(DrainMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<DeployerMenu>> DEPLOYER =
            MENUS.register("deployer", () -> IMenuTypeExtension.create(DeployerMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<MillstoneMenu>> MILLSTONE =
            MENUS.register("millstone", () -> IMenuTypeExtension.create(MillstoneMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<SawMenu>> SAW =
            MENUS.register("mechanical_saw", () -> IMenuTypeExtension.create(SawMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<CrafterMenu>> CRAFTER =
            MENUS.register("mechanical_crafter", () -> IMenuTypeExtension.create(CrafterMenu::fromNetwork));
}
