package org.minechestplate.cinecreate.mixin;

import dev.dyslexic.cinematic.client.ClientStation;
import dev.dyslexic.cinematic.client.Reach;
import dev.dyslexic.cinematic.client.Scene;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import org.minechestplate.cinecreate.client.CiNotice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Хук 4 (клиент, необязательный): причина отказа в строке уведомлений CI, и при «логике высоты», где CI молчит
@Mixin(value = Scene.class, remap = false)
public abstract class SceneRefuseMixin {

    @Inject(method = "refuse", at = @At("TAIL"), require = 0)
    private static void cinecreate$notice(ClientStation at, @Nullable Reach.Reason reason, @Nullable Component blocker, CallbackInfo ci) {
        CiNotice.refused(at, reason, blocker);
    }
}
