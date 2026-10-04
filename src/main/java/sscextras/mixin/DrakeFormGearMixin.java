package sscextras.mixin;

import com.google.gson.JsonObject;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.DrakeFormGearLayer;

@Mixin(value = FormRenderer.class, remap = false)
public abstract class DrakeFormGearMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void sscExtras$drakeGear(JsonObject config, CallbackInfo ci) {
        ((FormRenderer)(Object)this).addRenderLayer(new sscextras.client.CuffsFormLayer((FormRenderer)(Object)this));
        if (config.has("model") && config.get("model").getAsString().startsWith("ssc-extras:geo/form/earthen_drake_"))
            ((FormRenderer)(Object)this).addRenderLayer(new DrakeFormGearLayer((FormRenderer)(Object)this));
    }
}
