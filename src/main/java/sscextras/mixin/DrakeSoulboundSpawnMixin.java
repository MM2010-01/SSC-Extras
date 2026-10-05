package sscextras.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeOutpostOwnership;

@Mixin(ServerPlayerEntity.class)
public abstract class DrakeSoulboundSpawnMixin {
    @Inject(method = "setSpawnPoint", at = @At("HEAD"), cancellable = true)
    private void sscExtras$stallSpawn(RegistryKey<World> dimension, BlockPos pos, float angle, boolean forced, boolean message, CallbackInfo ci) {
        var player = (ServerPlayerEntity)(Object)this;
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null && claim.soulbound && (!claim.world.equals(dimension) || !claim.bed().equals(pos) || !forced)) {
            player.setSpawnPoint(claim.world, claim.bed(), 180, true, false);
            ci.cancel();
        }
    }
}
