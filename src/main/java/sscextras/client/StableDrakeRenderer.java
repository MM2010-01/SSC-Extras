package sscextras.client;

import mod.azure.azurelib.cache.object.GeoBone;
import mod.azure.azurelib.core.animation.AnimationState;
import mod.azure.azurelib.model.GeoModel;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import sscextras.drake.*;

public final class StableDrakeRenderer extends GeoEntityRenderer<StableDrakeEntity> {
    public StableDrakeRenderer(EntityRendererFactory.Context context) {
        super(context, new Model());
        withScale(1.5f);
        shadowRadius = .8f;
        addRenderLayer(new GeoRenderLayer<StableDrakeEntity>(this) {
            private final DrakeGearModel gear = new DrakeGearModel(3);
            @Override public void renderForBone(MatrixStack matrices, StableDrakeEntity drake, GeoBone bone, RenderLayer type,
                    VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
                gear.render(bone.getName(), matrices, buffers, light, drake.hasReins(), drake.isSaddled(), false, false, drake.hasVanillaSaddle());
                buffers.getBuffer(type);
            }
        });
    }

    @Override protected void applyRotations(StableDrakeEntity drake, MatrixStack matrices, float age, float yaw,
            float tickDelta, float nativeScale) {
        super.applyRotations(drake, matrices, age, yaw + 180, tickDelta, nativeScale);
    }

    private static final class Model extends GeoModel<StableDrakeEntity> {
        @Override public Identifier getModelResource(StableDrakeEntity drake) { return EarthenDrake.id("geo/entity/stable_drake.geo.json"); }
        @Override public Identifier getTextureResource(StableDrakeEntity drake) { return EarthenDrake.id("textures/form/earthen_drake.png"); }
        @Override public Identifier getAnimationResource(StableDrakeEntity drake) { return EarthenDrake.id("animations/stable_drake.animation.json"); }

        @Override public void setCustomAnimations(StableDrakeEntity drake, long instanceId, AnimationState<StableDrakeEntity> state) {
            float time = drake.age + state.getPartialTick();
            float swing = MathHelper.sin(state.getLimbSwing() * .65f) * Math.min(.5f, state.getLimbSwingAmount()) * .7f;
            getBone("bipedLeftArm").ifPresent(bone -> bone.setRotX(swing));
            getBone("bipedRightArm").ifPresent(bone -> bone.setRotX(-swing));
            getBone("bipedLeftLeg").ifPresent(bone -> bone.setRotX(-swing));
            getBone("bipedRightLeg").ifPresent(bone -> bone.setRotX(swing));
            for (int i = 0; i < 5; i++) {
                float yaw = MathHelper.sin(time * .04f - i * .55f) * .035f;
                getBone("tail_" + i).ifPresent(bone -> bone.setRotY(yaw));
            }
            getBone("bipedHead").ifPresent(bone -> {
                bone.setRotY(MathHelper.clamp(MathHelper.wrapDegrees(drake.headYaw - drake.bodyYaw), -12, 12) * MathHelper.RADIANS_PER_DEGREE);
                bone.setRotX(MathHelper.clamp(drake.getPitch(), -8, 8) * MathHelper.RADIANS_PER_DEGREE);
            });
        }
    }
}
