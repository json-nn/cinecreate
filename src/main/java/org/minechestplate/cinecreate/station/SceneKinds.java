package org.minechestplate.cinecreate.station;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.minechestplate.cinecreate.CineCreate;

/** Every kind of scene, each with its own trigger in the client config. */
public final class SceneKinds {
    private SceneKinds() {}

    /** A bare depot: used dozens of times an hour, so the scene waits for the key and a right-click stays Create's. */
    public static final SceneKind DEPOT = new SceneKind(CineCreate.id("depot"), Trigger.KEY);
    /** A depot under a Mechanical Press (DESIGN §7.2): the machine moment, so a right-click starts it. */
    public static final SceneKind PRESSING = new SceneKind(CineCreate.id("pressing"), Trigger.USE);
    /** Any of the 17 Valve Handles (DESIGN §7.4): turned now and then, so a right-click starts it. */
    public static final SceneKind VALVE = new SceneKind(CineCreate.id("valve_handle"), Trigger.USE);
    /** A group of Mechanical Crafters (DESIGN §7.3): filled for a recipe now and then, so a right-click starts it. */
    public static final SceneKind CRAFTER = new SceneKind(CineCreate.id("mechanical_crafter"), Trigger.USE);

    /** The Hand Crank (DESIGN §7.5): turned by hand for as long as the player likes, so a right-click starts it. */
    public static final SceneKind CRANK = new SceneKind(CineCreate.id("hand_crank"), Trigger.USE);

    /** A Basin, bare or worked by a mixer or a press above (DESIGN §7.6): filled for a recipe, so a right-click starts it. */
    public static final SceneKind BASIN = new SceneKind(CineCreate.id("basin"), Trigger.USE);
    /** A Blaze Burner (DESIGN §7.7): fed now and then, so a right-click on its cage starts it. */
    public static final SceneKind BURNER = new SceneKind(CineCreate.id("blaze_burner"), Trigger.USE);

    /** A depot under a Spout (DESIGN §7.13): the machine fills what is laid there, so a right-click starts it. */
    public static final SceneKind SPOUTING = new SceneKind(CineCreate.id("spouting"), Trigger.USE);
    /** A depot under a Deployer facing down (DESIGN §7.10, "Application"): a right-click starts it. */
    public static final SceneKind DEPLOYING = new SceneKind(CineCreate.id("deploying"), Trigger.USE);
    /** A depot in a fan's washing, smoking, blasting or haunting stream (DESIGN §7.11): a right-click starts it. */
    public static final SceneKind FAN = new SceneKind(CineCreate.id("fan_processing"), Trigger.USE);
    /** An Item Drain (DESIGN §7.13): a container is laid on it now and then, so a right-click starts it. */
    public static final SceneKind DRAIN = new SceneKind(CineCreate.id("item_drain"), Trigger.USE);
    /** A Deployer, sideways (DESIGN §7.10, the hand-off): a right-click on its hand starts it. */
    public static final SceneKind DEPLOYER = new SceneKind(CineCreate.id("deployer"), Trigger.USE);
    /** A Millstone (DESIGN §7.9): a right-click starts it. */
    public static final SceneKind MILLSTONE = new SceneKind(CineCreate.id("millstone"), Trigger.USE);
    /** A Mechanical Saw facing up (DESIGN §7.8), the stonecutter of Create: a right-click starts it. */
    public static final SceneKind SAW = new SceneKind(CineCreate.id("mechanical_saw"), Trigger.USE);
    /** A Weighted Ejector (DESIGN §7.12): a right-click on its plate starts it. */
    public static final SceneKind EJECTOR = new SceneKind(CineCreate.id("weighted_ejector"), Trigger.USE);

    private static final List<SceneKind> ALL = List.of(DEPOT, PRESSING, VALVE, CRAFTER, CRANK, BASIN, BURNER, SPOUTING, DEPLOYING,
            FAN, DRAIN, DEPLOYER, MILLSTONE, SAW, EJECTOR);

    public static List<SceneKind> all() {
        return ALL;
    }

    @Nullable
    public static SceneKind byId(ResourceLocation id) {
        for (SceneKind kind : ALL) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
