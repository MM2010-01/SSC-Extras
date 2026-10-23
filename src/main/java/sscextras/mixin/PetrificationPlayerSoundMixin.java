package sscextras.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.petrification.Petrification;

@Mixin(ClientPlayerEntity.class)
public abstract class PetrificationPlayerSoundMixin {
    @Inject(method="playSound", at=@At("HEAD"), cancellable=true)
    private void petrificationSilence(CallbackInfo ci) {
        if (Petrification.poseLocked((ClientPlayerEntity)(Object)this)) ci.cancel();
    }
}
