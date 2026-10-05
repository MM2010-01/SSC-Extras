package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateController.TransformingController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeSoulbinding;
import sscextras.drake.EarthenDrake;

@Mixin(value = TransformingController.class, remap = false)
public abstract class DrakeRitualTransformAnimationMixin {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private void sscExtras$keepBeastPosture(PlayerEntity player, AnimSystem.AnimSystemData data, CallbackInfoReturnable<Boolean> cir) {
        if (DrakeSoulbinding.restrained(player) && EarthenDrake.stage(player) >= 2) cir.setReturnValue(false);
    }
}
