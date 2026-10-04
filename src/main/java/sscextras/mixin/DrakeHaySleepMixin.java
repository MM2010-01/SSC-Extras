package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeHaySleep;

@Mixin(LivingEntity.class)
public abstract class DrakeHaySleepMixin {
    @Inject(method = "isSleepingInBed", at = @At("HEAD"), cancellable = true)
    private void sscExtras$hayIsBed(CallbackInfoReturnable<Boolean> cir) {
        var entity = (LivingEntity)(Object)this;
        if (DrakeHaySleep.isHayBed(entity, entity.getSleepingPosition().orElse(null))) cir.setReturnValue(true);
    }

    @Inject(method = "setPositionInBed", at = @At("HEAD"), cancellable = true)
    private void sscExtras$hayPosition(BlockPos pos, CallbackInfo ci) {
        var entity = (LivingEntity)(Object)this;
        if (DrakeHaySleep.isHayBed(entity, pos)) { entity.setPosition(DrakeHaySleep.position(pos)); ci.cancel(); }
    }

    @Inject(method = "getSleepingDirection", at = @At("HEAD"), cancellable = true)
    private void sscExtras$hayDirection(CallbackInfoReturnable<Direction> cir) {
        var entity = (LivingEntity)(Object)this;
        if (DrakeHaySleep.isHayBed(entity, entity.getSleepingPosition().orElse(null))) cir.setReturnValue(Direction.SOUTH);
    }
}
