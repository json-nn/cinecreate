package org.minechestplate.cinecreate.client;

import dev.dyslexic.cinematic.client.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.minechestplate.cinecreate.station.SceneBlocks;

/** Game-bus listeners of the client. */
public final class ClientEvents {
    private ClientEvents() {}

    public static void register(IEventBus bus) {
        // раньше всех: сцены, которые CI готовит к кадру, видят уже его номер
        bus.addListener(EventPriority.HIGHEST, FrameCounter::onFrame);
        bus.addListener(WorkKey::onTick);
        // при входе на сервер он узнаёт, как этот игрок начинает сцены
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> ClientConfig.sendToServer());
        // после выбора цели в тике (pick), но раньше Create
        bus.addListener(EventPriority.HIGH, ClientEvents::noTargetInScenes);
    }

    /**
     * While one of our scenes plays, the player's crosshair (hidden by the scene) points at nothing for the tick, so
     * Create draws none of its crosshair overlays over the work: the valve's own angle box would sit on the hub next
     * to the scene's readout, showing the old value. (The tick picks the target between {@code ClientTickEvent.Pre}
     * and {@code Post}, and Create reads it in its {@code Post}; the frame's pick restores it before anything is drawn.)
     */
    private static void noTargetInScenes(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Scene.active() || mc.level == null || mc.player == null
                || SceneBlocks.of(mc.level.getBlockState(Scene.station.pos)) == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        mc.hitResult = BlockHitResult.miss(eye, Direction.UP, BlockPos.containing(eye));
    }
}
