package sscextras.mixin;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityModel.class)
public abstract class TrainerModelMixin {
    @Inject(method = "getTexturedModelData", at = @At("RETURN"))
    private static void trainerJoints(Dilation dilation, boolean slim, CallbackInfoReturnable<ModelData> cir) {
        sscextras.client.TrainerLimbModel.add(cir.getReturnValue(), slim);
    }
}
