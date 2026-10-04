package sscextras.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.BlindingReinVision;

@Mixin(GameRenderer.class)
public abstract class BlindingReinOverlayMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/DiffuseLighting;enableGuiDepthLighting()V", shift = At.Shift.AFTER))
    private void sscExtras$blinkers(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (!tick || client.world == null || !client.options.getPerspective().isFirstPerson() || !BlindingReinVision.worn()) return;
        var context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        BlindingReinVision.renderBlinkers(context);
        context.draw();
    }
}
