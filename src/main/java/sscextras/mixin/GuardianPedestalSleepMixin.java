package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.*;

@Mixin(LivingEntity.class)
public abstract class GuardianPedestalSleepMixin {
    @Inject(method = "isSleepingInBed", at = @At("HEAD"), cancellable = true)
    private void sscExtras$pedestalIsBed(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof PlayerEntity player && GuardianSleep.statue(player)) {
            var pos = player.getSleepingPosition().orElse(null);
            cir.setReturnValue(pos != null && GuardianPedestal.intact(player.getWorld(), pos));
        }
    }
    @Inject(method = "setPositionInBed", at = @At("HEAD"), cancellable = true)
    private void sscExtras$pedestalPosition(BlockPos pos, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEntity player && RuneboundGuardian.state(player).pedestalSleeping
                && GuardianPedestal.intact(player.getWorld(), pos)) {
            player.setPosition(GuardianPedestal.position(pos)); ci.cancel();
        }
    }
}
