package sscextras.mixin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.GuardianClient;
@Mixin(InGameHud.class)
public abstract class GuardianHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void guardianRecoveryBlackout(DrawContext context, float delta, CallbackInfo ci) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) return;
        int ticks = sscextras.guardian.RuneboundGuardian.state(client.player).recoveryBlackout;
        if (ticks > 0) context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), 0xff000000);
    }
    @Redirect(method="renderStatusBars", at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
    private void guardianHunger(DrawContext context, Identifier texture,int x,int y,int u,int v,int width,int height) {
        if (v != 27 || !GuardianClient.energy()) context.drawTexture(texture,x,y,u,v,width,height);
    }
}
