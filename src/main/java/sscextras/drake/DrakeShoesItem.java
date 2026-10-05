package sscextras.drake;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import sscextras.collar.Collars;
import sscextras.collar.CollarSlots;
import java.util.List;

public final class DrakeShoesItem extends DrakeAccessoryItem {
    public DrakeShoesItem() {
        super("hand", "glove", "cursed_iron_drake_shoes", false, false);
    }

    @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData data) {
        String slot = data.slot().getPath();
        return entity instanceof PlayerEntity && CollarSlots.isActive(data)
                && (data.slot().getNamespace().equals("curios") ? slot.equals("hands") || slot.equals("feet")
                    : slot.equals("hand/glove") || slot.equals("feet/shoes"));
    }

    public static void bind(ItemStack stack) {
        if (!EnchantmentHelper.hasBindingCurse(stack)) stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
    }

    @Override public ItemStack getDefaultStack() {
        var stack = super.getDefaultStack();
        bind(stack);
        return stack;
    }

    @Override public void onUnequip(ItemStack stack, LivingEntity entity, SlotData data) {
        if (!entity.getWorld().isClient && entity instanceof PlayerEntity player) DrakeEquipment.refreshBinding(player);
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ssc-extras.iron_drake_shoes_hands").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ssc-extras.iron_drake_shoes_feet").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ssc-extras.iron_drake_shoes_curse").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("tooltip.ssc-extras.collar.gain", Collars.CURSED.strength(), Collars.CURSED.strength() * 2)
                .formatted(Formatting.GRAY));
    }
}
