package sscextras.mixin;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.RuneboundGuardian;

@Mixin(HungerManager.class)
public abstract class GuardianHungerMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void sscExtras$energyInsteadOfHunger(PlayerEntity player, CallbackInfo ci) {
        if (RuneboundGuardian.construct(player)) ci.cancel();
    }
}
