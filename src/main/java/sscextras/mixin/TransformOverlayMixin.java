package sscextras.mixin;

import net.onixary.shapeShifterCurseFabric.screen_effect.TransformOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TransformOverlay.class, remap = false)
public abstract class TransformOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void sscExtras$skipDarkOverlay(CallbackInfo ci) {
        ci.cancel();
    }
}
