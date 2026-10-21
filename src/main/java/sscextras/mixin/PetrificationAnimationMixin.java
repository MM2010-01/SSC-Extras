package sscextras.mixin;

import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.petrification.Petrification;

@Mixin(value = FormAnimatable.class, remap = false)
public abstract class PetrificationAnimationMixin {
    @Unique private double petrificationTick = Double.NaN;
    @Inject(method = "getTick", at = @At("RETURN"), cancellable = true)
    private void petrificationPause(Object context, CallbackInfoReturnable<Double> cir) {
        var form = (FormAnimatable)(Object)this;
        if (form.e != null && Petrification.frozen(form.e)) {
            if (Double.isNaN(petrificationTick)) petrificationTick = cir.getReturnValueD();
            cir.setReturnValue(petrificationTick);
        } else petrificationTick = Double.NaN;
    }
}
