package sscextras.drake;

import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import java.util.List;
import sscextras.collar.CuriosCompat;

public final class DrakeShoes {
    public static final int PAW_TICKS = 100, RITUAL_TICKS = 4 * PAW_TICKS;
    private DrakeShoes() { }

    public static List<ItemStack> stacks(PlayerEntity player, boolean feet) {
        var io = DrakeEquipment.slots();
        if (io == null) return List.of();
        var result = io.getEntitySlot(player, CuriosCompat.instance == null ? feet ? "feet" : "hand" : "",
                CuriosCompat.instance == null ? feet ? "shoes" : "glove" : feet ? "feet" : "hands");
        return result == null ? List.of() : result;
    }
    public static boolean hands(PlayerEntity player) { return !player.isSpectator() && stacks(player, false).stream().anyMatch(s -> s.isOf(DrakeEquipment.SHOES)); }
    public static boolean feet(PlayerEntity player) { return !player.isSpectator() && stacks(player, true).stream().anyMatch(s -> s.isOf(DrakeEquipment.SHOES)); }
    public static boolean visible(PlayerEntity player, boolean feet) {
        var stacks = stacks(player, feet);
        for (int i = 0; i < stacks.size(); i++) if (stacks.get(i).isOf(DrakeEquipment.SHOES)
                && (CuriosCompat.instance == null || CuriosCompat.instance.visible(player, feet ? "feet" : "hands", i))) return true;
        return false;
    }
    public static boolean fullyEquipped(PlayerEntity player) { return hands(player) && feet(player); }
    public static boolean restricted(PlayerEntity player) { return !player.isCreative() && !player.isSpectator() && hands(player); }
    public static boolean consumable(ItemStack stack) {
        return stack.getUseAction() == UseAction.EAT || stack.getUseAction() == UseAction.DRINK;
    }
    public static boolean canUse(PlayerEntity player, Hand hand) {
        return !restricted(player) || hand == Hand.MAIN_HAND && consumable(player.getStackInHand(hand));
    }

    public static ActionResult useBlock(PlayerEntity player, BlockPos pos) {
        if (!restricted(player)) return ActionResult.PASS;
        if (!DrakeSoulbinding.restrained(player) && player.getWorld().getBlockState(pos).isOf(Blocks.HAY_BLOCK)) {
            if (player instanceof ServerPlayerEntity serverPlayer) DrakeHaySleep.sleep(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    public static boolean equipPair(PlayerEntity player, boolean feet) {
        if (feet ? feet(player) : hands(player)) return true;
        var io = DrakeEquipment.slots();
        var stacks = stacks(player, feet);
        if (io == null || stacks.isEmpty()) return false;
        if (stacks.stream().noneMatch(ItemStack::isEmpty)) {
            String group = CuriosCompat.instance == null ? feet ? "feet" : "hand" : "";
            String slot = CuriosCompat.instance == null ? feet ? "shoes" : "glove" : feet ? "feet" : "hands";
            var displaced = stacks.get(0).copy();
            io.setEntitySlot(player, group, slot, 0, ItemStack.EMPTY);
            if (!io.getEntitySlot(player, group, slot, 0).isEmpty()) return false;
            player.getInventory().offerOrDrop(displaced);
        }
        return tryEquip(player, DrakeEquipment.SHOES.getDefaultStack(), feet, false);
    }

    public static boolean tryEquip(PlayerEntity player, ItemStack source, boolean feet, boolean consume) {
        if (!player.isAlive() || player.isSpectator() || !source.isOf(DrakeEquipment.SHOES)) return false;
        var io = DrakeEquipment.slots();
        if (io == null) return false;
        String group = CuriosCompat.instance == null ? feet ? "feet" : "hand" : "";
        String slot = CuriosCompat.instance == null ? feet ? "shoes" : "glove" : feet ? "feet" : "hands";
        var stacks = stacks(player, feet);
        for (int index = 0; index < stacks.size(); index++) {
            if (!stacks.get(index).isEmpty()) continue;
            if (player.getWorld().isClient) return true;
            var copy = source.copyWithCount(1);
            DrakeShoesItem.bind(copy);
            io.setEntitySlot(player, group, slot, index, copy);
            var actual = io.getEntitySlot(player, group, slot, index);
            if (actual == null || !ItemStack.areEqual(actual, copy)) return false;
            DrakeEquipment.SHOES.onEquip(actual, player, new AccessoryItem.SlotData(new Identifier(
                    CuriosCompat.instance == null ? "trinkets" : "curios", CuriosCompat.instance == null ? group + "/" + slot : slot), index));
            if (consume) source.decrement(1);
            player.currentScreenHandler.sendContentUpdates();
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1, 1);
            return true;
        }
        return false;
    }

    static void tick(PlayerEntity player, ItemStack stack) {
        if (!player.isCreative() && !player.isSpectator() && player.isInLava()) {
            breakSound(player);
            player.incrementStat(Stats.BROKEN.getOrCreateStat(stack.getItem()));
            stack.decrement(1);
            return;
        }
        DrakeShoesItem.bind(stack);
        if (DrakeSoulbinding.bound(player)) SoulboundEquipment.enchant(stack);
        if (hands(player) && !player.getOffHandStack().isEmpty()) {
            var offhand = player.getOffHandStack();
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().offerOrDrop(offhand);
        }
    }

    public static void damage(PlayerEntity player, float amount) {
        if (player.getWorld().isClient || player.isCreative() || player.isSpectator() || amount <= 0) return;
        int wear = Math.max(1, (int)(amount / 4));
        for (var stack : DrakeEquipment.stacks(player, DrakeEquipment.SHOES)) {
            if (stack.isOf(DrakeEquipment.SHOES)) stack.damage(wear, player, DrakeShoes::breakSound);
        }
    }

    private static void breakSound(PlayerEntity player) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1, 1);
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                restricted(player) || DrakeSoulbinding.restrained(player) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
                !restricted(player) && !DrakeSoulbinding.restrained(player));
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> DrakeStableMaintenance.caughtBreaking(player, pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (DrakeSoulbinding.restrained(player)) return ActionResult.FAIL;
            return useBlock(player, hit.getBlockPos());
        });
        UseItemCallback.EVENT.register((player, world, hand) -> !canUse(player, hand) || DrakeSoulbinding.restrained(player)
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                DrakeSoulbinding.restrained(player) || restricted(player) && !player.getMainHandStack().isEmpty()
                        ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                DrakeSoulbinding.restrained(player) || restricted(player) && !player.getStackInHand(hand).isEmpty()
                        ? ActionResult.FAIL : ActionResult.PASS);
    }
}
