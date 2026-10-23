package sscextras.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.GuardianCores;

@Mixin(ItemEntity.class)
public abstract class GuardianCoreEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sscExtras$followDroppedCore(CallbackInfo ci) {
        var item = (ItemEntity)(Object)this;
        if (item.getWorld().isClient || !item.getStack().hasNbt()) return;
        GuardianCores.observe(item);
        sscextras.statue.StatueCrates.observe(item.getStack(), item);
        if (item.getY() < item.getWorld().getBottomY() - 64) GuardianCores.destroyed(item.getStack(), item.getServer());
    }
    @Inject(method = "damage", at = @At("RETURN"))
    private void sscExtras$coreDestroyed(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        var item = (ItemEntity)(Object)this;
        if (!item.getWorld().isClient && item.isRemoved()) GuardianCores.destroyed(item.getStack(), item.getServer());
    }
}
