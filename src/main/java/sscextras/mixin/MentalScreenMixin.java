package sscextras.mixin;

import java.util.OptionalInt;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.*;

@Mixin(ServerPlayerEntity.class)
public abstract class MentalScreenMixin {
    @Inject(method = "openHandledScreen", at = @At("RETURN"))
    private void sscExtras$screenInt(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        var player = (ServerPlayerEntity)(Object)this;
        if (cir.getReturnValue().isEmpty() || player.currentScreenHandler == player.playerScreenHandler) return;
        if (!PlayerMind.permits(player, MentalRequirements.screen(player.currentScreenHandler))) player.closeHandledScreen();
    }
}
