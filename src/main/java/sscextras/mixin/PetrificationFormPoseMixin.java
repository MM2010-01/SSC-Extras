package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import sscextras.client.PetrificationFormPose;

@Mixin(value = FormRenderFeature.class, remap = false)
public abstract class PetrificationFormPoseMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", desc = @Desc(owner = IModelAnimationSystem.class, value = "processAnimation",
            args = {FormRenderer.class, FormModel.class, PlayerEntityRenderer.class, PlayerEntity.class,
                    float.class, float.class, float.class, float.class, float.class, float.class})))
    private void petrificationPose(IModelAnimationSystem system, FormRenderer form, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, float limb, float speed, float delta, float age, float yaw, float pitch, Operation<Void> original) {
        original.call(system, form, model, renderer, player, limb, speed, delta, age, yaw, pitch);
        PetrificationFormPose.apply(player, model);
    }
}
