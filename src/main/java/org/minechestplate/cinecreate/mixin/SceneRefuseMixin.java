package org.minechestplate.cinecreate.mixin;

import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Reach;
import dev.dyslexic.cinematic.client.Scene;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import org.minechestplate.cinecreate.client.Notices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Хук 4 (клиент, необязательный): CI отказал в сцене одного из наших блоков — карточка с причиной.
// CI с включённой «логикой высоты» в этом случае молча делает обычный клик Create; без хука игрок не узнает почему.
@Mixin(value = Scene.class, remap = false)
public abstract class SceneRefuseMixin {

    @Inject(method = "refuse", at = @At("HEAD"), require = 0)
    private static void cinecreate$notice(ClientStation at, @Nullable Reach.Reason reason, @Nullable Component blocker, CallbackInfo ci) {
        Notices.refused(at, reason, blocker);
    }
}
