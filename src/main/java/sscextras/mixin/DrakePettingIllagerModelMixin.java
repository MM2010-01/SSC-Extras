package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.IllagerEntityModel;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeAttention;

@Mixin(IllagerEntityModel.class)
public abstract class DrakePettingIllagerModelMixin {
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart rightArm;
    @Shadow @Final private ModelPart leftArm;

    @Inject(method = "setAngles(Lnet/minecraft/entity/mob/IllagerEntity;FFFFF)V", at = @At("TAIL"))
    private void sscExtras$stroke(IllagerEntity entity, float limbAngle, float limbDistance, float time, float headYaw, float headPitch, CallbackInfo ci) {
        int role = sscextras.drake.DrakeSoulbinding.role(entity);
        rightArm.yScale = leftArm.yScale = 1;
        if (role == sscextras.drake.DrakeSoulbinding.SHOEING) {
            arms.visible = false; rightArm.visible = leftArm.visible = true;
            rightArm.pitch = -.65f + MathHelper.sin(time * .65f) * .4f;
            leftArm.pitch = -.9f; rightArm.yaw = -.15f; leftArm.yaw = .15f;
            rightArm.roll = leftArm.roll = 0;
            var grip = sscextras.client.DrakeShoeModel.grip(entity);
            if (grip != null) {
                sscextras.client.DrakeShoeModel.reach(leftArm, entity, grip, time);
                sscextras.client.DrakeShoeModel.reach(rightArm, entity, grip.add(0, .08, 0), time);
                rightArm.pitch += MathHelper.sin(time * .65f) * .16f;
            }
            return;
        }
        if (role == sscextras.drake.DrakeSoulbinding.FEEDING) {
            arms.visible = false; rightArm.visible = leftArm.visible = true;
            rightArm.pitch = -1.2f + MathHelper.sin(time * .25f) * .15f;
            rightArm.yaw = -.15f; rightArm.roll = 0;
            leftArm.pitch = -.5f; leftArm.yaw = leftArm.roll = 0;
            return;
        }
        if (role == sscextras.drake.DrakeSoulbinding.HOLDING || role == sscextras.drake.DrakeSoulbinding.CHANTING) {
            arms.visible = false; rightArm.visible = leftArm.visible = true;
            boolean chanting = role == sscextras.drake.DrakeSoulbinding.CHANTING;
            rightArm.pitch = leftArm.pitch = chanting ? -2.35f + MathHelper.sin(time * .18f) * .12f : -.9f;
            rightArm.yaw = chanting ? -.35f : -.12f; leftArm.yaw = -rightArm.yaw;
            rightArm.roll = chanting ? -.3f : 0; leftArm.roll = -rightArm.roll;
            if (!chanting) {
                var grip = sscextras.client.DrakeShoeModel.grip(entity);
                if (grip != null) {
                    sscextras.client.DrakeShoeModel.reach(leftArm, entity, grip, time);
                    sscextras.client.DrakeShoeModel.reach(rightArm, entity, grip, time);
                }
            }
            return;
        }
        if (!((DrakeAttention.State)entity).sscExtras$petting()) return;
        arms.visible = false; rightArm.visible = leftArm.visible = true;
        leftArm.pitch = -.85f + MathHelper.sin(time * .3f) * .16f;
        leftArm.yaw = .12f; leftArm.roll = 0;
        rightArm.pitch = .1f; rightArm.yaw = rightArm.roll = 0;
    }
}
