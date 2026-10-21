package sscextras.collar;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared server-side accessory fitting; features own eligibility, callbacks and presentation. */
public final class AccessoryEquipment {
    private AccessoryEquipment() { }

    public record Fit(int index, ItemStack stack, boolean changed) { }

    public static AccessoryUtils.AccessoryIO slots() {
        return CuriosCompat.instance != null ? CuriosCompat.instance : AccessoryUtils.nowAccessoryMod;
    }

    public static Fit forceEquip(PlayerEntity player, String group, String slot, ItemStack template) {
        return forceEquip(player, group, slot, template, stack -> stack.isOf(template.getItem()),
                player.getInventory()::offerOrDrop);
    }

    /** Returns the existing/new fit, or null without consuming the template or displacing an item on failure. */
    public static Fit forceEquip(PlayerEntity player, String group, String slot, ItemStack template,
                                 Predicate<ItemStack> matches, Consumer<ItemStack> displaced) {
        if (!canChange(player) || template.isEmpty()) return null;
        var io = slots();
        if (io == null) return null;
        var stacks = io.getEntitySlot(player, group, slot);
        if (stacks == null || stacks.isEmpty()) return null;
        int empty = -1;
        for (int i = 0; i < stacks.size(); i++) {
            var stack = stacks.get(i);
            if (stack == null) return null;
            if (!stack.isEmpty() && matches.test(stack)) return new Fit(i, stack, false);
            if (stack.isEmpty() && empty < 0) empty = i;
        }
        int index = empty >= 0 ? empty : 0;
        if (!replace(player, io, group, slot, index, template.copyWithCount(1), displaced)) return null;
        return new Fit(index, io.getEntitySlot(player, group, slot, index), true);
    }

    /** Also accepts a legacy backend when its owning feature explicitly requests migration/cleanup. */
    public static boolean clear(PlayerEntity player, AccessoryUtils.AccessoryIO io, String group,
                                String slot, int index, Consumer<ItemStack> displaced) {
        return replace(player, io, group, slot, index, ItemStack.EMPTY, displaced);
    }

    private static boolean canChange(PlayerEntity player) {
        return !player.getWorld().isClient && player.isAlive() && !player.isSpectator();
    }

    private static boolean replace(PlayerEntity player, AccessoryUtils.AccessoryIO io, String group,
                                   String slot, int index, ItemStack replacement, Consumer<ItemStack> displaced) {
        if (!canChange(player) || io == null) return false;
        var stacks = io.getEntitySlot(player, group, slot);
        if (stacks == null || index < 0 || index >= stacks.size() || stacks.get(index) == null) return false;
        var previous = stacks.get(index).copy();
        if (previous.isEmpty() && replacement.isEmpty()) return true;
        io.setEntitySlot(player, group, slot, index, replacement);
        var actual = io.getEntitySlot(player, group, slot, index);
        if (actual == null || !ItemStack.areEqual(actual, replacement)) return false;
        if (!previous.isEmpty()) displaced.accept(previous);
        player.currentScreenHandler.sendContentUpdates();
        return true;
    }
}
