package sscextras.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(InGameHud.class)
public abstract class DrakeFeralHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void sscExtras$passengerBar(float tickDelta, DrawContext context, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !DrakeFeralization.controlled(client.player)) return;
        int center = context.getScaledWindowWidth() / 2;
        int bottom = context.getScaledWindowHeight();
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        context.fill(center - 91, bottom - 22, center + 91, bottom, 0x99555555);
        if (!client.player.getOffHandStack().isEmpty()) {
            int left = client.player.getMainArm() == net.minecraft.util.Arm.RIGHT ? center - 120 : center + 91;
            context.fill(left, bottom - 23, left + 29, bottom, 0x99555555);
        }
        context.getMatrices().pop();
    }
}
