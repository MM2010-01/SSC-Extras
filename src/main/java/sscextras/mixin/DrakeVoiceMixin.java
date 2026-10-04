package sscextras.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.EarthenDrake;

@Mixin(PlayerEntity.class)
public abstract class DrakeVoiceMixin {
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void sscExtras$drakeHurt(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (EarthenDrake.stage((PlayerEntity)(Object)this) == 3) cir.setReturnValue(SoundEvents.ENTITY_RAVAGER_HURT);
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void sscExtras$drakeDeath(CallbackInfoReturnable<SoundEvent> cir) {
        if (EarthenDrake.stage((PlayerEntity)(Object)this) == 3) cir.setReturnValue(SoundEvents.ENTITY_RAVAGER_DEATH);
    }
}
