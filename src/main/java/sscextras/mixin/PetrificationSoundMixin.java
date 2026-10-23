package sscextras.mixin;

import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.petrification.Petrification;

/** Native player sound overloads bypass Entity.playSound; silence both dispatch owners. */
@Mixin({Entity.class, PlayerEntity.class})
public abstract class PetrificationSoundMixin {
    @Inject(method="playSound", at=@At("HEAD"), cancellable=true)
    private void petrificationSilence(CallbackInfo ci) {
        if ((Object)this instanceof LivingEntity living && Petrification.poseLocked(living)) ci.cancel();
    }
}
