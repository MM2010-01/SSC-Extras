package sscextras.mixin;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.EarthenDrakeAnimation;

@Mixin(PlayerEntityModel.class)
public abstract class DrakeShoeingBodyMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void sscExtras$poseRealLimbs(LivingEntity entity, float limbAngle, float limbDistance, float age,
            float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player)
            EarthenDrakeAnimation.poseShoeingBody((PlayerEntityModel<?>)(Object)this, player);
    }
}
