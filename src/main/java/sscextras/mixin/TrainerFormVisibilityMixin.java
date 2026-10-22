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
import sscextras.client.TrainerFormSkin;

@Mixin(value = FormRenderFeature.class, remap = false)
public abstract class TrainerFormVisibilityMixin {
    @Inject(method = "rM_PartA", at = @At("TAIL"))
    private static void trainerLimbs(PlayerEntityRenderer renderer, AbstractClientPlayerEntity player, float yaw, float delta,
            MatrixStack matrices, VertexConsumerProvider buffers, int light, CallbackInfo ci) {
        if (player.isSpectator()) return;
        var model = renderer.getModel();
        if (TrainerLimbModel.active(player, true)) {
            model.leftArm.visible = !TrainerFormSkin.covers(player,model,true,true);
            model.rightArm.visible = !TrainerFormSkin.covers(player,model,true,false);
            model.leftSleeve.visible = model.leftArm.visible && player.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
            model.rightSleeve.visible = model.rightArm.visible && player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
        }
        if (TrainerLimbModel.active(player, false)) {
            model.leftLeg.visible = !TrainerFormSkin.covers(player,model,false,true);
            model.rightLeg.visible = !TrainerFormSkin.covers(player,model,false,false);
            model.leftPants.visible = model.leftLeg.visible && player.isPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
            model.rightPants.visible = model.rightLeg.visible && player.isPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
        }
    }
    @Inject(method = "rFPM_PartA", at = @At("TAIL"))
    private static void trainerHand(PlayerEntityRenderer renderer, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (!TrainerLimbModel.active(player, true)) return;
        arm.visible = !TrainerFormSkin.covers(player,renderer.getModel(),true,arm==renderer.getModel().leftArm);
        sleeve.visible = arm.visible && player.isPartVisible(arm == renderer.getModel().leftArm ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
        TrainerLimbModel.apply(renderer.getModel(), player);
    }
    @Inject(method="rM_PartB",at=@At("TAIL"))
    private static void trainerSkin(PlayerEntityRenderer renderer,AbstractClientPlayerEntity player,float yaw,float delta,
            MatrixStack matrices,VertexConsumerProvider buffers,int light,CallbackInfo ci) {
        TrainerFormSkin.render(player,renderer.getModel(),matrices,buffers,light,null);
    }
    @Inject(method="rFPM_PartB",at=@At("TAIL"))
    private static void trainerHandSkin(PlayerEntityRenderer renderer,MatrixStack matrices,VertexConsumerProvider buffers,int light,
            AbstractClientPlayerEntity player,ModelPart arm,ModelPart sleeve,CallbackInfo ci) {
        TrainerFormSkin.render(player,renderer.getModel(),matrices,buffers,light,arm);
    }
}
