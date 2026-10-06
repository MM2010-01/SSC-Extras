package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.FeralMobBrain;

@Mixin(TargetPredicate.class)
public abstract class FeralMobTargetMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void sscExtras$excludeOwnBody(LivingEntity base, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (base instanceof FeralMobBrain.Bridge brain && brain.sscExtras$feralPlayer() == target)
            cir.setReturnValue(false);
    }
}
