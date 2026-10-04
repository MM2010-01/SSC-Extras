package sscextras.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeOutpostOwnership;

@Mixin(ServerPlayerEntity.class)
public abstract class DrakeMountTabNameMixin {
    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    private void sscExtras$tabName(CallbackInfoReturnable<Text> cir) {
        var player = (ServerPlayerEntity)(Object)this;
        if (!((DrakeOutpostOwnership.Display)player).sscExtras$mountName().isEmpty()) cir.setReturnValue(player.getDisplayName());
    }
}
