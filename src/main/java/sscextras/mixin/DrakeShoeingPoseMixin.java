package sscextras.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeSoulbinding;
import sscextras.drake.EarthenDrake;

@Mixin(PlayerEntityRenderer.class)
public abstract class DrakeShoeingPoseMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V", at = @At("TAIL"))
    private void sscExtras$lieOnBack(AbstractClientPlayerEntity player, MatrixStack matrices, float age, float bodyYaw,
            float tickDelta, CallbackInfo ci) {
        if (!DrakeSoulbinding.shoeing(player)) return;
        if (EarthenDrake.stage(player) == 3) {
            matrices.translate(0, .86, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        } else {
            matrices.translate(0, .3, -.8);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        }
    }
}
