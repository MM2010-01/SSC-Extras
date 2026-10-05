package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeRiding;
import sscextras.drake.StableDrakeEntity;

@Mixin(LivingEntity.class)
public abstract class DrakeRiderDamageMixin {
    @Unique private int sscExtras$headInBlockTicks;

    @Unique private static boolean sscExtras$ridingDrake(LivingEntity rider) {
        return rider.getVehicle() instanceof StableDrakeEntity
                || rider.getVehicle() instanceof PlayerEntity player && DrakeRiding.mountForm(player);
    }

    @Redirect(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInsideWall()Z"))
    private boolean sscExtras$riderSuffocationGrace(LivingEntity rider) {
        boolean insideWall = rider.isInsideWall();
        if (!insideWall || !sscExtras$ridingDrake(rider)) {
            sscExtras$headInBlockTicks = 0;
            return insideWall;
        }
        sscExtras$headInBlockTicks = Math.min(sscExtras$headInBlockTicks + 1, 61);
        return sscExtras$headInBlockTicks > 60;
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sscExtras$riderCollision(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.isOf(DamageTypes.FLY_INTO_WALL) && sscExtras$ridingDrake((LivingEntity)(Object)this))
            cir.setReturnValue(false);
    }
}
