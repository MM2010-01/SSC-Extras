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
    @Redirect(method="renderStatusBars", at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
    private void guardianHunger(DrawContext context, Identifier texture,int x,int y,int u,int v,int width,int height) {
        if (v != 27 || !GuardianClient.energy()) context.drawTexture(texture,x,y,u,v,width,height);
    }
}
