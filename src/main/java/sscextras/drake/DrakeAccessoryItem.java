package sscextras.drake;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import java.util.List;

public class DrakeAccessoryItem extends AccessoryItem {
    public final String group, slot, description;
    public final boolean permanentOnly;

    public DrakeAccessoryItem(String group, String slot, String description, boolean permanentOnly, boolean fireproof) {
        super(fireproof ? new Settings().maxCount(1).fireproof() : new Settings().maxCount(1));
        this.group = group;
        this.slot = slot;
        this.description = description;
        this.permanentOnly = permanentOnly;
    }

    public String curiosSlot() {
        return switch (slot) { case "face" -> "head"; case "cape" -> "body"; case "glove" -> "hands"; default -> slot; };
    }

    @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData data) {
        return entity instanceof PlayerEntity player && CollarSlots.isActive(data)
                && data.slot().getPath().equals(data.slot().getNamespace().equals("curios") ? curiosSlot() : group + "/" + slot)
                && (!permanentOnly || EarthenDrake.stage(player) == 3);
    }

    @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData data) {
        return entity instanceof PlayerEntity player && player.isCreative() || !EnchantmentHelper.hasBindingCurse(stack);
    }

    @Override public void onEquip(ItemStack stack, LivingEntity entity, SlotData data) {
        accessoryTick(stack, entity, data);
    }

    @Override public void accessoryTick(ItemStack stack, LivingEntity entity, SlotData data) {
        if (!entity.getWorld().isClient && entity instanceof PlayerEntity player && CollarSlots.isActive(data)) {
            DrakeEquipment.tickEquipped(player, stack);
        }
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ssc-extras." + description).formatted(Formatting.GRAY));
        if (!permanentOnly) tooltip.add(Text.translatable("tooltip.ssc-extras.collar.gain",
                Collars.CURSED.strength(), Collars.CURSED.strength() * 2).formatted(Formatting.GRAY));
        if (permanentOnly) tooltip.add(Text.translatable("tooltip.ssc-extras.drake_permanent").formatted(Formatting.DARK_PURPLE));
        else tooltip.add(Text.translatable("tooltip.ssc-extras.drake_binding").formatted(Formatting.DARK_PURPLE));
    }

    @Override public int getEnchantability() { return this == DrakeEquipment.CLAW_TIPS ? 15 : 0; }
    @Override public boolean isEnchantable(ItemStack stack) { return this == DrakeEquipment.CLAW_TIPS; }
}
