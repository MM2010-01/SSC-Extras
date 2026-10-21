package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.client.PetrificationCracks;

@Mixin(EntityRenderDispatcher.class)
public abstract class PetrificationCracksMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void petrificationCracks(EntityRenderer renderer, Entity entity, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider buffers, int light, Operation<Void> original) {
        original.call(renderer, entity, yaw, delta, matrices, PetrificationCracks.buffers(entity, matrices, buffers), light);
    }
}
