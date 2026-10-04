package sscextras.effigy;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PreventItemUsePower;
import io.github.apace100.apoli.power.RestrictArmorPower;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.onixary.shapeShifterCurseFabric.additional_power.IsMorphScaleItemCondition;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import java.util.ArrayList;
import java.util.List;

public final class Infusions {
    private static final Identifier DROP_TOOL = new Identifier("shape-shifter-curse", "drop_tool_after_digging");
    private static final Identifier DROP_WEAPON = new Identifier("shape-shifter-curse", "drop_weapon_after_hit");
    private Infusions() { }

    public static InfusionInventory inventory(PlayerEntity player) { return InfusionComponents.KEY.get(player); }

    public static boolean restricted(PlayerEntity player, InfusionSlot slot, ItemStack stack) {
        if (!slot.accepts(stack)) return false;
        var form = FormAbilityManager.getForm(player);
        if (form == RegPlayerForms.ORIGINAL_SHIFTER || form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE) return false;
        var powers = PowerHolderComponent.KEY.get(player);
        if (slot.armor()) return powers.getPowers(RestrictArmorPower.class).stream()
                .anyMatch(power -> power.isActive() && !power.canEquip(stack, slot.equipment));
        if (powers.getPowers(PreventItemUsePower.class).stream().anyMatch(power -> power.isActive() && power.doesPrevent(stack))) return true;
        if (IsMorphScaleItemCondition.MSI_condition(null, stack)) return false;
        boolean dropWeapon = powers.getPowers().stream().anyMatch(power -> power.isActive() && DROP_WEAPON.equals(power.getType().getIdentifier()));
        if (slot == InfusionSlot.WEAPON) return dropWeapon && (stack.isIn(TagKey.of(RegistryKeys.ITEM,
                new Identifier("origins", "melee_weapons"))) || stack.isIn(TagKey.of(RegistryKeys.ITEM, new Identifier("origins", "tools"))));
        return stack.getItem() instanceof ToolItem tool && tool.getMaterial().getMiningLevel() >= 1
                && powers.getPowers().stream().anyMatch(power -> power.isActive() && DROP_TOOL.equals(power.getType().getIdentifier()));
    }

    public static boolean available(PlayerEntity player, InfusionSlot slot) {
        return restricted(player, slot, slot.example.getDefaultStack());
    }

    public static ItemStack active(PlayerEntity player, InfusionSlot slot) {
        if (player.isSpectator()) return ItemStack.EMPTY;
        ItemStack stack = inventory(player).getStack(slot.ordinal());
        if (stack.isEmpty() || !restricted(player, slot, stack)) return ItemStack.EMPTY;
        ItemStack real = slot.armor() ? player.getInventory().armor.get(slot.equipment.getEntitySlotId())
                : player.getInventory().main.get(player.getInventory().selectedSlot);
        return real.isEmpty() ? stack : ItemStack.EMPTY;
    }

    public static ItemStack weapon(PlayerEntity player) { return active(player, InfusionSlot.WEAPON); }

    public static ItemStack tool(PlayerEntity player, BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        for (InfusionSlot slot : InfusionSlot.values()) {
            if (slot.armor()) continue;
            ItemStack stack = active(player, slot);
            if (stack.isEmpty()) continue;
            boolean suitable = stack.isSuitableFor(state), oldSuitable = best.isSuitableFor(state);
            if (best.isEmpty() || suitable && !oldSuitable || suitable == oldSuitable
                    && miningSpeed(stack, state) > miningSpeed(best, state)) best = stack;
        }
        return best;
    }

    private static float miningSpeed(ItemStack stack, BlockState state) {
        float speed = stack.getMiningSpeedMultiplier(state);
        int efficiency = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
        return speed > 1 && efficiency > 0 ? speed + efficiency * efficiency + 1 : speed;
    }

    public static ItemStack enchantmentEquipment(LivingEntity entity, EquipmentSlot slot) {
        ItemStack actual = entity.getEquippedStack(slot);
        if (!actual.isEmpty() || !(entity instanceof PlayerEntity player)) return actual;
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            for (InfusionSlot infusion : InfusionSlot.values()) if (infusion.equipment == slot) return active(player, infusion);
        }
        return slot == EquipmentSlot.MAINHAND ? weapon(player) : actual;
    }

    public static Iterable<ItemStack> armor(PlayerEntity player, Iterable<ItemStack> original) {
        if (inventory(player).isEmpty()) return original;
        List<ItemStack> result = new ArrayList<>(4);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            result.add(enchantmentEquipment(player, slot));
        }
        return result;
    }

    public static void changed(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        var inventory = inventory(player);
        inventory.markDirty();
        inventory.refreshAttributes();
        inventory.flush();
    }

    public static ActionResult useTool(PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND || player.isSpectator() || !player.getInventory().getMainHandStack().isEmpty()) return ActionResult.PASS;
        BlockState block = player.getWorld().getBlockState(hit.getBlockPos());
        if (block.isOf(FeralEffigy.BLOCK) || block.createScreenHandlerFactory(player.getWorld(), hit.getBlockPos()) != null) return ActionResult.PASS;
        InfusionSlot[] order = player.isSneaking()
                ? new InfusionSlot[]{InfusionSlot.HOE, InfusionSlot.AXE, InfusionSlot.SHOVEL}
                : new InfusionSlot[]{InfusionSlot.AXE, InfusionSlot.SHOVEL, InfusionSlot.HOE};
        for (InfusionSlot slot : order) {
            ItemStack stack = active(player, slot);
            if (stack.isEmpty()) continue;
            try (var ignored = new InfusionHand(player, stack)) {
                ActionResult result = stack.useOnBlock(new ItemUsageContext(player, hand, hit));
                if (result != ActionResult.PASS) return result;
            } finally {
                changed(player);
            }
        }
        return ActionResult.PASS;
    }
}
