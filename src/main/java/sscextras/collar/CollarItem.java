package sscextras.collar;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import java.util.List;

public final class CollarItem extends AccessoryItem {
    private final boolean cursed;
    private final boolean taming;

    public CollarItem(boolean cursed) {
        this(cursed, false);
    }

    public CollarItem(boolean cursed, boolean taming) {
        super(new Settings().maxCount(1));
        this.cursed = cursed;
        this.taming = taming;
    }

    public int strength() { return cursed ? 2 : 1; }

    public void ensureBinding(ItemStack stack) {
        if (cursed && !EnchantmentHelper.hasBindingCurse(stack)) {
            stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
        }
    }

    private void refreshBinding(ItemStack stack, PlayerEntity player) {
        if (cursed && !taming && FormAbilityManager.getForm(player).getIndex() == 3) {
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
        if (cursed) Collars.ensureNaturalCurse(stack, net.minecraft.util.math.random.Random.create());
        return stack;
    }

    @Override public boolean hasGlint(ItemStack stack) { return cursed || super.hasGlint(stack); }

    @Override public void onEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof ServerPlayerEntity player) {
            if (!CollarSlots.isActive(slot)) {
                CollarSlots.recoverLegacy(player);
                return;
            }
            if (!player.isSilent() && !player.isSpectator()) {
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, player.getSoundCategory(), 1.0f, 1.0f);
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
            Collars.ensureNaturalCurse(stack, player.getRandom());
            refreshBinding(stack, player);
            if (stack.hasNbt() && stack.getNbt().getBoolean(Collars.AWAKENING)
                    && !net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager.getPlayerTransformData(player).isTransforming) {
                stack.getNbt().remove(Collars.AWAKENING);
                Collars.applyCurse(player, stack);
            }
        }
    }

    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && entity instanceof PlayerEntity player && cursed && !taming) {
            // Curios also ticks equipped stacks here, with slot -1.
            if (slot < 0 || slot >= player.getInventory().size() || player.getInventory().getStack(slot) != stack) return;
            Collars.ensureNaturalCurse(stack, player.getRandom());
            ensureBinding(stack);
            if (Collars.tryEquip(player, stack)) player.getInventory().markDirty();
        }
    }

    @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof PlayerEntity player && player.isCreative()) return true;
        if (taming) return false;
        if (cursed) return entity instanceof PlayerEntity player && FormAbilityManager.getForm(player).getIndex() == 3;
        return super.canUnequip(stack, entity, slot);
    }

    @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        return !(entity instanceof PlayerEntity player && !taming && TamingCollar.worn(player)) && super.canEquip(stack, entity, slot);
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ssc-extras.collar.gain", strength(), strength() * 2).formatted(Formatting.GRAY));
        if (cursed) {
            tooltip.add(Text.translatable(taming ? "tooltip.ssc-extras.taming_collar.restrictions" : "tooltip.ssc-extras.collar.cursed").formatted(Formatting.DARK_PURPLE));
            tooltip.add(Text.translatable(taming ? "tooltip.ssc-extras.taming_collar.release" : "tooltip.ssc-extras.collar.release").formatted(Formatting.GRAY));
        }
        var effect = Collars.infusion(stack);
        var form = Collars.infusionForm(stack);
        tooltip.add(effect == null ? Text.translatable("tooltip.ssc-extras.collar.infuse").formatted(Formatting.GRAY)
                : Text.translatable("tooltip.ssc-extras.collar.infused", form == null ? effect.getName() : form.getFormName())
                        .formatted(Formatting.LIGHT_PURPLE));
    }
}
