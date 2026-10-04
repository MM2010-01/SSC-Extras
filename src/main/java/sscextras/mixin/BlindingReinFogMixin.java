package sscextras.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.BlindingReinVision;

@Mixin(BackgroundRenderer.class)
public abstract class BlindingReinFogMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void sscExtras$blindColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
            float skyDarkness, CallbackInfo ci) {
        if (!BlindingReinVision.blind(camera)) return;
        red = green = blue = 0;
        RenderSystem.clearColor(0, 0, 0, 0);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void sscExtras$blindDistance(Camera camera, BackgroundRenderer.FogType type, float viewDistance,
            boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!BlindingReinVision.blind(camera)) return;
        boolean sky = type == BackgroundRenderer.FogType.FOG_SKY;
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), sky ? 0 : 1.25f));
        RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), sky ? 4 : 5));
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
