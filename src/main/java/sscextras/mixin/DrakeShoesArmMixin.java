package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.DrakeShoesFeatureRenderer;

@Mixin(PlayerEntityRenderer.class)
public abstract class DrakeShoesArmMixin {
    @Inject(method = "renderArm", at = @At("TAIL"))
    private void sscExtras$originalHandShoe(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        DrakeShoesFeatureRenderer.renderArm(player, arm, arm == ((PlayerEntityRenderer)(Object)this).getModel().leftArm,
                matrices, buffers, light);
    }
}
