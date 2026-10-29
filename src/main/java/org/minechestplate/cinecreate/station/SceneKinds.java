package org.minechestplate.cinecreate.station;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.minechestplate.cinecreate.CineCreate;

/** Every kind of scene, each with its own trigger in the client config (DESIGN §7 has a section per kind). */
public final class SceneKinds {
    private SceneKinds() {}

    /** A bare depot: used dozens of times an hour, so the scene waits for the key and a right-click stays Create's. */
    public static final SceneKind DEPOT = new SceneKind(CineCreate.id("depot"), Trigger.KEY);
    /** A depot under a Mechanical Press. */
    public static final SceneKind PRESSING = new SceneKind(CineCreate.id("pressing"), Trigger.USE);
    /** Any of the 17 Valve Handles. */
    public static final SceneKind VALVE = new SceneKind(CineCreate.id("valve_handle"), Trigger.USE);
    public static final SceneKind CRAFTER = new SceneKind(CineCreate.id("mechanical_crafter"), Trigger.USE);

    public static final SceneKind CRANK = new SceneKind(CineCreate.id("hand_crank"), Trigger.USE);

    /** A Basin, bare or worked by a mixer or a press above. */
    public static final SceneKind BASIN = new SceneKind(CineCreate.id("basin"), Trigger.USE);
    public static final SceneKind BURNER = new SceneKind(CineCreate.id("blaze_burner"), Trigger.USE);

    /** A depot under a Spout. */
    public static final SceneKind SPOUTING = new SceneKind(CineCreate.id("spouting"), Trigger.USE);
    /** A depot under a Deployer facing down ("Application"). */
    public static final SceneKind DEPLOYING = new SceneKind(CineCreate.id("deploying"), Trigger.USE);
    /** A depot in a fan's washing, smoking, blasting or haunting stream. */
    public static final SceneKind FAN = new SceneKind(CineCreate.id("fan_processing"), Trigger.USE);
    public static final SceneKind DRAIN = new SceneKind(CineCreate.id("item_drain"), Trigger.USE);
    /** The hand-off at a Deployer's hand, any facing (one facing down over a depot plays DEPLOYING there). */
    public static final SceneKind DEPLOYER = new SceneKind(CineCreate.id("deployer"), Trigger.USE);
    public static final SceneKind MILLSTONE = new SceneKind(CineCreate.id("millstone"), Trigger.USE);
    /** A Mechanical Saw facing up, the stonecutter of Create. */
    public static final SceneKind SAW = new SceneKind(CineCreate.id("mechanical_saw"), Trigger.USE);
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
