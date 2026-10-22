package sscextras.inventory;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** One server-side physical inventory search. Native inventories retain ownership of their contents. */
public final class CarriedItemSearch {
    private final ServerPlayerEntity player;
    private final Predicate<ItemStack> selected;
    private final Consumer<ItemStack> removed;
    private final Function<ItemStack, Inventory> inventories;
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    public CarriedItemSearch(ServerPlayerEntity player, Predicate<ItemStack> selected,
                             Consumer<ItemStack> removed, Function<ItemStack, Inventory> inventories) {
        this.player = player;
        this.selected = selected;
        this.removed = removed;
        this.inventories = inventories;
    }

    public void inventory(Inventory inventory) { inventory(inventory, 0); }

    private void inventory(Inventory inventory, int depth) {
        if (!visited.add(inventory)) return;
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (selected.test(stack)) collect(inventory.removeStack(i));
            else contents(stack, depth);
        }
        inventory.markDirty();
    }

    /** Searches inside a retained root; the caller removes root cursor/accessory stacks through their own API. */
    public void contents(ItemStack stack) { contents(stack, 0); }

    private void contents(ItemStack stack, int depth) {
        if (stack.isEmpty() || depth >= 64 || !visited.add(stack)) return;
        var inventory = inventories.apply(stack);
        if (inventory != null) { inventory(inventory, depth + 1); return; }
        var handler = ForgeItemHandlers.get(stack);
        if (handler != null) { handler(handler, depth + 1); return; }
        // Vanilla shulkers/bundles and mods using the same serialized ItemStack list format.
        // Only this explicit inventory layout is edited; arbitrary nested NBT is never searched.
        var tag = stack.getNbt();
        if (tag == null) return;
        if (tag.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) tag = tag.getCompound("BlockEntityTag");
        var items = tag.getList("Items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < items.size();) {
            var entry = items.getCompound(i);
            var child = ItemStack.fromNbt(entry);
            if (child.isEmpty()) { i++; continue; }
            if (selected.test(child)) {
                items.remove(i);
                collect(child);
            } else {
                contents(child, depth + 1);
                // Retain Slot and any provider metadata alongside the serialized stack.
                child.writeNbt(entry);
                i++;
            }
        }
    }

    /** Includes extra physical player slots exposed through Forge's standard item-handler capability. */
    public void playerStorage() {
        var handler = ForgeItemHandlers.get(player);
        if (handler != null) handler(handler, 0);
    }

    private boolean container(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (inventories.apply(stack) != null || ForgeItemHandlers.get(stack) != null) return true;
        var tag = stack.getNbt();
        if (tag == null) return false;
        if (tag.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) tag = tag.getCompound("BlockEntityTag");
        return tag.contains("Items", NbtElement.LIST_TYPE);
    }

    private void handler(Object handler, int depth) {
        if (depth >= 64 || !visited.add(handler)) return;
        for (int slot = 0; slot < ForgeItemHandlers.size(handler); slot++) {
            var stack = ForgeItemHandlers.stack(handler, slot);
            boolean take = selected.test(stack);
            if (!take && (visited.contains(stack) || !container(stack))) continue;
            // IItemHandler forbids mutating getStackInSlot's return value. Detach through
            // extraction before opening nested storage, then restore through insertion.
            var extracted = ForgeItemHandlers.extract(handler, slot, stack.getCount());
            if (extracted.isEmpty()) continue;
            if (take) {
                collect(extracted);
                // Some providers cap each extraction even when more was requested.
                while (selected.test(ForgeItemHandlers.stack(handler, slot))) {
                    var next = ForgeItemHandlers.extract(handler, slot, Integer.MAX_VALUE);
                    if (next.isEmpty()) break;
                    collect(next);
                }
            }
            else {
                contents(extracted, depth + 1);
                var remainder = ForgeItemHandlers.insert(handler, slot, extracted);
                if (!remainder.isEmpty()) player.getInventory().offerOrDrop(remainder);
            }
        }
    }

    private void collect(ItemStack stack) { if (!stack.isEmpty()) removed.accept(stack); }
}
