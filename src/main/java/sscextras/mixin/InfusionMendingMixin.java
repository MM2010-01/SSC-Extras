package sscextras.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.effigy.InfusionSlot;
import sscextras.effigy.Infusions;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(EnchantmentHelper.class)
public abstract class InfusionMendingMixin {
    @Inject(method = "chooseEquipmentWith(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Map$Entry;",
            at = @At("HEAD"), cancellable = true)
    private static void sscExtras$mendTools(Enchantment enchantment, LivingEntity entity, Predicate<ItemStack> condition,
            CallbackInfoReturnable<Map.Entry<EquipmentSlot, ItemStack>> cir) {
        if (enchantment != Enchantments.MENDING || !(entity instanceof PlayerEntity player) || Infusions.inventory(player).isEmpty()) return;
        var tools = new ArrayList<ItemStack>();
        var nativeEquipment = enchantment.getEquipment(entity).values();
        for (InfusionSlot slot : InfusionSlot.values()) {
            if (slot.armor() || slot == InfusionSlot.WEAPON) continue;
            ItemStack stack = Infusions.active(player, slot);
            if (!stack.isEmpty() && !nativeEquipment.contains(stack)
                    && EnchantmentHelper.getLevel(enchantment, stack) > 0 && condition.test(stack)) tools.add(stack);
        }
        if (tools.isEmpty()) return;
        long nativeCount = nativeEquipment.stream()
                .filter(stack -> EnchantmentHelper.getLevel(enchantment, stack) > 0 && condition.test(stack)).count();
        int selection = entity.getRandom().nextInt(tools.size() + (int) nativeCount);
        if (selection < tools.size()) cir.setReturnValue(Map.entry(EquipmentSlot.MAINHAND, tools.get(selection)));
    }
}
