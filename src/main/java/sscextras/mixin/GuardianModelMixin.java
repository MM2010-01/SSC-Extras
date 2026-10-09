package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.RuneboundGuardian;
import sscextras.client.GuardianMesh;

@Mixin(PlayerEntityRenderer.class)
public abstract class GuardianModelMixin {
    @Inject(method = "setModelPose", at = @At("TAIL"))
    private void guardianBody(AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (RuneboundGuardian.stage(player) >= 0) ((PlayerEntityRenderer)(Object)this).getModel().setVisible(false);
    }
    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
    private void guardianHand(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        int stage = RuneboundGuardian.stage(player); if (stage < 0) return;
        var model = ((PlayerEntityRenderer)(Object)this).getModel();
        boolean left = arm == model.leftArm;
        float oldPitch = arm.pitch, oldYaw = arm.yaw, oldRoll = arm.roll;
        arm.pitch = arm.yaw = arm.roll = 0;
        var state = RuneboundGuardian.state(player);
        GuardianMesh.get(stage).render(matrices, buffers, light, model, stage < 2 || state.energy > 0,
                state.weatherTicks >= 72000, 1, left ? "bipedLeftArm" : "bipedRightArm", sscextras.guardian.GuardianEquipment.visuals(player));
        arm.pitch = oldPitch; arm.yaw = oldYaw; arm.roll = oldRoll; ci.cancel();
    }
    @Inject(method = "renderArm", at = @At("TAIL"))
    private void guardianOriginalCuff(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager.getForm(player)
                != net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_SHIFTER
                || sscextras.guardian.GuardianEquipment.equipped(player, "wrist").isEmpty()) return;
        sscextras.client.GuardianEquipmentRenderer.limb(player, arm, arm == ((PlayerEntityRenderer)(Object)this).getModel().leftArm,
                true, matrices, buffers, light);
    }
}
