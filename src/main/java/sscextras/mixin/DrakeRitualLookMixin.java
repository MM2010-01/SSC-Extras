package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeSoulbinding;

@Mixin(Entity.class)
public abstract class DrakeRitualLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void sscExtras$heldFacing(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (sscextras.client.DrakeCameraControl.look((Entity)(Object)this, cursorDeltaX, cursorDeltaY)
                || (Object)this instanceof PlayerEntity player && DrakeSoulbinding.restrained(player)) ci.cancel();
    }
}
