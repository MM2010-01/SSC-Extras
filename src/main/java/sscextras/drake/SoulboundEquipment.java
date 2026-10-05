package sscextras.drake;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.GameRules;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;

public final class SoulboundEquipment {
    public static final Enchantment SOULBOUND = new Enchantment(Enchantment.Rarity.VERY_RARE,
            EnchantmentTarget.BREAKABLE, EquipmentSlot.values()) {
        @Override public boolean isTreasure() { return true; }
        @Override public boolean isAvailableForRandomSelection() { return false; }
        @Override public boolean isAvailableForEnchantedBookOffer() { return false; }
        @Override public boolean isAcceptableItem(ItemStack stack) { return cursed(stack); }
    };

    private SoulboundEquipment() { }

    public static boolean bound(ItemStack stack) { return !stack.isEmpty() && EnchantmentHelper.getLevel(SOULBOUND, stack) > 0; }

    private static boolean cursed(ItemStack stack) {
        return stack.isOf(Collars.CURSED) || stack.isOf(Collars.TAMING) || DrakeEquipment.isReins(stack)
                || stack.getItem() instanceof DrakeShoesItem
                || stack.isOf(DrakeEquipment.SADDLE) || EnchantmentHelper.get(stack).keySet().stream().anyMatch(Enchantment::isCursed);
    }

    public static void enchant(ItemStack stack) {
        if (!stack.isEmpty() && cursed(stack) && !bound(stack)) stack.addEnchantment(SOULBOUND, 1);
    }

    public static void enchant(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) enchant(player.getInventory().getStack(i));
        for (var slot : CollarSlots.includingLegacy(player)) enchant(slot.get(player));
        for (var item : new DrakeAccessoryItem[]{DrakeEquipment.REINS, DrakeEquipment.SADDLE,
                DrakeEquipment.RIDERS_CHEST, DrakeEquipment.CLAW_TIPS, DrakeEquipment.SHOES})
            for (var stack : DrakeEquipment.stacks(player, item)) enchant(stack);
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void register() {
        Registry.register(Registries.ENCHANTMENT, EarthenDrake.id("soulbound"), SOULBOUND);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets"))
            dev.emi.trinkets.api.event.TrinketDropCallback.EVENT.register((rule, stack, ref, entity) ->
                    bound(stack) ? dev.emi.trinkets.api.TrinketEnums.DropRule.KEEP : rule);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, player, alive) -> {
            if (alive || oldPlayer.isSpectator() || oldPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;
            for (int i = 0; i < oldPlayer.getInventory().size(); i++) {
                var stack = oldPlayer.getInventory().getStack(i);
                if (!bound(stack)) continue;
                if (player.getInventory().getStack(i).isEmpty()) player.getInventory().setStack(i, stack.copy());
                else if (!ItemStack.areEqual(player.getInventory().getStack(i), stack)) player.getInventory().offerOrDrop(stack.copy());
                oldPlayer.getInventory().setStack(i, ItemStack.EMPTY);
            }
        });
    }
}
