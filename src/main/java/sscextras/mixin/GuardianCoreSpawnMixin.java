package sscextras.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class GuardianCoreSpawnMixin {
    @Inject(method = "onSpawn", at = @At("HEAD"))
    private void sscExtras$copiedAccessorySlots(CallbackInfo ci) {
        sscextras.guardian.GuardianCores.beforeSpawn((ServerPlayerEntity)(Object)this);
    }
}
