package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.petrification.Petrification;
import sscextras.client.PetrificationClient;

@Mixin(LivingEntityRenderer.class)
public abstract class PetrificationRendererMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    protected java.util.List<FeatureRenderer> features;
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void petrificationModel(EntityModel model, MatrixStack matrices, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha, Operation<Void> original) {
        try (var pass = sscextras.client.PetrificationModelPose.pass("body")) {
            original.call(model, matrices, buffer, light, overlay, red, green, blue, alpha);
        }
    }
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V"))
    private void petrificationBodyTransform(LivingEntityRenderer renderer, LivingEntity entity, MatrixStack matrices,
            float age, float yaw, float delta, Operation<Void> original) {
        sscextras.client.PetrificationModelPose.transform("body", matrices, () -> original.call(renderer, entity, matrices, age, yaw, delta));
    }
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V"))
    private void petrificationBodyScale(LivingEntityRenderer renderer, LivingEntity entity, MatrixStack matrices,
            float delta, Operation<Void> original) {
        sscextras.client.PetrificationModelPose.transform("scale", matrices, () -> original.call(renderer, entity, matrices, delta));
    }
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "setupTransforms", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/LivingEntity;deathTime:I"))
    private int petrificationNoDeathRoll(int time, LivingEntity entity) {
        return Petrification.stoneAppearance(entity) ? 0 : time;
    }
    @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true)
    private static void petrificationNoRedFlash(LivingEntity entity, float progress, CallbackInfoReturnable<Integer> cir) {
        if (Petrification.stoneAppearance(entity)) cir.setReturnValue(OverlayTexture.DEFAULT_UV);
    }
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void petrificationTexture(LivingEntity entity, boolean visible, boolean translucent, boolean outline, CallbackInfoReturnable<RenderLayer> cir) {
        if (!Petrification.replaceTexture(entity)) return;
        var texture = PetrificationClient.texture(((LivingEntityRenderer)(Object)this).getTexture(entity));
        cir.setReturnValue(translucent ? RenderLayer.getItemEntityTranslucentCull(texture)
                : visible ? RenderLayer.getEntityCutoutNoCull(texture) : outline ? RenderLayer.getOutline(texture) : null);
    }
    @Inject(method = "getHandSwingProgress", at = @At("HEAD"), cancellable = true)
    private void petrificationSwing(LivingEntity entity, float delta, CallbackInfoReturnable<Float> cir) {
        if (Petrification.poseLocked(entity)) cir.setReturnValue(Petrification.state(entity).swing);
    }
    @Inject(method = "getAnimationProgress", at = @At("HEAD"), cancellable = true)
    private void petrificationAge(LivingEntity entity, float delta, CallbackInfoReturnable<Float> cir) {
        if (Petrification.poseLocked(entity)) cir.setReturnValue(Petrification.state(entity).animationAge);
    }
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;animateModel(Lnet/minecraft/entity/Entity;FFF)V"))
    private void petrificationAnimate(EntityModel model, net.minecraft.entity.Entity entity, float limb, float speed, float delta, Operation<Void> original) {
        if (entity instanceof LivingEntity living && Petrification.poseLocked(living)) {
            var state = Petrification.state(living); limb = state.limbPos; speed = state.limbSpeed; delta = 0;
        }
        original.call(model, entity, limb, speed, delta);
    }
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V"))
    private void petrificationAngles(EntityModel model, net.minecraft.entity.Entity entity, float limb, float speed, float age, float yaw, float pitch, Operation<Void> original) {
        if (entity instanceof LivingEntity living && Petrification.poseLocked(living)) {
            var state = Petrification.state(living); limb = state.limbPos; speed = state.limbSpeed; age = state.animationAge;
            yaw = state.headYaw - state.bodyYaw; pitch = state.pitch;
        }
        original.call(model, entity, limb, speed, age, yaw, pitch);
        if (entity instanceof net.minecraft.entity.player.PlayerEntity
                && model instanceof net.minecraft.client.render.entity.model.PlayerEntityModel<?> playerModel)
            sscextras.client.PetrificationModelPose.playerModel(playerModel);
    }
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void petrificationFeatures(FeatureRenderer feature, MatrixStack matrices, VertexConsumerProvider buffers, int light, net.minecraft.entity.Entity entity,
            float limb, float speed, float delta, float age, float yaw, float pitch, Operation<Void> original) {
        if (entity instanceof LivingEntity living && Petrification.replaceTexture(living)) {
            if (feature instanceof net.minecraft.client.render.entity.feature.EyesFeatureRenderer) return;
            if (feature instanceof net.minecraft.client.render.entity.feature.SheepWoolFeatureRenderer
                    || feature instanceof net.minecraft.client.render.entity.feature.SlimeOverlayFeatureRenderer) {
                var originalBuffers = buffers;
                var texture = PetrificationClient.texture(new net.minecraft.util.Identifier("minecraft", feature instanceof net.minecraft.client.render.entity.feature.SheepWoolFeatureRenderer
                        ? "textures/entity/sheep/sheep_fur.png" : "textures/entity/slime/slime.png"));
                buffers = layer -> originalBuffers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
            }
        }
        if (entity instanceof LivingEntity living && Petrification.poseLocked(living)) {
            var state = Petrification.state(living); limb = state.limbPos; speed = state.limbSpeed; age = state.animationAge;
            delta = 0; yaw = state.headYaw - state.bodyYaw; pitch = state.pitch;
        }
        try (var pass = sscextras.client.PetrificationModelPose.pass("feature/" + features.indexOf(feature))) {
            original.call(feature, matrices, buffers, light, entity, limb, speed, delta, age, yaw, pitch);
        }
    }
}
