package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.petrification.PetrificationTargeting;

@Mixin(TargetPredicate.class)
public abstract class PetrificationTargetMixin {
    @Shadow @Final private boolean attackable;
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void petrificationNoNewAggro(LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (attackable && !PetrificationTargeting.allows(attacker, target)) cir.setReturnValue(false);
    }
}
