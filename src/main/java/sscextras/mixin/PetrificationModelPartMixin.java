package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.PetrificationModelPose;

// Apply after the optimizer's mixin so this HEAD hook precedes its cancellable fast path.
@Mixin(value = ModelPart.class, priority = 900)
public abstract class PetrificationModelPartMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void petrificationPose(CallbackInfo ci) { PetrificationModelPose.apply((ModelPart)(Object)this); }
}
