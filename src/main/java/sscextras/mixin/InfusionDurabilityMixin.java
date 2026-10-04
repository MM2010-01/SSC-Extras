package sscextras.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.effigy.Infusions;

@Mixin(ItemStack.class)
public abstract class InfusionDurabilityMixin {
    @Inject(method = "damage(ILnet/minecraft/util/math/random/Random;Lnet/minecraft/server/network/ServerPlayerEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void sscExtras$preserveInfusion(int amount, Random random, ServerPlayerEntity player,
            CallbackInfoReturnable<Boolean> cir) {
        if (amount <= 0 || player == null) return;
        var inventory = Infusions.inventory(player);
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot) == (ItemStack)(Object)this) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
