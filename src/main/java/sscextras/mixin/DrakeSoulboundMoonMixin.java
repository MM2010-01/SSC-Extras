package sscextras.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeSoulbinding;

@Mixin(value = CursedMoon.class, remap = false)
public abstract class DrakeSoulboundMoonMixin {
    @Inject(method = {"applyMoonEffect", "applyEndMoonEffect"}, at = @At("HEAD"), cancellable = true)
    private static void sscExtras$protectSoulboundForm(ServerPlayerEntity player, CallbackInfo ci) {
        if (DrakeSoulbinding.bound(player)) ci.cancel();
    }
}
