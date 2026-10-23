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
    @Inject(method = "onStatusEffectRemoved", at = @At("TAIL"))
    private void petrificationEffectChanged(StatusEffectInstance effect, CallbackInfo ci) {
        if (effect.getEffectType() == Petrification.EFFECT) Petrification.state((LivingEntity)(Object)this).effectRemoved();
    }
    @Inject(method = "onStatusEffectApplied", at = @At("TAIL"))
    private void petrificationEffectApplied(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        if (effect.getEffectType() == Petrification.EFFECT) Petrification.state((LivingEntity)(Object)this).effectChanged();
    }
    @Inject(method = "onStatusEffectUpgraded", at = @At("TAIL"))
    private void petrificationEffectUpgraded(StatusEffectInstance effect, boolean reapply, Entity source, CallbackInfo ci) {
        if (reapply && effect.getEffectType() == Petrification.EFFECT) Petrification.state((LivingEntity)(Object)this).effectChanged();
    }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "tickStatusEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
    private void petrificationNoSwirls(net.minecraft.world.World world, net.minecraft.particle.ParticleEffect particle,
            double x, double y, double z, double vx, double vy, double vz,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!Petrification.stoneAppearance((LivingEntity)(Object)this)) original.call(world, particle, x, y, z, vx, vy, vz);
    }
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
