package sscextras.mixin;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.client.PetrificationClient;
import sscextras.petrification.Petrification;

@Mixin(value = FormModel.class, remap = false)
public abstract class PetrificationFormMixin {
    @org.spongepowered.asm.mixin.Shadow public net.minecraft.entity.player.PlayerEntity entity;
    @Inject(method = {"getOverlayTextureResource", "getEmissiveTextureResource"}, at = @At("HEAD"), cancellable = true)
    private void petrificationOverlay(boolean slim, CallbackInfoReturnable<Identifier> cir) {
        if (entity != null && Petrification.replaceTexture(entity)) cir.setReturnValue(PetrificationClient.TRANSPARENT);
    }
    @Inject(target = @Desc(value = "getTextureResource", args = boolean.class, ret = Identifier.class), at = @At("RETURN"), cancellable = true)
    private void petrificationSkin(boolean slim, CallbackInfoReturnable<Identifier> cir) {
        if (entity != null && Petrification.replaceTexture(entity)) cir.setReturnValue(PetrificationClient.texture(cir.getReturnValue()));
    }
    @Inject(method = "getFullbrightTextureResource", at = @At("HEAD"), cancellable = true)
    private void petrificationNoGlow(FormAnimatable form, CallbackInfoReturnable<Identifier> cir) {
        if (form.e != null && Petrification.replaceTexture(form.e)) cir.setReturnValue(PetrificationClient.TRANSPARENT);
    }
}
