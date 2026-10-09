package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.*;

@Mixin(LivingEntity.class)
public abstract class GuardianDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sscExtras$guardianImmunity(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (source.getAttacker() instanceof PlayerEntity attacker && GuardianEquipment.protects(attacker, (Entity)self)) {
            cir.setReturnValue(false); return;
        }
        if (self instanceof VillagerEntity && source.getAttacker() instanceof PlayerEntity attacker
                && RuneboundGuardian.stage(attacker) >= 1 || self instanceof PlayerEntity player && GuardianCombat.immune(player, source))
            cir.setReturnValue(false);
    }
    @WrapOperation(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
    private void sscExtras$guardianResistance(LivingEntity entity, DamageSource source, float amount, Operation<Void> original) {
        float adjusted = entity instanceof PlayerEntity player ? GuardianCombat.incoming(player, source, amount) : amount;
        if (adjusted > 0) original.call(entity, source, adjusted);
    }
    @Inject(method = "damage", at = @At("RETURN"))
    private void sscExtras$guardianWitness(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && (Object)this instanceof VillagerEntity villager) GuardianVillages.harmed(villager, source.getAttacker());
    }
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void sscExtras$absorbDebuff(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        Entity owner = source instanceof net.minecraft.entity.projectile.ProjectileEntity projectile ? projectile.getOwner()
                : source instanceof net.minecraft.entity.AreaEffectCloudEntity cloud ? cloud.getOwner() : source;
        if (!effect.getEffectType().isBeneficial() && owner instanceof PlayerEntity attacker
                && GuardianEquipment.protects(attacker, (Entity)(Object)this)) { cir.setReturnValue(false); return; }
        if ((Object)this instanceof PlayerEntity player && GuardianCombat.absorbEffect(player, effect)) cir.setReturnValue(false);
    }
    @ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StatusEffectInstance sscExtras$shorterDebuff(StatusEffectInstance effect) {
        return (Object)this instanceof PlayerEntity player ? GuardianCombat.shorten(player, effect) : effect;
    }
}
