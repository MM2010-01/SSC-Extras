package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import sscextras.petrification.Petrification;

@Mixin(LivingEntity.class)
public abstract class PetrificationLivingMixin {
    @ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private StatusEffectInstance petrificationDose(StatusEffectInstance effect) {
        return effect.getEffectType() == Petrification.EFFECT ? Petrification.state((LivingEntity)(Object)this).dose(effect) : effect;
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void petrificationFacing(CallbackInfo ci) {
        var self = (LivingEntity)(Object)this;
        Petrification.state(self).updateLock();
        if (Petrification.frozen(self)) Petrification.state(self).hold();
    }
    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void petrificationNoAi(CallbackInfoReturnable<Boolean> cir) {
        if (Petrification.frozen((LivingEntity)(Object)this)) cir.setReturnValue(true);
    }
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void petrificationNoJump(CallbackInfo ci) {
        if (Petrification.frozen((LivingEntity)(Object)this)) ci.cancel();
    }
    @Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
    private void petrificationStoneLoot(net.minecraft.entity.damage.DamageSource source, boolean player, CallbackInfo ci) {
        if (Petrification.state((LivingEntity)(Object)this).shattering) ci.cancel();
    }
}
