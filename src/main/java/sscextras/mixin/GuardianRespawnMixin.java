package sscextras.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.GuardianEquipment;

@Mixin(PlayerManager.class)
public abstract class GuardianRespawnMixin {
    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void sscExtras$guardianSoul(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        GuardianEquipment.respawn(oldPlayer, cir.getReturnValue(), alive);
    }
}
