package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeView;

@Mixin(Entity.class)
public abstract class DrakeViewMixin {
    @Inject(method = "getCameraPosVec", at = @At("RETURN"), cancellable = true)
    private void sscextras$headRay(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        cir.setReturnValue(DrakeView.atHead((Entity)(Object)this, tickDelta, cir.getReturnValue()));
    }
}
