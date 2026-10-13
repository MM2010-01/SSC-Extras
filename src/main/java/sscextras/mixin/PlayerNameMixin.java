package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.PlayerNames;

@Mixin(PlayerEntity.class)
public abstract class PlayerNameMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void sscExtras$name(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(PlayerNames.name((PlayerEntity)(Object)this, cir.getReturnValue(), false));
    }
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void sscExtras$displayName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(PlayerNames.name((PlayerEntity)(Object)this, cir.getReturnValue(), true));
    }
}
