package sscextras.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.petrification.PetrificationMining;

@Mixin(MinecraftClient.class)
public abstract class PetrificationMiningMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void petrificationMineInsteadOfHit(CallbackInfoReturnable<Boolean> cir) {
        var client = (MinecraftClient)(Object)this;
        if (client.player != null && client.crosshairTarget instanceof EntityHitResult hit
                && PetrificationMining.mineable(client.player, hit.getEntity())) cir.setReturnValue(false);
    }
}
