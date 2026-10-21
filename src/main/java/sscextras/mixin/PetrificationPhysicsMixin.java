package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.*;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.StoneBodies;

@Mixin(LivingEntity.class)
public abstract class PetrificationPhysicsMixin {
    @ModifyVariable(method = "travel", at = @At("STORE"), ordinal = 0)
    private double petrificationGravity(double gravity) {
        return StoneBodies.physical((LivingEntity)(Object)this) ? gravity * 2 : gravity;
    }
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void petrificationNoSuffocation(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (StoneBodies.physical((LivingEntity)(Object)this) && (source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.IN_WALL)))
            cir.setReturnValue(false);
    }
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float petrificationFallDamage(float amount, DamageSource source, float originalAmount) {
        return StoneBodies.physical((LivingEntity)(Object)this) && source.isIn(DamageTypeTags.IS_FALL) ? amount * 1.5f : amount;
    }
    @Inject(method = "canBreatheInWater", at = @At("HEAD"), cancellable = true)
    private void petrificationNoBreathing(CallbackInfoReturnable<Boolean> cir) {
        if (StoneBodies.physical((LivingEntity)(Object)this)) cir.setReturnValue(true);
    }
    @WrapOperation(method = {"playHurtSound", "onDamaged"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHurtSound(Lnet/minecraft/entity/damage/DamageSource;)Lnet/minecraft/sound/SoundEvent;"))
    private SoundEvent petrificationHurtSound(LivingEntity entity, DamageSource source, Operation<SoundEvent> original) {
        return StoneBodies.sound(entity, original.call(entity, source));
    }
    @WrapOperation(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getDeathSound()Lnet/minecraft/sound/SoundEvent;"))
    private SoundEvent petrificationDeathSound(LivingEntity entity, Operation<SoundEvent> original) {
        return StoneBodies.sound(entity, original.call(entity));
    }
}
