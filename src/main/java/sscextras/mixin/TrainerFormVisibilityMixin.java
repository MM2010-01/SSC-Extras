package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.TrainerLimbModel;

@Mixin(value = FormRenderFeature.class, remap = false)
public abstract class TrainerFormVisibilityMixin {
    @Inject(method = "rM_PartA", at = @At("TAIL"))
    private static void trainerLimbs(PlayerEntityRenderer renderer, AbstractClientPlayerEntity player, float yaw, float delta,
            MatrixStack matrices, VertexConsumerProvider buffers, int light, CallbackInfo ci) {
        if (player.isSpectator()) return;
        var model = renderer.getModel();
        if (TrainerLimbModel.active(player, true)) {
            model.leftArm.visible = model.rightArm.visible = true;
            model.leftSleeve.visible = player.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
            model.rightSleeve.visible = player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
        }
        if (TrainerLimbModel.active(player, false)) {
            model.leftLeg.visible = model.rightLeg.visible = true;
            model.leftPants.visible = player.isPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
            model.rightPants.visible = player.isPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
        }
    }
    @Inject(method = "rFPM_PartA", at = @At("TAIL"))
    private static void trainerHand(PlayerEntityRenderer renderer, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (!TrainerLimbModel.active(player, true)) return;
        arm.visible = true;
        sleeve.visible = player.isPartVisible(arm == renderer.getModel().leftArm ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
        TrainerLimbModel.apply(renderer.getModel(), player);
    }
}
