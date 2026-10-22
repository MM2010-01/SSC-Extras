package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class TrainerModelPartMixin implements sscextras.client.TrainerLimbModel.Part {
    @Unique private boolean sscExtras$reshaped;
    public void sscExtras$reshaped(boolean active) { sscExtras$reshaped = active; }
    @Inject(method = "renderCuboids", at = @At("HEAD"), cancellable = true)
    private void trainerArticulatedGeometry(CallbackInfo ci) { if (sscExtras$reshaped) ci.cancel(); }
}
