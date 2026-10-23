package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.statue.StatueCrates;

@Mixin(Entity.class)
public abstract class StatueCrateItemEntityMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void statueCrateDestroyed(Entity.RemovalReason reason, CallbackInfo ci) {
        // Native pickup/merge clears the stack before discard. Unload and dimension transfer are not destruction.
        if ((Object)this instanceof ItemEntity item && !item.getWorld().isClient && !item.getStack().isEmpty()
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED))
            StatueCrates.destroyed(item.getStack(), item.getServer());
    }
}
