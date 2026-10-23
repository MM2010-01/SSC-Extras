package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.GuardianCores;

@Mixin(ItemStack.class)
public abstract class GuardianCoreItemMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void sscExtras$followCarriedCore(World world, Entity holder, int slot, boolean selected, CallbackInfo ci) {
        if (!world.isClient && ((ItemStack)(Object)this).hasNbt()) {
            GuardianCores.observe((ItemStack)(Object)this, holder, "entity");
            sscextras.statue.StatueCrates.observe((ItemStack)(Object)this, holder);
        }
    }
}
