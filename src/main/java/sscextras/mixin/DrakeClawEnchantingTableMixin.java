package sscextras.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeEquipment;
import java.util.List;

@Mixin(EnchantmentHelper.class)
public abstract class DrakeClawEnchantingTableMixin {
    @Inject(method = "getPossibleEntries", at = @At("RETURN"), cancellable = true)
    private static void sscExtras$clawTable(int power, ItemStack stack, boolean treasureAllowed,
            CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        if (stack.isOf(DrakeEquipment.CLAW_TIPS)) cir.setReturnValue(EnchantmentHelper.getPossibleEntries(power,
                new ItemStack(Items.BOOK), treasureAllowed).stream().filter(entry -> entry.enchantment.isAcceptableItem(stack))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
    }
}
