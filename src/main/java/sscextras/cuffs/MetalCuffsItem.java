package sscextras.cuffs;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.CollarSlots;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;

public final class MetalCuffsItem extends AccessoryItem {
    private final Map<PlayerEntity, Map<SlotData, ItemStack>> worn = new WeakHashMap<>();
    public MetalCuffsItem() { super(new Settings().maxDamage(2400)); }

    @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (!(entity instanceof PlayerEntity) || !CollarSlots.isActive(slot)) return false;
        String path = slot.slot().getPath();
        return slot.slot().getNamespace().equals("curios") ? path.equals("bracelet") || path.equals("anklet")
                : path.equals("hand/wrist") || path.equals("feet/ankle");
    }

    @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData slot) {
        return entity instanceof PlayerEntity player && player.isCreative() || super.canUnequip(stack, entity, slot);
    }

    @Override public void onEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof ServerPlayerEntity player && canEquip(stack, entity, slot)) {
            // Both APIs also call onEquip when an already worn stack's NBT changes.
            if (worn.computeIfAbsent(player, key -> new HashMap<>()).put(slot, stack) == stack) return;
            if (player.isSilent() || player.isSpectator()) return;
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_IRON, player.getSoundCategory(), 1, 1);
        }
    }

    @Override public void onUnequip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof ServerPlayerEntity player) {
            var slots = worn.get(player);
            if (slots != null && MetalCuffs.equipped(player, slot.slot().getPath().contains("ankl"), false) != slots.get(slot)) {
                slots.remove(slot);
                if (slots.isEmpty()) worn.remove(player);
            }
        }
    }

    @Override public int getEnchantability() { return 14; }

    @Override public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(Items.IRON_INGOT) || super.canRepair(stack, ingredient);
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable("tooltip.ssc-extras.metal_cuffs.suppress").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.metal_cuffs.first").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.metal_cuffs.pair").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.metal_cuffs.durability").formatted(Formatting.GRAY));
    }
}
