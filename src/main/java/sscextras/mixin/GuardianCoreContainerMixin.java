package sscextras.mixin;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.GuardianCores;

@Mixin(BlockEntity.class)
public abstract class GuardianCoreContainerMixin {
    @Inject(method = "markDirty()V", at = @At("TAIL"))
    private void sscExtras$followStoredCore(CallbackInfo ci) { GuardianCores.observe((BlockEntity)(Object)this); }
}
