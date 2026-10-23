package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.statue.StatueCrates;

/** /clear deletes items; ordinary inventory transfer and save/load do not use this method. */
@Mixin(PlayerInventory.class)
public abstract class StatueCrateClearMixin {
    @WrapOperation(method = "remove(Ljava/util/function/Predicate;ILnet/minecraft/inventory/Inventory;)I", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/inventory/Inventories;remove(Lnet/minecraft/inventory/Inventory;Ljava/util/function/Predicate;IZ)I"))
    private int statueCrateCleared(Inventory inventory, Predicate<ItemStack> predicate, int count, boolean dryRun, Operation<Integer> original) {
        var before = new java.util.ArrayList<ItemStack>();
        if (!dryRun) for (int i = 0; i < inventory.size(); i++) before.add(inventory.getStack(i).copy());
        int removed = original.call(inventory, predicate, count, dryRun);
        if (removed > 0 && !dryRun) for (int i = 0; i < before.size(); i++)
            if (inventory.getStack(i).getCount() < before.get(i).getCount()) StatueCrates.destroyed(before.get(i), ((PlayerInventory)(Object)this).player.getServer());
        return removed;
    }
    @WrapOperation(method = "remove(Ljava/util/function/Predicate;ILnet/minecraft/inventory/Inventory;)I", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/inventory/Inventories;remove(Lnet/minecraft/item/ItemStack;Ljava/util/function/Predicate;IZ)I"))
    private int statueCursorCleared(ItemStack stack, Predicate<ItemStack> predicate, int count, boolean dryRun, Operation<Integer> original) {
        var before = stack.copy(); int removed = original.call(stack, predicate, count, dryRun);
        if (removed > 0 && !dryRun) StatueCrates.destroyed(before, ((PlayerInventory)(Object)this).player.getServer());
        return removed;
    }
}
