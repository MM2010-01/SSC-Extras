package sscextras.collar;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import java.util.List;

public final class CollarItem extends AccessoryItem {
    private final boolean cursed;

    public CollarItem(boolean cursed) {
        super(new Settings().maxCount(1));
        this.cursed = cursed;
    }

    public int strength() { return cursed ? 2 : 1; }

    public void ensureBinding(ItemStack stack) {
        if (cursed && !EnchantmentHelper.hasBindingCurse(stack)) {
            stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
        }
    }

    private void refreshBinding(ItemStack stack, PlayerEntity player) {
        if (cursed && FormAbilityManager.getForm(player).getIndex() == 3) {
            // Curios checks the enchantment before consulting canUnequip.
            if (EnchantmentHelper.hasBindingCurse(stack)) {
                var enchantments = EnchantmentHelper.get(stack);
                enchantments.remove(Enchantments.BINDING_CURSE);
                EnchantmentHelper.set(enchantments, stack);
            }
        } else {
            ensureBinding(stack);
        }
    }

    @Override public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        ensureBinding(stack);
        return stack;
    }

    @Override public boolean hasGlint(ItemStack stack) { return cursed || super.hasGlint(stack); }

    @Override public void onEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof ServerPlayerEntity player) {
            if (!CollarSlots.isActive(slot)) {
                CollarSlots.recoverLegacy(player);
                return;
            }
            refreshBinding(stack, player);
            Collars.applyCurse(player, stack);
        }
    }

    @Override public void accessoryTick(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof ServerPlayerEntity player) {
            if (!CollarSlots.isActive(slot)) {
                CollarSlots.recoverLegacy(player);
                return;
            }
            refreshBinding(stack, player);
            if (stack.hasNbt() && stack.getNbt().getBoolean(Collars.AWAKENING)
                    && !net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager.getPlayerTransformData(player).isTransforming) {
                stack.getNbt().remove(Collars.AWAKENING);
                Collars.applyCurse(player, stack);
            }
        }
    }

    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && entity instanceof PlayerEntity player && cursed) {
            // Curios also ticks equipped stacks here, with slot -1.
            if (slot < 0 || slot >= player.getInventory().size() || player.getInventory().getStack(slot) != stack) return;
            ensureBinding(stack);
            if (Collars.tryEquip(player, stack)) player.getInventory().markDirty();
        }
    }

    @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof PlayerEntity player && player.isCreative()) return true;
        if (cursed) return entity instanceof PlayerEntity player && FormAbilityManager.getForm(player).getIndex() == 3;
        return super.canUnequip(stack, entity, slot);
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ssc-extras.collar.gain", strength(), strength() * 2).formatted(Formatting.GRAY));
        if (cursed) {
            tooltip.add(Text.translatable("tooltip.ssc-extras.collar.cursed").formatted(Formatting.DARK_PURPLE));
            tooltip.add(Text.translatable("tooltip.ssc-extras.collar.release").formatted(Formatting.GRAY));
        }
        var effect = Collars.infusion(stack);
        tooltip.add(effect == null ? Text.translatable("tooltip.ssc-extras.collar.infuse").formatted(Formatting.GRAY)
                : Text.translatable("tooltip.ssc-extras.collar.infused", effect.getName()).formatted(Formatting.LIGHT_PURPLE));
    }
}
