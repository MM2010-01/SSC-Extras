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
        if (!((DrakeAttention.State)entity).sscExtras$petting()) return;
        arms.visible = false; rightArm.visible = leftArm.visible = true;
        leftArm.pitch = -.85f + MathHelper.sin(time * .3f) * .16f;
        leftArm.yaw = .12f; leftArm.roll = 0;
        rightArm.pitch = .1f; rightArm.yaw = rightArm.roll = 0;
    }
}
