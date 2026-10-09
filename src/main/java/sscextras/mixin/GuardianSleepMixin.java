package sscextras.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.GuardianSleep;

@Mixin(PlayerEntity.class)
public abstract class GuardianSleepMixin {
    @Inject(method = "updatePose", at = @At("TAIL"))
    private void sscExtras$standingStatue(CallbackInfo ci) {
        if (GuardianSleep.statue((PlayerEntity)(Object)this)) ((PlayerEntity)(Object)this).setPose(EntityPose.STANDING);
    }
}
