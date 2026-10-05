package sscextras.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeSoulbinding;

@Mixin(PlayerManager.class)
public abstract class DrakeSoulboundRespawnMixin {
    @Inject(method = "respawnPlayer", at = @At("HEAD"))
    private void sscExtras$prepareRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        DrakeSoulbinding.beforeRespawn(player, alive);
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void sscExtras$restoreSoul(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        DrakeSoulbinding.afterRespawn(cir.getReturnValue(), alive);
    }
}
