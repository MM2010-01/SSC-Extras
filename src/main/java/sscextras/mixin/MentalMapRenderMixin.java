package sscextras.mixin;

import net.minecraft.client.render.MapRenderer;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public abstract class MentalMapRenderMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void sscExtras$unreadableMap(CallbackInfo ci) {
        if (sscextras.client.MentalPerception.unreadable(Items.FILLED_MAP.getDefaultStack())) ci.cancel();
    }
}
