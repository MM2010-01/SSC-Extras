package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.*;

@Mixin(Entity.class)
public abstract class GuardianMovementOwnerMixin {
    @Inject(method = "isLogicalSideForUpdatingMovement", at = @At("HEAD"), cancellable = true)
    private void sscExtras$guardianServerMovement(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof PlayerEntity player && (RuneboundGuardian.state(player).protecting || RuneboundGuardian.state(player).deactivated || RuneboundGuardian.state(player).ritualGuideId != 0))
            cir.setReturnValue(!player.getWorld().isClient);
    }
}
