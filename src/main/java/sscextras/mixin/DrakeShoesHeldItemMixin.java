package sscextras.mixin;

import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeShoes;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class DrakeShoesHeldItemMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void sscExtras$hidePawItems(MatrixStack matrices, VertexConsumerProvider vertices, int light, LivingEntity entity,
            float limbAngle, float limbDistance, float tickDelta, float age, float yaw, float pitch, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && DrakeShoes.hands(player)) ci.cancel();
    }
}
