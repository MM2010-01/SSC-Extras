package sscextras.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeEquipment;

@Mixin(Enchantment.class)
public abstract class DrakeClawEnchantmentMixin {
    @Shadow @Final public EnchantmentTarget target;
    @Inject(method = "isAcceptableItem", at = @At("HEAD"), cancellable = true)
    private void sscExtras$clawEnchantments(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isOf(DrakeEquipment.CLAW_TIPS)) cir.setReturnValue(target == EnchantmentTarget.WEAPON
                || target == EnchantmentTarget.DIGGER || target == EnchantmentTarget.BREAKABLE
                || target == EnchantmentTarget.VANISHABLE);
    }
}
