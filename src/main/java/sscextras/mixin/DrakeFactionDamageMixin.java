package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeFaction;

@Mixin(LivingEntity.class)
public abstract class DrakeFactionDamageMixin {
    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void sscExtras$dropFriendlyTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof PlayerEntity player && DrakeFaction.member((LivingEntity)(Object)this)
                && DrakeFaction.friendly(player)) cir.setReturnValue(false);
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sscExtras$boundFriendlyFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (DrakeFaction.blocksDamage((LivingEntity)(Object)this, source)) cir.setReturnValue(false);
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void sscExtras$neutralRetaliation(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) DrakeFaction.damaged((LivingEntity)(Object)this, source);
    }
}
