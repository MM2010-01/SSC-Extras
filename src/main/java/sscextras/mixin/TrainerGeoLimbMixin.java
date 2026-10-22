package sscextras.mixin;

import mod.azure.azurelib.core.animatable.GeoAnimatable;
import mod.azure.azurelib.renderer.GeoObjectRenderer;
import mod.azure.azurelib.cache.object.GeoBone;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.TrainerLimbModel;

/** Only the reshaped limb branch is now owned by the actual player limb joints. */
@Mixin(value = GeoObjectRenderer.class, remap = false)
public abstract class TrainerGeoLimbMixin {
    @Inject(method = "renderRecursively", at = @At("HEAD"), cancellable = true)
    private void trainerLimb(MatrixStack matrices, GeoAnimatable animatable, GeoBone bone, RenderLayer layer,
            VertexConsumerProvider buffers, VertexConsumer vertices, boolean again, float delta, int light, int overlay,
            float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (!(animatable instanceof FormAnimatable form) || form.e == null) return;
        String name = bone.getName();
        if ((name.equals("bipedLeftArm") || name.equals("bipedRightArm")) && TrainerLimbModel.active(form.e, true)
                || (name.equals("bipedLeftLeg") || name.equals("bipedRightLeg")) && TrainerLimbModel.active(form.e, false)) ci.cancel();
    }
}
