package sscextras.collar;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem.SlotData;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import java.util.ArrayList;
import java.util.List;

public final class CollarSlots {
    private CollarSlots() { }

    public record Slot(AccessoryUtils.AccessoryIO io, String group, int index, ItemStack stack) {
        public ItemStack get(PlayerEntity player) { return io.getEntitySlot(player, group, "necklace", index); }
        public void set(PlayerEntity player, ItemStack value) { io.setEntitySlot(player, group, "necklace", index, value); }
        public boolean visible(PlayerEntity player) {
            return !(io instanceof CuriosCompat curios) || curios.visible(player, "necklace", index);
        }
    }

    public static List<Slot> get(PlayerEntity player) {
        List<Slot> result = new ArrayList<>();
        if (CuriosCompat.instance != null) {
            append(result, CuriosCompat.instance, player, "");
        } else if (AccessoryUtils.nowAccessoryMod != null) {
            append(result, AccessoryUtils.nowAccessoryMod, player, "chest");
        }
        return result;
    }

    public static List<Slot> includingLegacy(PlayerEntity player) {
        List<Slot> result = get(player);
        result.addAll(legacy(player));
        return result;
    }

    private static List<Slot> legacy(PlayerEntity player) {
        List<Slot> result = new ArrayList<>();
        var trinkets = AccessoryUtils.activeAccessoryModInterfaces.get("trinkets");
        if (CuriosCompat.instance != null && trinkets != null) append(result, trinkets, player, "chest");
        return result;
    }

    public static void recoverLegacy(ServerPlayerEntity player) {
        if (CuriosCompat.instance == null) return;
        List<Slot> active = get(player);
        if (active.isEmpty()) return;
        for (Slot source : legacy(player)) {
            ItemStack stack = source.get(player);
            if (stack == null || !(stack.getItem() instanceof CollarItem)) continue;
            // Compatibility layers may already expose the same inventory through both APIs.
            if (active.stream().anyMatch(slot -> slot.get(player) == stack)) continue;
            Slot destination = active.stream().allMatch(slot -> slot.get(player).isEmpty()) ? active.get(0) : null;
            ItemStack moved = stack.copy();
            source.set(player, ItemStack.EMPTY);
            if (!source.get(player).isEmpty()) continue;
            if (destination == null) {
                player.getInventory().offerOrDrop(moved);
            } else {
                destination.set(player, moved);
                if (!ItemStack.areEqual(destination.get(player), moved)) source.set(player, stack);
            }
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    public static boolean isActive(SlotData slot) {
        return CuriosCompat.instance == null || slot.slot().getNamespace().equals("curios");
    }

    private static void append(List<Slot> result, AccessoryUtils.AccessoryIO io, PlayerEntity player, String group) {
        List<ItemStack> stacks = io.getEntitySlot(player, group, "necklace");
        if (stacks == null) return;
        for (int i = 0; i < stacks.size(); i++) result.add(new Slot(io, group, i, stacks.get(i)));
    }

    public static ItemStack visibleCollar(PlayerEntity player) {
        ItemStack best = ItemStack.EMPTY;
        for (Slot slot : get(player)) {
            if (slot.stack().getItem() instanceof CollarItem item && slot.visible(player)
                    && (best.isEmpty() || item.strength() > ((CollarItem) best.getItem()).strength())) best = slot.stack();
        }
        return best;
    }
}
