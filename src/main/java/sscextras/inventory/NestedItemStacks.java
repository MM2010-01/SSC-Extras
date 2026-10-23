package sscextras.inventory;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;

/** Read-only traversal of native serialized containers; callbacks must not edit decoded copies. */
public final class NestedItemStacks {
    private NestedItemStacks() { }
    public static void visit(ItemStack stack, Predicate<ItemStack> match, Consumer<ItemStack> callback, int depth) {
        if (stack.isEmpty() || depth > 16 || !stack.hasNbt()) return;
        if (match.test(stack)) { callback.accept(stack); return; }
        var tag = stack.getNbt();
        if (tag.contains("BlockEntityTag")) tag = tag.getCompound("BlockEntityTag");
        var items = tag.getList("Items", NbtElement.COMPOUND_TYPE);
        if (depth == 16) return;
        for (int i = 0; i < items.size(); i++) {
            var item = items.getCompound(i);
            if (item.contains("tag", NbtElement.COMPOUND_TYPE)) visit(ItemStack.fromNbt(item), match, callback, depth + 1);
        }
    }
}
