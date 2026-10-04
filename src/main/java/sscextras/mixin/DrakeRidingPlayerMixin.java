package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.*;

@Mixin(PlayerEntity.class)
public abstract class DrakeRidingPlayerMixin {
    @Unique private boolean sscExtras$ejectReady;
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sscExtras$boundPillagerAttack(Entity target, CallbackInfo ci) {
        if (DrakeFaction.blocksAttack((PlayerEntity)(Object)this, target)) ci.cancel();
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d sscExtras$riderMovement(Vec3d own) {
        return DrakeRiding.movement((PlayerEntity)(Object)this, own);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sscExtras$releasePassenger(CallbackInfo ci) {
        var self = (PlayerEntity)(Object)this;
        if (!self.getWorld().isClient && ((DrakeRiding.State)self).sscExtras$tracksDrakePassenger()
                && self.hasPassengers()) {
            if (!self.isSneaking()) sscExtras$ejectReady = true;
            if (!DrakeRiding.accepts(self, self.getFirstPassenger()) || sscExtras$ejectReady && self.isSneaking()) {
                self.removeAllPassengers();
                sscExtras$ejectReady = false;
            }
        } else sscExtras$ejectReady = false;
    }
}
